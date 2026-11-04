package org.kwakmunsu.auddum.support.web;

import java.util.UUID;

/**
 * 쓰기 요청을 보낸 사람. 회원이면 {@code memberId}만 있고, 비회원이면 {@code anonId}만 있다.
 * 쿠키를 새로 발급했을 때만 {@code newCookieValue}가 채워지고, 아니면 null이다.
 */
public record Requester(Long memberId, UUID anonId, String newCookieValue) {

}
