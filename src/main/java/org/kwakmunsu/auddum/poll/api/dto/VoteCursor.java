package org.kwakmunsu.auddum.poll.api.dto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kwakmunsu.auddum.support.error.AuddumException;
import org.kwakmunsu.auddum.support.error.DefaultErrorCode;

/**
 * 내가 참여한 투표 목록 커서. 마지막으로 본 참여 기록 id를 {@code {"id":123}}로 만들어 URL-safe base64로 감싼 불투명 문자열이다.
 * 바깥에서는 해석하지 않는 값이라 형식이 틀리면 MALFORMED_REQUEST다.
 */
public record VoteCursor(long id) {

    private static final Pattern PAYLOAD = Pattern.compile("\\{\"id\":(\\d{1,18})}");

    /** 커서가 가리키는 내부 id. 커서가 없거나 비어 있으면 첫 페이지이므로 null이고, 형식이 틀리면 MALFORMED_REQUEST다. */
    public static Long idOf(String cursor) {
        VoteCursor decoded = from(cursor);

        return decoded == null ? null : decoded.id();
    }

    /** 커서가 없거나 비어 있으면 첫 페이지이므로 null이다. */
    static VoteCursor from(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            Matcher matcher = PAYLOAD.matcher(payload);
            if (matcher.matches()) {
                return new VoteCursor(Long.parseLong(matcher.group(1)));
            }
        } catch (IllegalArgumentException _) {
            // base64가 아니면 아래에서 형식 오류로 던진다.
        }

        throw new AuddumException(DefaultErrorCode.MALFORMED_REQUEST);
    }

    /** 응답의 nextCursor로 내리는 불투명 문자열. */
    String encode() {
        byte[] payload = ("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

}
