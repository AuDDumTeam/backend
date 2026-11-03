package org.kwakmunsu.auddum.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.kwakmunsu.auddum.poll.application.PollFixture.createNewPoll;
import static org.kwakmunsu.auddum.poll.application.PollFixture.QUESTION;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PublicId;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 생성·삭제 서비스의 통합 테스트(H2). 작성자는 Member를 저장하고 memberId로 넘긴다.
 * 생성은 연속 생성과 같은 질문을 제한하지 않는 것, 공개 ID가 겹치면 재발급 없이 저장에 실패하는 것을 확인한다.
 * 작성자 삭제는 회원 상태를 투표 조회보다 먼저 확인하는 순서, 404·403 구분(Poll.deleteByAuthor에 위임한 결과), 삭제 뒤 참여 보존을 확인한다.
 */
@RequiredArgsConstructor
@Transactional
class PollCommandServiceTest extends IntegrationTestSupport {

    final PollCommandService pollCommandService;
    final PollJpaRepository pollJpaRepository;
    final MemberJpaRepository memberJpaRepository;
    final VoteJpaRepository voteJpaRepository;
    final EntityManager entityManager;

    @Test
    void 투표를_생성하면_PUBLIC_상태로_작성자와_함께_저장된다() {
        var memberId = saveMember();

        var created = pollCommandService.createPoll(createNewPoll(QUESTION, List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), null), memberId);
        entityManager.flush();
        entityManager.clear();

        var poll = pollJpaRepository.findById(created.getId()).orElseThrow();
        assertThat(poll.getAuthorId()).isEqualTo(memberId);
        assertThat(poll.getStatus()).isEqualTo(PollStatus.PUBLIC);
        assertThat(poll.getQuestion().value()).isEqualTo(QUESTION);
        assertThat(poll.getCategory()).isEqualTo(Category.SOME);
        assertThat(poll.getOptions()).hasSize(3);
    }

    @Test
    void 같은_질문으로_연속_생성할_수_있다() {
        var memberId = saveMember();
        pollCommandService.createPoll(createNewPoll(QUESTION), memberId);

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll(QUESTION), memberId))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void 다른_질문으로_간격_없이_연속_생성할_수_있다() {
        var memberId = saveMember();
        pollCommandService.createPoll(createNewPoll("첫 번째 질문"), memberId);

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll("두 번째 질문"), memberId))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void 삭제한_투표와_같은_질문으로_다시_만들_수_있다() {
        var memberId = saveMember();
        var first = pollCommandService.createPoll(createNewPoll(QUESTION), memberId);
        pollCommandService.deleteByAuthor(memberId, first.getPublicId().value());

        assertThatCode(() -> pollCommandService.createPoll(createNewPoll(QUESTION), memberId))
            .doesNotThrowAnyException();

        assertThat(pollJpaRepository.count()).isEqualTo(2);
    }

    @Test
    void 공개_ID가_겹치면_다시_발급하지_않고_저장에_실패한다() {
        var firstMemberId = saveMember();
        var otherMemberId = saveMember();
        var first = pollCommandService.createPoll(createNewPoll("먼저 만든 질문"), firstMemberId);
        var taken = first.getPublicId();
        entityManager.flush();
        var fresh = PublicId.of("zzzzzzzzzzzz");

        try (var publicIds = mockStatic(PublicId.class, CALLS_REAL_METHODS)) {
            publicIds.when(PublicId::generate).thenReturn(taken, fresh);

            assertThatThrownBy(() -> pollCommandService.createPoll(createNewPoll("다른 질문"), otherMemberId))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void 작성자가_공개_투표를_삭제하면_DELETED가_되고_투표와_선택지는_남는다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);

        pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value());
        entityManager.flush();
        entityManager.clear();

        var found = pollJpaRepository.findById(poll.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PollStatus.DELETED);
        assertThat(found.getOptions()).hasSize(2);
    }

    @Test
    void 작성자가_투표를_삭제해도_이미_들어온_참여는_남는다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);
        var voterId = saveMember();
        var vote = voteJpaRepository.saveAndFlush(
            Vote.cast(poll, 1, Voter.member(voterId), LocalDateTime.now(TimeConfig.KST)));

        pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value());
        entityManager.flush();
        entityManager.clear();

        var found = voteJpaRepository.findById(vote.getId()).orElseThrow();
        assertThat(found.getPollId()).isEqualTo(poll.getId());
        assertThat(found.getVoter()).isEqualTo(Voter.member(voterId));
    }

    @Test
    void 작성자가_아닌_회원이_공개_투표를_삭제하면_FORBIDDEN이고_상태는_그대로다() {
        var poll = saveOpenPoll(saveMember());
        var otherId = saveMember();

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(otherId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.FORBIDDEN.getMessage());

        assertThat(pollJpaRepository.findById(poll.getId()).orElseThrow().getStatus()).isEqualTo(PollStatus.PUBLIC);
    }

    @Test
    void 작성자가_아닌_회원이_숨김_투표를_삭제하면_POLL_NOT_FOUND다() {
        var poll = saveOpenPoll(saveMember());
        poll.hide();
        entityManager.flush();
        var otherId = saveMember();

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(otherId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자가_아닌_회원이_삭제된_투표를_삭제하면_POLL_NOT_FOUND다() {
        var poll = saveOpenPoll(saveMember());
        poll.deleteByAdmin();
        entityManager.flush();
        var otherId = saveMember();

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(otherId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 작성자라도_관리자가_숨긴_투표는_POLL_NOT_DELETABLE이고_상태는_HIDDEN_그대로다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);
        poll.hide();
        entityManager.flush();

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_DELETABLE.getMessage());

        assertThat(pollJpaRepository.findById(poll.getId()).orElseThrow().getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 이미_삭제한_투표를_다시_삭제하면_POLL_NOT_FOUND다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);
        pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value());

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(PollErrorCode.POLL_NOT_FOUND.getMessage());
    }

    @Test
    void 정지_회원은_자기_투표도_삭제할_수_없다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);
        suspend(authorId);

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());

        assertThat(pollJpaRepository.findById(poll.getId()).orElseThrow().getStatus()).isEqualTo(PollStatus.PUBLIC);
    }

    @Test
    void 정지_회원은_없는_투표를_삭제해도_투표_조회보다_먼저_MEMBER_SUSPENDED다() {
        var memberId = saveMember();
        suspend(memberId);

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(memberId, PublicId.generate().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원은_UNAUTHENTICATED다() {
        var authorId = saveMember();
        var poll = saveOpenPoll(authorId);
        var author = memberJpaRepository.findById(authorId).orElseThrow();
        author.withdraw(LocalDateTime.now(TimeConfig.KST));
        entityManager.flush();

        assertThatThrownBy(() -> pollCommandService.deleteByAuthor(authorId, poll.getPublicId().value()))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    private void suspend(Long memberId) {
        memberJpaRepository.findById(memberId).orElseThrow().suspend(LocalDateTime.now(TimeConfig.KST));
        entityManager.flush();
    }

    private Poll saveOpenPoll(Long authorId) {
        var poll = Poll.create(authorId, QUESTION, Category.SOME, List.of("관심 없음", "그냥 바쁜 것"), null,
            LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

    private Long saveMember() {
        return memberJpaRepository.save(MemberFixture.createMember()).getId();
    }

}
