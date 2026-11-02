package io.qwenbridge.multilingual;

import io.qwenbridge.core.translation.ProviderAvailability;
import io.qwenbridge.core.translation.TranslationCapabilities;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.core.translation.TranslationRequest;
import io.qwenbridge.core.translation.TranslationResult;
import java.util.List;

/**
 * Stands in for a configured provider that could not be initialized (QB-286): its targets are
 * reported {@code PROVIDER_UNAVAILABLE} with the reason, instead of disappearing or failing
 * startup. It is never called.
 */
final class UnavailableTranslationProvider implements TranslationProvider {

  private final String id;
  private final ProviderAvailability availability;
  private final List<String> requirements;

  UnavailableTranslationProvider(
      String id, ProviderAvailability.State state, String detail, List<String> requirements) {
    this.id = id;
    this.availability = ProviderAvailability.of(state, detail);
    this.requirements = List.copyOf(requirements);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public TranslationCapabilities capabilities() {
    return TranslationCapabilities.anyToAny(1);
  }

  @Override
  public ProviderAvailability availability() {
    return availability;
  }

  @Override
  public List<String> requirements() {
    return requirements;
  }

  @Override
  public TranslationResult translate(TranslationRequest request) {
    return TranslationResult.Failed.of(TranslationResult.Failure.NO_PROVIDER, id);
  }
}
