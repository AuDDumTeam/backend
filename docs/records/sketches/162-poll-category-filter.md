---
issue: 162
sources: [이슈 #162, features/01-둘러보기.md, engineering/API-공통-규칙.md, engineering/설계-결정.md, engineering/테스트-전략.md]
features: [01-둘러보기]
ids: [UC-01, P-07, P-23, R-POLL-18]
decisions: []
status: 완료
---

# GET /polls에 category 필터 파라미터를 추가한다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

목록 화면에서 카테고리별로 투표를 걸러 볼 수 있어야 한다. 지금은 카테고리를 표시만 한다(P-23, `01-둘러보기.md` 한계 칸). MVP 제외 결정을 뒤집는 일이라 P-23을 고친다(#162). **카테고리는 한 번에 하나만 거른다.** 여러 카테고리를 한꺼번에 거는 것은 나중에 필요해지면 따로 한다(사용자, 2026-11-02).

**2차 개정(2026-11-02).** 처음 합의는 `category`를 문자열로 받아 모르는 값을 전체로 보고 `ALL`을 만드는 것이었다. PR #169까지 그렇게 구현했다. 그 뒤 사용자가 "열거형대로 받으면 되지 않느냐"고 물었고, 모르는 값을 전체로 보기 때문에 문자열로 받는 우회가 생긴 것임을 확인한 뒤 **열거형으로 받고 모르는 값은 400으로 거절하는 안(A)** 을 골랐다(사용자, 2026-11-02). 아래 규칙은 이 개정 기준이다. 1차의 규칙은 PR #169의 커밋에 있다.

소스와 달라진 점:

- 이슈는 "없으면 지금처럼 전체"라고만 했다. 이 sketch는 파라미터를 생략하면 전체로 둔다. 1차에서 만들었던 전체 표시 값 `ALL`은 **만들지 않는다.**
- 이슈는 허용되지 않는 값의 응답을 "400 `VALIDATION_FAILED` 여부를 sketch에서 정한다"고 열어 두었다. `cursor`·`size`·신고 `status`의 형식 오류와 같은 **400 `MALFORMED_REQUEST`** 로 한다(`VALIDATION_FAILED`는 `AuddumException.validation`용이다).

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| `GET /polls`는 선택 쿼리 파라미터 `category`를 받는다. 생략하면 전체다 | — | UC-01 · P-23(개정) |
| `category`가 `Category` enum 이름 7개(`SOME`, `BLIND_DATE`, `CONTACT`, `IN_RELATIONSHIP`, `BREAKUP`, `LOVE_VALUES`, `ETC`) 중 하나이면 그 카테고리의 공개 투표만 최신순으로 준다 | — | UC-01 · P-23(개정) |
| `category`가 비어 있으면(`?category=`) 생략한 것과 같이 전체다(질문 4의 확인 대기) | — | `API-공통-규칙.md`가 빈 `cursor`를 첫 페이지로 보는 것과 같다 |
| 위 7개 이름이 아닌 값은 거절한다. 소문자, 오타, `ALL`, 모르는 값이 여기에 든다 | 400 `MALFORMED_REQUEST` | `API-공통-규칙.md`(형식 오류) |
| 카테고리 필터가 있어도 커서 페이징은 그대로다. 커서는 투표가 만들어진 순서를 따르고, 한 페이지는 걸러진 투표만 `size`개(마지막이면 그보다 적게) 담긴다. `nextCursor`는 필터를 건 채로 다음 페이지를 이어 가는 값이다 | — | P-07 |
| 필터에도 공개 투표만 나온다. 숨김·삭제 투표는 카테고리가 맞아도 나오지 않는다(R-POLL-18) | — | P-01 |
| 맞는 투표가 없으면 빈 목록이다(200, `items: []`, `nextCursor: null`) | — | UC-01 |

앞뒤 공백이 붙은 값(`" SOME"`), 공백만 있는 값(`%20`), 같은 이름을 여러 번 보낸 값(`category=SOME&category=ETC`)은 스프링의 열거형 변환에 맡기고 **정본에서 약속하지 않는다**(질문 5, 사용자 답). 공백만 있는 값은 스프링이 공백을 잘라 전체로 볼 수 있어 "비어 있으면 전체"와 "이름이 아니면 400" 사이에 걸리므로, 빈 값 줄은 `?category=`처럼 완전히 빈 값만 가리킨다. 약속하지 않으므로 테스트로 고정하지 않는다.

바꾸는 문장(정본, `/wrap`에서):

- `01-둘러보기.md` P-23: 1차에서 적은 "카테고리를 주지 않거나 카테고리 이름이 아닌 값이면 전체다."를 "카테고리를 주지 않으면 전체이고, 카테고리 이름이 아닌 값은 거절한다."로 고친다.
- `01-둘러보기.md` 한눈에의 같은 문장, 한계 칸(그대로 "한 번에 하나만"), UC-01 결과 칸, API 표(`GET /polls`)와 이력 표의 #162 줄을 새 규칙에 맞춘다.
- `01-둘러보기.md` 규칙 표의 1차 줄("이름이 아닌 값은 오류가 아니라 전체. `ALL`…")을 "7개 이름이 아닌 값은 400 `MALFORMED_REQUEST`, 비어 있거나 생략하면 전체"로 고친다. "커서는 필터를 담지 않는다" 문장은 그대로 둔다.
- 생성 문서(`API-목록.md`, `openapi.json`)는 코드에서 만들어지므로 build가 비교한다. `API-목록.md`의 `GET /polls` 행 오류 코드에 `MALFORMED_REQUEST`는 이미 있다.
- `01-둘러보기.md` 규칙 표의 근거·테스트 칸에서 지워지는 `PollListRequestTest`를 빼고 새 `PollListCategoryChainTest`를 더한다. 정본이 없는 테스트를 가리키지 않게 하려는 것이다. 문장은 실제로 바뀌는 줄만 고친다(UC-01 결과와 한계 칸은 새 규칙과 어긋나지 않으면 그대로 둔다).

## 건드리는 것

- API: `GET /polls`의 선택 쿼리 파라미터 `category`가 문자열에서 `Category`로 바뀐다. `ALL`과 기본값이 사라지고 모르는 값은 400이 된다. 응답 모양과 오류 코드 목록은 그대로다(`MALFORMED_REQUEST`는 이미 이 엔드포인트의 오류다).
- 코드: `PollWebApi.getPolls`(`@RequestParam(required = false) Category category`), `PollListRequest`(`category` 필드와 `categoryFilter()`를 **지운다.** cursor·size만 남는다), `PollWebApiDocs`(설명).
- 그대로 두는 것: `PollQueryService`, `PollReader.findPublicOlderThan(cursorId, category, size)`, `PollJpaRepository.findOlderThanByCategory`, `PollQueryServiceTest`. 서비스 이후 계층이 `Category`(또는 `null`)를 받는 것은 1차와 같다.
- 테스트: `PollListRequestTest`는 대상이 없어져서 **지운다.** `PollListWebApiTest`의 category 케이스를 열거형 받기에 맞게 고친다. 400은 슬라이스가 아니라 새 체인 테스트에서 본다(질문 6).
- 문서: `01-둘러보기.md`, 생성 문서(`openapi.json`의 `category` 파라미터가 enum으로 나오고 `default`가 사라진다), `PollWebApiDocs`.
- 건드리지 않는 것: 응답 DTO, 오류 코드 목록, DB 스키마, 투표 만들기·상세·삭제, `member` 모듈, `PollCommandService`, `테스트-전략.md`(체인 테스트로 보므로 예외 한 줄을 더하지 않는다).

## 접근

`PollWebApi`가 `@RequestParam(required = false) Category category`로 받는다. 생략하면 `null`이고 `null`은 "필터 없음"이다. 열거형 변환에 실패하면 스프링이 타입 불일치 400을 내고, `GlobalExceptionHandler`가 이 400에 `MALFORMED_REQUEST` 코드를 붙인다(그 클래스의 `handleExceptionInternal`이 "타입 불일치 등"을 다룬다고 주석에 적혀 있다). 새 오류 처리를 만들지 않는다. 컨트롤러는 `category`를 그대로 서비스로 넘기고, `PollListRequest`는 category와 상관이 없어진다.

저장소는 1차와 같다. 카테고리가 없을 때(`null`)는 지금의 `findOlderThan`을, 있을 때는 `p.category = :category` 조건을 더한 `findOlderThanByCategory`를 쓰고 `PollReader`가 `null`인지로 고른다. `is null or` 조건을 피하므로 H2와 MySQL의 null 바인딩 타입 추론이 갈릴 일이 없다. QueryDSL은 도입하지 않는다.

검토한 대안:

- 문자열로 받아 직접 해석하고 모르는 값은 전체로 본다(1차): `ALL`을 받을 수 있고 오타가 오류가 되지 않는다. 하지만 이 규칙 때문에 컨트롤러가 문자열로 받고 `PollListRequest`가 해석하는 우회가 생기고, 오타가 조용히 전체가 되는 위험이 있다. 사용자가 열거형 받기를 골라 대체했다.
- 열거형으로 받으면서 모르는 값은 전체로 본다: 변환 실패를 삼키는 `Converter`나 `@InitBinder`가 필요하고 전역으로 번질 위험이 있어 문자열 해석보다 코드가 많다.
- 모르는 값을 400 `VALIDATION_FAILED`로 거절한다: `컨벤션.md`상 `AuddumException.validation`용이라 형식 오류에 맞지 않는다.
- 쿼리를 하나로 두고 `(:category is null or ...)`: null 바인딩 문제 때문에 쓰지 않는다.
- 카테고리별 경로(`/polls/categories/{c}`): 같은 목록의 조건일 뿐이라 파라미터가 맞다.
- 여러 카테고리를 한 번에 받는다: 이번에는 하지 않는다. 반복·쉼표 방식, 빈 값과 공백 처리가 규칙을 키운다.

질문과 답:

- 질문 1. 허용되지 않는 `category` 값의 응답은? 답: 400 `MALFORMED_REQUEST`(사용자, 2026-11-02, 2차 개정). 1차에서는 전체로 보기로 했었다.
- 질문 2. 빈 `category`는? 1차 답은 전체였다(사용자, 2026-11-02). 2차에서도 열거형 변환이 빈 문자열을 `null`로 보는 동작에 기대 전체로 둔다. 질문 4에서 이 동작을 확인한다.
- 질문 3. 카테고리 인덱스(`poll(status, category, id)`)는 이번에 만들지 않는다. 답: 후속(사용자, 2026-11-02). `설계-결정.md`의 "조회 최적화 인덱스는 MVP 이후 측정한 뒤 정한다"와 같은 방향이다.
- 질문 4. 열거형 변환이 빈 문자열을 `null`로 보는가? 사람이 정할 일이 아니라 확인할 일이다. 스프링의 열거형 변환이 빈 문자열을 `null`로 바꾼다고 알려져 있지만 이 레포(Spring Boot 4.1)에서 직접 확인하지 못했다. 답: test-writer가 슬라이스 테스트(`?category=` → 서비스에 `null`)로 확인한다. `null`이 아니라 400이 나오면 구현 대신 이 sketch의 빈 값 줄을 먼저 고치고 다시 승인받는다.
- 질문 5. 앞뒤 공백(`" SOME"`)과 반복(`category=SOME&category=ETC`)은 어떻게 하는가? 이전 검토(r1, SR-3)가 같은 점을 물었다. 스프링의 변환이 공백을 자르는지, 공백만 있는 값(`%20`)을 `null`(전체)로 보는지, 반복 값에서 첫 값을 쓰는지는 이 레포에서 확인하지 못했다. 답: 스프링의 변환에 맡기고 정본에서 약속하지 않으며 테스트로 고정하지 않는다. 정확한 이름 7개만 보장한다(사용자, 2026-11-02).
- 질문 6. 400을 어느 계층에서 테스트하는가? `테스트-전략.md`의 controller 테스트 줄은 "오류 코드→상태 매핑은 보지 않는다"이고 이전 검토(r1, SR-4)도 같은 점을 짚었다. 제안: 슬라이스에서 보지 않고 체인 테스트 한 곳(`PollListCategoryChainTest`)에서 "모르는 값이면 400 `MALFORMED_REQUEST`"와 "카테고리를 주면 그 카테고리만 JSON으로 나온다"를 대표 시나리오로 본다. `테스트-전략.md`에 예외를 더하지 않는다. 이 체인 테스트는 1차 리뷰의 DR-1 권고(필터가 걸린 목록이 JSON으로 나오는 경로)도 함께 채운다. 답: 체인 테스트 한 곳(사용자, 2026-11-02).

## 구조

- `poll/api/PollWebApi`(`Category category` 파라미터), `poll/api/dto/PollListRequest`(category 제거), `poll/api/PollWebApiDocs`(설명)
- 그대로: `poll/application/PollQueryService`, `PollReader`, `poll/infrastructure/PollJpaRepository`
- 테스트: `PollListRequestTest`(지움), `PollListWebApiTest`(슬라이스, 고침), `PollListCategoryChainTest`(체인, 새로 만듦), `PollQueryServiceTest`(그대로)
- 문서: `docs/features/01-둘러보기.md`, 생성 문서

## 순서와 테스트

계층은 `테스트-전략.md` 표를 따른다. 이 레포에서 `…WebApiTest`는 슬라이스, `…ChainTest`는 체인이다.

1. `PollListWebApiTest`(슬라이스, 서비스는 목): `category`가 7개 이름이면 서비스에 그 `Category`가 넘어간다(전수). `category`를 생략하면 서비스에 `null`이 넘어간다. `?category=`(빈 값)도 서비스에 `null`이 넘어가고 200이다(질문 4). `size`와 함께 줄 수 있다. 400은 여기서 보지 않는다. 1차에서 쓴 "모르는 값·`ALL`·반복 값 → `null`" 케이스는 규칙이 바뀌었으므로 지운다. red는 지운 규칙을 기대하던 것이 사라지는 것이 아니라 새 케이스가 아직 통과하지 못하는 것이다(빈 값·생략 케이스는 현재 문자열 구현에서도 통과한다. 이번 개정의 red는 **2번 체인 테스트의 400 시나리오 하나뿐일 수 있고**, 이미 통과하는 테스트는 test-writer가 note에 이유와 함께 보고한다. 컴파일이 깨져서 갈리는 red는 아니다).
2. `PollListCategoryChainTest`(체인, 실제 DB): 카테고리가 섞인 데이터에서 `?category=BREAKUP`을 주면 그 카테고리의 공개 투표만 JSON `items`로 나온다. `?category=some`이나 `?category=ALL`처럼 이름이 아닌 값은 400 `MALFORMED_REQUEST`다. 대표 시나리오만 둔다. 현재 구현에서는 400이 나오지 않으므로 이 테스트가 red다.
3. `PollQueryServiceTest`(서비스 통합)는 1차에서 이미 필터, 최신순, `null`이면 전체, 숨김·삭제 제외, 커서로 이어 가기, 빈 슬라이스를 본다. 바꾸지 않는다.
4. 구현: `PollListRequest`에서 category 제거 → `PollWebApi` → `PollWebApiDocs`. 지울 것: `PollListRequestTest`.
5. `./gradlew build`(생성 문서 비교 포함)가 통과한다. `openapi.json`은 같은 PR에서 갱신한다.

## 지킬 규칙

- UC-01, P-07, R-POLL-18(공개 투표만), `API-공통-규칙.md`(형식 오류는 400 `MALFORMED_REQUEST`).
- 테스트 메서드 이름은 한글이고 `@DisplayName`은 쓰지 않는다(`컨벤션.md`).

## 안전장치

- 1차 구현을 되돌리는 작업이다. 이미 push한 브랜치(PR #169)에 커밋을 추가하는 방식으로 하고 force push를 하지 않는다. `ALL`과 "이름이 아닌 값은 전체"를 정본(`01-둘러보기.md`)에 이미 적었으므로 wrap에서 반드시 고친다. 놓치면 정본이 코드와 어긋난다.
- 프런트 영향: 모르는 값이 전에는 전체였고 이제는 400이다. 프런트가 `ALL`을 보내거나 소문자를 보내면 깨진다. PR 본문에 알린다.
- 커서와 필터가 따로 놀기: 커서는 id만 담고 카테고리는 담지 않는다. 클라이언트가 다음 요청에 다른 `category`를 보내면 그 필터 안에서 그 id보다 오래된 것을 준다. 서버는 이를 오류로 보지 않는다. 서비스 통합 테스트의 이어 가기 시나리오로 같은 필터 안에서 빠짐·중복이 없음을 확인한다.
- N+1: 참여 수 집계(`countByPollIds`)는 기존 그대로 페이지 단위로 한다. 필터가 이 경로를 바꾸지 않는다.
- 인덱스 없음: 질문 3 참고. 투표 수가 늘면 필터가 느려질 수 있다.

## 결과

`GET /polls`가 선택 쿼리 파라미터 `category`를 `Category` 열거형으로 받아 목록을 카테고리 하나로 거른다. 이름 7개 중 하나이면 그 카테고리의 공개 투표만 최신순으로 주고, 생략하거나 비우면(`?category=`) 전체다. 7개 이름이 아닌 값(소문자, 오타, `ALL`)은 400 `MALFORMED_REQUEST`다. 변환 실패 400은 기존 `GlobalExceptionHandler`가 `MALFORMED_REQUEST`로 만들어서 새 오류 처리는 없다. 서비스·`PollReader`·쿼리(`findOlderThanByCategory`)는 1차 그대로다. 커서 페이징과 응답 모양은 그대로다. `01-둘러보기.md`(한눈에, 규칙 표, P-23, 이력)와 `openapi.json`(`category`가 enum 7개)을 고쳤다.

계획과 달라진 점:

- 1차(PR #169 초안)는 문자열로 받아 모르는 값을 전체로 보고 `ALL`을 만들었다. 사용자가 열거형 받기를 골라 2차로 되돌려 고쳤다. `PollListRequest`의 category 해석과 `PollListRequestTest`는 지웠고, `ALL`과 기본값도 없앴다.
- 질문 4(빈 값)는 구현에서 확인됐다. `?category=`는 서비스에 `null`로 넘어가 200이다.
- 400은 슬라이스가 아니라 새 `PollListCategoryChainTest`에서 본다(질문 6). 이 체인 테스트가 1차 리뷰의 권고 DR-1(필터가 걸린 목록의 JSON 응답 경로)도 채웠다.
- 정본에는 1차 문장이 먼저 들어갔다가 2차에서 고쳤다.

한계와 후속:

- 여러 카테고리 동시 필터는 없다. 필요해지면 따로 한다.
- 카테고리 인덱스 `poll(status, category, id)`는 만들지 않았다. 느려지는 것이 측정되면 만든다(`설계-결정.md`의 조회 최적화 인덱스 방침).
- 프런트가 `ALL`이나 소문자를 보내면 이제 400이다. 1차 PR 초안을 본 프런트가 있다면 알린다.
- 공백이 붙은 값, 공백만 있는 값, 반복 값은 스프링의 변환에 맡기고 정본에서 약속하지 않는다(질문 5).
- 두 JPQL 쿼리(`findOlderThan`, `findOlderThanByCategory`)는 필터 조건이 늘면 QueryDSL 등으로 합치는 것을 검토한다.
