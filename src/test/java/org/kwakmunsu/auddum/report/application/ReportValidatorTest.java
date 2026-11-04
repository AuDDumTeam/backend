package org.kwakmunsu.auddum.report.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.member.application.MemberValidator;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberAuthJpaRepository;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;

/**
 * 신고 검증 단위 테스트. 기준은 features/05-신고하기.md R-REPORT-04·P-05:
 * 정지 회원은 403 MEMBER_SUSPENDED, 탈퇴·없는 회원은 401 UNAUTHENTICATED, 비회원은 회원 조회 없이 통과한다.
 * MemberReader는 실제 객체를 쓰고 repository만 목으로 둔다.
 */
class ReportValidatorTest {

    private static final Long MEMBER_ID = 7L;

    private final MemberJpaRepository memberRepository = mock(MemberJpaRepository.class);
    private final MemberReader memberReader = new MemberReader(memberRepository, mock(MemberAuthJpaRepository.class));
    private final ReportValidator reportValidator =
            new ReportValidator(mock(ReportJpaRepository.class), new MemberValidator(memberReader));

    @Test
    void 정상_회원_신고자는_통과한다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.createMember()));

        assertThatCode(() -> reportValidator.validateReporter(Reporter.member(MEMBER_ID))).doesNotThrowAnyException();
    }

    @Test
    void 정지_회원_신고자는_MEMBER_SUSPENDED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(
                MemberFixture.changeStatus(MemberFixture.createMember(), MemberStatus.SUSPENDED)));

        assertThatThrownBy(() -> reportValidator.validateReporter(Reporter.member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(MemberErrorCode.MEMBER_SUSPENDED.getMessage());
    }

    @Test
    void 탈퇴한_회원_신고자는_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 26, 12, 0));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> reportValidator.validateReporter(Reporter.member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 없는_회원_신고자는_UNAUTHENTICATED다() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportValidator.validateReporter(Reporter.member(MEMBER_ID)))
                .isInstanceOf(AuddumException.class)
                .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void 익명_신고자는_회원_조회를_하지_않고_통과한다() {
        assertThatCode(() -> reportValidator.validateReporter(Reporter.anonymous(UUID.randomUUID())))
                .doesNotThrowAnyException();

        verifyNoInteractions(memberRepository);
    }

}
