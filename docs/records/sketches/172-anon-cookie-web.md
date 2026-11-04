---
issue: 172
sources: [이슈 #172, features/03-참여하기.md, features/05-신고하기.md, engineering/동시성-중복-방지.md, engineering/설계-결정.md, engineering/아키텍처.md, engineering/인증.md]
features: [03-참여하기, 05-신고하기]
ids: [P-05, P-09, R-VOTE-05, R-REPORT-04]
decisions: []
status: 완료
---

# 익명 쿠키 처리를 컨트롤러로 옮기고 회원 상태 확인을 검증 단계로 옮긴다

> 이슈 하나의 의도와 접근, 결과를 남기는 이력 문서다. 정본이 아니다. 끝나면 규칙은 기능 페이지와 도메인 문서로 옮기고 여기에는 "왜 그렇게 했나"가 남는다.
> `features`에 적은 기능 페이지의 이력 표에도 이 문서 링크를 단다(양쪽을 맞춘다).

## 요구사항과 규칙

왜 하나: 이슈 #172. 신원 결정은 핵심 업무가 아닌데 리졸버 하나에 회원 상태 확인, 쿠키 해석·발급, 값 감싸기가 섞여 있고, 새 쿠키 값이 컨트롤러로 돌아가려고 서비스를 왕복한다.

소스와 달라진 점: API 동작은 그대로다. 내부 처리 순서와 구조 결정만 바뀌므로 규칙 표는 없고 아래 정본 문장을 바꾼다.

| 정본 | 지금 | 바꾼 뒤 |
|---|---|---|
| `동시성-중복-방지.md` 23행 | 신원 결정은 투표 조회 다음에 한다 | 익명 쿠키 해석은 컨트롤러가 서비스 호출 전에 한다. 회원 상태 확인은 투표 조회 다음에 한다 |
| `설계-결정.md` 서비스 인자 | `VoterCredential`, `ReporterCredential`을 받는다 | 쓰기 서비스는 `Voter`, `Reporter`를 받는다. `VoterCredential`은 조회 경로에만 남는다 |
| `설계-결정.md` 신고자 결정 | `ReporterResolver`가 확인 후 감싼다 | `ReportValidator`가 회원 상태를 확인하고, 신원은 컨트롤러가 만든다 |
| `설계-결정.md` 쿠키 값 | 컨트롤러가 받아 서비스에 넘긴다 | 컨트롤러가 받아 `RequesterResolver`로 해석한다 |
| `설계-결정.md` AnonId 생성자 | 쓰는 곳이 `VoterResolver`와 `Vote` 둘 | 쓰는 곳을 맞춘다 |
| `아키텍처.md` 109행 | `anon` 설명 | 새 클래스 위치를 맞춘다 |
| `features/05-신고하기.md` 38행 | `ReporterResolverTest` | 새 테스트 이름으로 바꾼다 |
| `인증.md` 117행(위조 처리) | 위조 WARN | 쿠키 해석이 투표 조회보다 먼저라 없는 투표에도 WARN이 남는다고 적는다. `동시성-중복-방지.md`와 서로 링크한다 |
| `설계-결정.md` 85행(조회자 신원) | 해석은 `VoterResolver`가 한다 | 조회 경로는 그대로라 바꾸지 않는다 |

## 건드리는 것

- 새로 만든다: `support.web.Requester`(memberId, anonId, newCookieValue), `support.web.RequesterResolver`, `Voter.of(Long, UUID)`, `Reporter.of(Long, UUID)`, `VoteValidator.validateVoter`, `ReportValidator.validateReporter`. 회원이면 `anonId`와 `newCookieValue`는 항상 null이다(`Voter`·`Reporter`의 "정확히 하나" 불변식)
- 바꾼다: `VoteWebApi`, `ReportWebApi`, `VoteCommandService.castVote(PollChoice, Voter)`, `ReportCommandService.submit(String, ReportReason, Reporter)`(반환 없음), `VoteParticipator.participate`(`Voter`를 받는다), `CastResult`(`newCookieValue` 제거), `VoterResolver`(`resolve`와 회원 확인 제거, `find`만 남긴다)
- 지운다: `ReporterResolver`, `ResolvedReporter`, `ReporterCredential`, `SubmitResult`, `ResolvedVoter`
- 건드리지 않는다: `VoteParticipator`를 서비스에 펼치는 일(사용자가 미룸, 2026-11-02), 트랜잭션 경계, 조회 경로의 `Viewer`·`VoterCredential`·`VoterResolver.find`, 쿠키 속성, DB, API 응답과 생성 문서

## 접근

고른 안: 쿠키 해석은 도메인을 모르는 `support.web`의 `RequesterResolver`가 하고, 컨트롤러가 결과로 `Voter`·`Reporter`를 만들어 서비스에 넘긴다. 성공하면 컨트롤러가 새 쿠키를 쓴다.

```java
Requester requester = requesterResolver.resolve(memberId, anonCookieValue);
CastResult cast = voteCommandService.castVote(
    new PollChoice(pollId, request.position()),
    Voter.of(requester.memberId(), requester.anonId())
);
if (requester.newCookieValue() != null) {
    anonCookies.write(response, requester.newCookieValue());
}
```

회원이면 쿠키를 보지 않고, 비회원이면 지금의 `AnonIdentityResolver.resolve`를 쓴다.

서비스는 흐름만 남긴다.

```java
public void submit(String pollPublicId, ReportReason reason, Reporter reporter) {
    Poll poll = pollReader.getPublic(pollPublicId);

    reportValidator.validateReporter(reporter);
    reportValidator.validateNotReported(poll, reporter);
    reportWriter.submit(poll, reporter, reason);
}
```

참여도 같은 순서다. `voteValidator.validateVoter(voter)` 뒤에 `voteParticipator.participate`를 부른다.

대화에서 예시로 보인 `validateSubmittable` 하나로 합치지 않고 둘로 나눴다. 참여와 모양이 같아지고 기존 `validateNotReported` 테스트를 그대로 쓸 수 있어서다.

검토한 대안:

- `ReportSubmitter`를 만들어 서비스에서 숨긴다: 쿠키가 서비스를 왕복하는 문제가 남는다.
- `ArgumentResolver`로 `Requester`를 주입한다: 쿠키는 요청이 성공했을 때만 써야 하므로 새 쿠키 값을 결국 컨트롤러가 알아야 하고, 사용처가 두 곳이라 `설계-결정.md`가 이미 모으지 않기로 했다.
- 컨트롤러마다 분기를 직접 쓴다: 같은 분기가 두 컨트롤러에 복제된다.

질문과 답:

- 쿠키 처리를 컨트롤러 쪽으로 옮기는가? 그렇다(사용자, 2026-11-02).
- 조회 경로도 옮기는가? 옮기지 않는다. poll이 쿠키를 해석하지 않고 vote가 포트로 해석하는 구조(`Viewer`, `ParticipationReader`, #63)는 다른 결정이고, 쿠키 왕복 문제도 없다(사용자, 2026-11-02).
- 회원 상태 확인과 중복 확인을 `validateSubmittable` 하나로 합치는가, 나누는가? 나눈다. 참여와 모양이 같아지고 기존 테스트를 그대로 쓸 수 있다(사용자, 2026-11-02).
- 없는 투표에 위조 쿠키로 요청하면 새로 남는 WARN 로그를 받아들이는가? 받아들인다. 응답은 같다(사용자, 2026-11-02).
- `Reporter.of(Long, UUID)`를 추가하는가? 추가한다. 컨트롤러에 회원/비회원 분기가 생기지 않는다(사용자, 2026-11-02).

## 구조

- 단위 테스트(`support/web/RequesterResolverTest`): 회원은 쿠키를 보지 않음, 유효한 쿠키, 쿠키 없음, 위조 쿠키와 WARN 로그. `VoterResolverTest`·`ReporterResolverTest`의 쿠키 케이스를 옮긴다.
- 단위 테스트(`vote/domain/VoterTest`, `report/domain/ReporterTest`): `Voter.of`, `Reporter.of`. `RequesterResolverTest`는 회원일 때 `anonId`·`newCookieValue`가 null임을 단언한다.
- 구현 클래스 테스트(`vote/application/VoteValidatorTest`, `report/application/ReportValidatorTest`): 정상 통과, 정지 403, 탈퇴·없음 401, 비회원은 회원 조회 없음. 두 Resolver 테스트의 회원 케이스를 옮긴다.
- 웹 테스트(`VoteWebApiTest`): 서비스 호출 인자를 `Voter`로 바꾸고, 쿠키는 성공했을 때만 내려가는지 본다.
- MockMvc 체인 테스트(`ReportSubmitChainTest`), 서비스·동시성 테스트(`VoteCommandServiceTest`, `VoteConcurrencyTest`), 픽스처(`VoteFixture`): 응답과 오류 우선순위 단언은 그대로 두고 호출 배선만 맞춘다. 이 테스트들이 동작이 그대로라는 안전망이다.
- 조회: `VoterResolverTest`의 `find` 케이스는 그대로 둔다.

## 순서와 테스트

1. 먼저 쓸 테스트(red): `RequesterResolverTest`, `VoteValidatorTest`, `ReportValidatorTest`, `Voter.of` 테스트. 대상이 없어 컴파일부터 실패한다.
2. 새 클래스를 만들어 green으로 만든다.
3. 신고를 옮긴다. 체인 테스트의 단언은 고치지 않는다.
4. 참여를 옮긴다. 웹 테스트는 호출 인자만 바꾼다.
5. 쓰지 않게 된 클래스를 지우고 정본 문장을 맞춘다. `./gradlew build`로 생성 문서 변화가 없는지 확인한다.

## 지킬 규칙

- 오류 우선순위: 투표 조회(404) → 회원 상태(403·401) → 기존 참여·신고(409) → 마감(409) → 선택지(400). `features/03-참여하기.md`, `features/05-신고하기.md`
- P-05: 정지 회원은 쓸 수 없다. 조회는 상태를 확인하지 않는다.
- P-09: 위조된 쿠키는 오류 없이 새로 발급하고 WARN을 남긴다. 값 원문은 남기지 않는다(`인증.md`).
- `인증.md`: 요청이 오류로 끝나면 새 쿠키를 내리지 않고, 회원은 쿠키를 보지 않는다.
- `support`는 도메인에 의존하지 않는다(ArchUnit).
- `Vote`와 `Report`는 값 객체를 공유하지 않는다(`설계-결정.md`).

## 안전장치

- 로그 변화: 쿠키 해석이 투표 조회보다 먼저 일어나, 없는 투표에 위조 쿠키로 요청하면 지금은 없던 WARN이 남는다. 응답은 같다. 받아들이고 `동시성-중복-방지.md`에 적는다.
- 회원 상태 확인 누락: 서비스가 호출을 빠뜨리면 정지 회원이 쓰게 된다. 기존 `ReportSubmitChainTest`와 `VoteCommandServiceTest`의 정지·탈퇴 케이스가 막는다. 있는지 먼저 확인하고 없으면 추가한다.
- 트랜잭션: 쿠키 해석이 트랜잭션 밖으로 나가지만 DB를 쓰지 않으므로 경계의 의미는 같다.

## 결과

- 익명 쿠키 해석과 발급을 서비스에서 컨트롤러로 옮겼다. `support.web.RequesterResolver`가 `Requester`(회원 ID 또는 익명 UUID, 새로 발급할 쿠키 값)를 만들고, 컨트롤러가 `Voter.of`/`Reporter.of`로 감싸 서비스에 넘긴 뒤 새 쿠키가 있으면 쓴다. API 응답은 그대로다.
- 회원 상태 확인은 `VoteValidator.validateVoter`, `ReportValidator.validateReporter`로 옮겼다. 서비스는 투표 조회 다음에 부르므로 오류 우선순위(404 → 403·401 → 409 → 400)는 그대로다.
- 지운 것: `ResolvedVoter`, `ReporterResolver`, `ResolvedReporter`, `ReporterCredential`, `SubmitResult`. `VoterCredential`은 조회에만 남았다.
- 리뷰 반영: 신고 체인 테스트에 정지(이미 신고)·탈퇴·없는 회원 케이스를 추가했고, 쓰이지 않는 import 3개를 지웠다. `Reporter.of`는 `Voter.of`와 짝으로 두기로 한 결정(2026-11-02)대로 유지했다.
- 한계: 쿠키 해석이 투표 조회보다 앞서 없는 투표에 위조 쿠키를 보내도 WARN이 남는다(응답은 404로 같다). `VoteWebApiTest`는 `RequesterResolver`를 목으로 둔다. 프런트 쪽은 확인할 수 없다.
- 후속: 익명 쓰기 컨트롤러가 세 번째로 생기면 요청자 해석을 `HandlerMethodArgumentResolver`로 모으는 것을 검토한다.
