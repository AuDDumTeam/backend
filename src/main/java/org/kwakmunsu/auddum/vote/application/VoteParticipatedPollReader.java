package org.kwakmunsu.auddum.vote.application;

import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.ParticipatedPollReader;
import org.kwakmunsu.auddum.poll.application.dto.ParticipatedPoll;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * poll이 선언한 {@link ParticipatedPollReader}의 구현(의존 방향 {@code vote → poll}을 지킨다).
 */
@RequiredArgsConstructor
@Component
class VoteParticipatedPollReader implements ParticipatedPollReader {

    private final VoteReader voteReader;

    @Override
    public Slice<ParticipatedPoll> findAllPublicByMemberId(Long memberId, Long cursorId, int size) {
        return voteReader.findAllPublicByMember(memberId, cursorId, size);
    }

    @Override
    public long countPublicByMemberId(Long memberId) {
        return voteReader.countPublicByMember(memberId);
    }

}
