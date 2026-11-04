---
issue: 170
sources: [이슈 #170, features/02-투표-만들기와-삭제.md, domain/도메인-모델.md, product/PRD.md]
features: [02-투표-만들기와-삭제]
ids: [R-POLL-03, R-POLL-04, R-POLL-21, P-06, P-17, UC-07]
decisions: []
status: 완료
---

# 투표 생성의 연속 생성 제한과 공개 ID 사전 중복 확인을 없앤다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.
> `features`에 적은 기능 페이지의 이력 표에도 이 문서 링크를 단다(양쪽을 맞춘다).

## 요구사항과 규칙

왜 하나: 이슈 #170. 투표는 수정할 수 없어 오타를 고치려면 지우고 다시 만들어야 하는데, 직전 투표 판단에 삭제된 투표도 포함되어 같은 질문이 409로 막힌다. 공개 ID는 경우의 수가 약 3×10²¹이라 사전 확인과 재시도의 실익이 없고, 유니크 제약이 이미 마지막 방어선이다.

소스와 달라진 점: 정본의 연속 생성 제한을 없애기로 사용자가 정했다(2026-11-02). 같은 질문 금지만 없애자는 제안과 달리 30초 간격도 함께 없앤다.

없애는 규칙: R-POLL-03, R-POLL-04, P-06, P-17. 번호는 다시 쓰지 않는다(`docs/index.md` 번호 범례).

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| 같은 작성자의 연속 생성과 같은 질문 생성을 서버에서 제한하지 않는다. 삭제한 투표와 같은 질문으로 다시 만들 수 있다(UC-07) | — | `PollCommandServiceTest` |
| 공개 ID는 12자 영숫자이고 겹치지 않음은 DB 유니크 제약이 보장한다. 겹치면 재발급하지 않고 서버 오류다(R-POLL-21 변경) | 500 `INTERNAL_ERROR` | `PollCommandServiceTest` |

## 건드리는 것

- 지운다: `PollValidator`, `Poll.validateCreatableAfter`, `Poll.CREATE_INTERVAL`, `PollErrorCode.DUPLICATE_POLL`, `PollErrorCode.POLL_RATE_LIMITED`, `PollReader.findLatestByAuthor`, `PollJpaRepository.findFirstByAuthorIdOrderByIdDesc`, `PollJpaRepository.existsByPublicIdValue`, `PollWriter.MAX_PUBLIC_ID_ATTEMPTS`
- 바꾼다: `PollCommandService.createPoll`, `PollWriter`(`createWithUniquePublicId`·`save`를 `create` 하나로), `PollWebApiDocs`의 생성 응답(409·429 제거), `Poll.create`·`PublicId.generate`의 javadoc
- 문서: `features/02-투표-만들기와-삭제.md`(한눈에, 흐름, 규칙, 정책 표, 이력), `02-투표-만들기와-삭제.d2`와 SVG, `domain/도메인-모델.md`(171행, 190행, 199행), `product/PRD.md` 86행, `docs/index.md` 106행(P 번호 범위 문구를 "19번은 비어 있다"에서 "06·17·19번은 비어 있다"로)
- 생성 문서: `오류-코드.md`, `API-목록.md`, `openapi.json`은 다시 만든다
- 건드리지 않는다: DB 스키마와 `uq_poll_public_id`, 입력 검증 규칙, 회원 상태 확인, 삭제 흐름, `docs/records`의 지난 sketch, `product/로드맵.md`(완료 이력이라 그대로 둔다)

## 접근

고른 안: 서비스는 회원 상태 확인, 시각 결정, `pollWriter.create(newPoll, authorId, now)` 호출만 한다. `create`는 `Poll.create`로 입력을 검증해 저장한다. 공개 ID 발급 방법이 서비스에 드러나지 않는다.

`BaseEntity`가 `IDENTITY`라 저장 호출에서 INSERT가 나가므로, 공개 ID 충돌은 `DataIntegrityViolationException`으로 올라와 `GlobalExceptionHandler`의 500이 된다. 별도 변환은 두지 않는다.

검토한 대안:

- 같은 질문 금지만 없애고 30초를 남긴다: 제안했지만 사용자가 30초도 빼기로 했다(2026-11-02).
- 공개 ID 재시도를 유니크 위반 catch로 옮긴다: 사실상 일어나지 않는 경우에 코드를 남기고, flush 실패 뒤에는 트랜잭션을 다시 시작해야 해 구조만 복잡해진다.

로그: 공개 ID 충돌은 `DataIntegrityViolationException`으로 올라오고, `GlobalExceptionHandler.handleUnexpected`가 예외를 포함해 ERROR로 남기므로 원인을 알 수 있다. 지금의 "공개 ID 발급 실패" 로그는 따로 두지 않는다.

질문과 답:

- 연타로 투표가 여러 개 생기는 것을 서버에서 막는가? 막지 않는다. 사용자가 제거를 결정했고(2026-11-02), 프런트의 버튼 비활성화에 맡긴다.
- 없앤 규칙(R-POLL-03·04, P-06, P-17)을 정본에서 어떻게 처리하는가? 행을 지운다. 이유는 이 sketch와 git에 남고, 번호는 다시 쓰지 않는다(사용자, 2026-11-02).
- 공개 ID 충돌을 재시도 없이 500으로 두고 409로 바꾸지 않는가? 그렇다. 사용자 잘못이 아닌 서버 쪽 사고다(사용자, 2026-11-02, 이슈 #170 본문).

## 구조

- 구현: `poll/application/PollCommandService.java`, `poll/application/PollWriter.java`, `poll/domain/Poll.java`, `poll/domain/PublicId.java`, `poll/domain/PollErrorCode.java`, `poll/application/PollReader.java`, `poll/infrastructure/PollJpaRepository.java`, `poll/api/PollWebApiDocs.java`
- 서비스 통합 테스트(`PollCommandServiceTest`, H2): 새 동작을 여기서 확인한다. 공개 ID 충돌은 기존 테스트처럼 `mockStatic(PublicId.class)`로 재현한다.
- 도메인 단위 테스트(`PollTest`): `validateCreatableAfter` 테스트를 지운다.
- API 테스트(`PollWebApiTest`): 409·429 응답 테스트를 지운다.

## 순서와 테스트

먼저 쓸 테스트(red):

1. `같은_질문으로_연속_생성할_수_있다`: 같은 작성자, 같은 질문, 간격 없음. 지금은 409로 실패한다.
2. `다른_질문으로_간격_없이_연속_생성할_수_있다`: 지금은 429로 실패한다.
3. `삭제한_투표와_같은_질문으로_다시_만들_수_있다`: 지금은 409로 실패한다.
4. `공개_ID가_겹치면_다시_발급하지_않고_저장에_실패한다`: 지금은 재발급해 성공한다. `mockStatic(PublicId.class)`로 같은 ID를 반환시키고, 두 번째로 줄 ID가 있어도 재발급 없이 `DataIntegrityViolationException`이 던져지는지 단언한다. 클래스 `@Transactional` 안에서는 예외 뒤 세션이 오염되어 롤백(투표·선택지가 남지 않음)을 관찰할 수 없어 단언하지 않는다(사용자, 2026-11-03). (UC-07)

그다음 코드에서 정책을 지우고, 지운 규칙의 테스트(`DUPLICATE_POLL`, `POLL_RATE_LIMITED`, 재발급)를 함께 지운다. 생성 문서를 다시 만들고 `./gradlew build`로 확인한다.

## 지킬 규칙

- UC-07: 투표와 선택지는 한꺼번에 저장되고 실패하면 아무것도 남지 않는다.
- R-POLL-01, R-POLL-02, R-POLL-05, R-POLL-08: 입력 검증은 그대로다.
- P-05: 정지 회원은 만들 수 없다. 회원 상태 확인은 그대로 첫 단계다.
- 생성 문서는 직접 고치지 않는다(`docs/generated/README.md`). `.d2`를 고치면 같은 커밋에서 SVG도 다시 만든다.

## 안전장치

- 같은 작성자의 도배: 서버 제한이 없어진다. 일일 제한도 원래 없었다. 정지와 삭제 같은 관리자 조치만 남는다는 점을 PR에 적는다.
- 공개 ID 충돌: 유니크 제약이 그대로라 중복 저장은 불가능하다.
- 지운 오류 코드를 쓰는 곳: `grep`으로 main·test·docs에 남은 참조가 없는지 확인한다. 프런트 저장소는 이 레포에 없어 확인할 수 없으므로 PR에 적는다.

## 결과

같은 작성자의 연속 생성과 같은 질문 생성을 서버에서 제한하지 않게 했다. `PollValidator`, `Poll.validateCreatableAfter`, `CREATE_INTERVAL`, `DUPLICATE_POLL`(409), `POLL_RATE_LIMITED`(429), `PollReader.findLatestByAuthor`와 repository 메서드 2개를 지웠다. 공개 ID 사전 중복 확인과 재발급도 지워 `PollWriter.create(newPoll, authorId, now)` 하나로 합쳤다. 공개 ID가 겹치면 DB 유니크 제약이 막고 `DataIntegrityViolationException`이 500 `INTERNAL_ERROR`로 내려간다. 정본(기능 페이지 02, 도메인 모델, PRD, `index.md`)과 흐름도(`.d2`, SVG), 생성 문서를 같은 PR에서 고쳤다. 정본 행은 지웠고 번호는 다시 쓰지 않는다(R-POLL-03·04, P-06, P-17).

계획과 달라진 점:

- 공개 ID 충돌 테스트에서 "두 번째 투표와 선택지가 남지 않는다" 단언을 뺐다. 클래스 `@Transactional` 안에서는 예외 뒤 세션이 오염되어(`poll_option`의 `poll_id` NULL flush 실패) 롤백을 관찰할 수 없다. 사용자가 "예외 단언만 남기기"를 골랐다(2026-11-03).
- 웹 계층 `PollWebApiTest`의 생성 오류 파라미터 테스트는 두 오류 코드만 다뤄 통째로 지웠다.
- 지운 정책의 서비스 테스트 8개와 도메인 테스트 6개도 지웠다.

한계와 후속:

- 같은 작성자의 도배를 서버가 막지 않는다. 일일 제한도 원래 없었다. 정지와 삭제 같은 관리자 조치만 남는다. 프런트의 버튼 비활성화에 맡긴다.
- 공개 ID 충돌이 500 `INTERNAL_ERROR`로 내려가는 웹 계층 테스트는 없다(domain-reviewer 권고 DR-1). 일반 예외 500 변환은 기존 공통 테스트(`VoteWebApiTest`, `ProblemDetailResponseTest`)가 확인한다.
- 프런트가 지운 오류 코드(`DUPLICATE_POLL`, `POLL_RATE_LIMITED`)를 쓰는지 이 레포에서 확인할 수 없다.
