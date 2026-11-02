package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kwakmunsu.auddum.member.application.MemberReader;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.stereotype.CommandService;

@Slf4j
@RequiredArgsConstructor
@CommandService
public class PollCommandService {

    private final PollWriter pollWriter;
    private final PollValidator pollValidator;
    private final PollReader pollReader;
    private final MemberReader memberReader;

    /**
     * 새로운 투표를 생성합니다. 정상 회원만 만들 수 있고(정지 403, 탈퇴·없음 401), 입력 검증, 직전 동일 질문(409), 생성 간격(429) 순서로 판단합니다.
     *
     * @param newPoll 새로운 투표 정보
     * @param authorId 투표 작성자 ID
     * @return 생성된 투표
     */
    public Poll createPoll(NewPoll newPoll, Long authorId) {
        memberReader.getActiveMember(authorId);

        LocalDateTime now = LocalDateTime.now(TimeConfig.KST);
        Poll poll = pollWriter.createWithUniquePublicId(newPoll, authorId, now);
        pollValidator.validateCreatable(poll, now);

        Poll saved = pollWriter.save(poll);

        log.info("[PollCommandService] Poll 생성 완료 - pollId={}, authorId={}", saved.getId(), authorId);

        return saved;
    }

    /**
     * 작성자가 자기 투표를 삭제한다(논리 삭제). 정상 회원만 할 수 있고(정지 403, 탈퇴·없음 401), 회원 상태를 투표 조회보다 먼저 확인한다.
     * 작성자 판정과 404·403 구분은 {@link Poll#deleteByAuthor}가 맡는다.
     *
     * @param memberId 삭제를 요청한 회원 ID
     * @param pollPublicId 삭제할 투표의 공개 ID
     */
    public void deleteByAuthor(Long memberId, String pollPublicId) {
        memberReader.getActiveMember(memberId);

        Poll poll = pollReader.find(pollPublicId);
        poll.deleteByAuthor(memberId);

        log.info("[PollCommandService] Poll 삭제 완료 - pollId={}, memberId={}", poll.getId(), memberId);
    }

}
