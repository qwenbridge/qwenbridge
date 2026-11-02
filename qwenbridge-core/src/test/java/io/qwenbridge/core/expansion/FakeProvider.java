package io.qwenbridge.core.expansion;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.translation.ProviderAvailability;
import io.qwenbridge.core.translation.TranslationCapabilities;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.core.translation.TranslationRequest;
import io.qwenbridge.core.translation.TranslationResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Deterministic test provider for orchestration tests (QB-286). Its "translations" are labelled
 * fakes ({@code [sv] text}); they prove orchestration, never translation quality.
 */
final class FakeProvider implements TranslationProvider {
  final String id;
  final TranslationCapabilities capabilities;
  final ProviderAvailability availability;
  final Function<TranslationRequest, TranslationResult> behaviour;
  final AtomicInteger calls = new AtomicInteger();
  final AtomicInteger active = new AtomicInteger();
  final AtomicInteger maxActive = new AtomicInteger();

  FakeProvider(
      String id,
      TranslationCapabilities capabilities,
      ProviderAvailability availability,
      Function<TranslationRequest, TranslationResult> behaviour) {
    this.id = id;
    this.capabilities = capabilities;
    this.availability = availability;
    this.behaviour = behaviour;
  }

  /** Any pair; prefixes the target tag. */
  static FakeProvider labelling(String id) {
    return new FakeProvider(
        id,
        TranslationCapabilities.anyToAny(2000),
        ProviderAvailability.ready(),
        r -> translated(r, "[" + r.target().value() + "] " + r.text()));
  }

  static TranslationResult translated(TranslationRequest r, String text) {
    return translated(r, text, Confidence.modelDecision(), Map.of());
  }

  static TranslationResult translated(
      TranslationRequest r, String text, Confidence confidence, Map<String, String> metadata) {
    var source = r.source().isUndetermined() ? r.target() : r.source();
    return new TranslationResult.Translated(text, source, r.target(), "fake", confidence, metadata);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public TranslationCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public ProviderAvailability availability() {
    return availability;
  }

  @Override
  public TranslationResult translate(TranslationRequest request) {
    calls.incrementAndGet();
    int now = active.incrementAndGet();
    maxActive.accumulateAndGet(now, Math::max);
    try {
      return behaviour.apply(request);
    } finally {
      active.decrementAndGet();
    }
  }
}
