package org.kwakmunsu.auddum.member.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;

/**
 * 쓰기 가능한 회원인지 검증하는 단위 테스트(R-MEMBER-11). 탈퇴 401 UNAUTHENTICATED, 없음 404 MEMBER_NOT_FOUND, 정지 403 MEMBER_SUSPENDED.
 * MemberReader는 실제 객체를 쓰고 repository만 목으로 둔다.
 */
class MemberValidatorTest {

    private static final Long MEMBER_ID = 7L;

    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository, mock(MemberAuthJpaRepository.class));
    private final MemberValidator validator = new MemberValidator(memberReader);

    @Test
    void 정상_회원이면_예외_없이_통과한다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));

        assertThatCode(() -> validator.validateActive(MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    void 정지_회원이면_MEMBER_SUSPENDED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> validator.validateActive(MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원이면_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 19, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> validator.validateActive(MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 없는_회원이면_MEMBER_NOT_FOUND다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateActive(MEMBER_ID))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

}
