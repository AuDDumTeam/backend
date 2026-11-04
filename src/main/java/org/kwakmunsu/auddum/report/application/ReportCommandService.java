package org.kwakmunsu.auddum.report.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.PollReader;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.report.domain.ReportReason;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

/** 신고 접수. 서비스는 처리 순서만 잇고, 규칙의 실행은 구현 클래스에 시킨다. */
@RequiredArgsConstructor
@CommandService
public class ReportCommandService {

    private final PollReader pollReader;
    private final ReportValidator reportValidator;
    private final ReportWriter reportWriter;

    /**
     * 투표를 신고한다. 공개 투표 조회(없거나 형식이 틀리거나 숨김·삭제면 404) → 신고자 확인(정지 403, 탈퇴 401, 없음 404)
     * → 중복 확인(409) → 저장 순서다. 마감된 투표도 신고할 수 있고, 투표의 노출 상태는 바꾸지 않는다.
     *
     * @param pollPublicId 신고할 투표의 공개 ID
     * @param reason       신고 사유와 상세
     * @param reporter     신고하려는 사람(회원 또는 익명). 익명 쿠키 해석은 컨트롤러가 이미 했다
     */
    public void submit(String pollPublicId, ReportReason reason, Reporter reporter) {
        Poll poll = pollReader.getPublic(pollPublicId);

        reportValidator.validateReporter(reporter);
        reportValidator.validateNotReported(poll, reporter);
        reportWriter.submit(poll, reporter, reason);
    }

}
