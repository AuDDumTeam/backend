---
issue: 76
sources: [이슈 #76, features/02-투표-만들기.md, features/07-로그인과-회원.md, features/06-관리자-조치.md, domain/도메인-모델.md]
features: [02-투표-만들기]
ids: [UC-17, OP-08, P-01, P-02, P-05, P-29, P-30, R-POLL-06, R-POLL-09, R-POLL-13, R-POLL-16, R-POLL-17, R-MEMBER-11]
decisions: []
status: 완료
---

# 작성자가 자기 투표를 삭제하는 API를 만든다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.
> `features`에 적은 기능 페이지의 이력 표에도 이 문서 링크를 단다(양쪽을 맞춘다).
> `sources`는 이 작업의 근거다(이슈, 정본 문서, 결정 기록). `status`는 `초안` → `승인됨`(사람이 승인한 뒤 구현 시작) → `완료`(PR 전에 결과를 채운 뒤)다.

## 요구사항과 규칙

정본은 작성자 삭제(UC-17)를 이미 정해 두었고 도메인(`Poll.deleteByAuthor`)과 그 테스트(`PollTest`)도 있지만, 이 삭제를 부르는 서비스와 `DELETE /api/v1/polls/{pollId}`가 없다. 이슈 #76이 그 연결을 만든다.

소스와 달라진 점: 없음. 이슈 본문의 응답 규칙을 정본(R-POLL 표, P-05)에 맞춰 이슈 쪽을 고쳤다(낡은 문서 참조 제거).

새 규칙은 없다. 정본에 있는 규칙을 API로 노출한다. 아래는 이 API가 지켜야 하는 정본 규칙이고, 표 오른쪽 칸이 이번에 새로 쓰는 테스트다.

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| 작성자 본인이 공개 투표를 삭제하면 `DELETED`가 되고 204로 응답한다. 투표와 참여 기록은 남고, 삭제 시각은 `updated_at`이다(R-POLL-13) | — | P-02 · 새 `PollCommandServiceTest`, `PollDeleteWebApiTest` |
| 작성자가 아닌 사람의 삭제는 공개 투표면 403 `FORBIDDEN`, 숨김·삭제 투표면 404 `POLL_NOT_FOUND`(R-POLL-17) | 거절 | P-01 · 같은 테스트 |
| 관리자가 숨긴 투표는 작성자도 삭제할 수 없다(R-POLL-09) | 거절(403 `POLL_NOT_DELETABLE`) | P-30 · 같은 테스트 |
| 이미 삭제된 투표는 404다(R-POLL-16) | 거절(404 `POLL_NOT_FOUND`) | P-02 · 같은 테스트 |
| 정지 회원은 삭제할 수 없고, 탈퇴한 회원은 인증이 안 된다(R-MEMBER-11) | 거절(정지 403 `MEMBER_SUSPENDED`, 탈퇴 401 `UNAUTHENTICATED`) | P-05 · 같은 테스트 |
| 형식이 틀린 공개 ID는 400, 없는 투표는 404다 | 거절(400 `MALFORMED_REQUEST`, 404 `POLL_NOT_FOUND`) | 기존 `PollReader.find` 동작 · 같은 테스트 |
| 로그인하지 않은 요청은 거절한다 | 거절(401 `UNAUTHENTICATED`) | 기존 `@CurrentMemberId` 동작 · 같은 테스트 |

## 건드리는 것

- 바뀌는 것: `PollCommandService`에 삭제 메서드, `PollWebApi`·`PollWebApiDocs`에 `DELETE /{pollId}`, 생성 문서(`docs/generated/API-목록.md`, `openapi.json`).
- 바뀌는 문서(wrap): `02-투표-만들기.md`(사용하는 API 표의 "구현 전" 문구, 이력 표, 테스트 이름 칸), UC-17의 기록 문구(삭제 시각은 `updated_at`), `설계-결정.md` 4절 알려진 한계(경합 시 마지막 쓰기), 로드맵 M5의 #76 체크박스.
- 건드리지 않는 것: `Poll.deleteByAuthor`(이미 정본과 같다), 관리자 조치, 신고 도메인, DB 스키마(Q1: `updated_at`을 쓰므로 변경 없음), 신고 처리(Q3: 그대로 둠), 참여 API.

## 접근

도메인은 이미 있으므로 `PollCommandService.deleteByAuthor(memberId, pollPublicId)`가 `memberReader.getActiveMember(memberId)` → `pollReader.find(publicId)` → `poll.deleteByAuthor(memberId)`를 한 트랜잭션에서 부른다. 컨트롤러는 `@CurrentMemberId`로 작성자를 얻고 `204 No Content`를 돌려준다.

- 회원 상태를 투표 조회보다 먼저 확인한다. 이유: `createPoll`이 같은 순서이고, P-05가 "정지 회원은 모든 쓰기 불가"라서 정지 회원은 투표 존재 여부와 상관없이 403이다(R-MEMBER-11).
- 성공 응답은 204다. 이유: 같은 종류의 쓰기 응답(`AdminPollWebApi.takedownPoll`, 탈퇴, 로그아웃)이 모두 204다(코드).
- 투표를 `pollReader.find`로 읽는다(노출 상태와 상관없이). 이유: 작성자 판정과 404/403 구분은 `Poll.deleteByAuthor`가 맡는다(R-POLL-17).

검토한 대안:
- `getReadable`로 읽기: 숨김 투표를 작성자만 읽게 걸러 주지만, 관리자가 읽을 수 있게 되어 "작성자 아님" 판정이 두 곳에 갈린다. 도메인 메서드가 한 곳에서 맡도록 `find`를 고른다.
- 컨트롤러가 도메인을 직접 호출하기: 트랜잭션 경계와 회원 상태 확인이 서비스에 있어야 해서 고르지 않는다.

질문과 답:

- **Q1. UC-17의 "삭제한 주체와 시각을 기록한다"를 어디에 남기나?** 작성자 삭제의 주체는 항상 작성자 본인이라 `author_id`와 같아서 따로 기록하지 않는다. 시각은 `poll.updated_at`을 쓴다. 투표는 수정할 수 없고 삭제된 투표는 관리자 조치도 막혀(`validateNotDeleted`) 삭제 뒤 행이 바뀌지 않으므로 `updated_at`이 삭제 시각이다(관리자 조치와 겹치는 경합 때는 예외이고 Q2의 한계에 포함된다). 새 컬럼과 마이그레이션은 없다. 관리자 삭제는 기존대로 `AdminAction`에 처리자·시각이 남는다. 답: (a). 주체 기록은 두지 않는다. 출처: 사용자, 2026-11-02. 정본의 "삭제한 주체와 시각을 기록한다"는 wrap에서 "삭제 시각은 `updated_at`이다(관리자 조치와 겹친 경우는 제외)"로 고친다.
- **Q2. 작성자 삭제와 관리자 숨김·삭제가 동시에 오면?** 마지막 쓰기가 이기는 것으로 둔다. 겹치는 일이 드물고, `설계-결정.md`가 투표 행 잠금을 쓰지 않기로 한 결정(#19)과 같은 수준이다. 이번에 잠금을 넣지 않고, 작성자 삭제 뒤 늦게 커밋된 관리자 숨김이 `DELETED`를 `HIDDEN`으로 되돌릴 수 있는 한계를 `설계-결정.md` 4절 "알려진 한계"에 wrap에서 올린다. 출처: 사용자, 2026-11-02.
- **Q3. 작성자 삭제 시 접수 중인 신고는?** 건드리지 않고 대기로 둔다. 관리자가 신고 목록에서 기각해서 닫는다. 관리자의 `AdminReportCommandService.dismiss`는 신고만 읽고 투표 상태를 보지 않아서 삭제된 투표의 신고도 기각할 수 있음을 코드로 확인했다(신고가 영원히 안 닫히지 않는다). 출처: 사용자, 2026-11-02 / 코드 확인.

## 구조

- `poll/application/PollCommandService.java`: `deleteByAuthor` 추가.
- `poll/api/PollWebApi.java`, `PollWebApiDocs.java`: `DELETE /{pollId}`와 응답 코드 문서(204, 400, 401, 403, 404).
- 테스트: `PollCommandServiceTest`에 서비스 규칙, `poll/api/PollDeleteWebApiTest`(신규)에 API 연결, 필요하면 기존 `HiddenPollViewChainTest`에 삭제 뒤 조회 사슬. 도메인 규칙 테스트는 `PollTest`에 이미 있어서 새로 쓰지 않는다.

## 순서와 테스트

1. (red) 서비스 테스트: 작성자 삭제 성공, 비작성자(공개 403·숨김 404), 숨김 투표 작성자 403 `POLL_NOT_DELETABLE`, 이미 삭제 404(두 번째 삭제), 정지 403, 정지 회원이 없는 투표를 삭제해도 403(회원 상태가 먼저), 탈퇴 401.
2. (red) API 테스트: 204, 인증 없음 401, 형식 오류 400, 없는 투표 404, 삭제 뒤 일반 사용자 조회 404와 작성자 조회 가능, 참여 기록 보존.
3. 구현(서비스, 컨트롤러, 문서 애너테이션) → green.
4. `./gradlew build`로 생성 문서 비교까지 통과.

## 지킬 규칙

- R-POLL-06·09·13·16·17, P-01·02·05·29·30, R-MEMBER-11.
- `docs/engineering/컨벤션.md`: 테스트 메서드 이름은 한글, `@DisplayName` 금지, 계층 의존 방향(ArchUnit).
- 작성자는 요청 본문이 아니라 로그인 정보(`@CurrentMemberId`)로만 판단한다(UC-17).

## 안전장치

- **다른 사람의 투표를 지우는 일**: 작성자 판정은 도메인(`isAuthor`)이 맡고, 서비스 테스트에 비작성자 케이스를 둔다.
- **존재 노출**: 숨김·삭제 투표의 비작성자에게는 403이 아니라 404로 답한다(R-POLL-17).
- **동시 요청**: 마지막 쓰기가 이긴다(Q2). 잠금은 넣지 않고 한계로 기록한다.
- **삭제 직전에 도착한 참여**: 삭제와 참여는 서로 다른 트랜잭션이라 P-29대로 먼저 커밋된 쪽이 반영된다. 이 순서를 보장하는 별도 코드는 없다.

## 결과

`DELETE /api/v1/polls/{pollId}`를 만들었다. `PollCommandService.deleteByAuthor`가 회원 상태 확인(`getActiveMember`) → `pollReader.find` → `Poll.deleteByAuthor` 순으로 한 트랜잭션에서 처리하고, `PollWebApi.deletePoll`이 204로 응답한다. 도메인 규칙은 이미 있어서 새로 쓰지 않았다. 서비스 테스트 10개(규칙 전부와 삭제 뒤 참여 보존)와 슬라이스 테스트 1개를 썼고, 생성 문서(`API-목록.md` 15개, `openapi.json`)를 다시 만들었다. 정본은 `02-투표-만들기.md`(UC-17 문구, 테스트 칸, API 표, 이력), `설계-결정.md` 4절(경합 한계), 로드맵에 반영했다. 리뷰: sketch-reviewer r1 FAIL → r2 PASS_WITH_FIXES, domain-reviewer·code-reviewer PASS_WITH_FIXES(반영), fact-verifier r1은 잘못된 브랜치 입력으로 FAIL → r2 PASS_WITH_FIXES.

계획과 달라진 점:
- API 테스트: 계획한 신규 `PollDeleteWebApiTest`(체인 성격) 대신 기존 `PollWebApiTest`에 슬라이스 테스트 1개만 넣었다. `테스트-전략.md`의 계층 구분(controller 슬라이스는 매핑·인자·응답 모양만, 오류 매핑·입력 검증은 안 본다)과 사용자 결정(2026-11-02)에 따라 체인 테스트와 400·404·401 API 테스트는 두지 않았다. 해당 규칙은 서비스 테스트(실제 DB)와 기존 `PollReader`·`@CurrentMemberId` 동작이 확인한다. 위 규칙 표의 "새 `PollDeleteWebApiTest`"와 구조 칸의 해당 파일은 이 결정으로 쓰지 않았다.
- 형식 오류 400, 없는 투표 404, 비로그인 401은 새 테스트 없이 기존 동작에 기댄다.
- 삭제 시각이 `updated_at`이라는 규칙(Q1)은 같은 트랜잭션 안의 감사 시각 비교가 불안정해 테스트로 확인하지 않았다.

한계와 후속:
- 작성자 삭제와 관리자 숨김·삭제가 겹치면 마지막 쓰기가 이긴다(`설계-결정.md` 4절에 올림).
- 회원 상태 검증이 `getActiveMember` 호출로 5곳에 반복된다. 이름 정리는 #160.
- 기능 페이지 이름이 삭제까지 담지 못한다. 이름 원칙은 #161.
- 접근 제어(비로그인 401, 일반 회원의 관리자 API 403)를 모든 API에서 한곳으로 모아 확인하는 테스트는 후속 후보다(`/next`에서 정한다).
- `07-로그인과-회원.md` R-MEMBER-11 행의 테스트 칸에 `PollCommandServiceTest`를 연결하면 추적이 좋아진다(선택).
