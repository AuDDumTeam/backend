package org.kwakmunsu.auddum.poll.api.dto;

import org.kwakmunsu.auddum.poll.domain.Category;

/**
 * 목록 조회 파라미터(cursor, size, category 쿼리 파라미터를 묶는다).
 * 커서는 불투명 문자열이고, size는 없으면 기본값, 범위를 벗어나면 1~50으로 보정한다.
 * category는 Category 이름이면 그 카테고리, 그 밖의 모든 값은 필터 없음(전체)이다.
 */
public record PollListRequest(
    String cursor,
    Integer size,
    String category
) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 50;

    /** 커서가 없으면 null이고 첫 페이지다. 형식이 틀리면 MALFORMED_REQUEST다. */
    public Long cursorId() {
        PollCursor decoded = PollCursor.from(cursor);

        return decoded == null ? null : decoded.id();
    }

    public int pageSize() {
        return size == null ? DEFAULT_SIZE : Math.clamp(size, MIN_SIZE, MAX_SIZE);
    }

    /**
     * category가 Category 이름이면 그 Category, 아니면(없음, 빈 값, ALL, 모르는 값 모두) null이다.
     * null은 필터 없음이다. 예외를 던지지 않는다.
     */
    public Category categoryFilter() {
        if (category == null) {
            return null;
        }

        for (Category candidate : Category.values()) {
            if (candidate.name().equals(category)) {
                return candidate;
            }
        }

        return null;
    }

    public static String nextCursor(Long lastId) {
        return new PollCursor(lastId).encode();
    }

}
