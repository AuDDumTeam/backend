package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.auth.api.AuthCookies;
import org.kwakmunsu.auddum.auth.infrastructure.JwtTokenProvider;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/polls/mine 을 실제 필터 체인(액세스 토큰 쿠키 -> 신원 인자 -> 서비스 -> DB)으로 확인한다(#163).
 * 쿠키가 회원 신원으로 이어져 그 회원의 투표만 나오는지, 비로그인 401, 형식이 틀린 cursor 400만 본다. 규칙의 전수 조합은 PollQueryServiceMyPollsTest가 맡는다.
 * MockMvc 요청은 테스트와 같은 스레드라 @Transactional로 만든 데이터가 요청에서 보이고, 끝나면 롤백된다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class MyPollListChainTest extends IntegrationTestSupport {

    static final String ALLOWED_ORIGIN = "http://localhost:3000";

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;
    final PollJpaRepository pollJpaRepository;

    @Test
    void 로그인한_회원이_투표를_만들면_내가_올린_목록에_나온다() throws Exception {
        var memberId = saveMember();
        var otherId = saveMember();
        savePoll(otherId);

        var created = mvcTester.post().uri("/api/v1/polls")
            .cookie(accessCookie(memberId))
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"question":"읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?","category":"SOME","options":["관심 없음","그냥 바쁜 것"]}
                """)
            .exchange();
        assertThat(created).hasStatus(201);
        var publicId = created.getResponse().getContentAsString();

        var result = mvcTester.get().uri("/api/v1/polls/mine").cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(publicId);
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("PUBLIC");
        assertThat(result).bodyJson().extractingPath("$.totalCount").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void 숨김_투표도_내_목록에_HIDDEN으로_나오고_totalCount에_포함한다() {
        var memberId = saveMember();
        var hidden = savePoll(memberId);
        hidden.hide();
        pollJpaRepository.saveAndFlush(hidden);

        var result = mvcTester.get().uri("/api/v1/polls/mine").cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(hidden.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("HIDDEN");
        assertThat(result).bodyJson().extractingPath("$.totalCount").isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401_UNAUTHENTICATED다() {
        var result = mvcTester.get().uri("/api/v1/polls/mine").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 형식이_틀린_cursor는_400_MALFORMED_REQUEST다() {
        var memberId = saveMember();

        var result = mvcTester.get().uri("/api/v1/polls/mine").queryParam("cursor", "!!not-a-cursor!!")
            .cookie(accessCookie(memberId))
            .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    private Long saveMember() {
        return memberJpaRepository.saveAndFlush(MemberFixture.createMember(Role.MEMBER)).getId();
    }

    private Poll savePoll(Long authorId) {
        var poll = Poll.create(authorId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것"), null, LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

    private Cookie accessCookie(Long memberId) {
        return new Cookie(AuthCookies.ACCESS_COOKIE,
            jwtTokenProvider.createAccessToken(memberId, Role.MEMBER, LocalDateTime.now(TimeConfig.KST)));
    }

}
