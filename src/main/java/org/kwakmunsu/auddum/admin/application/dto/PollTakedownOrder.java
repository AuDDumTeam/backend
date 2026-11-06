package org.kwakmunsu.auddum.admin.application.dto;

import org.kwakmunsu.auddum.admin.domain.PollTakedown;

/**
 * 관리자의 투표 조치 지시. 바꿀 상태와 사유를 묶는다. 값 검증은 넣지 않는다(사유는 {@code AdminAction}이, 상태 전이는 {@code PollTakedown}이 한다).
 *
 * @param status 바꿀 투표 상태(숨김 또는 삭제)
 * @param reason 조치 사유
 */
public record PollTakedownOrder(
    PollTakedown status,
    String reason
) {

}
