package io.qwenbridge.execution.provider.resolver;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSearchProviderResolverTest {

    @Test
    void shouldResolveDefaultProviderFromContext() {
        SearchProvider provider = new InMemorySearchProvider();

        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(List.of(provider))
                );

        SearchProvider resolved = resolver.resolve(new ExecutionContext("iphone"));

        assertThat(resolved).isSameAs(provider);
        assertThat(resolved.name()).isEqualTo("inmemory");
    }

    @Test
    void shouldResolveInMemoryBackend() {
        SearchProvider provider = new InMemorySearchProvider();

        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(List.of(provider))
                );

        SearchProvider resolved = resolver.resolve(SearchBackend.IN_MEMORY);

        assertThat(resolved).isSameAs(provider);
        assertThat(resolved.name()).isEqualTo("inmemory");
    }

    @Test
    void shouldFailWhenProviderDoesNotExist() {
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(List.of())
                );

        assertThatThrownBy(() -> resolver.resolve(new ExecutionContext("iphone")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SearchProvider registered with name: inmemory");
    }

    @Test
    void shouldFailWhenBackendHasNoProvider() {
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(List.of())
                );

        assertThatThrownBy(() -> resolver.resolve(SearchBackend.OPENSEARCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SearchProvider registered with name: opensearch");
    }

    @Test
    void shouldFailForNoneBackend() {
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(List.of())
                );

        assertThatThrownBy(() -> resolver.resolve(SearchBackend.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SearchProvider registered for backend: NONE");
    }
}
