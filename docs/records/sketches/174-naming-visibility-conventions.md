---
issue: 174
sources: [이슈 #174, docs/engineering/컨벤션.md, docs/engineering/아키텍처.md, docs/engineering/설계-결정.md, docs/engineering/인증.md]
features: [07-로그인과-회원]
ids: [R-MEMBER-11]
decisions: []
status: 완료
---

# 조회 이름 규칙과 record 개행, 접근 제한자를 정리하고 값 객체를 그대로 넘긴다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

이름만으로 동작을 알 수 없고(`find`가 `Optional`도 예외도 뜻한다), record 개행과 접근 제한자에 기준이 없으며, 레포지토리에 값 객체의 내부 값을 꺼내 넘긴다. 자세한 문제는 #174에 있다. 이 작업은 API 동작을 바꾸지 않는다. **단 하나, 요청한 회원이 DB에 없을 때의 응답이 401에서 404로 바뀐다**(아래 '소스와 달라진 점').

### 소스와 달라진 점

- 이슈 #174의 완료 기준은 "API 동작은 그대로"인데, `MemberReader.getMember`(없으면 401)를 없애고 조회를 하나로 합치기로 해서(사용자, 2026-11-04) 없는 회원의 응답이 **401 → 404 `MEMBER_NOT_FOUND`**로 바뀐다.
  - 영향: 쓰기 요청(`MemberValidator`), 탈퇴(`MemberCommandService`), 재발급(`RefreshTokenService`), `GET /members/me`(`MemberQueryService`)에서 토큰은 유효한데 회원 행이 없는 경우. 탈퇴는 행 삭제가 아니라 상태 변경이라 DB를 직접 지우지 않으면 생기지 않는다.
  - 탈퇴한 회원의 401은 그대로다(`validateNotWithdrawn`).
  - 정본에서 같은 PR에 고치는 문장(사용자 2026-11-04 확인):
    - `인증.md` L59·L61의 "탈퇴·없음 401"을 "탈퇴 401, 없음 404"로. L68~L71 표는 "없는 회원 404" 행·문구를 더한다(탈퇴 행과 재발급 행 포함). L174(재발급)에 "회원이 없으면 404"를 쓴다.
    - `07-로그인과-회원.md` L53(R-MEMBER-11)에 "없는 회원 404 `MEMBER_NOT_FOUND`"를 새로 쓴다.
    - `설계-결정.md` L60의 "정지 403, 탈퇴·없음 401"을 "정지 403, 탈퇴 401, 없음 404"로.
    - `로드맵.md` L87·L91은 완료 이력이라 원문을 두고 "(#174에서 없는 회원은 404로 변경)" 주석을 단다.
    - `docs/generated/`(`openapi.json`, `API-목록.md`, `오류-코드.md`)는 `@ApiResponse` 선언에 404가 추가되는지 확인하고 다시 만든다.
  - 관련 테스트의 기대값도 바뀐다(순서 1).
  - 재발급·`GET /members/me`도 같은 404다(사용자 2026-11-04). 프런트가 401로 로그인 상태를 판단하는 곳과 `인증.md` L174의 "401이면 로그아웃을 불러 쿠키를 정리" 계약은 404에서 어긋나므로 후속으로 남긴다(안전장치·결과의 후속 칸).
- 사용자는 "401은 필터에서 처리해야 한다"는 방향도 말했다. 이 이슈에서는 하지 않는다. 요청한 회원이 없으면 서비스가 404로 거절하고, 필터로 옮기는 일은 따로 정할 일이다(`인증.md` L55가 상태 확인을 서비스에 두는 결정이다).

### 새 규칙 (고칠 문장)

| 규칙 | 둘 곳 | 근거 |
|---|---|---|
| `application`의 `*Reader`에서 있어야 하는 단건은 `get…`이고 없으면 예외를 던진다. 없을 수 있는 단건은 `find…`이고 `Optional`을 돌려주며 던지지 않는다. 호출자가 쓰지 않는 `find`는 두지 않는다. | 컨벤션 '이름' | 사용자 2026-11-04 (기준은 의미, 반환형은 결과) |
| 컬렉션(`List`·`Slice`·`Map`)을 돌려주는 조회는 `findAll…`로 쓴다. `count…`·`is…`·`tally`는 이 규칙 밖이다. | 컨벤션 '이름' | 사용자 2026-11-04 |
| record 파라미터가 2개 이상이면 130자 안에 들어와도 한 줄에 하나씩 쓴다. 1개면 한 줄이다. `src/main`·`src/test`, 중첩 record, 어노테이션 붙은 파라미터에 모두 적용한다. | 컨벤션 '형식과 도구' | 사용자 2026-11-04 |
| 값 객체는 내부 값을 꺼내 레포지토리에 넘기지 않고 그대로 넘긴다. | 컨벤션 '코드 작성' | 이슈 #174 |
| 가시성 표: 다른 도메인에 여는 것은 서비스 진입점과 다른 도메인이 실제로 부르는 구현 계층 클래스(`MemberReader`, `PollReader`, `ReportReader`)다. 이 클래스들을 표의 예로 든다. | 아키텍처 '가시성' | 사용자 2026-11-04, `설계-결정.md` #19 |

## 건드리는 것

- 이름 (`application`의 `*Reader`만):
  - `MemberReader`: `getMember`(401)를 없앤다. 조회를 `get(Long)` 하나로 합친다(없으면 404 `MEMBER_NOT_FOUND`). 회원 조회는 "있어야 하는 것"이라 `get`이다. 기존 `find(Long)`(`Member`, 없으면 404)는 `get`으로 이름이 바뀌고, 호출자가 `Optional`로 쓰는 곳이 없으므로 `Optional`을 돌려주는 `find`는 두지 않는다(사용자 추천 수락, 2026-11-04). 호출처는 `MemberValidator`, `MemberCommandService`(탈퇴), `AdminMemberCommandService`와 Reader 안의 `getNotWithdrawnMember`로, 모두 `get`을 부르므로 `orElseThrow` 중복이 없다. `getNotWithdrawnMember`는 이름을 그대로 두고 안에서 `get`을 쓴다. `import DefaultErrorCode`, 클래스 javadoc, `getNotWithdrawnMember` javadoc("없거나 탈퇴했으면 401" → "없으면 404, 탈퇴 401")도 맞춘다.
  - `findByAuth`(소셜 계정에 연동된 회원이 없을 수 있음)는 "없을 수 있는 것"이라 `find`로 그대로다.
- Swagger 선언(생성 문서의 원천): `VoteWebApiDocs:26`, `ReportWebApiDocs:25`의 `401 "탈퇴했거나 없는 회원"`을 "탈퇴한 회원"으로 줄이고 404 `MEMBER_NOT_FOUND`를 더한다. 쓰기 API 전체(투표 만들기·삭제, 탈퇴 등)에 같은 선언이 있는지 구현 시작 때 `grep`으로 확인한다.
  - `PollReader`: `find(String)` → `getByPublicId`, `find(Long)` → `getById`, `findAllByIds`는 그대로. javadoc에 "노출 상태 무관"을 적어 `getPublic`·`getReadable`과 구분한다.
  - `ReportReader`: `find(Long)` → `get`, `findByStatusAfter` → `findAllByStatusAfter`, `findOthersOfSamePoll` → `findAllOthersOfSamePoll`.
  - `AdminActionReader.findByReport` → `findAllByReport`, `PollReader.findPublicOlderThan` → `findAllPublicOlderThan`.
  - `RefreshTokenReader`·`VoteReader`·`MemberReader.findByAuth`는 이미 단건 `Optional`의 `find…`라 그대로다.
  - 호출 지점 수는 구현 시작 때 **변수 이름이 아니라 타입 기준**(`MemberReader`·`PollReader`·`ReportReader`·`AdminActionReader`를 쓰는 곳 전부, mock stub 포함)으로 다시 센다. 1차 `grep` 값은 이슈와 달라질 수 있다.
- record 선언 `src/main` 60개 중 한 줄 선언과 `src/test` 전체.
- 접근 제한자: `src/main` 전체를 점검한다. 사용처를 `grep`으로 세어 패키지 밖에서 쓰지 않는 `public`을 줄인다. ArchUnit 가시성 규칙은 이번에 추가하지 않는다(후속).
- 레포지토리: 값 객체의 내부 값을 받는 메서드(`VoteJpaRepository.findByPollIdAndVoterAnonIdValue(Long, UUID)`, `ReportJpaRepository.existsByPollIdAndReporterAnonId(Long, UUID)`)를 서비스·Reader가 직접 부르지 않고 `Voter`·`Reporter`를 받는 메서드로만 부르게 한다. 이미 있는 `default` 메서드(`findByPollIdAndVoter`, `existsByPollIdAndReporter`)가 그 역할이다. `existsByPublicIdValue`는 #176에서 없어졌다.
- 문서: `컨벤션.md`, `아키텍처.md`(가시성 표), `인증.md`(L59·L61·L68~L71), `07-로그인과-회원.md`(R-MEMBER-11), 그리고 옛 메서드 이름이 적힌 문장(`설계-결정.md` L69의 `find`·`findAllByIds`, L88의 `getReadable` 등). 구현 시작 때 `docs/` 전체를 `grep`해 옛 이름(`getMember`, `find(`, `getNotWithdrawnMember`)을 찾아 이 목록에 더한다.
- 안 건드리는 것: API 경로·응답 모양·DB 스키마. `docs/generated/`는 바뀌면 다시 만든다(없는 회원 응답 문구가 생성 문서에 있으면 따라 바뀐다).

## 접근

한 PR에서 네 작업을 묶는다. 이슈 #174가 네 작업을 하나로 묶어 두었고, 같은 파일(Reader, 레포지토리)을 건드려 따로 하면 충돌이 잦다. **커밋은 작업별로 나눈다**(이름 → record → 접근 제한자 → 값 객체). 네 작업이 아닌 회원 조회 통합(401→404)은 이름 커밋에 포함하되 별도 커밋으로 분리해 되돌리기 쉽게 한다.

검토한 대안: 이슈 네 개로 쪼개기(같은 파일 충돌), 401을 필터로 옮기기(`인증.md` L55의 서비스 확인 결정과 요청별 예외(탈퇴·재발급 통과)를 필터가 알아야 해서 이번 범위 밖).

### 질문과 답

| 질문 | 답 | 출처 |
|---|---|---|
| 가시성 표와 admin이 쓰는 `ReportReader`가 어긋난다. | 표를 고친다(일반화, `ReportReader`를 예로 추가). 코드의 `public` Reader는 둔다. | 사용자 2026-11-04 |
| `Voter`는 `AnonId`, `Reporter`는 `UUID`다. | 지금처럼 둔다. `설계-결정.md` L49·L50을 유지한다(report는 vote에 의존하지 못하고, 애그리거트마다 의미가 달라질 수 있다). 레포지토리 UUID 인자만 위 '값 객체' 작업에서 본다. | 사용자 2026-11-04 |
| get/find 규칙을 어디까지? | `application`의 `*Reader`. 레포지토리 파생 쿼리 이름(`findBy…`)은 Spring Data 이름이라 밖이다. | 사용자 2026-11-04 |
| 컬렉션을 돌려주는 `find…`는? | `findAll…`로 통일한다. | 사용자 2026-11-04 |
| `MemberReader` 두 메서드(401/404)를 어떻게? | `getMember`(401)를 없애고 하나로 합친다. 없는 회원은 404로 합친다. 401을 필터로 옮기는 일은 이번에 하지 않는다. | 사용자 2026-11-04 |
| `PollReader.find` 두 오버로드는? | `getByPublicId` / `getById`로 나눈다. | 사용자 2026-11-04 |
| 접근 제한자 점검 범위는? | `src/main` 전체, ArchUnit 규칙은 추가하지 않는다. | 사용자 2026-11-04 |
| record 개행 범위는? | `src/main`·`src/test` 전부, 중첩·어노테이션 포함. 2개 이상이면 130자 이하여도 한 줄에 하나씩. | 사용자 2026-11-04 |
| `get`/`find`를 나누는 기준은? | 의미로 나눈다. 있어야 하는 것은 `get`(없으면 예외), 없을 수 있는 것은 `find`(`Optional`, 던지지 않음). 반환형은 결과다. 합친 `MemberReader` 조회는 `get`(404)이다. 처음 "find로 하자"에서 바뀌었다. | 사용자 2026-11-04 (추천 수락: "너 추천대로 해바") |
| 없는 회원 404를 재발급·`GET /members/me`에도? | 모두 404. 프런트 영향은 후속. | 사용자 2026-11-04 |
| `설계-결정.md` L60, `로드맵.md` L87·L91 문구는? | 같은 PR에서 모두 고친다(로드맵은 이력 주석). | 사용자 2026-11-04 |
| 한 PR로 묶고 커밋은 작업별, 회원 조회 통합은 별도 커밋? | 그대로. | 사용자 2026-11-04 |

승인 때 확인할 것:
- 합친 `MemberReader` 조회가 `find`가 아니라 `get`이 된 것(기준이 의미로 바뀜).
- Swagger 선언 두 파일(`src/main`)을 이 PR 범위에 넣는 것.

## 구조

- 문서: `docs/engineering/컨벤션.md`(이름, 형식, 코드 작성), `아키텍처.md`(가시성), `인증.md`, `07-로그인과-회원.md`, `설계-결정.md`의 메서드 이름 문장.
- 코드: 각 도메인의 `application/*Reader.java`, `domain/*`, `infrastructure/*JpaRepository.java`와 호출하는 서비스·테스트.

## 순서와 테스트

1. **회원 조회 통합(동작 변경)**: 먼저 테스트를 바꾼다. 없는 회원이 쓰기·탈퇴·재발급·`GET /members/me`에서 404 `MEMBER_NOT_FOUND`가 되는 기대로 고치고 red를 확인한다. 그다음 `getMember`를 없애고 `MemberReader`·호출 지점을 맞춰 green으로 만든다. 탈퇴 회원 401, 정지 403은 그대로 통과해야 한다. 바뀌는 테스트(구현 시작 때 타입·기대값 기준으로 다시 센다):
   - 서비스 통합: `MemberStatusAndWithdrawTest:73`(`DB에_없는_회원의_투표_생성은_401…` → 404), `MemberQueryServiceTest:62`, `RefreshTokenServiceTest`(없는 회원 기대가 있으면).
   - 검증 단위(실제 `MemberReader`+mock 레포): `MemberValidatorTest:60`, `VoteValidatorTest:75`, `ReportValidatorTest:68`(javadoc L27 포함).
   - 슬라이스: `MemberMeWebApiTest`(없는 회원 기대가 있으면).
   - **바꾸지 않는다**: `MemberStatusAndWithdrawTest:163`(GET에서 없는 회원 토큰이 200으로 통과하는 테스트).
2. **나머지 이름**: 컨벤션에 `get`/`find`/`findAll` 규칙을 쓰고 Reader와 호출 지점을 바꾼다. 확인: `grep`으로 옛 이름 0건, 기존 테스트 통과.
3. **record 개행**: 컨벤션에 규칙을 쓰고 선언을 맞춘다. 확인: `./gradlew build`.
4. **접근 제한자**: 사용처를 센 뒤 좁히고 가시성 표를 고친다. 확인: 컴파일과 기존 테스트. 테스트가 같은 패키지에 있어야 하는 점을 확인한다.
5. **값 객체**: 값 객체의 내부 값을 받는 레포지토리 메서드의 직접 호출을 없앤다. 확인: `VoteReaderTest`, `ReportSubmitChainTest`, `VoteConcurrencyTest`(동시성·유니크 제약)가 시그니처만 바뀌고 통과한다.
6. 1 말고는 새 동작이 없어 red 테스트가 없다. 기존 테스트가 방어선이다.

## 지킬 규칙

- 코드가 정본과 다르면 코드를 고친다. 이 PR이 정하는 새 규칙은 `/wrap`에서 컨벤션에 확정한다.
- R-MEMBER-11: 정지·탈퇴 회원의 쓰기 거절 규칙(정지 403, 탈퇴 401)은 그대로다. 없는 회원만 404로 바뀐다.
- 참여·신고·생성의 확인 순서는 그대로다. 투표 조회(`POLL_NOT_FOUND` 404) 다음에 회원 확인(`MEMBER_NOT_FOUND` 404)이다(`설계-결정.md` L60). 둘 다 404라 순서가 바뀌면 응답 코드가 바뀐다.
- `report`는 `vote`에 의존하지 않는다(ArchUnit). 서비스는 `*JpaRepository`와 다른 서비스에 의존하지 않는다.
- 가시성: 기본 package-private, 다른 도메인에 여는 것은 서비스 진입점과 다른 도메인이 실제로 부르는 Reader뿐이다.
- `docs/index.md`·`컨벤션.md`의 표기 규칙을 지킨다.

## 안전장치

- 이름 변경은 호출 지점을 놓치기 쉽다(pitfalls: 이슈 본문의 호출 지점 수). 타입 기준으로 다시 세고 끝난 뒤 옛 이름이 0건인지 `grep`으로 확인한다.
- 값 객체를 레포지토리에 넘길 때 파생 쿼리가 `@Embedded` record 비교로 안 되면 JPQL을 쓴다(`설계-결정.md` L29가 허용). 동시성·유니크 제약 테스트로 확인한다.
- 접근 제한자를 좁히면 다른 패키지의 테스트가 깨진다. 깨지면 테스트를 옮기지 않고 `public`을 유지할 이유를 적는다.
- 남은 `public`을 잡는 도구는 없다(ArchUnit 가시성 규칙은 후속). 점검은 `grep` 사용처 수로 한다.
- 회원 조회 통합이 재발급 401(탈퇴·폐기)을 건드리지 않게 `RefreshTokenServiceTest`를 돌려 확인한다.
- 없는 회원이 재발급·`GET /members/me`에서 404가 되면 프런트가 401로 로그인 상태를 판단하는 곳과 `인증.md` L174의 "401이면 로그아웃을 불러 쿠키를 정리" 계약이 어긋난다. 이번 PR에서는 정본에 404를 쓰는 데까지 하고, 프런트·쿠키 정리 계약은 후속 이슈로 남긴다(PR 본문에 적는다).

## 결과

`application`의 `*Reader` 조회 이름을 `get`(있어야 하는 단건, 없으면 예외)·`find`(없을 수 있는 단건, `Optional`)·`findAll`(컬렉션)로 정리하고, record 개행과 접근 제한자를 맞추고, 레포지토리가 `AnonId`를 그대로 받게 했다. 규칙은 `컨벤션.md`(조회 메서드 이름, record, 값 객체)와 `아키텍처.md`(가시성 표)에 옮겼다.

- **이름**: `MemberReader.getMember`(401)를 없애고 `find`(404)를 `get`으로 합쳤다. `PollReader.find`는 `getByPublicId`·`getById`, `ReportReader.find`는 `get`으로 바꿨다. 컬렉션 조회 4개는 `findAll…`이 됐다(`findAllByStatusAfter`, `findAllOthersOfSamePoll`, `findAllByReport`, `findAllPublicOlderThan`).
- **동작 변경 하나**: 요청한 회원이 DB에 없을 때 401이 아니라 404 `MEMBER_NOT_FOUND`다(쓰기·탈퇴·재발급·`GET /members/me`). 탈퇴 401, 정지 403은 그대로다. `인증.md`, `07-로그인과-회원.md`(R-MEMBER-11), `설계-결정.md`, `로드맵.md`(이력 주석)와 Swagger 선언·생성 문서를 같이 고쳤다.
- **record**: 파라미터 2개 이상인 한 줄 record 25개(파일 24개)를 한 줄에 하나씩으로 바꿨다.
- **접근 제한자**: 클래스 9개를 package-private으로, `Member.isWithdrawn`을 private으로, 메서드 8개를 package-private으로 좁혔다. 가시성 표에는 `ReportReader`를 예에서 일반화하고 package-private 행을 더했다.
- **값 객체**: `VoteJpaRepository.findByPollIdAndVoterAnonId(Long, AnonId)`로 바꿨다. `Reporter`는 `UUID`를 유지한다(`설계-결정.md` L49).
- **커밋**: 이름·동작(`9a8b525`), 문서·Swagger·생성 문서(`bac2106`), Reader 이름(`b2fb12b`), record(`e3be14e`), 접근 제한자(`e69e111`), 값 객체(`80ca286`), 리뷰 반영(`450a218`), 정본(`bc6a287`).

### 계획과 달라진 점

- **회원 조회 이름**: 처음 사용자가 "find로 하자"고 했으나, `find`=`Optional` 규칙과 충돌하고 쓰는 곳이 모두 예외를 원해서 기준을 "의미"(있어야 하는 것은 `get`)로 바꿔 `get`이 됐다(사용자 2026-11-04).
- **Swagger 선언 범위**: sketch는 투표 참여·신고 두 곳만 적었으나 `grep`으로 같은 선언을 가진 투표 생성·삭제, 내 정보, 탈퇴, 재발급까지 고쳤다.
- **테스트**: test-writer가 grep을 쓰지 못해 `ReportSubmitChainTest`, `VoteCommandServiceTest`의 기대값은 전체 `./gradlew test`에서 드러나 내가 고쳤다. 탈퇴 경로에 404를 확인하는 케이스는 새로 추가했다.
- **접근 제한자 후보**: 계획한 `poll` 값 객체 5개 등은 컴파일에서 막혀 되돌렸다(아래).

### public을 유지한 것과 이유

접근 제한자 점검은 후보를 `grep`으로 뽑고 컴파일로 판정했다. 아래는 `src/main`에서 패키지 안에서만 쓰이거나 쓰이는 곳이 없어 보이지만 `public`이다. 아키텍처 표의 "같은 패키지에서만 쓰면 package-private" 행에 대한 예외이고, 정본 표는 늘리지 않았다.

- **다른 패키지의 테스트가 직접 쓴다**: `VoteReader`(`VoteConcurrencyTest`), `AdminActionWriter`(`AdminReportActionRollbackTest`), `AnonCookieProvider`·`AnonCookie`(여러 도메인의 테스트), `FieldViolation`(`ReportReasonTest`, `DomainValidationReasonTest`). sketch 안전장치대로 테스트를 옮기지 않고 유지했다.
- **다른 패키지가 체이닝으로 쓴다**: `poll` 도메인 값 객체 `ClosesAt`, `OptionContent`, `PollQuestion`, `Position`, `PublicId`(`poll.getQuestion().value()` 형태).
- **이슈 #20이 `admin`에서 쓸 행위**: `Poll.restore`.
- **프레임워크가 만드는 진입점**은 점검에서 제외했다: 컨트롤러, `*WebApiDocs`, 필터·설정, `@ConfigurationProperties`, 응답 DTO, `main`.

### 리뷰

sketch-reviewer r1 FAIL(차단 4)→r2 PASS_WITH_FIXES→r3 PASS_WITH_FIXES(권고 반영), domain-reviewer PASS, code-reviewer PASS_WITH_FIXES(재발급 주석, `PollReader` javadoc을 반영했고 주석만 바뀌어 재리뷰는 생략: 리뷰 후 가독성 수정), fact-verifier r1 PASS_WITH_FIXES→r2 PASS.

### 한계와 후속

- **프런트·쿠키 계약**: 없는 회원이 재발급·`GET /members/me`에서 404가 되면 프런트가 401로 로그인 상태를 판단하는 곳과 `인증.md`의 "401이면 로그아웃을 불러 쿠키를 정리" 계약이 어긋난다. 404일 때도 쿠키는 지워지지 않는다. 실제로는 DB를 직접 지우지 않으면 생기지 않지만, 프런트 처리를 정하는 후속 이슈가 필요하다(PR 본문에 적는다).
- **401을 필터로 옮기는 안**: 사용자 의견이 있었으나 `인증.md`의 "상태는 서비스가 `validateActive()`로 확인" 결정과 요청별 예외(탈퇴·재발급 통과)를 필터가 알아야 해서 이번 범위 밖이다. 별도로 정한다.
- **탈퇴 401의 이유**: 설계 문서에서 직접 적은 근거를 찾지 못했다(`sketches/76`의 "탈퇴한 회원은 인증이 안 된다" 한 줄뿐). `인증.md`에 한 줄 적는 후속이 있다.
- **ArchUnit 가시성 규칙**: 남은 `public`을 잡는 도구가 없다. 이번에는 추가하지 않았다.
- **테스트 위치**: 위 "다른 패키지의 테스트가 직접 쓰는" 클래스는 테스트를 같은 패키지로 옮기면 `public`을 줄일 수 있다.
- **슬라이스 테스트**: `MemberMeWebApiTest`에 서비스가 404를 던질 때의 응답을 확인하는 케이스가 없다(서비스 계층 `MemberQueryServiceTest`가 확인한다).
