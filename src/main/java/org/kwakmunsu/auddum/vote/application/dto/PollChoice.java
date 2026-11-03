package org.kwakmunsu.auddum.vote.application.dto;

/**
 * 어느 투표의 몇 번 선택지인지. 형식이 틀린 공개 ID는 없는 투표와 같게 투표 조회가 404로 답하고, 순번의 범위는 도메인이 검증한다.
 *
 * @param pollPublicId 투표 공개 ID
 * @param position     고른 선택지 순번
 */
public record PollChoice(String pollPublicId, int position) {

}
