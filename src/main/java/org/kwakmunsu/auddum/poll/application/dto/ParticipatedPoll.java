package org.kwakmunsu.auddum.poll.application.dto;

import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 회원이 참여한 투표 한 건.
 *
 * @param voteId 참여 기록 id. 참여 순서를 가리키고 다음 쪽 커서가 된다
 * @param poll   참여한 투표
 */
public record ParticipatedPoll(
    Long voteId,
    Poll poll
) {

    public static ParticipatedPoll of(Long voteId, Poll poll) {
        return new ParticipatedPoll(voteId, poll);
    }

}
