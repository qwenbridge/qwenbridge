package io.qwenbridge.execution.provider.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSearchProviderRegistryTest {

  @Test
  void shouldRegisterAllProviders() {

    SearchProvider provider = new InMemorySearchProvider();

    DefaultSearchProviderRegistry registry = new DefaultSearchProviderRegistry(List.of(provider));

    assertTrue(registry.find("inmemory").isPresent());
  }

  @Test
  void shouldReturnEmptyWhenProviderDoesNotExist() {

    DefaultSearchProviderRegistry registry = new DefaultSearchProviderRegistry(List.of());

    assertTrue(registry.find("unknown").isEmpty());
  }

  @Test
  void shouldContainCorrectProviderInstance() {

    SearchProvider provider = new InMemorySearchProvider();

    DefaultSearchProviderRegistry registry = new DefaultSearchProviderRegistry(List.of(provider));

    SearchProvider resolved = registry.find("inmemory").orElseThrow();

    assertSame(provider, resolved);
  }
}
