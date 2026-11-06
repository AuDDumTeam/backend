package org.kwakmunsu.auddum.poll.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.application.dto.MyPollPage;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.springframework.data.domain.Slice;

@Builder(access = AccessLevel.PRIVATE)
@Schema(description = "내가 올린 투표 목록 한 페이지")
public record MyPollListResponse(
    List<MyPollSummaryResponse> items,

    @Schema(description = "다음 페이지 커서. 마지막이면 null", nullable = true, example = "eyJpZCI6MTIzfQ")
    String nextCursor,

    @Schema(description = "목록에 나올 수 있는 전체 개수(공개·숨김 투표). 커서와 페이지 크기와 무관하다", example = "25")
    long totalCount
) {

    public static MyPollListResponse of(MyPollPage page, LocalDateTime now) {
        Slice<PollSummary> slice = page.summaries();

        List<MyPollSummaryResponse> items = slice.getContent().stream()
            .map(summary -> MyPollSummaryResponse.of(summary, now))
            .toList();

        // 다음 페이지 커서를 계산한다. 마지막 페이지(빈 목록 포함)면 null이다.
        String nextCursor = null;
        if (slice.hasNext()) {
            Long lastId = slice.getContent().getLast().poll().getId();
            nextCursor = new PollCursor(lastId).encode();
        }

        return MyPollListResponse.builder()
            .items(items)
            .nextCursor(nextCursor)
            .totalCount(page.totalCount())
            .build();
    }

}
