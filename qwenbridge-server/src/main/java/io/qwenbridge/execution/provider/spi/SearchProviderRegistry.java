package io.qwenbridge.execution.provider.spi;

import java.util.Collection;
import java.util.Optional;

public interface SearchProviderRegistry {

  void register(SearchProvider provider);

  Optional<SearchProvider> find(String providerName);

  Collection<SearchProvider> providers();
}
