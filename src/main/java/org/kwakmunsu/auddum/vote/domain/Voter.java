package org.kwakmunsu.auddum.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.UUID;
import org.kwakmunsu.auddum.support.error.AuddumException;

/**
 * 참여를 한 주체. 회원 ID와 익명 식별자 중 정확히 하나다. 같은 참여자인지는 값 동등성으로 판단한다.
 */
@Embeddable
public record Voter(
    @Column(name = "member_id")
    Long memberId,

    @Embedded
    AnonId anonId
) {

    public Voter {
        if ((memberId == null) == (anonId == null)) {
            throw AuddumException.validation("voter", "참여자는 회원 ID와 익명 식별자 중 하나여야 합니다.");
        }
    }

    public static Voter member(Long memberId) {
        return new Voter(memberId, null);
    }

    public static Voter anonymous(AnonId anonId) {
        return new Voter(null, anonId);
    }

    /** 회원 ID와 익명 식별자 중 하나만 채워 만든다. 둘 다 있거나 둘 다 없으면 거절한다. */
    public static Voter of(Long memberId, UUID anonId) {
        return new Voter(memberId, anonId == null ? null : new AnonId(anonId));
    }

}
