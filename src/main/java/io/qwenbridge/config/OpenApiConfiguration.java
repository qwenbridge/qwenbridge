package io.qwenbridge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QwenBridge API")
                        .description("""
                                QwenBridge is an AI-native search and provider bridge platform.

                                It provides public APIs for search analysis, AI provider access,
                                metadata, health checks, and future streaming/SDK integrations.
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("QwenBridge")
                                .url("https://github.com/Mahad-Banai/qwenbridge")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")
                ))
                .components(new Components()
                        .addHeaders("X-Request-ID", new Header()
                                .description("Correlation ID for tracing requests across QwenBridge")
                                .schema(new StringSchema()))
                        .addHeaders("X-QwenBridge-Version", new Header()
                                .description("QwenBridge application version")
                                .schema(new StringSchema())));
    }
}
