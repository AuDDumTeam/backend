package org.kwakmunsu.auddum.poll.api.dto;

import org.kwakmunsu.auddum.poll.application.dto.CursorPaging;

/** 목록 조회 파라미터(cursor, size 쿼리 파라미터를 묶는다). 커서는 불투명 문자열이고, size는 없으면 기본값, 범위를 벗어나면 1~50으로 보정한다. */
public record PollListRequest(
    String cursor,
    Integer size
) {

    /** 커서가 없으면 null이고 첫 페이지다. 형식이 틀리면 MALFORMED_REQUEST다. */
    public Long cursorId() {
        return PollCursor.idOf(cursor);
    }

    public int pageSize() {
        return CursorPaging.clampSize(size);
    }

    public static String nextCursor(Long lastId) {
        return new PollCursor(lastId).encode();
    }

}
