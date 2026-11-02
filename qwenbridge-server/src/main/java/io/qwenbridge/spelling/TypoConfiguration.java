package io.qwenbridge.spelling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.spelling.CorrectionMode;
import io.qwenbridge.core.spelling.CorrectionProvider;
import io.qwenbridge.core.spelling.DictionaryCorrectionProvider;
import io.qwenbridge.core.spelling.LanguageSupport;
import io.qwenbridge.core.spelling.TypoCorrector;
import io.qwenbridge.core.spelling.TypoSettings;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Typo correction configuration (QB-291). Nothing is enabled by default and no language is ever
 * assumed:
 *
 * <ul>
 *   <li>{@code qwenbridge.typo.enabled} (false), {@code qwenbridge.typo.languages} (none) and
 *       {@code qwenbridge.typo.mode} ({@code SUGGEST_ONLY}: never changes a query unless a
 *       correcting mode is chosen explicitly) for the application;
 *   <li>{@code qwenbridge.typo.tenants}: a JSON object of per-tenant settings ({@code enabled},
 *       {@code languages}, {@code mode}) that replace the application settings for that tenant
 *       only; no database is involved;
 *   <li>{@code qwenbridge.typo.dictionary-dir}: word lists for the built-in dictionary provider
 *       ({@code <bcp47>.txt}, optional {@code domain.txt}); other providers are discovered with
 *       {@link ServiceLoader} ({@link CorrectionProvider}).
 * </ul>
 *
 * A broken dictionary directory or provider is logged and skipped; startup never fails because of
 * typo configuration.
 */
@Singleton
public final class TypoConfiguration {

  private static final Logger LOG = Logger.getLogger(TypoConfiguration.class);

  private final TypoSettings application;
  private final Map<String, TypoSettings> tenants;
  private final List<CorrectionProvider> providers;

  public TypoConfiguration(
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.typo.enabled", defaultValue = "false") boolean enabled,
      @ConfigProperty(name = "qwenbridge.typo.languages") Optional<List<String>> languages,
      @ConfigProperty(name = "qwenbridge.typo.mode", defaultValue = "SUGGEST_ONLY") String mode,
      @ConfigProperty(name = "qwenbridge.typo.safe-threshold", defaultValue = "0.75") double safe,
      @ConfigProperty(name = "qwenbridge.typo.aggressive-threshold", defaultValue = "0.5")
          double aggressive,
      @ConfigProperty(name = "qwenbridge.typo.tenants", defaultValue = "{}") String tenantJson,
      @ConfigProperty(name = "qwenbridge.typo.dictionary-dir") Optional<String> dictionaryDir) {
    this.application =
        new TypoSettings(enabled, languages.orElse(List.of()), mode(mode), safe, aggressive);
    this.tenants = tenants(mapper, tenantJson, safe, aggressive);
    List<CorrectionProvider> found = new ArrayList<>();
    dictionaryDir
        .filter(d -> !d.isBlank())
        .ifPresent(
            dir -> {
              try {
                Path path = Path.of(dir);
                if (!Files.isDirectory(path)) throw new IllegalArgumentException("not a directory");
                found.add(DictionaryCorrectionProvider.load("dictionary", path));
              } catch (Exception e) {
                LOG.warnf("Typo dictionaries in %s not loaded: %s", dir, e.getMessage());
              }
            });
    ServiceLoader.load(CorrectionProvider.class, TypoConfiguration.class.getClassLoader()).stream()
        .forEach(
            p -> {
              try {
                found.add(p.get());
              } catch (RuntimeException | java.util.ServiceConfigurationError e) {
                LOG.warnf(
                    "Typo provider %s skipped: %s",
                    p.type().getName(), e.getClass().getSimpleName());
              }
            });
    this.providers = List.copyOf(found);
    report("application", application);
    tenants.forEach(this::report);
  }

  static CorrectionMode mode(String value) {
    try {
      return CorrectionMode.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "qwenbridge.typo.mode must be DISABLED, SUGGEST_ONLY, SAFE_AUTO_CORRECT or AGGRESSIVE");
    }
  }

  static Map<String, TypoSettings> tenants(
      ObjectMapper mapper, String json, double safe, double aggressive) {
    Map<String, TypoSettings> out = new LinkedHashMap<>();
    try {
      JsonNode root = mapper.readTree(json);
      if (!root.isObject()) throw new IllegalArgumentException("must be a JSON object");
      for (var entry : root.properties()) {
        if (!entry.getKey().matches("[A-Za-z0-9_-]{1,64}"))
          throw new IllegalArgumentException("invalid tenant id " + entry.getKey());
        JsonNode t = entry.getValue();
        List<String> langs = new ArrayList<>();
        t.path("languages").forEach(l -> langs.add(l.asText()));
        out.put(
            entry.getKey(),
            new TypoSettings(
                t.path("enabled").asBoolean(false),
                langs,
                mode(t.path("mode").asText("SUGGEST_ONLY")),
                safe,
                aggressive));
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("qwenbridge.typo.tenants is not valid JSON");
    }
    return Map.copyOf(out);
  }

  private void report(String scope, TypoSettings settings) {
    if (!settings.enabled()) {
      LOG.infof("Typo correction for %s: disabled", scope);
      return;
    }
    if (settings.languages().isEmpty()) {
      LOG.warnf(
          "Typo correction for %s is enabled but no language is configured; nothing is corrected",
          scope);
      return;
    }
    for (LanguageSupport s : TypoCorrector.languages(settings, providers))
      if (s.status() != LanguageSupport.Status.SUPPORTED)
        LOG.warnf("Typo correction for %s: language %s is %s", scope, s.configured(), s.status());
    LOG.infof(
        "Typo correction for %s: mode %s, languages %s",
        scope, settings.mode(), settings.languages());
  }

  /** The settings of a tenant: its own when configured, otherwise the application's. */
  public TypoSettings settings(String tenant) {
    return tenant == null ? application : tenants.getOrDefault(tenant, application);
  }

  public boolean tenantSpecific(String tenant) {
    return tenant != null && tenants.containsKey(tenant);
  }

  public List<CorrectionProvider> providers() {
    return providers;
  }
}
