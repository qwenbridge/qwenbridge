package io.qwenbridge.execution.provider.opensearch.client;

import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenSearchClientConfig {

  @Bean
  public WebClient openSearchWebClient(OpenSearchProperties properties) {
    return WebClient.builder().baseUrl(properties.baseUrl()).build();
  }
}
