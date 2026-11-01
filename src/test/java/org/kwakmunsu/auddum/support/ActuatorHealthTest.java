package org.kwakmunsu.auddum.support;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kwakmunsu.auddum.IntegrationTestSupport;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Actuator는 {@code health}만 웹에 열고 응답은 {@code status} 하나다(sketch 155). DB가 끊겼을 때의 503 {@code DOWN}은 H2로 만들 수 없어
 * 이 테스트에 없고 배포 환경(#21)에서 확인한다.
 */
@AutoConfigureMockMvc
@RequiredArgsConstructor
class ActuatorHealthTest extends IntegrationTestSupport {

    final MockMvcTester mvcTester;

    @Test
    void 로그인_없이_헬스를_조회하면_200과_UP을_응답한다() {
        var result = mvcTester.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("UP"));
    }

    @Test
    void 헬스_응답은_status_키_하나뿐이다() {
        var result = mvcTester.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$")
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsOnlyKeys("status");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/info", "/actuator/prometheus"})
    void health_외_엔드포인트는_노출되지_않아_404다(String path) {
        var result = mvcTester.get().uri(path).exchange();

        assertThat(result).hasStatus(404);
    }

}
