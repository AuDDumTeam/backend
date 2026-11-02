package org.kwakmunsu.auddum.member.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.domain.Member;
import org.springframework.stereotype.Component;

/**
 * 회원 상태 검증. 쓰기 서비스가 첫 단계에서 {@code validateActive}를 호출한다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 그대로 참여한다. 다른 도메인은 이 클래스로 회원 상태를 확인한다.
 */
@RequiredArgsConstructor
@Component
public class MemberValidator {

    private final MemberReader memberReader;

    /** 쓰기를 할 수 있는 정상 회원인지 확인한다. 없거나 탈퇴했으면 401, 정지면 403이다. 정지 회원이 할 수 있는 탈퇴·재발급에서는 부르지 않는다. */
    public void validateActive(Long memberId) {
        Member member = memberReader.getMember(memberId);
        member.validateActive();
    }

}
