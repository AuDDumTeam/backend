package org.kwakmunsu.auddum.vote;

import static org.kwakmunsu.auddum.vote.application.VoteFixture.CREATED_AT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.kwakmunsu.auddum.vote.application.VoteFixture;
import org.kwakmunsu.auddum.DataIntegrationTestSupport;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.kwakmunsu.auddum.vote.infrastructure.VoteJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 참여 통합 테스트의 공통 저장 헬퍼. 회원·투표 저장은 {@link DataIntegrationTestSupport}에 있고, 여기에는 참여 기록 저장만 둔다.
 * 저장소 없이 쓰는 값(투표 데이터, 쿠키 조작)은 {@code VoteFixture}에 있다.
 */
public abstract class VoteIntegrationTestSupport extends DataIntegrationTestSupport {

    @Autowired
    protected VoteJpaRepository voteJpaRepository;

    protected void saveVote(Poll poll, int position, Voter voter) {
        saveVote(poll, position, voter, CREATED_AT);
    }

    protected void saveVote(Poll poll, int position, Voter voter, LocalDateTime castAt) {
        voteJpaRepository.saveAndFlush(Vote.cast(poll, position, voter, castAt));
    }

    /** 회원이 이 투표의 1번 선택지에 참여한 기록을 저장하고 vote.id를 돌려준다. */
    protected Long cast(Poll poll, Long memberId) {
        return voteJpaRepository.saveAndFlush(
            Vote.cast(poll, 1, Voter.member(memberId), CREATED_AT)).getId();
    }

    /** 새 공개 투표 count개에 차례로 참여하고 참여 순서(오래된 것부터)로 vote.id를 돌려준다. */
    protected List<Long> castOnNewPolls(Long memberId, int count) {
        var voteIds = new ArrayList<Long>();
        var authorId = saveMember();
        for (int i = 0; i < count; i++) {
            voteIds.add(cast(savePoll(VoteFixture.openPoll(authorId)), memberId));
        }

        return voteIds;
    }

}
