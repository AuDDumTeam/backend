package org.kwakmunsu.auddum.vote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymousVoter;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberVoter;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.poll.application.dto.OptionTally;
import org.kwakmunsu.auddum.poll.application.dto.PollTally;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.support.web.Requester;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * POST /api/v1/polls/{pollId}/votes 웹 계층(서비스와 요청자 해석은 목).
 * 컨트롤러는 쿠키 값과 회원 ID를 요청자 해석에 넘기고, 결과로 만든 Voter를 서비스에 넘기며, 요청자가 새 쿠키를 받았을 때만 Set-Cookie를 붙인다.
 * 쿠키 해석 규칙(위조·발급)은 RequesterResolverTest가 확인한다.
 */
class VoteWebApiTest extends WebApiTestSupport {

    private static final String POLL_ID = "aB3dE5fG7hJk";
    private static final String URI = "/api/v1/polls/" + POLL_ID + "/votes";
    private static final UUID ANON_ID = UUID.fromString("0b9a3c3e-6c1e-4f55-9a43-2f7e3b6d9a10");
    private static final String ISSUED_COOKIE = "v1." + ANON_ID + ".c2lnbmF0dXJl";
    private static final long ONE_YEAR_SECONDS = 365L * 24 * 60 * 60;

    // ---- 상태 코드와 본문 ----

    @Test
    void 참여는_201과_myChoice_result를_내리고_내부_식별자와_득표율은_없다() {
        given(requesterResolver.resolve(7L, null)).willReturn(new Requester(7L, null, null));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 2)), eq(memberVoter(7L))))
            .willReturn(castResult());

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .hasPathSatisfying("$.myChoice", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.total", v -> v.assertThat().isEqualTo(42))
            .hasPathSatisfying("$.result.options[0].position", v -> v.assertThat().isEqualTo(1))
            .hasPathSatisfying("$.result.options[0].count", v -> v.assertThat().isEqualTo(10))
            .hasPathSatisfying("$.result.options[1].position", v -> v.assertThat().isEqualTo(2))
            .hasPathSatisfying("$.result.options[1].count", v -> v.assertThat().isEqualTo(25))
            .hasPathSatisfying("$.result.options[2].count", v -> v.assertThat().isEqualTo(7))
            .doesNotHavePath("$.result.options[0].id")
            .doesNotHavePath("$.result.options[0].optionId")
            .doesNotHavePath("$.result.options[0].ratio")
            .doesNotHavePath("$.result.options[0].percent");
    }

    // ---- 신원 입력 ----

    @Test
    void 로그인하지_않아도_참여할_수_있고_요청자_해석에_회원_ID_null을_넘긴다() {
        given(requesterResolver.resolve(null, null)).willReturn(new Requester(null, ANON_ID, ISSUED_COOKIE));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(anonymousVoter(ANON_ID))))
            .willReturn(castResult());

        mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(requesterResolver).resolve(null, null);
        verify(voteCommandService).castVote(eq(choice(POLL_ID, 1)), eq(anonymousVoter(ANON_ID)));
    }

    @Test
    void auddum_anon_쿠키_값을_그대로_요청자_해석에_넘긴다() {
        given(requesterResolver.resolve(null, ISSUED_COOKIE)).willReturn(new Requester(null, ANON_ID, null));
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), any(Voter.class)))
            .willReturn(castResult());

        mvcTester.post()
            .uri(URI)
            .cookie(new Cookie(AnonCookieName.VALUE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);

        verify(requesterResolver).resolve(null, ISSUED_COOKIE);
        verify(voteCommandService).castVote(eq(choice(POLL_ID, 1)), eq(anonymousVoter(ANON_ID)));
    }

    // ---- Set-Cookie ----

    @Test
    void 요청자가_새_쿠키를_받았으면_Set_Cookie에_속성과_함께_내린다() {
        given(requesterResolver.resolve(null, null)).willReturn(new Requester(null, ANON_ID, ISSUED_COOKIE));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(anonymousVoter(ANON_ID))))
            .willReturn(castResult());

        var result = mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).startsWith(AnonCookieName.VALUE + "=" + ISSUED_COOKIE)
            .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/;", "Max-Age=" + ONE_YEAR_SECONDS);
    }

    @Test
    void 유효한_쿠키로_참여하면_Set_Cookie를_내리지_않는다() {
        given(requesterResolver.resolve(null, ISSUED_COOKIE)).willReturn(new Requester(null, ANON_ID, null));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(anonymousVoter(ANON_ID))))
            .willReturn(castResult());

        var result = mvcTester.post()
            .uri(URI)
            .cookie(new Cookie(AnonCookieName.VALUE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void 회원이_참여하면_쿠키가_와도_Set_Cookie를_내리지_않는다() {
        given(requesterResolver.resolve(7L, ISSUED_COOKIE)).willReturn(new Requester(7L, null, null));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 1)), eq(memberVoter(7L))))
            .willReturn(castResult());

        var result = mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .cookie(new Cookie(AnonCookieName.VALUE, ISSUED_COOKIE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ---- 서비스 내부 오류 (오류 코드→상태 매핑은 전역 처리기와 체인 테스트가 확인한다) ----

    @Test
    void 서비스_내부_오류는_500_INTERNAL_ERROR이고_Set_Cookie를_내리지_않는다() {
        given(requesterResolver.resolve(null, null)).willReturn(new Requester(null, ANON_ID, ISSUED_COOKIE));
        given(voteCommandService.castVote(argThat(x -> x.position() == 1), any(Voter.class)))
            .willThrow(new IllegalStateException("db down"));

        var result = mvcTester.post()
            .uri(URI)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 1}")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INTERNAL_ERROR"));
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    // ---- 요청 본문 ----

    @Test
    void 알_수_없는_요청_필드는_무시하고_참여한다() {
        given(requesterResolver.resolve(7L, null)).willReturn(new Requester(7L, null, null));
        given(voteCommandService.castVote(eq(choice(POLL_ID, 2)), eq(memberVoter(7L))))
            .willReturn(castResult());

        mvcTester.post()
            .uri(URI)
            .with(loginAs(7L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"position\": 2, \"optionId\": 999, \"memberId\": 1}")
            .assertThat()
            .hasStatus(HttpStatus.CREATED);
    }

    private CastResult castResult() {
        var tally = PollTally.of(42, List.of(OptionTally.of(1, 10), OptionTally.of(2, 25), OptionTally.of(3, 7)));

        return CastResult.of(2, tally);
    }

}
