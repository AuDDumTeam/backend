---
issue: 160
sources: [이슈 #160, features/07-로그인과-회원.md, engineering/아키텍처.md, engineering/인증.md, records/sketches/76-poll-author-delete.md]
features: [07-로그인과-회원]
ids: [R-MEMBER-11, P-05]
decisions: []
status: 완료
---

# 회원 상태 검증을 MemberValidator.validateActive로 분리한다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 정본으로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.

## 요구사항과 규칙

`memberReader.getActiveMember(id)`를 반환값을 버리고 예외 검증으로만 쓰는 곳이 4곳이라(이슈 본문은 5곳이라 썼지만 실제는 3개 파일 4곳), 이름(get)과 쓰임(검증)이 어긋난다(이슈 #160, #76 작업 중 짚임). 검증은 `Validator` 역할 클래스로 옮긴다(`컨벤션.md`의 구현 계층 이름 규칙).

소스와 달라진 점: 이슈는 `MemberReader.getActiveMember`를 그대로 두라고 했으나 호출자가 없어지므로 지운다(질문 1). 동작과 규칙은 바뀌지 않는다. 정지 403 `MEMBER_SUSPENDED`, 탈퇴·없음 401은 R-MEMBER-11 그대로이고, 기존 테스트가 안전망이다. 새 규칙이 없어 규칙 표는 없다.

바꾸는 문장(정본):

- `아키텍처.md` 3절: "쓰기 서비스가 `MemberReader.getActiveMember()`로 한다" → `MemberValidator.validateActive()`로 한다
- 그 밖에 `getActiveMember`가 나오는 모든 정본 줄(grep으로 센다)을 같은 이름으로 맞춘다: `인증.md` 55·68·70·74행, `설계-결정.md` 15·101행, `동시성-중복-방지.md` 24행

## 건드리는 것

- 새로 만든다: `member.application.MemberValidator`(`validateActive(Long memberId)`)
- 바꾼다: 호출 지점 3개 파일 4곳. `PollCommandService` 2곳(투표 생성, 작성자 삭제), `VoterResolver`, `ReporterResolver`
- 바꾼다(테스트 배선): `VoterResolverTest`, `ReporterResolverTest`가 `MemberReader`를 직접 만들어 주입하므로 `MemberValidator`로 배선을 맞춘다. 단언은 그대로다.
- 지운다: `MemberReader.getActiveMember`(호출자가 없어진다). `MemberReader`의 클래스 javadoc(검증을 위한 조회라는 설명)과 `getMember`·`getNotWithdrawnMember` javadoc이 지운 메서드를 가리키면 고친다
- 문서: 위 정본 4개. `docs/records/sketches/76-*.md`, `155-*.md` 같은 지난 sketch는 이력이라 고치지 않는다.
- 건드리지 않는다: API, 오류 코드, DB, `Member.validateActive()` 도메인 규칙, 정지 회원이 할 수 있는 경로(`getMember`·`getNotWithdrawnMember`), 생성 문서

## 접근

`MemberValidator`는 `member.application`의 `@Component`다. `validateActive(memberId)`가 `MemberReader.getMember`로 회원을 가져와 `member.validateActive()`를 부르는 검증(없음·탈퇴 401, 정지 403)을 하고 반환값은 없다. 다른 도메인은 `MemberReader`처럼 구현 계층 클래스로 부르므로 ArchUnit 규칙(서비스 간 의존 금지, 다른 도메인 repository 금지)에 걸리지 않는다. `AdminAccessValidator`가 `MemberReader`에 기대는 것과 같은 모양이다. 호출 지점 교체 뒤 `MemberReader.getActiveMember`는 쓰는 곳이 없으므로 지운다(질문 1).

검토한 대안:

- `getActiveMember` 이름만 `validateActiveMember`로 바꿔 `MemberReader`에 둔다: Reader에 검증 메서드가 섞여 역할 구분이 흐려진다. 이슈가 Validator 분리를 명시했다.
- `MemberValidator`가 repository를 직접 읽어 검증을 복제한다: 상태 규칙이 두 곳에 생긴다.

질문과 답:

- 질문 1. 호출 지점 교체 뒤 `MemberReader.getActiveMember`의 호출자가 `MemberValidator`뿐이 된다. 이슈는 "반환값이 필요한 곳을 위해 그대로 둔다"고 했는데, 지금은 반환값이 필요한 곳이 없다. 어떻게 할까? 답: 쓰이는 곳이 없으면 지운다(사용자, 2026-11-02). 이슈의 "그대로 둔다"와 달라진 점이다. 그래서 `MemberValidator`는 `getMember` + `member.validateActive()`로 직접 검증한다.
- 질문 2. 정본 갱신 범위를 이슈에 적힌 `아키텍처.md`에서 `인증.md`·`설계-결정.md`·`동시성-중복-방지.md`까지 넓혀도 되는가? 같은 이름이 남으면 문서가 코드와 어긋난다(`CLAUDE.md` 규칙). 추천: 넓힌다. 답: 넓힌다(사용자, 2026-11-02).

## 구조

- `src/main/java/org/kwakmunsu/auddum/member/application/MemberValidator.java`
- 호출 지점: `poll/application/PollCommandService`, `vote/application/VoterResolver`, `report/application/ReporterResolver`
- 테스트: `src/test/java/org/kwakmunsu/auddum/member/application/MemberValidatorTest.java`(신규), 기존 `VoterResolverTest`·`ReporterResolverTest` 배선 수정

## 순서와 테스트

계층은 `테스트-전략.md` 표를 따른다(pitfalls: 계층과 무엇을 보는지를 적는다).

1. `MemberValidatorTest`(서비스 단위, `MemberReader`는 실제 객체 + repository 목, 기존 `VoterResolverTest`와 같은 방식)를 먼저 쓴다. 정상 통과, 정지 403 `MEMBER_SUSPENDED`, 탈퇴 401, 없는 회원 401을 본다. 클래스가 없으므로 red는 컴파일 실패다.
2. `MemberValidator`를 만든다.
3. 호출 지점 4곳을 교체하고 `VoterResolverTest`·`ReporterResolverTest` 배선을 맞춘다. 기존 단언은 바꾸지 않는다. `PollCommandService` 테스트는 변경 없이 통과해야 한다.
4. 정본 문서를 맞춘다(wrap).
5. `./gradlew build`(ArchUnit 포함).

## 지킬 규칙

- R-MEMBER-11, P-05: 정지 403, 탈퇴·없음 401은 그대로.
- 컨벤션: 테스트 이름은 한글, `@DisplayName` 금지. 구현 계층 이름은 `Validator`(검증).
- 동작은 바꾸지 않는다. 기존 테스트의 단언을 고쳐서 통과시키지 않는다.

## 안전장치

- 호출 교체 중 검증을 빠뜨리는 것: 4곳을 `grep getActiveMember`로 세어 교체하고(끝나면 src에 0건이어야 한다), 기존 서비스·resolver 테스트(정지·탈퇴 거절)가 실패하는지로 확인한다.
- 트랜잭션: `MemberReader`처럼 자체 트랜잭션 경계를 두지 않아 호출한 서비스의 트랜잭션에 참여한다.

## 결과

`member.application.MemberValidator.validateActive(Long memberId)`를 만들고, `getActiveMember`를 반환값 없이 검증으로만 쓰던 3개 파일 4곳(`PollCommandService` 2곳, `VoterResolver`, `ReporterResolver`)을 바꿨다. `MemberValidator`는 `MemberReader.getMember`로 회원을 가져와 `member.validateActive()`를 부른다. 정지 403 `MEMBER_SUSPENDED`, 탈퇴·없음 401은 그대로다. `MemberReader.getActiveMember`는 호출자가 없어져 지웠고(질문 1), 클래스 javadoc은 조회 중심으로 고쳤다.

- 테스트: `MemberValidatorTest` 4개(정상·정지·탈퇴·없음)를 먼저 썼다. `VoterResolverTest`·`ReporterResolverTest`는 배선만 바꾸고 단언은 그대로다. `./gradlew build`(ArchUnit 포함)를 통과했다.
- 정본: `getActiveMember`가 나오던 `아키텍처.md`·`인증.md`·`설계-결정.md`·`동시성-중복-방지.md` 8줄을 `validateActive`로 맞췄다(질문 2). 기능 페이지 `07-로그인과-회원.md` 이력 표에 한 줄을 더했다.
- 계획과 달라진 점: 이슈 본문은 "5곳"이라 했으나 실제는 4곳이다. 이슈의 "`getActiveMember`를 그대로 둔다"와 달리 지웠다. 리팩터링은 보통 정본을 고치지 않지만 같은 이름이 남으면 문서가 어긋나므로 고쳤다.
- 리뷰: domain-reviewer PASS, code-reviewer PASS_WITH_FIXES(`MemberReader` javadoc, 반영함), fact-verifier PASS_WITH_FIXES(이력 표에 메서드 이름, 반영함. 한 줄 문구 수정이라 재검증은 사용자 판단으로 생략).
- 한계와 후속: 없음. 정지 회원이 할 수 있는 경로(`getMember`·`getNotWithdrawnMember`)는 건드리지 않았다.
