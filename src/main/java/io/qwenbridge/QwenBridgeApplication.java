package io.qwenbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class QwenBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QwenBridgeApplication.class, args);
    }
}
