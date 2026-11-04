package org.kwakmunsu.auddum.vote.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.support.stereotype.WebApi;
import org.kwakmunsu.auddum.support.web.AnonCookieName;
import org.kwakmunsu.auddum.support.web.AnonCookies;
import org.kwakmunsu.auddum.support.web.OptionalMemberId;
import org.kwakmunsu.auddum.support.web.Requester;
import org.kwakmunsu.auddum.support.web.RequesterResolver;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastRequest;
import org.kwakmunsu.auddum.vote.api.dto.VoteCastResponse;
import org.kwakmunsu.auddum.vote.application.VoteCommandService;
import org.kwakmunsu.auddum.vote.application.dto.CastResult;
import org.kwakmunsu.auddum.vote.application.dto.PollChoice;
import org.kwakmunsu.auddum.vote.domain.Voter;
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
public class VoteWebApi extends VoteWebApiDocs {

    private final VoteCommandService voteCommandService;
    private final RequesterResolver requesterResolver;
    private final AnonCookies anonCookies;

    @Override
    @PostMapping("/{pollId}/votes")
    public ResponseEntity<VoteCastResponse> castVote(
        @PathVariable String pollId,
        @OptionalMemberId Long memberId,
        @CookieValue(name = AnonCookieName.VALUE, required = false) String anonCookieValue,
        @RequestBody @Valid VoteCastRequest request,
        HttpServletResponse response
    ) {
        Requester requester = requesterResolver.resolve(memberId, anonCookieValue);
        CastResult cast = voteCommandService.castVote(
            new PollChoice(pollId, request.position()),
            Voter.of(requester.memberId(), requester.anonId())
        );
        if (requester.newCookieValue() != null) {
            anonCookies.write(response, requester.newCookieValue());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(VoteCastResponse.from(cast));
    }

}
