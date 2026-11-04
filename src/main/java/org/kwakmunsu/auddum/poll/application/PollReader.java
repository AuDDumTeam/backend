package org.kwakmunsu.auddum.poll.application;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollErrorCode;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * 투표 읽기. 열람할 수 있는 투표 한 건({@link #getReadable}), 공개된 투표 한 건({@link #getPublic}), 공개 투표 목록을 돌려준다. 다른 도메인(vote, report 등)도 서비스가 아니라 이 클래스로 투표를 읽는다. 다른 도메인에 열린 진입점은 {@code getPublic}(공개 투표)과 {@code getByPublicId}·{@code getById}(노출 상태 무관)이고, {@code getReadable}은 poll 안에서만 쓴다.
 * 자체 트랜잭션 경계가 없어 부른 서비스의 트랜잭션에 참여한다.
 */
@RequiredArgsConstructor
@Component
public class PollReader {

    private final MemberReader memberReader;
    private final PollJpaRepository pollRepository;

    /**
     * 열람할 수 있는 투표 한 건을 돌려준다. 숨김·삭제 투표는 작성자와 관리자에게만 보이고, 그 밖에는 없는 투표와 같이 404다.
     * 비회원은 viewerId가 null이다. 관리자는 DB의 role이 ADMIN인 회원이다.
     */
    public Poll getReadable(String publicId, Long viewerId) {
        return pollRepository.findByPublicId(publicId)
            .filter(poll -> poll.isReadableBy(viewerId, () -> memberReader.isAdmin(viewerId)))
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

    /**
     * 공개 투표 한 건을 돌려준다. 조회자와 상관없이 공개가 아닌 투표(숨김·삭제)는 없는 투표와 같이 404다. 작성자·관리자도 같다.
     * 참여·신고처럼 "공개된 투표만 대상"인 쓰기에 쓴다.
     */
    public Poll getPublic(String publicId) {
        return pollRepository.findByPublicId(publicId)
            .filter(Poll::isPublic)
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

    /** 노출 상태와 상관없이 공개 ID로 투표 한 건을 돌려준다. 형식이 틀리거나 없으면 404다. 관리자 숨김·삭제처럼 노출 상태를 직접 따지는 곳이 쓴다. */
    public Poll find(String publicId) {
        return pollRepository.findByPublicId(publicId)
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

    /** 노출 상태와 상관없이 내부 id로 투표 한 건을 돌려준다. 관리자 화면처럼 숨김·삭제 투표도 봐야 하는 곳이 쓴다. 없으면 404다. */
    public Poll find(Long id) {
        return pollRepository.findById(id)
            .orElseThrow(() -> new AuddumException(PollErrorCode.POLL_NOT_FOUND));
    }

    /** 노출 상태와 상관없이 내부 id들의 투표를 돌려준다. 없는 id는 결과에서 빠진다. */
    public List<Poll> findAllByIds(Collection<Long> ids) {
        return pollRepository.findAllById(ids);
    }

    /** 공개 투표를 최신순으로 size개 돌려준다. cursorId(마지막으로 본 내부 id)가 없으면 첫 페이지이고, category가 null이면 모든 카테고리다. */
    Slice<Poll> findPublicOlderThan(Long cursorId, Category category, int size) {
        long beforeId = cursorId == null ? Long.MAX_VALUE : cursorId;
        PageRequest pageable = PageRequest.ofSize(size);

        if (category == null) {
            return pollRepository.findOlderThan(PollStatus.PUBLIC, beforeId, pageable);
        }

        return pollRepository.findOlderThanByCategory(PollStatus.PUBLIC, category, beforeId, pageable);
    }

}
