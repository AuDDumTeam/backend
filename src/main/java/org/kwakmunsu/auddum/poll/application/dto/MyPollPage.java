package org.kwakmunsu.auddum.poll.application.dto;

import lombok.Builder;
import org.springframework.data.domain.Slice;

/**
 * 내가 올린 투표 목록 한 쪽.
 *
 * @param summaries  이 쪽의 투표
 * @param totalCount 목록에 나올 수 있는 전체 개수(커서·쪽 크기와 무관)
 */
@Builder
public record MyPollPage(
    Slice<PollSummary> summaries,
    long totalCount
) {

}
