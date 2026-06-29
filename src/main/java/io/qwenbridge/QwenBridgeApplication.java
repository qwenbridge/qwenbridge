package io.qwenbridge;

import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableConfigurationProperties(OpenSearchProperties.class)
public class QwenBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QwenBridgeApplication.class, args);
    }
}