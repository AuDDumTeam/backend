package org.kwakmunsu.auddum.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.anon.AnonCookieProperties;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider.Verification;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.slf4j.LoggerFactory;

/**
 * 요청자 해석 단위 테스트. 기준은 인증.md 익명 쿠키 절과 P-09:
 * 회원은 쿠키를 보지 않고(anonId·newCookieValue는 항상 null), 비회원은 유효한 쿠키면 그 UUID, 없거나 위조면 새로 발급하며 위조는 WARN을 남긴다(값 원문 제외).
 */
class RequesterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final RequesterResolver resolver = new RequesterResolver(new AnonIdentityResolver(anonCookieProvider));

    private final Logger anonLogger = (Logger) LoggerFactory.getLogger("org.kwakmunsu.auddum.support.anon");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        originalLevel = anonLogger.getLevel();
        anonLogger.setLevel(Level.ALL);
        appender.start();
        anonLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        anonLogger.detachAppender(appender);
        appender.stop();
        anonLogger.setLevel(originalLevel);
    }

    private String logText(ILoggingEvent event) {
        var arguments = event.getArgumentArray() == null ? "" : Arrays.toString(event.getArgumentArray());
        var throwable = event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage();
        return event.getFormattedMessage() + " " + arguments + " " + throwable;
    }

    // ---- 회원은 쿠키를 보지 않는다 ----

    @Test
    void 회원이고_쿠키가_없으면_회원_ID만_있고_익명_식별자와_새_쿠키가_없다() {
        var requester = resolver.resolve(MEMBER_ID, null);

        assertThat(requester.memberId()).isEqualTo(MEMBER_ID);
        assertThat(requester.anonId()).isNull();
        assertThat(requester.newCookieValue()).isNull();
    }

    @Test
    void 회원은_유효한_익명_쿠키가_있어도_익명_식별자와_새_쿠키가_없다() {
        var cookie = anonCookieProvider.issue();

        var requester = resolver.resolve(MEMBER_ID, cookie.value());

        assertThat(requester.memberId()).isEqualTo(MEMBER_ID);
        assertThat(requester.anonId()).isNull();
        assertThat(requester.newCookieValue()).isNull();
    }

    @Test
    void 회원은_위조된_익명_쿠키를_보지_않아_발급도_로그도_없다() {
        var requester = resolver.resolve(MEMBER_ID, "v1." + UUID.randomUUID() + ".forged");

        assertThat(requester.memberId()).isEqualTo(MEMBER_ID);
        assertThat(requester.anonId()).isNull();
        assertThat(requester.newCookieValue()).isNull();
        assertThat(appender.list).isEmpty();
    }

    // ---- 비회원 ----

    @Test
    void 유효한_쿠키면_쿠키의_UUID가_익명_식별자이고_다시_발급하지_않는다() {
        var cookie = anonCookieProvider.issue();

        var requester = resolver.resolve(null, cookie.value());

        assertThat(requester.memberId()).isNull();
        assertThat(requester.anonId()).isEqualTo(cookie.id());
        assertThat(requester.newCookieValue()).isNull();
    }

    @Test
    void 쿠키가_없으면_새_익명_식별자를_만들고_새_쿠키_값을_채운다() {
        var requester = resolver.resolve(null, null);

        assertThat(requester.memberId()).isNull();
        assertThat(requester.anonId()).isNotNull();
        assertThat(requester.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(requester.newCookieValue()))
                .isEqualTo(new Verification.Valid(requester.anonId()));
    }

    @Test
    void 빈_쿠키_값이면_새_익명_식별자를_발급한다() {
        var requester = resolver.resolve(null, "");

        assertThat(requester.anonId()).isNotNull();
        assertThat(requester.newCookieValue()).isNotNull();
    }

    @Test
    void 위조된_쿠키면_다른_사람의_식별자가_아닌_새_식별자로_발급한다() {
        var victim = anonCookieProvider.issue();

        var requester = resolver.resolve(null, "v1." + victim.id() + ".forged");

        assertThat(requester.anonId()).isNotEqualTo(victim.id());
        assertThat(requester.newCookieValue()).isNotNull();
        assertThat(anonCookieProvider.check(requester.newCookieValue()))
                .isEqualTo(new Verification.Valid(requester.anonId()));
    }

    @Test
    void 위조된_쿠키면_WARN을_남기고_쿠키_원문은_로그에_없다() {
        var forged = "v1." + UUID.randomUUID() + ".forged";

        resolver.resolve(null, forged);

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(logText(event)).doesNotContain(forged);
    }

    @Test
    void 유효한_쿠키나_쿠키_없음은_로그를_남기지_않는다() {
        resolver.resolve(null, anonCookieProvider.issue().value());
        resolver.resolve(null, null);

        assertThat(appender.list).isEmpty();
    }

}
