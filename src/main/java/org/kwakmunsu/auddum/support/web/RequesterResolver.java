package org.kwakmunsu.auddum.support.web;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.anon.AnonIdentity;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.springframework.stereotype.Component;

/**
 * 쓰기 요청을 보낸 사람을 정한다. 로그인한 회원이면 회원 ID만 쓰고 익명 쿠키는 보지 않는다.
 * 비회원이면 쿠키 해석(유효하면 그 UUID, 없거나 위조면 발급)을 {@link AnonIdentityResolver}에 시킨다.
 * 회원 상태 확인은 하지 않는다. 그 판단은 각 도메인의 검증이 한다.
 */
@RequiredArgsConstructor
@Component
public class RequesterResolver {

    private final AnonIdentityResolver anonIdentityResolver;

    public Requester resolve(Long memberId, String anonCookieValue) {
        if (memberId != null) {
            return new Requester(memberId, null, null);
        }

        AnonIdentity identity = anonIdentityResolver.resolve(anonCookieValue);

        return new Requester(null, identity.id(), identity.newCookieValue());
    }

}
