---
issue: 162
sources: [이슈 #162, features/01-둘러보기.md, engineering/API-공통-규칙.md, engineering/설계-결정.md]
features: [01-둘러보기]
ids: [UC-01, P-07, P-23, R-POLL-18]
decisions: []
status: 완료
---

# GET /polls에 category 필터 파라미터를 추가한다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

목록 화면에서 카테고리별로 투표를 걸러 볼 수 있어야 한다. 지금은 카테고리를 표시만 한다(P-23, `01-둘러보기.md` 한계 칸). MVP 제외 결정을 뒤집는 일이라 P-23을 고친다(#162). **카테고리는 한 번에 하나만 거른다.** 여러 카테고리를 한꺼번에 거는 것은 나중에 필요해지면 따로 한다(사용자, 2026-11-02).

소스와 달라진 점:

- 이슈는 "없으면 지금처럼 전체"라고만 했다. 이 sketch는 전체를 가리키는 값 `ALL`을 만들고 `category`의 기본값으로 둔다(사용자 제안, 2026-11-02).
- 이슈는 허용되지 않는 값의 응답을 "400 `VALIDATION_FAILED` 여부를 sketch에서 정한다"고 열어 두었다. **거절하지 않고 전체로 본다**(사용자, 2026-11-02). 400이 아니다.

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| `GET /polls`는 선택 쿼리 파라미터 `category`를 받는다. 기본값은 `ALL`이다 | — | UC-01 · P-23(개정) |
| `category`가 `Category` enum 이름 7개(`SOME`, `BLIND_DATE`, `CONTACT`, `IN_RELATIONSHIP`, `BREAKUP`, `LOVE_VALUES`, `ETC`) 중 하나이면 그 카테고리의 공개 투표만 최신순으로 준다 | — | UC-01 · P-23(개정) |
| 그 밖의 모든 경우는 필터 없이 모든 공개 투표를 최신순으로 준다. 파라미터가 없을 때, 비어 있을 때, `ALL`일 때, 7개 이름이 아닌 값(소문자, 오타, 공백이 붙은 값, 모르는 값, 여러 번 반복한 값)일 때가 모두 여기에 든다(사용자, 2026-11-02) | — (오류가 없다) | UC-01 · `PollListRequestTest`, `PollListWebApiTest` |
| 카테고리 필터가 있어도 커서 페이징은 그대로다. 커서는 투표가 만들어진 순서를 따르고, 한 페이지는 걸러진 투표만 `size`개(마지막이면 그보다 적게) 담긴다. `nextCursor`는 필터를 건 채로 다음 페이지를 이어 가는 값이다 | — | P-07 |
| 필터에도 공개 투표만 나온다. 숨김·삭제 투표는 카테고리가 맞아도 나오지 않는다(R-POLL-18) | — | P-01 |
| 맞는 투표가 없으면 빈 목록이다(200, `items: []`, `nextCursor: null`) | — | UC-01 |

`ALL`은 요청에서만 쓰는 표시다. `Category` enum 값이 아니고, 저장 데이터와 응답에는 나오지 않는다. 모르는 값도 전체가 되므로 `ALL`은 사실상 "전체를 일부러 고른다"는 것을 문서와 화면에서 드러내는 이름이다.

바꾸는 문장(정본, `/wrap`에서):

- `01-둘러보기.md` 한계 칸의 "카테고리로 거르는 기능은 없다. 카테고리는 표시만 한다." 줄을 지우고, 대신 "카테고리는 한 번에 하나만 거른다."를 한계로 적는다.
- `01-둘러보기.md` P-23: "카테고리 필터는 MVP에서 제외하고 목록에 표시만 한다." → "목록은 카테고리 하나로 거를 수 있다. 카테고리를 주지 않거나 카테고리 이름이 아닌 값이면 전체다."
- `01-둘러보기.md` UC-01 결과 칸, API 표(`GET /polls`), 이력 표에 #162 줄을 단다. 규칙 표에는 "카테고리 이름이 아닌 값은 오류가 아니라 전체로 본다"를 적는다.
- 생성 문서(`API-목록.md`, `openapi.json`)는 코드에서 만들어지므로 build가 비교한다.
- "커서는 필터를 담지 않는다" 한 줄을 UC-01 결과 칸에 넣을지는 `/wrap`에서 정한다.

## 건드리는 것

- API: `GET /polls`에 선택 쿼리 파라미터 `category`(기본값 `ALL`)가 늘어난다. 응답 모양과 오류 코드는 그대로다.
- 코드: `PollWebApi.getPolls`, `PollListRequest`(`category` 해석), `PollQueryService.getPollSummaries`, `PollReader.findPublicOlderThan`, `PollJpaRepository`(카테고리 조건이 있는 조회)
- 기존 호출 수정: `findPublicOlderThan(cursorId, size)`의 시그니처가 `(cursorId, category, size)`로 바뀐다. 호출처는 `PollQueryService` 한 곳이고, `PollQueryServiceTest`가 14번 직접 부른다. 이 호출들은 `category`에 `null`(전체)을 넘기도록 고치고 **단언은 바꾸지 않는다.** 필터가 없을 때 기존 동작이 그대로라는 것을 기존 테스트가 지킨다.
- 문서: `01-둘러보기.md`, 생성 문서, `PollWebApiDocs`(`category` 파라미터 설명에 기본값 `ALL`, 이름 7개, 한 번에 하나만 가능, 모르는 값은 전체로 본다를 적는다. 문자열로 받으므로 openapi enum이 자동으로 나오지 않는다)
- 건드리지 않는 것: 응답 DTO, 오류 코드 목록, DB 스키마, 투표 만들기·상세·삭제, `member` 모듈, `PollCommandService`(#160이 건드린다), `테스트-전략.md`(400을 만들지 않으므로 예외 한 줄이 필요 없다)

## 접근

`PollWebApi`가 `category`를 `String`으로 받고 `@RequestParam(required = false, defaultValue = "ALL")`을 둔다(`defaultValue`는 문서에 기본값을 드러내려는 것이다). `PollListRequest`가 이 문자열을 `Category`로 바꿀 수 있으면 그 `Category`로, 못 바꾸면(`null`, 빈 문자열, `ALL`, 모르는 값 모두) `null`로 해석한다. `null`은 "필터 없음"이다. 서비스 이후 계층은 `Category`(또는 `null`)만 받는다. 오류를 던지는 곳이 없다.

값을 `String` 하나로 받으므로 스프링의 리스트 변환을 타지 않는다. 반복된 값은 쉼표로 이어진 한 문자열(`SOME,ETC`)이 되어 이름이 아니므로 전체가 된다. 어떤 입력이 와도 결과가 "그 카테고리" 또는 "전체" 둘 중 하나라서, 입력별로 스프링 동작을 확인할 필요가 없다.

저장소는 카테고리가 없을 때(`null`)와 있을 때 조회를 나눈다. 없을 때는 지금의 `findOlderThan`을 그대로 쓰고, 있을 때는 `p.category = :category` 조건을 더한 조회를 추가한다. `PollReader`가 `null`인지로 고른다.

- null 파라미터에 `is null or` 조건을 거는 JPQL을 피하므로 H2와 MySQL에서 null 바인딩 타입 추론이 갈릴 일이 없다.
- QueryDSL은 도입하지 않는다(사용자가 "복잡하면 검토 가능"이라 했다). 이 프로젝트에는 아직 없고, 조건이 카테고리 하나라 JPQL 둘로 충분하다. 여러 카테고리나 다른 필터가 붙어 쿼리 조합이 복잡해지면 그때 도입을 검토한다.

검토한 대안:

- 모르는 값을 400 `MALFORMED_REQUEST`로 거절한다: 처음에 이 안으로 정했었다(`cursor`·`size`·신고 `status`의 형식 오류가 같은 코드). 사용자가 모르는 값도 전체로 보기로 바꿨다(2026-11-02). 거절하면 오타를 바로 알 수 있고 선례와 맞지만, 전체로 보면 규칙과 오류 처리, 슬라이스의 400 예외가 모두 필요 없어진다.
- 컨트롤러에서 `@RequestParam Category category`로 바로 받는다: 변환 실패가 스프링의 400(`MALFORMED_REQUEST`)이 되어, 모르는 값을 전체로 보는 규칙과 맞지 않고 `ALL`도 따로 받아야 한다.
- 쿼리를 하나로 두고 `(:category is null or ...)`: 위의 null 바인딩 문제 때문에 쓰지 않는다.
- 카테고리별 경로(`/polls/categories/{c}`): 같은 목록의 조건일 뿐이라 파라미터가 맞다.
- 여러 카테고리를 한 번에 받는다: 이번에는 하지 않는다. 반복·쉼표 방식, 빈 값과 공백 처리, 스프링의 리스트 변환 동작이 규칙을 키운다(사용자, 2026-11-02).

질문과 답:

- 질문 1. 허용되지 않는 `category` 값의 응답은? 처음 답은 400 `MALFORMED_REQUEST` 동의였으나, 이후 모르는 값도 전체(`ALL`)로 처리하기로 바꿨다(사용자, 2026-11-02). 오류가 없다.
- 질문 2. 빈 `category`는? 답: 전체(사용자, 2026-11-02).
- 질문 3. 카테고리 인덱스(`poll(status, category, id)`)는 이번에 만들지 않는다. 답: 후속(사용자, 2026-11-02). `설계-결정.md`의 "조회 최적화 인덱스는 MVP 이후 측정한 뒤 정한다"와 같은 방향이다. 결과 칸의 후속으로 남긴다.
- 질문 4. null 바인딩을 어떻게 피할까? 답: 복잡하면 QueryDSL 검토 가능(사용자, 2026-11-02). 이 sketch는 JPQL 둘로 나누어 복잡하지 않게 해결하므로 QueryDSL은 도입하지 않는다.
- 질문 5. 해석을 어느 계층에서 하는가? 답: API 계층(사용자, 2026-11-02). `PollListRequest`에 둔다.
- 질문 6. 여러 카테고리를 한 번에 거는가? 답: 이번에는 하나만, 나중에 필요하면 한다(사용자, 2026-11-02).
- 질문 7. 공백만 있는 값은? 처음 답은 거절이었으나 모르는 값을 전체로 보기로 하면서 전체가 된다(사용자, 2026-11-02).

## 구조

- `poll/api/PollWebApi`(파라미터와 기본값), `poll/api/dto/PollListRequest`(해석), `poll/api/PollWebApiDocs`(설명)
- `poll/application/PollQueryService`, `PollReader`(`findPublicOlderThan(cursorId, category, size)`), `poll/infrastructure/PollJpaRepository`(카테고리 조건이 있는 조회 추가)
- 테스트: `PollListRequestTest`(단위), `PollListWebApiTest`(슬라이스), `PollQueryServiceTest`(서비스 통합)
- 문서: `docs/features/01-둘러보기.md`, 생성 문서

## 순서와 테스트

계층은 `테스트-전략.md` 표를 따른다. 이 레포에서 `…WebApiTest`는 슬라이스, `…ChainTest`는 체인이다. 오류를 만들지 않으므로 슬라이스가 오류 매핑을 볼 일이 없다.

1. `PollListRequestTest`(단위, `api.dto`): 7개 값 각각 `Category`로 해석, `ALL`·`null`·`""`·소문자·오타·앞뒤 공백·공백만 있는 값·쉼표로 이은 값(`SOME,ETC`)·모르는 값은 모두 `null`(전체). 어떤 입력도 예외를 던지지 않는다. red는 컴파일 실패다.
2. `PollListWebApiTest`(슬라이스, 서비스는 목): `category=SOME`을 주면 서비스에 `Category.SOME`이 넘어간다. `category`를 안 주거나 `?category=`, `ALL`, 모르는 값을 주면 서비스에 `null`이 넘어가고 200이다.
3. `PollQueryServiceTest`(서비스 통합, 실제 DB): 카테고리를 주면 그 카테고리의 공개 투표만 최신순, `null`이면 전체, 숨김·삭제 투표는 카테고리가 맞아도 제외, 카테고리가 섞인 데이터에서 `size`개씩 커서로 이어 가도 걸러진 투표만 빠짐·중복 없이 나옴, 맞는 투표가 없으면 빈 슬라이스. 기존 14개 호출은 `null`로 고치고 단언은 그대로 둔다.
4. 구현: `PollListRequest` → `PollWebApi` → `PollQueryService` → `PollReader` → `PollJpaRepository`.
5. `./gradlew build`(생성 문서 비교 포함)가 통과한다. 생성 문서가 바뀌면 같은 PR에서 갱신한다. H2 통과로 충분하다고 본다(조건이 `=` 하나와 기존 쿼리 둘뿐이라 MySQL 전용 동작이 없다).

## 지킬 규칙

- UC-01, P-07, R-POLL-18(공개 투표만).
- 테스트 메서드 이름은 한글이고 `@DisplayName`은 쓰지 않는다(`컨벤션.md`).

## 안전장치

- 오타가 조용히 전체가 된다: 모르는 값을 오류로 알리지 않으므로 프런트가 카테고리 이름을 잘못 보내도(`some`, `Some`) 눈에 띄는 실패 없이 전체 목록이 나온다. 사용자가 정한 대로 받아들이고, 대신 프런트가 보낼 값 7개와 `ALL`을 `PollWebApiDocs`와 정본에 적고 슬라이스 테스트로 고정한다. 문제가 되면 후속에서 거절로 바꾼다(`cursor`·`size`·신고 `status`와 같은 400 `MALFORMED_REQUEST`).
- 커서와 필터가 따로 놀기: 커서는 id만 담고 카테고리는 담지 않는다. 클라이언트가 다음 요청에 다른 `category`를 보내면 그 필터 안에서 그 id보다 오래된 것을 준다. 서버는 이를 오류로 보지 않는다(커서가 불투명 위치일 뿐이다). 서비스 통합 테스트의 이어 가기 시나리오로 같은 필터 안에서 빠짐·중복이 없음을 확인한다.
- N+1: 참여 수 집계(`countByPollIds`)는 기존 그대로 페이지 단위로 한다. 필터가 이 경로를 바꾸지 않는다.
- 인덱스 없음: 질문 3 참고. 투표 수가 늘면 필터가 느려질 수 있다.

## 결과

`GET /polls`가 선택 쿼리 파라미터 `category`(기본값 `ALL`)를 받아 목록을 카테고리 하나로 거른다. `Category` 이름 7개 중 하나이면 그 카테고리의 공개 투표만 최신순으로 주고, 그 밖의 모든 값(없음, 빈 값, `ALL`, 소문자, 오타, 공백이 붙은 값, 반복한 값)은 오류 없이 전체다. 해석은 `PollListRequest.categoryFilter()`가 하고(이름이 아니면 `null`), `PollReader`가 `null`이면 기존 `findOlderThan`을, 아니면 새 `findOlderThanByCategory`를 부른다. 커서 페이징과 응답 모양은 그대로다. `01-둘러보기.md`(한계 칸, UC-01, 규칙 표, P-23, API 표, 이력)와 `openapi.json`을 고쳤다.

계획과 달라진 점:

- `PollQueryServiceTest`에서 `findPublicOlderThan` 14개 호출만이 아니라 `getPollSummaries(null, n)` 3개 호출도 `(null, null, n)`로 고쳤다. 서비스 시그니처도 바뀌어서다. 단언은 바꾸지 않았다.
- "커서는 필터를 담지 않는다"는 UC-01 결과 칸이 아니라 정본 규칙 표에 넣었다. 프런트가 다음 쪽 요청에도 같은 `category`를 보내야 해서 규칙으로 적었다.
- 구현과 별개로 리뷰 지적(한 줄 130자 초과, import·공백 정리, 테스트 중복 정리, `ALL` 주석)을 반영하는 커밋이 하나 더 있다. 리뷰는 r2까지 했고 둘 다 FAIL이 아니다.

한계와 후속:

- 여러 카테고리 동시 필터는 없다. 필요해지면 따로 한다.
- 카테고리 인덱스 `poll(status, category, id)`는 만들지 않았다. 카테고리별 목록이 느려지는 것이 측정되면 만든다(`설계-결정.md`의 조회 최적화 인덱스 방침).
- 모르는 값을 전체로 보므로 프런트가 `some`처럼 잘못 보내도 눈에 띄는 실패 없이 전체 목록이 나온다. 문제가 되면 400 `MALFORMED_REQUEST`로 거절하도록 바꾼다(`cursor`·`size`·신고 `status`와 같은 코드).
- `categoryFilter()`가 `null`을 반환한다. 컨벤션은 반환값에 `Optional`을 쓰라고 하지만 sketch가 "null = 필터 없음"으로 승인했고 같은 클래스의 `cursorId()`도 `null`을 반환한다. 바꾸면 서비스·저장소 시그니처가 함께 바뀐다.
- 필터가 걸린 목록이 JSON 응답으로 나오는 경로를 체인 테스트로 보지는 않았다(domain-reviewer 권고 DR-1). 웹 계층은 서비스를 목으로 두고, 서비스 통합 테스트가 필터 결과·커서·숨김 제외를 확인한다.
- 두 JPQL 쿼리(`findOlderThan`, `findOlderThanByCategory`)는 필터 조건이 늘어나면 QueryDSL 등으로 합치는 것을 검토한다.
