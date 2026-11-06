package org.kwakmunsu.auddum.poll.api.dto;

import java.time.OffsetDateTime;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;

/**
 * 투표 응답이 내리는 시각 표현. 시각은 KST 오프셋으로 내리고, 마감이 없으면 null이다.
 */
final class PollTimes {

    private PollTimes() {
    }

    static OffsetDateTime closesAtOf(Poll poll) {
        return TimeConfig.toKstOffset(poll.getClosesAt() == null ? null : poll.getClosesAt().value());
    }

    static OffsetDateTime createdAtOf(Poll poll) {
        return TimeConfig.toKstOffset(poll.getCreatedAt());
    }

}
