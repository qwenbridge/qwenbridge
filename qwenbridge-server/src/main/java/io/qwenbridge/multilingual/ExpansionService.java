package io.qwenbridge.multilingual;

import io.qwenbridge.core.context.RequestScope;
import io.qwenbridge.core.expansion.ExpansionLimits;
import io.qwenbridge.core.expansion.ExpansionResult;
import io.qwenbridge.core.expansion.QueryExpander;
import io.qwenbridge.core.expansion.SourceLanguage;
import io.qwenbridge.core.expansion.TargetResult;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.queryir.QueryExpansion;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.language.LanguageAnalysis;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/**
 * Runs the expansion engine with the caller's tenant settings (QB-286). Translations across all
 * requests share one bounded semaphore ({@code max-concurrent-translations}); the request's own
 * deadline bounds the expansion and its cancellation stops it.
 */
@Singleton
public final class ExpansionService {

  private final ExpansionConfiguration configuration;
  private final Semaphore translations;

  public ExpansionService(ExpansionConfiguration configuration) {
    this.configuration = configuration;
    this.translations = new Semaphore(configuration.maxConcurrentTranslations());
  }

  /** Whether expansion is enabled for the tenant (the application's setting without one). */
  public boolean enabled(String tenant) {
    return configuration.settings(tenant).enabled();
  }

  /**
   * @param original the query exactly as received
   * @param sourceText the normalized query, with typo corrections when QB-291 applied them
   */
  public ExpansionResult expand(
      String tenant,
      String original,
      String sourceText,
      LanguageAnalysis language,
      EntityDictionary dictionary) {
    var settings = configuration.settings(tenant);
    ExpansionLimits limits = configuration.limits();
    Optional<RequestScope> scope = RequestScope.current();
    if (settings.enabled() && scope.isPresent()) {
      Duration overall = scope.get().bound(limits.overall());
      if (overall.isNegative() || overall.isZero()) overall = Duration.ofMillis(1);
      limits =
          new ExpansionLimits(
              limits.perLanguage().compareTo(overall) <= 0 ? limits.perLanguage() : overall,
              overall,
              limits.maxParallel());
    }
    BooleanSupplier cancelled = () -> scope.map(s -> s.cancelled() || s.expired()).orElse(false);
    return QueryExpander.expand(
        new QueryExpander.Request(original, sourceText, source(language), settings, dictionary),
        configuration.providers(),
        limits,
        Optional.of(translations),
        cancelled);
  }

  /**
   * The source language: detected when reliable, {@code mul} when mixed, otherwise the client's
   * declared language (documented fallback), otherwise undetermined. Never invented.
   */
  static SourceLanguage source(LanguageAnalysis language) {
    if (language == null) return SourceLanguage.undetermined();
    var detection = language.detection();
    if (language.mixed()) {
      List<LanguageTag> parts =
          language.segments().stream()
              .map(s -> s.detection())
              .filter(d -> d.determined())
              .map(d -> d.language())
              .distinct()
              .toList();
      return new SourceLanguage(Optional.empty(), false, true, parts, detection.confidence());
    }
    if (language.primaryDetermined())
      return LanguageTag.parse(language.primaryLanguage())
          .map(
              tag ->
                  new SourceLanguage(
                      Optional.of(tag), true, false, List.of(), detection.confidence()))
          .orElse(SourceLanguage.undetermined());
    if (language.declared().isPresent()) return SourceLanguage.declared(language.declared().get());
    return SourceLanguage.undetermined();
  }

  public static QueryExpansion toQueryIr(ExpansionResult r) {
    LanguageTag source = r.source().tag();
    return new QueryExpansion(
        r.status().name(),
        r.policy().name(),
        r.sourceText(),
        source.isUndetermined() ? Optional.empty() : Optional.of(source),
        r.configured().stream().map(c -> c.configured()).toList(),
        r.targets().stream().map(ExpansionService::target).toList());
  }

  private static QueryExpansion.Target target(TargetResult t) {
    return new QueryExpansion.Target(
        t.requested(),
        t.language(),
        t.status().name(),
        t.text(),
        t.provider(),
        t.confidence().kind().name(),
        t.reason(),
        t.preservation().map(p -> p.status().name()),
        t.retrieval());
  }
}
