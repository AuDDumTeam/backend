package org.kwakmunsu.auddum.poll.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollDetailResponse;
import org.kwakmunsu.auddum.poll.api.dto.PollListRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollListResponse;
import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.kwakmunsu.auddum.poll.application.dto.PollDetail;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.support.web.CurrentMemberId;
import org.kwakmunsu.auddum.support.web.OptionalMemberId;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
@WebApi
public class PollWebApi extends PollWebApiDocs {

    private final PollCommandService pollCommandService;
    private final PollQueryService pollQueryService;

    @Override
    @PostMapping
    public ResponseEntity<String> createPoll(
        @CurrentMemberId Long memberId,
        @RequestBody @Valid PollCreateRequest request
    ) {
        Poll poll = pollCommandService.createPoll(request.toNewPoll(), memberId);
        String publicId = poll.getPublicId().value();

        return ResponseEntity.created(URI.create("/api/v1/polls/" + publicId)).body(publicId);
    }

    @Override
    @GetMapping
    public ResponseEntity<PollListResponse> getPolls(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size,
        // defaultValue는 문서에 기본값을 드러내려는 것이다. 해석(ALL·모르는 값은 전체)은 PollListRequest가 한다.
        @RequestParam(required = false, defaultValue = "ALL") String category
    ) {
        PollListRequest request = new PollListRequest(cursor, size, category);
        Slice<PollSummary> slice = pollQueryService.getPollSummaries(
            request.cursorId(),
            request.categoryFilter(),
            request.pageSize()
        );

        return ResponseEntity.ok(PollListResponse.of(slice, LocalDateTime.now(TimeConfig.KST)));
    }

    @Override
    @GetMapping("/{pollId}")
    public ResponseEntity<PollDetailResponse> getPoll(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookieName.VALUE, required = false) String anonCookieValue
    ) {
        PollDetail detail = pollQueryService.getPollDetail(pollId, new Viewer(memberId, anonCookieValue));

        return ResponseEntity.ok(PollDetailResponse.from(detail));
    }

    @Override
    @DeleteMapping("/{pollId}")
    public ResponseEntity<Void> deletePoll(
        @CurrentMemberId Long memberId,
        @PathVariable String pollId
    ) {
        pollCommandService.deleteByAuthor(memberId, pollId);

        return ResponseEntity.noContent().build();
    }

}
