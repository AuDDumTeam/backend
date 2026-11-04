package org.kwakmunsu.auddum.vote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.anonymous;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.member;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.noCredential;
import static org.kwakmunsu.auddum.vote.application.VoteFixture.tamperSignature;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kwakmunsu.auddum.support.anon.AnonCookieProperties;
import org.kwakmunsu.auddum.support.anon.AnonCookieProvider;
import org.kwakmunsu.auddum.support.anon.AnonIdentityResolver;
import org.kwakmunsu.auddum.vote.domain.AnonId;
import org.kwakmunsu.auddum.vote.domain.Voter;
import org.slf4j.LoggerFactory;

/**
 * 조회용 참여자 찾기 단위 테스트. 쿠키 해석과 위조 로그는 AnonIdentityResolverTest가 검증하고, 여기서는 find의 회원 우선과 위임만 본다.
 * 회원 상태 확인과 쓰기 요청의 신원 결정은 각각 VoteValidatorTest와 RequesterResolverTest가 본다.
 */
class VoterResolverTest {

    private static final Long MEMBER_ID = 7L;
    private static final String SECRET = "test-anon-cookie-secret-0123456789-abcdef";

    private final AnonCookieProvider anonCookieProvider = new AnonCookieProvider(new AnonCookieProperties(SECRET));
    private final VoterResolver resolver = new VoterResolver(new AnonIdentityResolver(anonCookieProvider));

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

    // ---- find 위임 (조회용) ----

    @Test
    void find는_회원이면_회원_참여자를_돌려준다() {
        var found = resolver.find(member(MEMBER_ID));

        assertThat(found).contains(Voter.member(MEMBER_ID));
    }

    @Test
    void find는_유효한_쿠키면_그_UUID의_익명_참여자를_돌려준다() {
        var cookie = anonCookieProvider.issue();

        var found = resolver.find(anonymous(cookie.value()));

        assertThat(found).contains(Voter.anonymous(new AnonId(cookie.id())));
    }

    @Test
    void find는_쿠키가_없거나_위조되었으면_비어_있고_로그도_없다() {
        assertThat(resolver.find(noCredential())).isEmpty();
        assertThat(resolver.find(anonymous("garbage"))).isEmpty();
        assertThat(resolver.find(anonymous(tamperSignature(anonCookieProvider.issue().value())))).isEmpty();
        assertThat(appender.list).isEmpty();
    }

}
