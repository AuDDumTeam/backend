---
issue: 179
sources: [이슈 #179, docs/engineering/설계-결정.md, docs/engineering/컨벤션.md, docs/records/sketches/163-my-poll-lists.md]
features: [06-관리자-조치]
ids: []
decisions: []
status: 완료
---

# 컨트롤러가 요청 DTO를 풀어 넘기지 않고 서비스 인자를 개념별 record로 묶는다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

`설계-결정.md`는 서비스 인자를 개념별 record로 묶는다고 한다(`PollChoice`, `Viewer`, `VoterCredential`). 그런데 공개 목록(`GET /polls`)과 관리자 신고 목록은 요청 DTO(`PollListRequest`, `AdminReportListRequest`)로 쿼리 파라미터를 묶었다가 `cursorId()`·`pageSize()`로 다시 풀어 낱개로 넘긴다. 관리자 조치 API도 본문 DTO에서 `reason()`·`status()`를 꺼내 낱개로 넘긴다. 묶은 뜻이 없고 인자가 늘수록 순서를 틀리기 쉽다. #163에서 `CursorPaging`을 만들어 내가 올린·참여한 목록에는 이미 적용했고, 나머지를 맞춘다. (이슈 #179)

소스와 달라진 점: 한 곳이다. `GET /admin/reports`의 `status`를 `String`으로 받아 직접 해석하던 것을 `ReportStatus` 열거형으로 받는다(Q4). 그 결과 빈 값(`?status=`)이 400에서 생략과 같은 PENDING 200으로 바뀐다. 나머지는 동작을 바꾸지 않는 리팩터링이고 API 요청·응답 모양, 오류 코드는 그대로다.

| 규칙 | 어긋나면 | 근거·테스트 |
|---|---|---|
| `GET /admin/reports`의 `status`가 비어 있으면(`?status=`) 생략한 것과 같이 PENDING이다. 이전에는 400이었다 | — | Q4(사용자, 2026-11-06) · `GET /polls`의 `category`(#162)와 같은 방식 · `AdminReportChainTest` |
| `status`가 `ReportStatus` 이름(PENDING·RESOLVED·DISMISSED)이 아니면(`UNKNOWN`, 소문자 `pending`, `ALL`) 400 `MALFORMED_REQUEST`다. 이 규칙은 그대로이고 해석 방식만 프레임워크 변환으로 바뀐다 | 400 `MALFORMED_REQUEST` | 기존 `AdminReportChainTest` |

앞뒤 공백이 붙은 값(`" PENDING"`), 공백만 있는 값(`%20`), 같은 이름을 여러 번 보낸 값(`status=PENDING&status=RESOLVED`)은 지금 모두 400이지만 열거형 변환으로 바꾸면 PENDING 200이나 다른 결과가 될 수 있다. 이 경우는 스프링의 열거형 변환에 맡기고 정본에서 약속하지 않으며 테스트로 고정하지 않는다(#162의 질문 5와 같다). 그래서 이 값들의 결과가 바뀔 수 있다는 점이 이 작업이 바꾸는 동작에 포함된다.

고칠 정본 문장(동작 규칙이 아니라 구조 설명이다):

- `설계-결정.md`의 "내가 올린 투표 목록" 항목 끝 "공개 목록(`GET /polls`)은 아직 `PollListRequest`로 풀어 넘긴다(#179에서 맞춘다)"를 지우고 지금 구조로 맞춘다.
- `컨벤션.md`에 "컨트롤러가 요청 DTO를 만들었다가 풀어 넘기지 않는다"를 한 줄 더한다.

## 건드리는 것

- 건드린다: `GET /polls`와 `GET /admin/reports`의 컨트롤러·서비스 시그니처, 관리자 조치 서비스 3개(`AdminMemberCommandService.suspend`, `AdminReportCommandService.dismiss`, `AdminPollCommandService.takedown`) 시그니처, 요청 DTO `PollListRequest`·`AdminReportListRequest`, 커서 클래스 `ReportCursor`의 접근 범위, 해당 서비스를 부르는 테스트.
- 건드리지 않는다: 응답 모양, 오류 코드, 쿼리 파라미터 이름과 기본값(`size` 기본 20, 범위 1~50), 커서 형식, `AdminAction` 도메인의 사유 검증, DB.
- 생성 문서: `status` 파라미터의 스키마가 문자열에서 열거형으로 바뀌어 `openapi.json`(과 `API-목록.md`)이 바뀔 수 있다. 바뀌는 것이 이 파라미터 하나인지 확인한다. 그 밖에 바뀌면 이유를 찾는다.
- 정본: `06-관리자-조치.md`의 신고 목록 `status` 규칙을 "생략하거나 비우면 PENDING, `PENDING`·`RESOLVED`·`DISMISSED`가 아니면 400 `MALFORMED_REQUEST`"로 한 번에 적는다. 지금 이 400은 정본에 없고(L24의 400은 조치 API 본문 이야기다) `AdminReportWebApiDocs`와 체인 테스트에만 있다. 위치는 구현 때 L78 표 근처와 UC 설명을 grep으로 보고 정한다.
- `AdminReportWebApiDocs`: `getReports`의 `status` 파라미터 타입을 `String`에서 `ReportStatus`로 바꿔 구현과 맞추고, 파라미터·400 응답 설명에 빈 값은 PENDING임을 반영한다.
- 호출 지점은 구현할 때 `grep`으로 직접 세어 결과에 적는다(교훈: 이슈 본문의 수를 그대로 믿지 않는다).

## 접근

서비스 시그니처만 바꾸고 컨트롤러가 요청 DTO를 거치지 않고 서비스 인자 record를 바로 만든다. #163이 올린·참여한 목록에서 한 방식(`CursorPaging.of(PollCursor.idOf(cursor), size)`)을 나머지에 같게 쓴다. 이 방식이 이미 `설계-결정.md`에 있고 사용자가 #163에서 그렇게 정한 것이다.

- 공개 목록: `PollQueryService.getPollSummaries`가 `CursorPaging`을 받는다. `PollListRequest`의 `pageSize()`·`cursorId()`는 없어지고 남는 `nextCursor`는 `PollCursor`로 옮긴다.
- 관리자 신고 목록: `AdminReportQueryService.getReports`가 `CursorPaging`을 받는다. 컨트롤러는 `status`를 `ReportStatus`로 바로 받고(Q4), 없거나 비어 있으면 PENDING으로 보고, 모르는 값은 프레임워크의 변환 실패로 `MALFORMED_REQUEST`가 된다. `ReportCursor` 해석도 컨트롤러 쪽에서 한다. #163이 `PollCursor`를 `public`으로 연 것과 같이 `ReportCursor`에 `PollCursor.idOf`처럼 `Long`을 돌려주는 `idOf`를 추가하고 `public`으로 연다(지금은 `from`만 있다). 응답의 `nextCursor` 인코딩도 `AdminReportListRequest`가 하던 것을 `ReportCursor` 쪽으로 옮기고 응답이 쓸 수 있게 `encode`를 연다.
- 관리자 조치: 투표 조치(`takedown`)만 `PollTakedown`+`reason`을 record로 묶는다(Q2 A).
- `CursorPaging`은 `support.paging`으로 옮긴다(Q1 B). 필터는 `CursorPaging`과 따로 넘긴다(Q3 A).
- `AdminReportListRequest`는 하나도 남지 않으면 지운다. `PollListRequest`도 같다.

검토한 대안: 요청 DTO를 서비스에 그대로 넘긴다. 이 안은 `api` 계층 DTO를 `application`이 알게 되어 `application은_api에_의존하지_않는다` 규칙과 부딪혀 고르지 않는다.

### 질문과 답

답이 없는 것이 있으면 승인을 묻지 않는다.

**Q1. 쪽 조건 record `CursorPaging`을 어디에 두나.** 지금은 `poll.application.dto`에 있다. 관리자 신고 목록(`admin`→`report`)이 쓰려면 `admin`이 `poll`의 DTO를 알게 된다(ArchUnit의 허용 목록에는 `admin → poll`이 있어 막히지는 않는다). 출처가 되는 문서는 없다.

| 선택지 | 구현에 강제하는 것 |
|---|---|
| A. `poll.application.dto`에 그대로 두고 `admin`이 import | 코드 이동 없음. 신고 목록이 투표 도메인의 DTO에 기대고, 이후 `report`가 쓰면 `report → poll` 의존이 늘어난다(허용 목록에는 있다) |
| B. `support`로 옮긴다(예: `support.paging`) | `CursorPaging`은 도메인을 모르는 값이라 `support` 규칙(`support는_도메인에_의존하지_않는다`)은 지킨다. #163 코드의 import 변경과 정본 문장(`설계-결정.md`, 필요하면 `아키텍처.md`의 support 설명) 수정이 따른다 |
| C. 신고 목록용 쪽 조건을 `admin`에 따로 둔다 | 쪽 크기 상수(기본 20, 1~50)가 `admin`에 한 벌 더 생긴다(지금도 `AdminReportListRequest`에 복제돼 있다) |

**답: B** (사용자, 2026-11-06). `CursorPaging`을 `support.paging`으로 옮긴다.

**Q2. 관리자 조치의 `reason`·`status`를 어떻게 묶나.** 서비스 3개의 인자는 `adminId`, 대상 id, 그리고 `reason`(투표 조치는 `PollTakedown` 타입의 `status`도)이다. A안의 "묶는다"는 `PollTakedown`+`reason` 두 값이다. 이슈 문장은 "개념별 record로 묶는다"이고 어떤 개념인지는 정해져 있지 않다.

| 선택지 | 구현에 강제하는 것 |
|---|---|
| A. 투표 조치만 `status`+`reason`을 record 하나(예: `PollTakedownOrder`)로 묶는다. 회원 정지·신고 기각은 `reason` 하나라 그대로 둔다 | record 하나 추가. 이슈의 "3개 모두 묶는다"보다 범위가 좁다. 단일 값을 감싼 타입이 생기지 않는다 |
| B. 3개 모두 `reason`을 record(예: `ActionReason`)로 묶고 투표 조치는 `status`와 함께 하나로 묶는다 | record 2개 추가. 단일 값 래퍼가 생기고 값 검증은 `AdminAction`이 하므로 record에는 검증을 넣지 않는다 |
| C. 요청 DTO(`AdminReasonRequest`, `AdminPollTakedownRequest`)를 그대로 서비스에 넘긴다 | `application`이 `api` DTO에 의존해 ArchUnit 규칙을 어긴다. 고르지 않는 안이다 |

**답: A** (사용자, 2026-11-06). 투표 조치만 `PollTakedown`+`reason`을 record 하나로 묶고, 회원 정지·신고 기각은 `reason` 하나라 그대로 둔다. 이슈의 "3개 모두 묶는다"는 단일 값 래퍼를 만들게 되어 이 범위로 줄인다.

**Q4. 관리자 신고 목록의 `status`를 열거형으로 받나.** 문자열로 받으면 컨트롤러에 해석 코드(`reportStatusOf`)가 남고, 열거형으로 받으면 사라지지만 빈 값이 400에서 PENDING으로 바뀐다(스프링이 빈 값을 `null`로 변환). 출처가 되는 정본 문장은 없고 `GET /polls`의 `category`(#162)는 열거형이고 빈 값을 생략과 같게 본다.

| 선택지 | 구현에 강제하는 것 |
|---|---|
| A. `String` 유지 | 변경 없음. 컨트롤러에 해석 메서드가 남는다 |
| B. 열거형으로 받고 빈 값은 PENDING | 해석 코드 삭제. `?status=`가 400→200으로 바뀌어 규칙이 바뀐다. `AdminReportChainTest`의 `""` 케이스 변경, 정본 한 줄, `AdminReportWebApiDocs`·생성 문서 갱신이 따른다 |
| C. 이번엔 A, B는 후속 이슈 | 이번 PR은 순수 리팩터링으로 남고 빈 값의 의미는 따로 합의한다 |

**답: B** (사용자, 2026-11-06). 추천은 C였으나 사용자가 B를 골랐다. 규칙이 바뀌므로 `status`를 `초안`으로 되돌리고 다시 승인받는다.

**Q3. 조회 조건(필터+쪽)을 record로 한 번 더 묶나.** 공개 목록은 `category`+쪽, 신고 목록은 `status`+쪽이다. 이슈는 신고 목록만 "상태 필터와 함께 조회 조건 record"라고 적었다.

| 선택지 | 구현에 강제하는 것 |
|---|---|
| A. 필터와 `CursorPaging`을 서비스 인자 둘로 넘긴다(`getPollSummaries(Category, CursorPaging)`, `getReports(adminId, ReportStatus, CursorPaging)`) | record 추가 없음. 올린·참여한 목록과 같은 모양이다 |
| B. 둘 다 조회 조건 record로 묶는다(예: `PollListCondition`, `ReportListCondition`) | record 2개 추가. 서비스 인자는 줄지만 한 번 더 감싼다 |
| C. 신고 목록만 묶는다(이슈 문장대로) | 두 목록이 서로 다른 모양이 된다 |

**답: A** (사용자, 2026-11-06). 필터와 `CursorPaging`을 서비스 인자 둘로 넘긴다. 이슈의 "신고 목록 조회 조건 record"는 하지 않는다.

## 구조

- `poll/api/PollWebApi`: `CursorPaging.of(PollCursor.idOf(cursor), size)`로 바로 만든다. `poll/api/dto/PollListRequest` 삭제. `PollListResponse`가 쓰던 `nextCursor`는 `PollCursor`로 부른다.
- `support/paging/CursorPaging`: `poll.application.dto`에서 옮긴다(Q1 B). 내용은 그대로이고 `PollQueryService`, `PollWebApi` 등 import만 바뀐다.
- `poll/application/PollQueryService.getPollSummaries(Category, CursorPaging)`: 시그니처 변경(Q3 A).
- `admin/api/AdminReportWebApi`: `status`를 `ReportStatus`로 받아(없거나 비면 PENDING) 커서를 해석하고 `CursorPaging`을 만든다. 해석 메서드는 두지 않는다. `admin/api/dto/AdminReportListRequest` 삭제. `ReportCursor`에 `idOf`·`encode`를 `public`으로 연다.
- `admin/application/AdminReportQueryService.getReports(adminId, ReportStatus, CursorPaging)`: 시그니처 변경(Q3 A).
- `admin/application/AdminPollCommandService.takedown`: `PollTakedown`+`reason`을 record 하나(예: `PollTakedownOrder`, `admin.application.dto`)로 받는다(Q2 A). `AdminMemberCommandService.suspend`, `AdminReportCommandService.dismiss`는 그대로다.
- 정본: `설계-결정.md`, `컨벤션.md`, `CursorPaging` 위치가 적힌 문장(`아키텍처.md`의 `support` 설명 포함, 구현 때 grep으로 찾는다).

## 순서와 테스트

동작이 그대로라 새 규칙 테스트는 없고, 기존 테스트가 보호한다. 시그니처가 바뀌는 곳의 테스트가 같은 검증으로 통과해야 한다.

1. 호출 지점을 `grep`으로 세어 시그니처가 바뀌는 테스트(예: `PollListWebApiTest`, `PollQueryServiceTest`)를 목록으로 만든다.
2. test-writer는 기존 테스트 파일을 읽고 시그니처만 갱신한다(새 파일로 덮어쓰지 않는다. 교훈 #162). 계층은 그대로다: 슬라이스 테스트는 매핑·인자 모양, 서비스 통합 테스트는 쪽·필터 동작.
3. 관리자 조치·신고 목록은 기존 체인·롤백·동시성 테스트(`AdminReportActionChainTest` 등)가 통과해야 한다.
4. `AdminReportChainTest`(계층: 체인, 실제 DB·HTTP)에서 `허용되지_않는_status는_400…`의 `""` 케이스를 빼고, 빈 `status`가 PENDING 목록을 200으로 주는 케이스를 더한다(test-writer, 기존 파일을 읽고 케이스만 바꾼다).
5. 생성 문서는 `DOCS_UPDATE=1 ./gradlew test --tests '*OpenApiDocTest'`로 다시 만들고 `./gradlew cleanTest build`로 확인한다. 바뀐 곳이 `status` 파라미터뿐인지 diff로 본다.

## 지킬 규칙

- 서비스 인자는 개념별 record(`설계-결정.md`), 값 검증은 record에 넣지 않는다.
- `application은_api에_의존하지_않는다`, `support는_도메인에_의존하지_않는다`(ArchUnit).
- record 파라미터가 2개 이상이면 한 줄에 하나씩(`컨벤션.md`).
- 기존 같은 일을 하는 클래스가 있는지 먼저 찾는다(교훈 #163).

## 안전장치

- 모르는 `status`와 형식이 틀린 커서는 `MALFORMED_REQUEST`로 남아야 한다. 신고 목록의 슬라이스 테스트는 없고 `AdminReportChainTest`가 보호한다(`허용되지_않는_status는_400…`, `형식이_틀린_cursor는_400…`, `size가_숫자가_아니면_400…`).
- `size` 보정(기본 20, 1~50)이 한 곳(`CursorPaging.clampSize`)에만 남는지 확인한다. 같은 상수가 `AdminReportListRequest`에 복제돼 있다.
- 접근 범위를 넓히는 곳(`ReportCursor`)은 컨트롤러가 쓰는 `idOf`와 응답이 쓰는 `encode`만 연다. `from`과 생성자는 닫아 둔다.
- 구현 전에 `AdminPollWebApi`가 `request.status()`를 `PollTakedown`으로 넘기는지 확인한다.

## 결과

컨트롤러가 요청 DTO를 만들었다가 풀어 넘기던 목록 조회 두 곳과 관리자 투표 조치의 서비스 인자를 개념별 record로 맞췄다.

- **쪽 조건**: `CursorPaging`을 `poll.application.dto`에서 `support.paging`으로 옮기고 `package-info`를 더했다. `PollWebApi`·`AdminReportWebApi`가 `CursorPaging.of(XCursor.idOf(cursor), size)`로 바로 만든다. 쪽 크기 보정(기본 20, 1~50)은 `CursorPaging`의 비공개 `clampSize` 한 곳에만 있다.
- **서비스 시그니처**: `PollQueryService.getPollSummaries(Category, CursorPaging)`, `AdminReportQueryService.getReports(adminId, ReportStatus, CursorPaging)`, `AdminPollCommandService.takedown(adminId, pollPublicId, PollTakedownOrder)`. `PollListRequest`, `AdminReportListRequest`를 지웠다.
- **`PollTakedownOrder`**: `admin.application.dto`에 두고 `PollTakedown`과 `reason`을 묶는다. 값 검증은 넣지 않고 사유 검증은 그대로 `AdminAction`이 한다. 회원 정지·신고 기각의 `reason`은 묶지 않았다(Q2 A).
- **동작이 바뀐 곳(Q4 B)**: `GET /admin/reports`의 `status`를 `ReportStatus`로 받는다(`@RequestParam(defaultValue = "PENDING")`). 빈 값(`?status=`)이 400에서 생략과 같은 PENDING 200으로 바뀌었다. `UNKNOWN`·`pending`·`ALL`은 계속 400 `MALFORMED_REQUEST`다.
- **호출 지점(grep)**: `getPollSummaries` 호출은 `PollWebApi` 하나와 테스트 2파일, `getReports`는 `AdminReportWebApi` 하나, `takedown`은 `AdminPollWebApi` 하나이고 서비스를 직접 부르는 테스트는 없다(체인 테스트가 HTTP로 보호). `PollListRequest`·`AdminReportListRequest`·옛 `CursorPaging` import는 `src`에 0건이다.
- **테스트**: 새 규칙 테스트는 빈 `status` 케이스 하나다(`AdminReportChainTest`). 나머지는 기존 테스트의 시그니처·import 갱신뿐이고 지워진 검증은 `""` 케이스 하나다.
- **생성 문서**: `openapi.json`은 `status` 파라미터의 설명, `default`, `enum`과 400 설명만 바뀌었다.
- **정본**: `06-관리자-조치.md`(`status` 규칙, 이력), `설계-결정.md`, `컨벤션.md`, `아키텍처.md`.
- **리뷰**: sketch-reviewer r1 PASS_WITH_FIXES, r2(Q4 반영) PASS_WITH_FIXES, domain-reviewer r1 PASS_WITH_FIXES(정본 갱신은 wrap에서 반영), code-reviewer r1 PASS_WITH_FIXES(CR-1·2 반영), fact-verifier r1 PASS.

### 계획과 달라진 점

- **Q4 추가(사용자 지시)**: 처음에는 `status`를 `String`으로 받아 컨트롤러에서 해석하기로 했고 동작 변경이 없다고 승인받았다. 구현 뒤 사용자가 "꼭 String으로 받아야 하느냐"고 물어, 열거형으로 받으면 빈 값이 400에서 PENDING으로 바뀐다는 점을 선택지(A·B·C)로 올렸고 사용자가 B를 골랐다(추천은 C였다). 규칙이 바뀌어 `status`를 `초안`으로 되돌리고 sketch-reviewer r2를 거쳐 다시 승인받았다.
- **`ReportCursor.encode`**: sketch는 `public`으로 열라고 했으나 `AdminReportListResponse`가 같은 `api.dto` 패키지라 package-private으로 두었다. 열린 것은 타입과 `idOf`뿐이다.
- **신고 상태 해석**: 처음 구현한 컨트롤러의 private 해석 메서드는 Q4 B로 없어졌다.
- **`nextCursor` 계산**: 한 줄 삼항 체이닝이 읽기 어렵다는 사용자 지적으로 지역 변수를 둔 `if` 블록으로 바꿨고, 리뷰 CR-1에 따라 `MyPollListResponse`·`VotedPollListResponse`까지 4곳을 같은 모양으로 맞췄다. `CursorPaging.clampSize`도 리뷰 CR-2에 따라 비공개로 좁혔다. 이 둘은 동작이 같아 재리뷰하지 않았다("리뷰 후 가독성 수정").
- **조치 인자**: 이슈는 서비스 3개 모두 묶는다고 했으나 단일 값 래퍼를 피하려고 투표 조치만 묶었다(Q2 A). 이슈의 "신고 목록 조회 조건 record"도 하지 않았다(Q3 A).

### 한계와 후속

- **공백·중복 파라미터**: `status=%20`, 앞뒤 공백, 같은 이름을 여러 번 보낸 값의 결과는 스프링 변환에 맡기고 정본에서 약속하지 않으며 테스트로 고정하지 않았다(#162와 같다). 이전에는 모두 400이었다.
- **커서 클래스 중복**: `PollCursor`·`VoteCursor`·`ReportCursor`는 여전히 거의 같은 코드다. 설계 결정대로 따로 두고, 세 번째 이후 공용화나 형식 변경이 필요할 때 다시 본다.
- **응답 변환 중복**: #163이 남긴 `id`·`question`·`category` 변환 복사는 이번 범위가 아니라 손대지 않았다.
- **작은 이름 어색함**: `PollTakedownOrder.status`가 `PollTakedown` 타입이라 `order.status().apply(poll)`이 약간 어색하다(요청 DTO 필드 이름을 따랐다). 필요하면 이름을 바꾼다.
