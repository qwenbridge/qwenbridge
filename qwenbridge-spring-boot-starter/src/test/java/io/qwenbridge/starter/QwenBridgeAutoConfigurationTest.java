package io.qwenbridge.starter;

import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient;
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

    @Test
    void shouldCreateSdkBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(QwenBridgeClientConfig.class);
            assertThat(context).hasSingleBean(QwenBridgeClient.class);
            assertThat(context).hasSingleBean(QwenBridgeStreamingClient.class);
        });
    }

    @Test
    void shouldRespectCustomClientConfigBean() {
        QwenBridgeClientConfig customConfig = new QwenBridgeClientConfig(
                java.net.URI.create("http://custom-qwenbridge:8081"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99)
        );

        contextRunner
                .withBean(QwenBridgeClientConfig.class, () -> customConfig)
                .run(context -> {
                    assertThat(context).hasSingleBean(QwenBridgeClientConfig.class);
                    assertThat(context.getBean(QwenBridgeClientConfig.class))
                            .isSameAs(customConfig);
                    assertThat(context).hasSingleBean(QwenBridgeClient.class);
                    assertThat(context).hasSingleBean(QwenBridgeStreamingClient.class);
                });
    }

    @Test
    void shouldRespectCustomSdkBeans() {
        QwenBridgeClientConfig customConfig = new QwenBridgeClientConfig(
                java.net.URI.create("http://custom-qwenbridge:8081"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99)
        );

        QwenBridgeClient customClient = new QwenBridgeClient(customConfig);
        QwenBridgeStreamingClient customStreamingClient =
                new QwenBridgeStreamingClient(customConfig);

        contextRunner
                .withBean(QwenBridgeClient.class, () -> customClient)
                .withBean(QwenBridgeStreamingClient.class, () -> customStreamingClient)
                .run(context -> {
                    assertThat(context.getBean(QwenBridgeClient.class))
                            .isSameAs(customClient);
                    assertThat(context.getBean(QwenBridgeStreamingClient.class))
                            .isSameAs(customStreamingClient);
                });
    }

}

