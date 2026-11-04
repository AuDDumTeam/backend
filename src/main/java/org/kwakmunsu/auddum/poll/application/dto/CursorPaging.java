package org.kwakmunsu.auddum.poll.application.dto;

/**
 * 커서 페이징 조건. 쪽 크기가 없으면 기본값, 범위를 벗어나면 1~50으로 보정한다.
 *
 * @param cursorId 마지막으로 본 내부 id. 첫 페이지면 null이다
 * @param size     한 페이지 개수(보정된 값)
 */
public record CursorPaging(
    Long cursorId,
    int size
) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 50;

    public static CursorPaging of(Long cursorId, Integer size) {
        return new CursorPaging(cursorId, clampSize(size));
    }

    /** 쪽 크기를 보정한다. 없으면 기본 20, 1보다 작으면 1, 50을 넘으면 50이다. */
    public static int clampSize(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.clamp(size, MIN_SIZE, MAX_SIZE);
    }

}
