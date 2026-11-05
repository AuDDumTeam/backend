package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;

/**
 * 내가 참여한 투표 목록의 한 건. 내부 id는 담지 않고 공개 ID만 내리며, 참여 수는 담지 않는다.
 */
@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "내가 참여한 투표 목록 항목")
public record VotedPollSummaryResponse(
    @Schema(description = "공개 ID", example = "aB3dE5fG7hJk")
    String id,

    String question,

    Category category,

    boolean closed,

    @Schema(description = "마감 시각(KST 오프셋). 마감 없으면 null", nullable = true, example = "2026-10-19T23:59:00+09:00")
    OffsetDateTime closesAt,

    @Schema(description = "생성 시각(KST 오프셋)", example = "2026-10-12T13:00:00+09:00")
    OffsetDateTime createdAt
) {

    public static VotedPollSummaryResponse of(Poll poll, LocalDateTime now) {
        return VotedPollSummaryResponse.builder()
            .id(poll.getPublicId().value())
            .question(poll.getQuestion().value())
            .category(poll.getCategory())
            .closed(poll.isClosed(now))
            .closesAt(PollTimes.closesAtOf(poll))
            .createdAt(PollTimes.createdAtOf(poll))
            .build();
    }

}
