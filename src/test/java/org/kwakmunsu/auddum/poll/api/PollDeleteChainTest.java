package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.report.ReportFixture.ALLOWED_ORIGIN;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * DELETE /api/v1/polls/{pollId} 의 공개 ID 형식 오류를 실제 필터 체인과 DB로 확인한다(#171).
 * 형식이 틀린 ID는 없는 투표와 같이 404 POLL_NOT_FOUND다. 삭제는 회원 상태(401·403)가 먼저다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class PollDeleteChainTest extends VoteIntegrationTestSupport {

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest
    @ValueSource(strings = {"short", "ABCDEFGHIJK!", "ABCDEFGHIJKLM"})
    void 형식이_틀린_투표_ID를_삭제하면_404_POLL_NOT_FOUND다(String malformed) {
        var memberId = saveMember();

        var result = delete(malformed, accessCookie(memberId));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void 없는_투표_ID를_삭제하면_404_POLL_NOT_FOUND다() {
        var result = delete("abcdefghijkl", accessCookie(saveMember()));

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("POLL_NOT_FOUND");
    }

    @Test
    void 미로그인이_형식이_틀린_투표_ID를_삭제하면_회원_상태가_먼저라_401_UNAUTHENTICATED다() {
        var result = mvcTester.delete().uri("/api/v1/polls/short").header("Origin", ALLOWED_ORIGIN).exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 정지_회원이_형식이_틀린_투표_ID를_삭제하면_회원_상태가_먼저라_403_MEMBER_SUSPENDED다() {
        var suspendedId = saveSuspendedMember();

        var result = delete("short", accessCookie(suspendedId));

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
    }

    private MvcTestResult delete(String publicId, Cookie cookie) {
        return mvcTester.delete()
            .uri("/api/v1/polls/" + publicId)
            .header("Origin", ALLOWED_ORIGIN)
            .cookie(cookie)
            .exchange();
    }

    private Cookie accessCookie(Long memberId) {
        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, LocalDateTime.now(TimeConfig.KST)));
    }

}
