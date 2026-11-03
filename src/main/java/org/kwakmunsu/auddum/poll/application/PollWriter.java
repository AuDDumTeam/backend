package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.springframework.stereotype.Component;

/**
 * 투표 쓰기. 투표를 만들어 저장한다. 공개 ID가 겹치면 DB 유니크 제약이 막아 저장이 실패한다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
class PollWriter {

    private final PollJpaRepository pollRepository;

    Poll create(NewPoll newPoll, Long authorId, LocalDateTime now) {
        Poll poll = Poll.create(
            authorId,
            newPoll.question(),
            newPoll.category(),
            newPoll.options(),
            newPoll.closesAt(),
            now
        );

        return pollRepository.save(poll);
    }

}
