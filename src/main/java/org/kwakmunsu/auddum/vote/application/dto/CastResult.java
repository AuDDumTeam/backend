package org.kwakmunsu.auddum.vote.application.dto;

import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.application.dto.PollTally;

/**
 * 참여 결과.
 *
 * @param myChoice 내가 고른 선택지 순번
 * @param result   현재 결과
 */
@Builder(access = AccessLevel.PRIVATE)
public record CastResult(
    int myChoice,
    PollTally result
) {

    public static CastResult of(int myChoice, PollTally result) {
        return CastResult.builder()
            .myChoice(myChoice)
            .result(result)
            .build();
    }

}
