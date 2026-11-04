package org.kwakmunsu.auddum.vote.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberValidator;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.vote.domain.VoteErrorCode;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.springframework.stereotype.Component;

/** 참여 규칙 검증. 서비스가 시키면 규칙을 확인하고 어기면 도메인 오류를 던진다. */
@RequiredArgsConstructor
@Component
public class VoteValidator {

    private final VoteReader voteReader;
    private final MemberValidator memberValidator;

    /** 회원이면 정상 회원인지 확인한다(정지 403, 탈퇴 401, 없음 404). 비회원은 확인할 것이 없다. */
    public void validateVoter(Voter voter) {
        if (voter.memberId() != null) {
            memberValidator.validateActive(voter.memberId());
        }
    }

    /** 이미 참여한 사람이면 선택과 관계없이 409(ALREADY_VOTED)다. 마감 후에도 같다. */
    public void validateNotParticipated(Poll poll, Voter voter) {
        if (voteReader.find(poll, voter).isPresent()) {
            throw new AuddumException(VoteErrorCode.ALREADY_VOTED);
        }
    }

}
