package io.qwenbridge.starter;

import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.URI;

@AutoConfiguration
@EnableConfigurationProperties(QwenBridgeProperties.class)
public class QwenBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public QwenBridgeClientConfig qwenBridgeClientConfig(
            QwenBridgeProperties properties
    ) {
        return new QwenBridgeClientConfig(
                URI.create(properties.getBaseUrl()),
                properties.getConnectTimeout(),
                properties.getRequestTimeout()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public QwenBridgeClient qwenBridgeClient(
            QwenBridgeClientConfig config
    ) {
        return new QwenBridgeClient(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public QwenBridgeStreamingClient qwenBridgeStreamingClient(
            QwenBridgeClientConfig config
    ) {
        return new QwenBridgeStreamingClient(config);
    }
}
