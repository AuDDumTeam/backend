package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberValidator;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.ReportErrorCode;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.report.infrastructure.ReportJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.stereotype.Component;

/** 신고 규칙 검증. 서비스가 시키면 규칙을 확인하고 어기면 도메인 오류를 던진다. */
@RequiredArgsConstructor
@Component
class ReportValidator {

    private final ReportJpaRepository reportRepository;
    private final MemberValidator memberValidator;

    /** 회원이면 정상 회원인지 확인한다(정지 403, 탈퇴 401, 없음 404). 비회원은 확인할 것이 없다. */
    public void validateReporter(Reporter reporter) {
        if (reporter.memberId() != null) {
            memberValidator.validateActive(reporter.memberId());
        }
    }

    /** 이미 신고한 사람이면 409(REPORT_ALREADY_SUBMITTED)다. */
    public void validateNotReported(Poll poll, Reporter reporter) {
        if (reportRepository.existsByPollIdAndReporter(poll.getId(), reporter)) {
            throw new AuddumException(ReportErrorCode.REPORT_ALREADY_SUBMITTED);
        }
    }

}
