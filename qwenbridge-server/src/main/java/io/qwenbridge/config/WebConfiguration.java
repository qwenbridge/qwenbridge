package io.qwenbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  private final String allowedOriginPatterns;

  public WebConfiguration(
      @Value("${qwenbridge.security.cors.allowed-origin-patterns:*}")
          String allowedOriginPatterns) {
    this.allowedOriginPatterns = allowedOriginPatterns;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {

    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(allowedOriginPatterns.split(","))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("X-Request-Id", "X-QwenBridge-Version", "X-Trace-Id", "traceparent")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
