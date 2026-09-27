package io.qwenbridge.operations.config;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class ProductionConfigurationValidator {

  private static final List<String> REQUIRED_PRODUCTION_PROPERTIES =
      List.of(
          "qwenbridge.security.cors.allowed-origin-patterns",
          "qwenbridge.ai.ollama.base-url",
          "qwenbridge.search.opensearch.base-url",
          "qwenbridge.analysis.cache.redis.host");

  void validate(@Observes StartupEvent event) {
    Config config = ConfigProvider.getConfig();

    boolean production = config.unwrap(SmallRyeConfig.class).getProfiles().contains("production");
    if (!production) {
      return;
    }

    List<String> missing =
        REQUIRED_PRODUCTION_PROPERTIES.stream()
            .filter(
                property ->
                    config
                        .getOptionalValue(property, String.class)
                        .map(String::isBlank)
                        .orElse(true))
            .toList();

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Missing required production configuration: " + String.join(",", missing));
    }
  }
}
