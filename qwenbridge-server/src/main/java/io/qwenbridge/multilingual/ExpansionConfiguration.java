package io.qwenbridge.multilingual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.expansion.ExpansionLimits;
import io.qwenbridge.core.expansion.ExpansionPolicy;
import io.qwenbridge.core.expansion.ExpansionSettings;
import io.qwenbridge.core.expansion.TargetLanguage;
import io.qwenbridge.core.translation.GlossaryTranslationProvider;
import io.qwenbridge.core.translation.ProviderAvailability;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.translation.ModelTranslationProvider;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 * Multilingual query expansion configuration (QB-286). Nothing is enabled and no target language
 * exists by default; the application owner configures them.
 *
 * <ul>
 *   <li>{@code qwenbridge.multilingual.enabled} (false), {@code target-languages} (none), {@code
 *       policy} ({@code ALL_TARGETS}), {@code min-confidence} (0.5) for the application;
 *   <li>{@code qwenbridge.multilingual.tenants}: a JSON object of per-tenant settings ({@code
 *       enabled}, {@code targetLanguages}, {@code policy}) replacing the application settings for
 *       that tenant only; no database;
 *   <li>{@code qwenbridge.multilingual.aliases}: explicit compatibility aliases, {@code SE=sv} by
 *       default (documented; matched exactly, so {@code se} stays Northern Sami);
 *   <li>{@code qwenbridge.multilingual.providers}: the providers to use, in order (none by
 *       default): {@code glossary} (phrase tables in {@code glossary-dir}), {@code model} (the
 *       configured local chat model) or the id of a provider installed via {@link ServiceLoader};
 *   <li>bounds: {@code per-language-timeout} (5s), {@code timeout} (15s), {@code max-parallel} (4
 *       per request), {@code max-concurrent-translations} (16 per process).
 * </ul>
 *
 * A provider that cannot be initialized is kept as unavailable, so its targets report why; startup
 * never fails because of multilingual configuration, except for a malformed policy or tenant JSON.
 */
@Singleton
public final class ExpansionConfiguration {

  private static final Logger LOG = Logger.getLogger(ExpansionConfiguration.class);

  private final ExpansionSettings application;
  private final Map<String, ExpansionSettings> tenants;
  private final List<TranslationProvider> providers;
  private final ExpansionLimits limits;
  private final int maxConcurrentTranslations;

  public ExpansionConfiguration(
      ObjectMapper mapper,
      AIService ai,
      @ConfigProperty(name = "qwenbridge.multilingual.enabled", defaultValue = "false")
          boolean enabled,
      @ConfigProperty(name = "qwenbridge.multilingual.target-languages")
          Optional<List<String>> targets,
      @ConfigProperty(name = "qwenbridge.multilingual.policy", defaultValue = "ALL_TARGETS")
          String policy,
      @ConfigProperty(name = "qwenbridge.multilingual.aliases", defaultValue = "SE=sv")
          Optional<List<String>> aliases,
      @ConfigProperty(name = "qwenbridge.multilingual.min-confidence", defaultValue = "0.5")
          double minConfidence,
      @ConfigProperty(name = "qwenbridge.multilingual.tenants", defaultValue = "{}")
          String tenantJson,
      @ConfigProperty(name = "qwenbridge.multilingual.providers")
          Optional<List<String>> providerIds,
      @ConfigProperty(name = "qwenbridge.multilingual.glossary-dir") Optional<String> glossaryDir,
      @ConfigProperty(name = "qwenbridge.multilingual.per-language-timeout", defaultValue = "5s")
          Duration perLanguage,
      @ConfigProperty(name = "qwenbridge.multilingual.timeout", defaultValue = "15s")
          Duration overall,
      @ConfigProperty(name = "qwenbridge.multilingual.max-parallel", defaultValue = "4")
          int maxParallel,
      @ConfigProperty(
              name = "qwenbridge.multilingual.max-concurrent-translations",
              defaultValue = "16")
          int maxConcurrentTranslations) {
    Map<String, String> aliasMap = aliases(aliases.orElse(List.of()));
    this.application =
        new ExpansionSettings(
            enabled, targets.orElse(List.of()), policy(policy), aliasMap, minConfidence);
    this.tenants = tenants(mapper, tenantJson, aliasMap, minConfidence);
    this.limits = new ExpansionLimits(perLanguage, overall, maxParallel);
    if (maxConcurrentTranslations < 1)
      throw new IllegalArgumentException(
          "qwenbridge.multilingual.max-concurrent-translations must be positive");
    this.maxConcurrentTranslations = maxConcurrentTranslations;
    this.providers = List.copyOf(providers(providerIds.orElse(List.of()), glossaryDir, ai, mapper));
    report("application", application);
    tenants.forEach(this::report);
  }

  static ExpansionPolicy policy(String value) {
    try {
      return ExpansionPolicy.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "qwenbridge.multilingual.policy must be ALL_TARGETS, STRICT_ALL_TARGETS or"
              + " BEST_EFFORT_ALL_TARGETS");
    }
  }

  static Map<String, String> aliases(List<String> entries) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String entry : entries) {
      if (entry == null || entry.isBlank()) continue;
      int eq = entry.indexOf('=');
      if (eq < 1 || eq == entry.length() - 1)
        throw new IllegalArgumentException(
            "qwenbridge.multilingual.aliases entries are ALIAS=language, e.g. SE=sv");
      out.put(entry.substring(0, eq).strip(), entry.substring(eq + 1).strip());
    }
    return Map.copyOf(out);
  }

  static Map<String, ExpansionSettings> tenants(
      ObjectMapper mapper, String json, Map<String, String> aliases, double minConfidence) {
    Map<String, ExpansionSettings> out = new LinkedHashMap<>();
    try {
      JsonNode root = mapper.readTree(json);
      if (!root.isObject()) throw new IllegalArgumentException("must be a JSON object");
      for (var entry : root.properties()) {
        if (!entry.getKey().matches("[A-Za-z0-9_-]{1,64}"))
          throw new IllegalArgumentException("invalid tenant id " + entry.getKey());
        JsonNode t = entry.getValue();
        List<String> targets = new ArrayList<>();
        t.path("targetLanguages").forEach(l -> targets.add(l.asText()));
        out.put(
            entry.getKey(),
            new ExpansionSettings(
                t.path("enabled").asBoolean(false),
                targets,
                policy(t.path("policy").asText("ALL_TARGETS")),
                aliases,
                minConfidence));
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("qwenbridge.multilingual.tenants is not valid JSON");
    }
    return Map.copyOf(out);
  }

  private static List<TranslationProvider> providers(
      List<String> ids, Optional<String> glossaryDir, AIService ai, ObjectMapper mapper) {
    Map<String, TranslationProvider> installed = new LinkedHashMap<>();
    ServiceLoader.load(TranslationProvider.class, ExpansionConfiguration.class.getClassLoader())
        .stream()
        .forEach(
            p -> {
              try {
                TranslationProvider provider = p.get();
                installed.putIfAbsent(provider.id(), provider);
              } catch (RuntimeException | java.util.ServiceConfigurationError e) {
                LOG.warnf(
                    "Translation provider %s skipped: %s",
                    p.type().getName(), e.getClass().getSimpleName());
              }
            });
    List<TranslationProvider> out = new ArrayList<>();
    for (String raw : ids) {
      String id = raw == null ? "" : raw.strip();
      if (id.isEmpty() || out.stream().anyMatch(p -> p.id().equals(id))) continue;
      switch (id) {
        case "glossary" -> out.add(glossary(glossaryDir));
        case "model" -> out.add(new ModelTranslationProvider(ai, mapper));
        default -> {
          TranslationProvider p = installed.get(id);
          if (p != null) out.add(p);
          else {
            LOG.warnf("Translation provider %s is configured but not installed", id);
            out.add(
                new UnavailableTranslationProvider(
                    id,
                    ProviderAvailability.State.MISSING_RESOURCES,
                    "not installed",
                    List.of("a TranslationProvider with this id on the class path")));
          }
        }
      }
    }
    return List.copyOf(out);
  }

  private static TranslationProvider glossary(Optional<String> dir) {
    List<String> requirements = List.of("qwenbridge.multilingual.glossary-dir with tables");
    if (dir.isEmpty() || dir.get().isBlank())
      return new UnavailableTranslationProvider(
          "glossary",
          ProviderAvailability.State.MISSING_RESOURCES,
          "qwenbridge.multilingual.glossary-dir is not set",
          requirements);
    try {
      Path path = Path.of(dir.get());
      if (!Files.isDirectory(path)) throw new IllegalArgumentException("not a directory");
      return GlossaryTranslationProvider.load("glossary", path);
    } catch (Exception e) {
      LOG.warnf("Glossary tables in %s not loaded: %s", dir.get(), e.getMessage());
      return new UnavailableTranslationProvider(
          "glossary",
          ProviderAvailability.State.MISSING_RESOURCES,
          "glossary tables not loaded: " + e.getMessage(),
          requirements);
    }
  }

  private void report(String scope, ExpansionSettings settings) {
    if (!settings.enabled()) {
      LOG.infof("Multilingual expansion for %s: disabled", scope);
      return;
    }
    if (settings.targetLanguages().isEmpty()) {
      LOG.warnf(
          "Multilingual expansion for %s is enabled but no target language is configured;"
              + " nothing is expanded",
          scope);
      return;
    }
    for (TargetLanguage t : settings.resolved())
      if (!t.usable())
        LOG.warnf(
            "Multilingual expansion for %s: target %s is %s (%s)",
            scope, t.configured(), t.resolution(), t.note());
    if (providers.isEmpty())
      LOG.warnf(
          "Multilingual expansion for %s: no translation provider configured;"
              + " every target is reported PROVIDER_UNAVAILABLE",
          scope);
    LOG.infof(
        "Multilingual expansion for %s: policy %s, targets %s",
        scope, settings.policy(), settings.targetLanguages());
  }

  /** The settings of a tenant: its own when configured, otherwise the application's. */
  public ExpansionSettings settings(String tenant) {
    return tenant == null ? application : tenants.getOrDefault(tenant, application);
  }

  public boolean tenantSpecific(String tenant) {
    return tenant != null && tenants.containsKey(tenant);
  }

  public List<TranslationProvider> providers() {
    return providers;
  }

  public ExpansionLimits limits() {
    return limits;
  }

  public int maxConcurrentTranslations() {
    return maxConcurrentTranslations;
  }
}
