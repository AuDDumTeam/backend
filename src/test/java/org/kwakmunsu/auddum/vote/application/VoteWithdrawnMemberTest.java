package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberCommandService;
import org.kwakmunsu.auddum.vote.VoteIntegrationTestSupport;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 참여 기록 통합 테스트(H2). 기준은 R-VOTE-07: 탈퇴한 회원의 참여 기록은 남기고 집계에도 센다.
 * 탈퇴는 회원 상태만 바꾸므로(회원 행은 남는다) 참여 기록이 그대로여야 한다.
 */
@RequiredArgsConstructor
@Transactional
class VoteWithdrawnMemberTest extends VoteIntegrationTestSupport {

    final MemberCommandService memberCommandService;
    final VoteReader voteReader;
    final EntityManager entityManager;

    @Test
    void 탈퇴한_회원의_참여_기록은_남는다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));

        memberCommandService.withdraw(memberId);
        entityManager.flush();
        entityManager.clear();

        var found = voteReader.find(poll, Voter.member(memberId));
        assertThat(found).isPresent();
        assertThat(found.get().getOptionId()).isEqualTo(poll.getOptions().get(1).getId());
    }

    @Test
    void 탈퇴한_회원의_참여도_결과_집계에_센다() {
        var memberId = saveMember();
        var poll = savePoll(VoteFixture.openPoll(saveMember()));
        saveVote(poll, 2, Voter.member(memberId));
        saveVote(poll, 1, Voter.anonymous(new AnonId(UUID.randomUUID())));

        memberCommandService.withdraw(memberId);
        entityManager.flush();
        entityManager.clear();

        var tally = voteReader.tally(poll);
        assertThat(tally.total()).isEqualTo(2);
        assertThat(tally.options()).extracting("position", "count")
            .containsExactly(tuple(1, 1L), tuple(2, 1L));
    }

}
