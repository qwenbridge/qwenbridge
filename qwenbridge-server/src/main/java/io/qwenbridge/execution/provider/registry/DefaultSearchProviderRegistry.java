package io.qwenbridge.execution.provider.registry;

import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DefaultSearchProviderRegistry implements SearchProviderRegistry {

  private final Map<String, SearchProvider> providers = new ConcurrentHashMap<>();

  public DefaultSearchProviderRegistry(List<SearchProvider> searchProviders) {
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
