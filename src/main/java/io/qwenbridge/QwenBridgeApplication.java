package io.qwenbridge;

import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.qwenbridge.streaming.config.StreamingProperties;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableConfigurationProperties({
        OpenSearchProperties.class,
        StreamingProperties.class
})
public class QwenBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QwenBridgeApplication.class, args);
    }
}