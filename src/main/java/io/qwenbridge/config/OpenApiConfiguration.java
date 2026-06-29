package io.qwenbridge.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI openAPI() {

        return new OpenAPI()

                .info(
                        new Info()
                                .title("QwenBridge API")
                                .description("""
                                        AI-native Search Intelligence Platform.

                                        QwenBridge provides:

                                        • AI Search Intelligence
                                        • AI Provider Platform
                                        • Search Pipeline
                                        • Threat Detection
                                        • Semantic Analysis
                                        • Multi-provider AI Gateway
                                        """)
                                .version("v1")
                                .contact(
                                        new Contact()
                                                .name("QwenBridge")
                                                .url("https://github.com/Mahad-Banai/QwenBridge")
                                )
                                .license(
                                        new License()
                                                .name("Apache License 2.0")
                                )
                )

                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development")
                ))

                .tags(List.of(
                        new Tag()
                                .name("Search")
                                .description("AI Search Engine APIs"),

                        new Tag()
                                .name("AI")
                                .description("AI Provider APIs")
                ))

                .externalDocs(
                        new ExternalDocumentation()
                                .description("Project Documentation")
                                .url("https://github.com/Mahad-Banai/QwenBridge")
                );
    }
}
