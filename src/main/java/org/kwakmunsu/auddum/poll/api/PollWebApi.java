package org.kwakmunsu.auddum.poll.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.api.dto.MyPollListResponse;
import org.kwakmunsu.auddum.poll.api.dto.PollCreateRequest;
import org.kwakmunsu.auddum.poll.api.dto.PollDetailResponse;
import org.kwakmunsu.auddum.poll.api.dto.PollCursor;
import org.kwakmunsu.auddum.poll.api.dto.PollListResponse;
import org.kwakmunsu.auddum.poll.api.dto.VoteCursor;
import org.kwakmunsu.auddum.poll.api.dto.VotedPollListResponse;
import org.kwakmunsu.auddum.poll.application.PollCommandService;
import org.kwakmunsu.auddum.poll.application.PollQueryService;
import org.kwakmunsu.auddum.poll.application.dto.MyPollPage;
import org.kwakmunsu.auddum.poll.application.dto.PollDetail;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.application.dto.Viewer;
import org.kwakmunsu.auddum.poll.application.dto.VotedPollPage;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.paging.CursorPaging;
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
        @RequestParam(required = false) Category category
    ) {
        Slice<PollSummary> slice = pollQueryService.getPollSummaries(category, CursorPaging.of(PollCursor.idOf(cursor), size));

        return ResponseEntity.ok(PollListResponse.of(slice, LocalDateTime.now(TimeConfig.KST)));
    }

    @Override
    @GetMapping("/mine")
    public ResponseEntity<MyPollListResponse> getMyPolls(
        @CurrentMemberId Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        MyPollPage page = pollQueryService.getMyPolls(memberId, CursorPaging.of(PollCursor.idOf(cursor), size));

        return ResponseEntity.ok(MyPollListResponse.of(page, LocalDateTime.now(TimeConfig.KST)));
    }

    @Override
    @GetMapping("/voted")
    public ResponseEntity<VotedPollListResponse> getVotedPolls(
        @CurrentMemberId Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
    ) {
        VotedPollPage page = pollQueryService.getVotedPolls(memberId, CursorPaging.of(VoteCursor.idOf(cursor), size));

        return ResponseEntity.ok(VotedPollListResponse.of(page, LocalDateTime.now(TimeConfig.KST)));
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
