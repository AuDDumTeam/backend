package org.kwakmunsu.auddum.poll.application;

import org.kwakmunsu.auddum.poll.application.dto.ParticipatedPoll;
import org.springframework.data.domain.Slice;

/**
 * 회원이 참여한 공개 투표 목록이 필요로 하는 참여 기록. poll이 필요한 것을 선언하고 참여 기록을 가진 vote가 구현한다(poll은 vote를 쓸 수 없다).
 * 공개 투표만 돌려주므로 구현은 투표의 노출 상태를 읽는다. 공개의 정의는 {@code Poll}이 가진다.
 */
public interface ParticipatedPollReader {

    /**
     * 회원이 참여한 공개 투표를 최근 참여 먼저(참여 기록 id 내림차순)로 size개 돌려준다. cursorId(마지막으로 본 참여 기록 id)가 없으면 첫 쪽이다.
     * 숨김·삭제 투표와 비회원(익명) 참여는 포함하지 않는다.
     */
    Slice<ParticipatedPoll> findAllPublicByMemberId(Long memberId, Long cursorId, int size);

    /** 회원이 참여한 공개 투표의 전체 개수. 위 목록에 나올 수 있는 개수와 같다. */
    long countPublicByMemberId(Long memberId);

}
