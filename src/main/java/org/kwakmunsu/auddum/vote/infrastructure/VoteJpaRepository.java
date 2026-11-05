package org.kwakmunsu.auddum.vote.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Vote;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteJpaRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPollIdAndVoterMemberId(Long pollId, Long memberId);

    Optional<Vote> findByPollIdAndVoterAnonId(Long pollId, AnonId anonId);

    /**
     * 이 참여자의 투표 기록. 임베디드 값 전체를 비교하면 비어 있는 쪽(NULL)이 같다고 판단되지 않으므로
     * 회원이면 회원 ID로, 비회원이면 익명 식별자로 찾는다.
     */
    default Optional<Vote> findByPollIdAndVoter(Long pollId, Voter voter) {
        if (voter.memberId() != null) {
            return findByPollIdAndVoterMemberId(pollId, voter.memberId());
        }

        return findByPollIdAndVoterAnonId(pollId, voter.anonId());
    }

    /** 선택지별 득표 수. 득표가 없는 선택지는 나오지 않는다. */
    @Query("select v.optionId as optionId, count(v) as votes from Vote v where v.pollId = :pollId group by v.optionId")
    List<OptionCount> countByOption(@Param("pollId") Long pollId);

    /** 투표별 참여 수. 참여가 없는 투표는 나오지 않는다. */
    @Query("select v.pollId as pollId, count(v) as votes from Vote v where v.pollId in :pollIds group by v.pollId")
    List<PollCount> countByPollIds(@Param("pollIds") Collection<Long> pollIds);

    /**
     * 회원이 참여한 투표 중 주어진 노출 상태의 것을 id가 주어진 값보다 작은(더 오래된) 참여 기록부터 최근 참여 먼저로 가져온다. 첫 페이지는 Long.MAX_VALUE를 넘긴다.
     * 비회원 행은 member_id가 NULL이라 나오지 않는다.
     */
    @Query("select v.id as voteId, p as poll from Vote v join Poll p on p.id = v.pollId"
        + " where v.voter.memberId = :memberId and p.status = :status and v.id < :id"
        + " order by v.id desc"
    )
    Slice<ParticipatedPollRow> findByMemberIdAndPollStatusOlderThan(
        @Param("memberId") Long memberId,
        @Param("status") PollStatus status,
        @Param("id") long id,
        Pageable pageable
    );

    /** 회원이 참여한 투표 중 주어진 노출 상태의 것의 개수. 위 목록과 같은 조건이다. */
    @Query("select count(v) from Vote v join Poll p on p.id = v.pollId"
        + " where v.voter.memberId = :memberId and p.status = :status"
    )
    long countByMemberIdAndPollStatus(@Param("memberId") Long memberId, @Param("status") PollStatus status);

    interface ParticipatedPollRow {

        Long getVoteId();

        Poll getPoll();

    }

    interface PollCount {

        Long getPollId();

        long getVotes();

    }

    interface OptionCount {

        Long getOptionId();

        long getVotes();

    }

}
