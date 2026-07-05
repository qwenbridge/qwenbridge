package io.qwenbridge.ai.provider.ollama.client;

import io.netty.channel.ChannelOption;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class OllamaClientConfig {

  @Bean
  WebClient ollamaWebClient(WebClient.Builder builder, OllamaProperties properties) {
    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                Math.toIntExact(properties.connectTimeout().toMillis()))
            .responseTimeout(properties.readTimeout());

    return builder
        .baseUrl(properties.baseUrl().toString())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }
}
