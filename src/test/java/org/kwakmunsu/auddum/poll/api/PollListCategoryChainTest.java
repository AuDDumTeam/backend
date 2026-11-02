package org.kwakmunsu.auddum.poll.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.kwakmunsu.auddum.member.domain.MemberFixture;
import org.kwakmunsu.auddum.member.domain.Role;
import org.kwakmunsu.auddum.member.infrastructure.MemberJpaRepository;
import org.kwakmunsu.auddum.poll.domain.Category;
import org.kwakmunsu.auddum.poll.domain.Poll;
import org.kwakmunsu.auddum.poll.infrastructure.PollJpaRepository;
import org.kwakmunsu.auddum.support.config.TimeConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/polls?category= 를 실제 필터 체인과 DB로 확인한다(#162).
 * 카테고리를 주면 그 카테고리의 공개 투표만 JSON items로 나오는지, Category 이름이 아닌 값은 400 MALFORMED_REQUEST인지 본다.
 * 필터·최신순·커서 규칙의 전수 조합은 PollQueryServiceTest가, category 요청 매핑은 PollListWebApiTest가 맡는다.
 * MockMvc 요청은 테스트와 같은 스레드라 @Transactional로 만든 데이터가 요청에서 보이고, 끝나면 롤백된다.
 */
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
class PollListCategoryChainTest extends IntegrationTestSupport {

    final MockMvcTester mvcTester;
    final MemberJpaRepository memberJpaRepository;
    final PollJpaRepository pollJpaRepository;

    @Test
    void category가_BREAKUP이면_그_카테고리의_공개_투표만_items로_나온다() {
        var authorId = memberJpaRepository.saveAndFlush(MemberFixture.createMember(Role.MEMBER)).getId();
        var breakup = savePoll(authorId, Category.BREAKUP);
        savePoll(authorId, Category.SOME);
        savePoll(authorId, Category.ETC);
        var hiddenBreakup = savePoll(authorId, Category.BREAKUP);
        hiddenBreakup.hide();
        pollJpaRepository.saveAndFlush(hiddenBreakup);

        var result = mvcTester.get().uri("/api/v1/polls?category=BREAKUP").exchange();

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(breakup.getPublicId().value());
        assertThat(result).bodyJson().extractingPath("$.items[0].category").isEqualTo("BREAKUP");
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"some", "ALL", "SOEM"})
    void category가_Category_이름이_아니면_400_MALFORMED_REQUEST다(String category) {
        var result = mvcTester.get().uri("/api/v1/polls").queryParam("category", category).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    private Poll savePoll(Long authorId, Category category) {
        var poll = Poll.create(authorId, "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?", category,
            List.of("관심 없음", "그냥 바쁜 것"), null, LocalDateTime.now(TimeConfig.KST));

        return pollJpaRepository.saveAndFlush(poll);
    }

}
