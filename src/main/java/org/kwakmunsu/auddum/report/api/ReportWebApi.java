package org.kwakmunsu.auddum.report.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.report.api.dto.ReportSubmitRequest;
import org.kwakmunsu.auddum.report.application.ReportCommandService;
import org.kwakmunsu.auddum.report.domain.Reporter;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.support.web.AnonCookies;
import org.kwakmunsu.auddum.support.web.OptionalMemberId;
import org.kwakmunsu.auddum.support.web.Requester;
import org.kwakmunsu.auddum.support.web.RequesterResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class ReportWebApi extends ReportWebApiDocs {

    private final ReportCommandService reportCommandService;
    private final RequesterResolver requesterResolver;
    private final AnonCookies anonCookies;

    @Override
    @PostMapping("/{pollId}/reports")
    public ResponseEntity<Void> submitReport(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookieName.VALUE, required = false) String anonCookieValue,
        @RequestBody @Valid ReportSubmitRequest request,
        HttpServletResponse response
    ) {
        Requester requester = requesterResolver.resolve(memberId, anonCookieValue);
        reportCommandService.submit(
            pollId,
            request.toReason(),
            Reporter.of(requester.memberId(), requester.anonId())
        );

        if (requester.newCookieValue() != null) {
            anonCookies.write(response, requester.newCookieValue());
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
