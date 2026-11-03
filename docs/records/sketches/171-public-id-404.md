---
issue: 171
sources: [이슈 #171, features/01-둘러보기.md, domain/도메인-모델.md, engineering/API-공통-규칙.md]
features: [01-둘러보기]
ids: [R-POLL-21, P-01]
decisions: []
status: 완료
---

# 공개 ID 형식 오류를 404 POLL_NOT_FOUND로 통일한다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.
> `features`에 적은 기능 페이지의 이력 표에도 이 문서 링크를 단다(양쪽을 맞춘다).

## 요구사항과 규칙

왜 하나: 이슈 #171. 사용자에게는 형식이 틀린 ID와 없는 ID가 모두 "없는 투표"다. 같은 형식 검사가 `PollReader`의 세 메서드에 중복되어 있는데, 응답이 같아지면 검사가 할 일이 없다.

소스와 달라진 점: `features/01-둘러보기.md`의 "형식이 틀리면 거절(400 `MALFORMED_REQUEST`)"을 바꾼다. 사용자가 정했다(2026-11-02). 근거는 보안이 아니라 단순함이다. 형식은 공유 링크만 봐도 알 수 있어 숨겨서 얻는 것이 없다.

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| 투표는 12자 영숫자의 공개 ID로 가리키고, 내부 번호는 밖에 보이지 않는다. 형식이 틀린 ID는 없는 투표와 같게 답한다(R-POLL-21 변경) | 거절(404 `POLL_NOT_FOUND`) | `PublicIdTest`, `PollDetailChainTest`, `PollQueryServiceTest` |

바뀌지 않는 것: 커서·`size`·`category`·`status`·본문 형식 오류와 신고 ID·회원 ID 같은 숫자 경로 변수 형식 오류는 400 `MALFORMED_REQUEST`다. `PublicId` 생성자의 400 `VALIDATION_FAILED`도 그대로다.

## 건드리는 것

- 바꾼다: `PollReader.getReadable`, `getPublic`, `find(String)`에서 형식 검사를 지운다. `PublicId.isValid`는 `PublicId` 생성자가 쓰므로 남기고 낡은 javadoc만 고친다. 낡은 "형식 오류 400" 설명이 있는 `PollReader`의 javadoc과 `VoteCommandService.castVote`의 javadoc도 함께 고친다
- 스웨거 설명: `PollWebApiDocs`(상세, 삭제), `VoteWebApiDocs`, `ReportWebApiDocs`, `AdminPollWebApiDocs`. 400 설명에서 공개 ID 형식 오류를 빼고 404 설명에 넣는다. 400이 더는 없는 API는 400 응답을 지운다
- 문서: `features/01-둘러보기.md` 40행(근거·테스트 칸의 테스트 이름도 새 404 테스트로 바꾼다)과 이력 표, `domain/도메인-모델.md`의 R-POLL-21 문장(형식 오류 응답을 적는 경우)
- 생성 문서: `API-목록.md`, `openapi.json`은 다시 만든다
- 건드리지 않는다: `PollReader` 메서드 이름(#174), DB, 전역 예외 처리, 삭제된 ID의 404 규칙

## 접근

고른 안: `PollReader`의 세 메서드에서 형식 검사를 지우고 DB 조회 결과만으로 판단한다. 형식이 틀린 ID는 없는 ID와 똑같이 조회 결과가 비어 기존 `orElseThrow(POLL_NOT_FOUND)`로 이어진다. 검사가 응답을 가르지 못하게 되어 남길 이유가 없다.

검토한 대안:

- 형식 검사를 헬퍼 하나로 합쳐 남기고 틀리면 DB를 조회하지 않는다: 처음 고른 안이었다. 얻는 것이 불필요한 쿼리 절약뿐이라 사용자가 뺐다(2026-11-03).
- 400을 유지한다: 나눠서 얻는 사용처가 없다. 정본의 400 설명 외에 400을 필요로 하는 근거를 찾지 못했다.

오류 우선순위는 지금 순서를 유지한다. 삭제는 회원 상태(401·403)가 먼저이고, 관리자 조치는 관리자 권한(403)이 먼저이며, 본문 형식 오류와 `@Valid` 실패는 컨트롤러 진입 전에 걸려 400이 먼저다.

참여·신고는 투표 조회(404)가 회원 상태 확인(401·403)보다 먼저이고 이 순서를 유지한다. 그래서 정지 회원이 형식이 틀린 ID로 요청하면 지금의 400 대신 404를 받는다.

질문과 답:

- 공개 ID 형식 오류를 400에서 404로 통일하는가? 그렇다(사용자, 2026-11-02).
- 형식 검사를 남기는가? 남기지 않는다(사용자, 2026-11-03). 검사가 DB 조회를 아끼는 것 말고는 하는 일이 없다.
- 알려진 한계: DB 비교는 MySQL 기본 collation(`_ci`)이라 대소문자를 구분하지 않을 수 있고(소문자로 바꾼 공개 ID로도 같은 투표가 열린다), 악센트가 섞인 12자 문자열이 정상 ID와 같다고 비교될 수도 있다. 형식 검사로 막을 수 있는 것은 악센트뿐이었고 collation 설정은 확인하지 못했다. 이번 범위 밖이며 필요하면 `public_id`를 `_bin` collation으로 바꾸는 별도 이슈로 다룬다.

## 구조

- 구현: `poll/application/PollReader.java`, `poll/domain/PublicId.java`, 위 `*WebApiDocs`
- 구현 클래스 통합 테스트(`PollQueryServiceTest`, H2): `getReadable`·`getPublic`·`find(String)`의 형식 오류 404
- 서비스 통합 테스트(`VoteCommandServiceTest`): 참여의 형식 오류 404
- MockMvc 체인 테스트(`PollDetailChainTest`, `ReportSubmitChainTest`, `AdminReportActionChainTest`): 실제 HTTP 응답. `AdminReportActionChainTest`의 파라미터 테스트는 투표 경로만 404로 분리하고 `reports/abc`, `members/abc`는 400으로 남긴다

## 순서와 테스트

먼저 쓸 테스트(red):

1. `PollQueryServiceTest`: `형식이_틀린_공개_ID는_getPublic에서_404_POLL_NOT_FOUND다`, `getReadable`과 `find(String)`도 같다.
2. `VoteCommandServiceTest`: `공개_ID_형식이_틀리면_POLL_NOT_FOUND다`
3. `PollDetailChainTest`, `ReportSubmitChainTest`: `형식이_틀린_투표_ID는_404_POLL_NOT_FOUND다`
4. `AdminReportActionChainTest`: 투표 ID 형식 오류는 404이고 조치 이력이 없다. 숫자 ID 형식 오류는 400 그대로다.
5. 작성자 삭제 API의 형식 오류 404를 체인 테스트로 확인한다. 기존에 없으면 추가한다.

그다음 `PollReader`를 고치고 스웨거 설명을 바꾼 뒤 생성 문서를 다시 만들어 `./gradlew build`로 확인한다.

## 지킬 규칙

- P-01: 숨김·삭제 투표는 작성자·관리자가 아니면 404다. 응답 본문을 형식 오류와 구분하지 않는다.
- P-30: 관리자 API는 권한이 없으면 404가 아니라 403이다. 권한 확인이 먼저라 그대로다.
- 생성 문서는 직접 고치지 않는다(`docs/generated/README.md`).

## 안전장치

- 범위 넘침: `MALFORMED_REQUEST`를 모두 바꾸지 않도록 공개 ID를 받는 경로만 바꾼다. `grep`으로 400을 단언하는 공개 ID 테스트를 전부 찾는다(이슈 본문 수를 믿지 않는다, `pitfalls.md`).
- 형식이 아주 긴 입력: 컬럼 길이(36)보다 긴 문자열로 조회해도 오류 없이 빈 결과가 나오는지 `PollQueryServiceTest`의 13자 값으로 확인한다.
- 프런트: 400 분기가 있으면 바꿔야 한다. 프런트 저장소는 이 레포에 없어 확인할 수 없으므로 PR에 적는다.

## 결과

공개 ID 형식 오류를 400 `MALFORMED_REQUEST`에서 없는 투표와 같은 404 `POLL_NOT_FOUND`로 통일했다. 상세 조회, 작성자 삭제, 참여, 신고, 관리자 숨김·삭제가 모두 해당한다. `PollReader`의 세 메서드에서 형식 검사를 지웠고 조회 결과가 비면 기존 404 경로로 이어진다. 상세와 삭제 API는 400 응답 자체를 스웨거에서 지웠고, 나머지는 본문 형식 오류 등 다른 400 사유가 남아 설명만 고쳤다. 생성 문서를 다시 만들었고 정본은 `01-둘러보기.md`와 `설계-결정.md`를 고쳤다. 테스트는 새 404 단언 29개를 포함해 전체가 통과한다(`./gradlew build`).

계획과 달라진 점:

- 처음에는 형식 검사를 private 헬퍼로 합쳐 남기고 틀리면 DB를 조회하지 않기로 했다. 검사가 쿼리 절약 말고는 하는 일이 없다는 지적(사용자, 2026-11-03)으로 검사를 모두 지우고 sketch를 다시 승인받았다.
- `PublicId.isValid`가 패키지 전용으로 좁혀졌다. 호출처가 `PublicId` 생성자뿐이다.
- 리뷰 지적으로 `PollChoice` javadoc, 테스트 값 정리, 40자 입력 케이스를 더했다. `src/main`은 주석 한 줄이라 재리뷰는 생략했다.

한계와 후속:

- 40자 입력(컬럼 길이 36 초과)이 MySQL에서 오류 없이 빈 결과로 끝나는지는 H2 테스트만 있어 확인하지 못했다.
- MySQL 기본 collation(`_ci`)이면 대소문자나 악센트가 다른 공개 ID가 같은 투표로 열릴 수 있다. 이번 변경 전부터의 문제이고 collation 설정은 확인하지 못했다. 필요하면 `public_id`를 `_bin`으로 바꾸는 별도 이슈로 다룬다.
- 프런트에 400 분기가 있으면 바꿔야 한다. 이 레포에서 확인할 수 없다.
- 저장소 미조회를 직접 검증하는 테스트는 없다. 이제 검사 자체가 없어 해당하지 않는다.
- `isValid` 접근 범위와 `get`/`find` 이름 규칙은 #174에서 이어서 다룬다.
