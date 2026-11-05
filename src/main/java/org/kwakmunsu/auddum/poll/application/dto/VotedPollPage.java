package org.kwakmunsu.auddum.poll.application.dto;

import org.springframework.data.domain.Slice;

/**
 * 내가 참여한 투표 목록 한 쪽.
 *
 * @param items      이 쪽의 투표(최근에 참여한 것 먼저)
 * @param totalCount 목록에 나올 수 있는 전체 개수(커서·쪽 크기와 무관)
 */
public record VotedPollPage(
    Slice<ParticipatedPoll> items,
    long totalCount
) {

    public static VotedPollPage of(Slice<ParticipatedPoll> items, long totalCount) {
        return new VotedPollPage(items, totalCount);
    }

}
