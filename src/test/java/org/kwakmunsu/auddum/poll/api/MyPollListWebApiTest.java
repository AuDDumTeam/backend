package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.WebApiTestSupport;
import org.kwakmunsu.auddum.poll.application.dto.CursorPaging;
import org.kwakmunsu.auddum.poll.application.dto.MyPollPage;
import org.kwakmunsu.auddum.poll.application.dto.PollSummary;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * GET /api/v1/polls/mine 웹 계층(서비스는 목). 경로, 회원 ID·cursor·size 바인딩, 응답 모양(items, nextCursor, totalCount), category 무시만 본다.
 * 조회 규칙은 PollQueryServiceMyPollsTest가 맡는다.
 * 가정한 이름: PollQueryService#getMyPolls(Long memberId, CursorPaging paging) -> MyPollPage(Slice summaries, long totalCount).
 */
class MyPollListWebApiTest extends WebApiTestSupport {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 12, 13, 0);
    /** {"id":1}을 URL-safe base64로 감싼 값. 테스트 투표의 내부 id가 1이다. */
    private static final String CURSOR_OF_ID_1 = "eyJpZCI6MX0";

    @Test
    void 로그인한_회원이_조회하면_200과_items_nextCursor_totalCount를_내린다() {
        var poll = createPoll();
        given(pollQueryService.getMyPolls(7L, new CursorPaging(null, 20))).willReturn(page(List.of(summary(poll, 3)), true, 25L));

        mvcTester.get()
            .uri("/api/v1/polls/mine")
            .with(loginAs(7L))
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .apply(print())
            .bodyJson()
            .hasPathSatisfying("$.items[0].id", v -> v.assertThat().isEqualTo(poll.getPublicId().value()))
            .hasPathSatisfying("$.items[0].question", v -> v.assertThat().isEqualTo("읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?"))
            .hasPathSatisfying("$.items[0].category", v -> v.assertThat().isEqualTo("SOME"))
            .hasPathSatisfying("$.items[0].participantCount", v -> v.assertThat().isEqualTo(3))
            .hasPathSatisfying("$.items[0].closed", v -> v.assertThat().isEqualTo(false))
            .hasPathSatisfying("$.items[0].createdAt", v -> v.assertThat().isEqualTo("2026-10-12T13:00:00+09:00"))
            .hasPathSatisfying("$.items[0].status", v -> v.assertThat().isEqualTo("PUBLIC"))
            .hasPathSatisfying("$.nextCursor", v -> v.assertThat().isEqualTo(CURSOR_OF_ID_1))
            .hasPathSatisfying("$.totalCount", v -> v.assertThat().isEqualTo(25));
    }

    @Test
    void 숨김_투표_항목은_status가_HIDDEN이다() {
        var poll = createPoll();
        poll.hide();
        given(pollQueryService.getMyPolls(7L, new CursorPaging(null, 20))).willReturn(page(List.of(summary(poll, 0)), false, 1L));

        mvcTester.get()
            .uri("/api/v1/polls/mine")
            .with(loginAs(7L))
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .hasPathSatisfying("$.items[0].status", v -> v.assertThat().isEqualTo("HIDDEN"));
    }

    @Test
    void 항목은_내부_id를_노출하지_않고_목록_필드에_status를_더한_것만_담는다() {
        given(pollQueryService.getMyPolls(7L, new CursorPaging(null, 20))).willReturn(page(List.of(summary(createPoll(), 0)), false, 1L));

        var result = mvcTester.get().uri("/api/v1/polls/mine").with(loginAs(7L)).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);

        @SuppressWarnings("unchecked")
        var item = ((List<Map<String, Object>>) body.get("items")).getFirst();
        assertThat(item.keySet()).containsExactlyInAnyOrder(
            "id", "question", "category", "participantCount", "closed", "closesAt", "createdAt", "status");
        assertThat(body.keySet()).containsExactlyInAnyOrder("items", "nextCursor", "totalCount");
    }

    @Test
    void 올린_투표가_없으면_빈_items_null_nextCursor_totalCount_0으로_200이다() {
        given(pollQueryService.getMyPolls(7L, new CursorPaging(null, 20))).willReturn(page(List.of(), false, 0L));

        var result = mvcTester.get().uri("/api/v1/polls/mine").with(loginAs(7L)).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);

        assertThat(body.get("items")).isEqualTo(List.of());
        assertThat(body).containsKey("nextCursor");
        assertThat(body.get("nextCursor")).isNull();
        assertThat(body.get("totalCount")).isEqualTo(0);
    }

    @Test
    void cursor를_해석하고_size와_토큰의_회원_ID를_서비스에_넘긴다() {
        given(pollQueryService.getMyPolls(7L, new CursorPaging(5L, 10))).willReturn(page(List.of(), false, 0L));

        mvcTester.get()
            .uri("/api/v1/polls/mine?cursor=eyJpZCI6NX0&size=10")
            .with(loginAs(7L))
            .assertThat()
            .hasStatus(HttpStatus.OK);

        verify(pollQueryService).getMyPolls(7L, new CursorPaging(5L, 10));
    }

    @Test
    void category를_줘도_무시하고_잘못된_값에도_400이_아니라_200이다() {
        given(pollQueryService.getMyPolls(any(), any())).willReturn(page(List.of(), false, 0L));

        mvcTester.get()
            .uri("/api/v1/polls/mine?category=not-a-category")
            .with(loginAs(7L))
            .assertThat()
            .hasStatus(HttpStatus.OK);

        verify(pollQueryService).getMyPolls(7L, new CursorPaging(null, 20));
    }

    @Test
    void 로그인하지_않으면_401_UNAUTHENTICATED이고_서비스를_부르지_않는다() {
        mvcTester.get()
            .uri("/api/v1/polls/mine")
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
            .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson()
            .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("UNAUTHENTICATED"));

        verifyNoInteractions(pollQueryService);
    }

    // ---- 보조 ----

    private MyPollPage page(List<PollSummary> summaries, boolean hasNext, long totalCount) {
        return new MyPollPage(new SliceImpl<>(summaries, PageRequest.ofSize(20), hasNext), totalCount);
    }

    private PollSummary summary(Poll poll, long participantCount) {
        return PollSummary.of(poll, participantCount);
    }

    private Poll createPoll() {
        var poll = Poll.create(7L, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", Category.SOME,
            List.of("관심 없음", "그냥 바쁜 것", "더 지켜봐야 함"), null, LocalDateTime.now(TimeConfig.KST));
        ReflectionTestUtils.setField(poll, "id", 1L);
        ReflectionTestUtils.setField(poll, "createdAt", CREATED_AT);

        return poll;
    }

}
