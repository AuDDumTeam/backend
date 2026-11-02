package org.kwakmunsu.auddum.poll.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.poll.domain.Category;

/**
 * 목록 조회 category 파라미터 해석(#162).
 * Category 이름은 그 Category로, 그 밖의 모든 입력은 null(필터 없음)이며 어떤 입력도 예외를 던지지 않는다.
 */
class PollListRequestTest {

    @ParameterizedTest
    @EnumSource(Category.class)
    void Category_이름이면_그_Category로_해석한다(Category category) {
        var request = new PollListRequest(null, null, category.name());

        assertThat(request.categoryFilter()).isEqualTo(category);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ALL"})
    void 없거나_비어_있거나_ALL이면_필터_없음이다(String category) {
        var request = new PollListRequest(null, null, category);

        assertThat(request.categoryFilter()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "some", "Some", "blind_date",       // 대소문자가 다름
        " SOME", "SOME ", " ", "   ", "\t", // 공백이 붙었거나 공백만 있음
        "SOME,ETC", "SOME,SOME", ",SOME",   // 쉼표로 이음(같은 파라미터를 반복해 보낸 경우)
        "SOEM", "UNKNOWN", "전체", "0", "SOME;ETC", "ALL ", "all", // 오타, 모르는 값
        "\u0000", "😀"                      // 제어 문자, 이모지
    })
    void Category_이름이_아닌_값은_예외_없이_필터_없음이다(String category) {
        var request = new PollListRequest(null, null, category);

        assertThat(request.categoryFilter()).isNull();
    }

}
