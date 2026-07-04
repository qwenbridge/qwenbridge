package io.qwenbridge.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QwenBridgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(QwenBridgeAutoConfiguration.class)
                    );

    @Test
    void shouldLoadPropertiesWithDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QwenBridgeProperties.class);

            QwenBridgeProperties properties =
                    context.getBean(QwenBridgeProperties.class);

            assertThat(properties.getBaseUrl())
                    .isEqualTo("http://localhost:8080");

            assertThat(properties.getConnectTimeout())
                    .isEqualTo(Duration.ofSeconds(2));

            assertThat(properties.getRequestTimeout())
                    .isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void shouldBindConfiguredProperties() {
        contextRunner
                .withPropertyValues(
                        "qwenbridge.base-url=http://qwenbridge.internal:9090",
                        "qwenbridge.connect-timeout=5s",
                        "qwenbridge.request-timeout=45s"
                )
                .run(context -> {
                    QwenBridgeProperties properties =
                            context.getBean(QwenBridgeProperties.class);

                    assertThat(properties.getBaseUrl())
                            .isEqualTo("http://qwenbridge.internal:9090");

                    assertThat(properties.getConnectTimeout())
                            .isEqualTo(Duration.ofSeconds(5));

                    assertThat(properties.getRequestTimeout())
                            .isEqualTo(Duration.ofSeconds(45));
                });
    }
}
