package org.kwakmunsu.auddum.poll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberErrorCode;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.MemberStatus;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.domain.PollStatus;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;
import org.kwakmunsu.auddum.support.paging.CursorPaging;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내가 올린 투표 목록(PollQueryService#getMyPolls, GET /polls/mine)의 통합 테스트(H2). 이슈 #163 sketch(Q1-B, Q2-A, Q4-A, Q5-A) 기준이다.
 * 가정한 이름: getMyPolls(Long memberId, CursorPaging paging) -> MyPollPage(Slice summaries, long totalCount).
 * 항목의 status는 PollSummary.poll().getStatus()로 얻는다.
 */
@RequiredArgsConstructor
@Transactional
class PollQueryServiceMyPollsTest extends IntegrationTestSupport {

    final PollQueryService pollQueryService;
    final PollJpaRepository pollJpaRepository;
    final MemberJpaRepository memberJpaRepository;
    final EntityManager entityManager;

    // ---- 범위: 본인 것, 공개·숨김, 삭제 제외 ----

    @Test
    void 본인이_올린_공개_투표는_목록에_나온다() {
        var memberId = saveMember();
        var poll = savePoll(memberId);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(poll.getId());
    }

    @Test
    void 본인이_올린_숨김_투표도_status_HIDDEN으로_목록에_나온다() {
        var memberId = saveMember();
        var hidden = savePoll(memberId);
        hidden.hide();
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).hasSize(1);
        assertThat(page.summaries().getContent().getFirst().poll().getId()).isEqualTo(hidden.getId());
        assertThat(page.summaries().getContent().getFirst().poll().getStatus()).isEqualTo(PollStatus.HIDDEN);
    }

    @Test
    void 공개_투표_항목의_status는_PUBLIC이다() {
        var memberId = saveMember();
        savePoll(memberId);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getStatus())
            .containsExactly(PollStatus.PUBLIC);
    }

    @Test
    void 본인이_올린_삭제_투표는_목록에서_제외한다() {
        var memberId = saveMember();
        var publicPoll = savePoll(memberId);
        var deleted = savePoll(memberId);
        deleted.deleteByAdmin();
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(publicPoll.getId());
    }

    @Test
    void 다른_작성자의_투표는_목록에_나오지_않는다() {
        var memberId = saveMember();
        var otherId = saveMember();
        var mine = savePoll(memberId);
        var othersPublic = savePoll(otherId);
        var othersHidden = savePoll(otherId);
        othersHidden.hide();
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(mine.getId())
            .doesNotContain(othersPublic.getId(), othersHidden.getId());
    }

    @Test
    void 목록은_투표_id_내림차순_최신순이다() {
        var memberId = saveMember();
        var first = savePoll(memberId);
        var second = savePoll(memberId);
        var third = savePoll(memberId);
        second.hide();
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void 참여가_없는_내_투표의_참여_수는_0이다() {
        var memberId = saveMember();
        savePoll(memberId);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(PollSummary::participantCount).containsExactly(0L);
    }

    @Test
    void 올린_투표가_없으면_빈_목록이고_totalCount는_0이며_다음_쪽이_없다() {
        var memberId = saveMember();
        var otherId = saveMember();
        savePoll(otherId);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).isEmpty();
        assertThat(page.summaries().hasNext()).isFalse();
        assertThat(page.totalCount()).isZero();
    }

    // ---- totalCount ----

    @Test
    void totalCount는_공개와_숨김_투표만_세고_삭제와_타인의_투표는_세지_않는다() {
        var memberId = saveMember();
        var otherId = saveMember();
        savePoll(memberId);
        savePoll(memberId).hide();
        savePoll(memberId).deleteByAdmin();
        savePoll(otherId);
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.totalCount()).isEqualTo(2L);
    }

    @Test
    void totalCount는_목록_전체_길이와_같다() {
        var memberId = saveMember();
        savePoll(memberId);
        savePoll(memberId).hide();
        savePoll(memberId);
        savePoll(memberId).deleteByAdmin();
        flushAndClear();

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 50));

        assertThat(page.summaries().getContent()).hasSize(3);
        assertThat(page.totalCount()).isEqualTo(page.summaries().getContent().size());
    }

    @Test
    void totalCount는_size보다_커도_페이지와_무관하게_전체_개수다() {
        var memberId = saveMember();
        savePolls(memberId, 5);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 2));

        assertThat(page.summaries().getContent()).hasSize(2);
        assertThat(page.totalCount()).isEqualTo(5L);
    }

    @Test
    void totalCount는_커서를_넘겨도_같다() {
        var memberId = saveMember();
        var saved = savePolls(memberId, 5);
        var cursorId = saved.get(2).getId();

        var firstPage = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 2));
        var nextPage = pollQueryService.getMyPolls(memberId, new CursorPaging(cursorId, 2));

        assertThat(nextPage.totalCount()).isEqualTo(5L);
        assertThat(nextPage.totalCount()).isEqualTo(firstPage.totalCount());
    }

    // ---- 커서와 페이지 경계 ----

    @Test
    void 커서를_넘기면_그_id보다_오래된_투표부터_이어서_내려주고_마지막_쪽에_nextCursor가_없다() {
        var memberId = saveMember();
        var saved = savePolls(memberId, 5);
        var newestFirstIds = saved.reversed().stream().map(Poll::getId).toList();

        var firstPage = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 2));
        var secondPage = pollQueryService.getMyPolls(memberId, new CursorPaging(lastIdOf(firstPage.summaries().getContent()), 2));
        var thirdPage = pollQueryService.getMyPolls(memberId, new CursorPaging(lastIdOf(secondPage.summaries().getContent()), 2));

        assertThat(firstPage.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactlyElementsOf(newestFirstIds.subList(0, 2));
        assertThat(secondPage.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactlyElementsOf(newestFirstIds.subList(2, 4));
        assertThat(thirdPage.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(newestFirstIds.get(4));
        assertThat(firstPage.summaries().hasNext()).isTrue();
        assertThat(secondPage.summaries().hasNext()).isTrue();
        assertThat(thirdPage.summaries().hasNext()).isFalse();
    }

    @Test
    void 남은_개수가_size와_같으면_다음_쪽이_없다() {
        var memberId = saveMember();
        savePolls(memberId, 2);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 2));

        assertThat(page.summaries().getContent()).hasSize(2);
        assertThat(page.summaries().hasNext()).isFalse();
    }

    @Test
    void 남은_개수가_size보다_하나_많으면_다음_쪽이_있다() {
        var memberId = saveMember();
        savePolls(memberId, 3);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 2));

        assertThat(page.summaries().getContent()).hasSize(2);
        assertThat(page.summaries().hasNext()).isTrue();
    }

    @Test
    void size가_1이면_한_개와_다음_쪽을_내려준다() {
        var memberId = saveMember();
        savePolls(memberId, 3);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 1));

        assertThat(page.summaries().getContent()).hasSize(1);
        assertThat(page.summaries().hasNext()).isTrue();
    }

    @Test
    void size가_50이면_50개를_내려준다() {
        var memberId = saveMember();
        savePolls(memberId, 51);

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 50));

        assertThat(page.summaries().getContent()).hasSize(50);
        assertThat(page.summaries().hasNext()).isTrue();
    }

    @Test
    void 커서_사이에_삭제된_투표가_있어도_건너뛰고_빠짐_중복_없이_이어진다() {
        var memberId = saveMember();
        var first = savePoll(memberId);
        var deleted = savePoll(memberId);
        var third = savePoll(memberId);
        deleted.deleteByAdmin();
        flushAndClear();

        var firstPage = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 1));
        var secondPage = pollQueryService.getMyPolls(memberId, new CursorPaging(lastIdOf(firstPage.summaries().getContent()), 1));

        assertThat(firstPage.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(third.getId());
        assertThat(secondPage.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(first.getId());
        assertThat(secondPage.summaries().hasNext()).isFalse();
    }

    // ---- 회원 상태 (Q5-A) ----

    @Test
    void 정지_회원도_자기_목록을_조회한다() {
        var memberId = saveMember();
        var poll = savePoll(memberId);
        var member = memberJpaRepository.findById(memberId).orElseThrow();
        memberJpaRepository.saveAndFlush(MemberFixture.changeStatus(member, MemberStatus.SUSPENDED));

        var page = pollQueryService.getMyPolls(memberId, new CursorPaging(null, 20));

        assertThat(page.summaries().getContent()).extracting(summary -> summary.poll().getId())
            .containsExactly(poll.getId());
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    void 탈퇴한_회원은_401_UNAUTHENTICATED다() {
        var withdrawn = MemberFixture.createMember();
        withdrawn.withdraw(LocalDateTime.now(TimeConfig.KST));
        var withdrawnId = memberJpaRepository.saveAndFlush(withdrawn).getId();

        assertThatThrownBy(() -> pollQueryService.getMyPolls(withdrawnId, new CursorPaging(null, 20)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(DefaultErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    void DB에_없는_회원은_404_MEMBER_NOT_FOUND다() {
        assertThatThrownBy(() -> pollQueryService.getMyPolls(Long.MAX_VALUE, new CursorPaging(null, 20)))
            .isInstanceOf(AuddumException.class)
            .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    // ---- 보조 ----

    private Long lastIdOf(List<PollSummary> summaries) {
        return summaries.getLast().poll().getId();
    }

    private Long saveMember() {
        return memberJpaRepository.saveAndFlush(MemberFixture.createMember()).getId();
    }

    private Poll savePoll(Long authorId) {
        var poll = Poll.create(authorId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것"), null, LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

    /** 오래된 순(저장 순서)으로 돌려준다. */
    private List<Poll> savePolls(Long authorId, int count) {
        var polls = new ArrayList<Poll>();
        for (int i = 0; i < count; i++) {
            polls.add(savePoll(authorId));
        }
        flushAndClear();

        return polls;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

}
