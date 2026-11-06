package org.kwakmunsu.auddum.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.poll.application.dto.CursorPaging;
import org.kwakmunsu.auddum.poll.application.dto.ParticipatedPoll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.application.VoteFixture;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내가 참여한 투표 목록(PollQueryService#getVotedPolls, GET /polls/voted)의 통합 테스트(H2). 이슈 #163 sketch(Q2-A, Q3-B, Q4-A, Q5-A, Q7-B, Q11) 기준이다.
 * 서비스는 포트를 두 번 부를 뿐이라 쪽 크기, 커서, 쪽 경계, totalCount의 정밀 케이스는 포트 테스트(VoteParticipatedPollReaderTest)가 맡고
 * 여기는 범위, 참여 순서, 포트 결과 전달 대표 건, 회원 상태, 빈 목록만 둔다.
 */
@RequiredArgsConstructor
@Transactional
class PollQueryServiceVotedPollsTest extends VoteIntegrationTestSupport {

    final PollQueryService pollQueryService;
    final EntityManager entityManager;

    // ---- 범위: 본인 참여, 공개만 ----

    @Test
    void 본인이_참여한_공개_투표는_목록에_나온다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var voteId = cast(poll, memberId);

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId()).containsExactly(poll.getId());
        assertThat(page.items().getContent()).extracting(ParticipatedPoll::voteId).containsExactly(voteId);
    }

    @Test
    void 다른_회원의_참여는_목록에_나오지_않는다() {
        var memberId = saveMember();
        var otherId = saveMember();
        var mine = savePoll(VoteFixture.openPoll(saveMember()));
        var others = savePoll(VoteFixture.openPoll(saveMember()));
        cast(mine, memberId);
        cast(others, otherId);

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId())
            .containsExactly(mine.getId())
            .doesNotContain(others.getId());
    }

    @Test
    void 비회원_익명_참여는_회원_목록에_나오지_않는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var anonymousPoll = savePoll(VoteFixture.openPoll(saveMember()));
        cast(poll, memberId);
        saveVote(anonymousPoll, 1, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId()).containsExactly(poll.getId());
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    void 참여한_숨김_투표는_목록에_나오지_않는다() {
        var memberId = saveMember();
        var visible = savePoll(VoteFixture.openPoll(saveMember()));
        var hidden = savePoll(VoteFixture.openPoll(saveMember()));
        cast(visible, memberId);
        cast(hidden, memberId);
        hidden.hide();
        flushAndClear();

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId()).containsExactly(visible.getId());
    }

    @Test
    void 참여한_삭제_투표는_목록에_나오지_않는다() {
        var memberId = saveMember();
        var visible = savePoll(VoteFixture.openPoll(saveMember()));
        var deleted = savePoll(VoteFixture.openPoll(saveMember()));
        cast(visible, memberId);
        cast(deleted, memberId);
        deleted.deleteByAdmin();
        flushAndClear();

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId()).containsExactly(visible.getId());
    }

    @Test
    void 참여한_투표가_없으면_빈_목록이고_totalCount는_0이며_다음_쪽이_없다() {
        var memberId = saveMember();
        cast(savePoll(VoteFixture.openPoll(saveMember())), saveMember());

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).isEmpty();
        assertThat(page.items().hasNext()).isFalse();
        assertThat(page.totalCount()).isZero();
    }

    // ---- 정렬: 참여 순서의 역순(vote.id 내림차순) ----

    @Test
    void 투표_id_순서와_참여_순서가_엇갈릴_때_참여_순서로_나온다() {
        var memberId = saveMember();
        var first = savePoll(VoteFixture.openPoll(saveMember()));
        var second = savePoll(VoteFixture.openPoll(saveMember()));
        var third = savePoll(VoteFixture.openPoll(saveMember()));
        var secondVoteId = cast(second, memberId);
        var thirdVoteId = cast(third, memberId);
        var firstVoteId = cast(first, memberId);

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId())
            .containsExactly(first.getId(), third.getId(), second.getId());
        assertThat(page.items().getContent()).extracting(ParticipatedPoll::voteId)
            .containsExactly(firstVoteId, thirdVoteId, secondVoteId);
    }

    // ---- 포트 결과 전달(대표 건): 정밀 경계는 포트 테스트 ----

    @Test
    void 포트가_준_다음_쪽_여부와_totalCount를_그대로_전달한다() {
        var memberId = saveMember();
        castOnNewPolls(memberId, 3);
        flushAndClear();

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 2));

        assertThat(page.items().getContent()).hasSize(2);
        assertThat(page.items().hasNext()).isTrue();
        assertThat(page.totalCount()).isEqualTo(3L);
    }

    @Test
    void 커서로_이어받은_마지막_쪽도_전체_totalCount를_전달한다() {
        var memberId = saveMember();
        var voteIds = castOnNewPolls(memberId, 3);
        flushAndClear();

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(voteIds.get(1), 2));

        assertThat(page.items().getContent()).extracting(ParticipatedPoll::voteId).containsExactly(voteIds.get(0));
        assertThat(page.items().hasNext()).isFalse();
        assertThat(page.totalCount()).isEqualTo(3L);
    }

    // ---- 회원 상태 (Q5-A) ----

    @Test
    void 정지_회원도_자기_참여_목록을_조회한다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        cast(poll, memberId);
        suspend(memberId);

        var page = pollQueryService.getVotedPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.items().getContent()).extracting(item -> item.poll().getId()).containsExactly(poll.getId());
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    void 탈퇴한_회원은_401_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.now(TimeConfig.KST));
        var withdrawnId = memberJpaRepository.saveAndFlush(withdrawn).getId();

        assertThatThrownBy(() -> pollQueryService.getVotedPolls(withdrawnId, new CursorPaging(null, 20)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void DB에_없는_회원은_404_MEMBER_NOT_FOUND다() {
        assertThatThrownBy(() -> pollQueryService.getVotedPolls(Long.MAX_VALUE, new CursorPaging(null, 20)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    // ---- 보조 ----

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

}
