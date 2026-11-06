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
 * GET /api/v1/polls/voted 를 실제 필터 체인(액세스 토큰 쿠키 -> 신원 인자 -> 서비스 -> 포트 구현 -> DB)으로 확인한다(#163).
 * 참여 API로 참여한 투표가 그 회원의 목록에 참여 순서의 역순으로 나오는지, 비로그인 401, 형식이 틀린 cursor 400만 본다.
 * 규칙의 전수 조합은 PollQueryServiceVotedPollsTest가 맡는다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class VotedPollListChainTest extends IntegrationTestSupport {

    static final String ALLOWED_ORIGIN = "http://localhost:3000";

    final MockMvcTester mvcTester;
    final JwtTokenProvider jwtTokenProvider;
    final MemberJpaRepository memberJpaRepository;
    final PollJpaRepository pollJpaRepository;

    @Test
    void 참여한_투표가_참여_순서의_역순으로_내가_참여한_목록에_나온다() throws Exception {
        var memberId = saveMember();
        var authorId = saveMember();
        var first = savePoll(authorId);
        var second = savePoll(authorId);
        savePoll(authorId); // 참여하지 않은 투표는 나오지 않는다

        castAsMember(first, memberId);
        castAsMember(second, memberId);

        var result = mvcTester.get().uri("/api/v1/polls/voted").cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(second.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.items[1].id").isEqualTo(first.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.totalCount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void 숨겨진_투표에_참여했다면_목록과_totalCount에서_빠진다() throws Exception {
        var memberId = saveMember();
        var authorId = saveMember();
        var visible = savePoll(authorId);
        var willHide = savePoll(authorId);
        castAsMember(visible, memberId);
        castAsMember(willHide, memberId);
        willHide.hide();
        pollJpaRepository.saveAndFlush(willHide);

        var result = mvcTester.get().uri("/api/v1/polls/voted").cookie(accessCookie(memberId)).exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(visible.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.totalCount").isEqualTo(1);
    }

    @Test
    void 로그인하지_않으면_401_UNAUTHENTICATED다() {
        var result = mvcTester.get().uri("/api/v1/polls/voted").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 형식이_틀린_cursor는_400_MALFORMED_REQUEST다() {
        var memberId = saveMember();

        var result = mvcTester.get().uri("/api/v1/polls/voted").queryParam("cursor", "!!not-a-cursor!!")
            .cookie(accessCookie(memberId))
            .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    private void castAsMember(Poll poll, Long memberId) {
        var cast = mvcTester.post().uri("/api/v1/polls/{pollId}/votes", poll.getPublicId().value())
            .cookie(accessCookie(memberId))
            .header("Origin", ALLOWED_ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"position":1}
                """)
            .exchange();
        assertThat(cast).hasStatus(201);
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
