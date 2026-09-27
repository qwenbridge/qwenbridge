package io.qwenbridge.execution.provider.registry;

import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderRegistry;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DefaultSearchProviderRegistry implements SearchProviderRegistry {

  private final Map<String, SearchProvider> providers = new ConcurrentHashMap<>();

  @Inject
  public DefaultSearchProviderRegistry(@All List<SearchProvider> searchProviders) {
    searchProviders.forEach(this::register);
  }

  @Override
  public void register(SearchProvider provider) {
    providers.put(provider.name(), provider);
  }

  @Override
  public Optional<SearchProvider> find(String providerName) {
    return Optional.ofNullable(providers.get(providerName));
  }

  @Override
  public Collection<SearchProvider> providers() {
    return List.copyOf(providers.values());
  }
}
