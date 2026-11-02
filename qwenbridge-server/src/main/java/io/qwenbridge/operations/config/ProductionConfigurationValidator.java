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

  /**
   * Settings production must define. The optional OpenSearch backend, Connector Pack settings and
   * the optional Redis cache are not required (QB-293): they are needed only when enabled.
   */
  private static final List<String> REQUIRED_PRODUCTION_PROPERTIES =
      List.of("qwenbridge.security.cors.allowed-origin-patterns", "qwenbridge.ai.ollama.base-url");

  void validate(@Observes StartupEvent event) {
    Config config = ConfigProvider.getConfig();

    // Audit S1b: Quarkus's production profile is "prod"; "production" is kept for compatibility.
    var profiles = config.unwrap(SmallRyeConfig.class).getProfiles();
    boolean production = profiles.contains("prod") || profiles.contains("production");
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
