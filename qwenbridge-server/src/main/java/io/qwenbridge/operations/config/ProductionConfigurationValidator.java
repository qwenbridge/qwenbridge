package io.qwenbridge.operations.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationValidator {

  private static final List<String> REQUIRED_PRODUCTION_PROPERTIES =
      List.of(
          "qwenbridge.security.cors.allowed-origin-patterns",
          "qwenbridge.ai.ollama.base-url",
          "qwenbridge.search.opensearch.base-url",
          "qwenbridge.analysis.cache.redis.host");

  private final Environment environment;

  public ProductionConfigurationValidator(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    boolean production = Arrays.asList(environment.getActiveProfiles()).contains("production");
    if (!production) {
      return;
    }

    List<String> missing =
        REQUIRED_PRODUCTION_PROPERTIES.stream()
            .filter(
                property ->
                    environment.getProperty(property) == null
                        || environment.getProperty(property, "").isBlank())
            .toList();

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Missing required production configuration: " + String.join(",", missing));
    }
  }
}
