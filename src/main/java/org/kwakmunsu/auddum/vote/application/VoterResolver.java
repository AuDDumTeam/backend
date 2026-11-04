package org.kwakmunsu.auddum.vote.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.kwakmunsu.auddum.vote.application.dto.VoterCredential;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.stereotype.Component;

/**
 * 조회 요청의 참여자를 찾는다. 로그인한 회원이면 회원 ID를 쓰고, 비회원이면 쿠키 해석을 {@link AnonIdentityResolver}에 시켜 {@link Voter}로 감싼다.
 * 쓰기 요청의 신원은 컨트롤러가 {@code RequesterResolver}로 정한다.
 */
@RequiredArgsConstructor
@Component
public class VoterResolver {

    private final AnonIdentityResolver anonIdentityResolver;

    /**
     * 조회용으로 참여자를 찾는다. 쓰기와 달리 쿠키를 읽기만 하고 발급하지 않으며, 쿠키가 없거나 위조된 경우는 참여자가 없는 것으로 본다.
     * 회원은 상태를 확인하지 않는다(정지 회원도 조회할 수 있다).
     */
    public Optional<Voter> find(VoterCredential credential) {
        if (credential.isMember()) {
            return Optional.of(Voter.member(credential.memberId()));
        }

        return anonIdentityResolver.find(credential.anonCookieValue())
            .map(id -> Voter.anonymous(new AnonId(id)));
    }

}
