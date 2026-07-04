package io.qwenbridge.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(QwenBridgeProperties.class)
public class QwenBridgeAutoConfiguration {
}
