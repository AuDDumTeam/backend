package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.NOW;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymousVoter;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.choice;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.memberVoter;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 참여 서비스의 통합 테스트(H2). 기준은 R-VOTE-02, R-VOTE-05, P-08·P-11·P-12, 참여 처리 순서, 익명 식별 쿠키(중복 참여 방지).
 * 유니크 위반처럼 트랜잭션 경계가 필요한 시나리오는 VoteWriterConflictTest가 맡는다.
 */
@RequiredArgsConstructor
@Transactional
class VoteCommandServiceTest extends VoteIntegrationTestSupport {

    final VoteCommandService voteCommandService;
    final AnonCookieProvider anonCookieProvider;
    final EntityManager entityManager;

    // ---- 저장 성공과 응답 (본문 { myChoice, result }) ----

    @Test
    void 회원이_처음_참여하면_내_선택과_결과를_돌려준다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(saveMember()));
        saveVote(poll, 2, Voter.anonymous(new AnonId(UUID.randomUUID())));

        var result = voteCommandService.castVote(
            choice(poll, 2),
            memberVoter(memberId));

        assertThat(result.myChoice()).isEqualTo(2);
        assertThat(result.result().total()).isEqualTo(3);
        assertThat(result.result().options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 2L));
    }

    @Test
    void 아무도_고르지_않은_선택지도_득표_0으로_position_순서대로_내려준다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember(), VoteFixture.fiveOptions()));

        var result = voteCommandService.castVote(
            choice(poll, 3),
            memberVoter(memberId));

        assertThat(result.result().total()).isEqualTo(1);
        assertThat(result.result().options()).extracting("position", "count")
            .containsExactly(
                tuple(1, 0L),
                tuple(2, 0L),
                tuple(3, 1L),
                tuple(4, 0L),
                tuple(5, 0L)
            );
    }

    @Test
    void 회원_참여는_투표_선택지_회원_ID로_저장된다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        voteCommandService.castVote(choice(poll, 2), memberVoter(memberId));
        entityManager.flush();
        entityManager.clear();

        var saved = voteJpaRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPollId()).isEqualTo(poll.getId());
        assertThat(saved.get(0).getOptionId()).isEqualTo(poll.getOptions().get(1).getId());
        assertThat(saved.get(0).getVoter()).isEqualTo(Voter.member(memberId));
    }

    // ---- 익명 참여자 (쿠키 해석은 컨트롤러가 하고 서비스는 UUID만 받는다) ----

    @Test
    void 익명_참여는_익명_식별자로_저장된다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var anonId = UUID.randomUUID();

        voteCommandService.castVote(choice(poll, 1), anonymousVoter(anonId));
        entityManager.flush();
        entityManager.clear();

        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactly(Voter.anonymous(new AnonId(anonId)));
    }

    @Test
    void 같은_투표에_회원과_비회원은_각각_한_번씩_참여할_수_있다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();

        voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(memberId));
        voteCommandService.castVote(
            choice(poll, 1),
            anonymousVoter(cookie.id()));

        var anonId = new AnonId(cookie.id());
        assertThat(voteJpaRepository.findAll()).extracting(Vote::getVoter)
            .containsExactlyInAnyOrder(Voter.member(memberId), Voter.anonymous(anonId));
    }

    // ---- 기존 참여 (P-08) ----

    @Test
    void 이미_참여한_회원이_같은_선택을_다시_보내도_ALREADY_VOTED이고_기록은_한_건이다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 2),
            memberVoter(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 이미_참여한_비회원이_같은_쿠키로_같은_선택을_다시_보내도_ALREADY_VOTED다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        var cookie = anonCookieProvider.issue();
        saveVote(poll, 1, Voter.anonymous(new AnonId(cookie.id())));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            anonymousVoter(cookie.id())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(VoteErrorCode.ALREADY_VOTED.getMessage());

        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    // ---- 마감 (P-11, P-12) ----

    @Test
    void 마감_시각_전인_투표에는_참여할_수_있다() {
        var poll = savePoll(VoteFixture.pollClosingInOneHour(saveMember()));
        var memberId = saveMember();

        var result = voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(memberId));

        assertThat(result.myChoice()).isEqualTo(1);
        assertThat(voteJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void 마감된_투표에_기록이_없는_사람이_참여하면_POLL_CLOSED이고_저장되지_않는다() {
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));
        var memberId = saveMember();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_CLOSED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 마감_시각이_없는_투표는_언제든_참여할_수_있다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatCode(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(saveMember())))
            .doesNotThrowAnyException();
    }

    // ---- 선택지 소속 (INVALID_OPTION) ----

    // ---- 투표 조회 ----

    @Test
    void 없는_투표에는_POLL_NOT_FOUND다() {
        assertThatThrownBy(() -> voteCommandService.castVote(
            choice("abcdefghijkl", 1),
            memberVoter(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 공개_ID_형식이_틀리면_POLL_NOT_FOUND다() {
        assertThatThrownBy(() -> voteCommandService.castVote(choice("short", 1), memberVoter(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 정지_회원이_형식이_틀린_공개_ID로_보내도_투표_조회가_먼저라_POLL_NOT_FOUND다() {
        var suspendedId = saveSuspendedMember();

        assertThatThrownBy(() -> voteCommandService.castVote(choice("short", 1), memberVoter(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 숨김_투표에는_POLL_NOT_FOUND다() {
        var poll = VoteFixture.openPoll(saveMember());
        poll.hide();
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 삭제된_투표에는_POLL_NOT_FOUND다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.deleteByAuthor(authorId);
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(saveMember())))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 숨겨진_뒤에는_이미_참여한_사람도_POLL_NOT_FOUND다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        poll.hide();
        entityManager.flush();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자도_숨긴_투표에는_참여할_수_없다() {
        var authorId = saveMember();
        var poll = VoteFixture.openPoll(authorId);
        poll.hide();
        savePoll(poll);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(authorId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    // ---- 회원 상태 ----

    @Test
    void 정지_회원은_MEMBER_SUSPENDED이고_저장되지_않는다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());

        assertThat(voteJpaRepository.count()).isZero();
    }

    @Test
    void 정지_회원은_이전_참여_기록이_있어도_MEMBER_SUSPENDED가_먼저다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 1, Voter.member(memberId));
        suspend(memberId);

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(memberId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 정지_회원이_마감된_투표에_보내도_MEMBER_SUSPENDED가_먼저다() {
        var suspendedId = saveSuspendedMember();
        var poll = savePoll(VoteFixture.expire(VoteFixture.openPoll(saveMember())));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 정지_회원이_없는_투표에_보내면_투표_조회가_먼저라_POLL_NOT_FOUND다() {
        var suspendedId = saveSuspendedMember();

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice("abcdefghijkl", 1),
            memberVoter(suspendedId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 탈퇴한_회원은_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(NOW);
        var withdrawnId = memberJpaRepository.save(withdrawn).getId();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(withdrawnId)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 존재하지_않는_회원_ID는_MEMBER_NOT_FOUND다() {
        var poll = savePoll(VoteFixture.openPoll(saveMember()));

        assertThatThrownBy(() -> voteCommandService.castVote(
            choice(poll, 1),
            memberVoter(Long.MAX_VALUE)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

}
