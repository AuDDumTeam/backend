package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;

/**
 * 내가 올린 투표 목록의 한 건. 투표 목록 항목에 노출 상태를 더한 것이고, 내부 id는 담지 않는다.
 */
@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "내가 올린 투표 목록 항목")
public record MyPollSummaryResponse(
    @Schema(description = "공개 ID", example = "aB3dE5fG7hJk")
    String id,

    String question,

    Category category,

    @Schema(description = "노출 상태. 공개(PUBLIC) 또는 숨김(HIDDEN)이다. 삭제된 투표는 목록에 없다", example = "PUBLIC")
    PollStatus status,

    @Schema(description = "전체 참여 수", example = "42")
    long participantCount,

    boolean closed,

    @Schema(description = "마감 시각(KST 오프셋). 마감 없으면 null", nullable = true, example = "2026-10-19T23:59:00+09:00")
    OffsetDateTime closesAt,

    @Schema(description = "생성 시각(KST 오프셋)", example = "2026-10-12T13:00:00+09:00")
    OffsetDateTime createdAt
) {

    public static MyPollSummaryResponse of(PollSummary summary, LocalDateTime now) {
        Poll poll = summary.poll();

        return MyPollSummaryResponse.builder()
            .id(poll.getPublicId().value())
            .question(poll.getQuestion().value())
            .category(poll.getCategory())
            .status(poll.getStatus())
            .participantCount(summary.participantCount())
            .closed(poll.isClosed(now))
            .closesAt(PollTimes.closesAtOf(poll))
            .createdAt(PollTimes.createdAtOf(poll))
            .build();
    }

}
