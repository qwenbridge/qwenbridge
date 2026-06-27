package io.qwenbridge.execution.provider.resolver;

import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.context.ContextKeys;
import io.qwenbridge.pipeline.context.ExecutionHints;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSearchProviderResolverTest {

    @Test
    void shouldResolveDefaultProviderWhenNoExecutionHintsExist() {
        SearchProvider provider = new InMemorySearchProvider();
        DefaultSearchProviderRegistry registry =
                new DefaultSearchProviderRegistry(List.of(provider));
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(registry);

        SearchProvider resolved = resolver.resolve(new ExecutionContext("iphone"));

        assertThat(resolved).isSameAs(provider);
    }

    @Test
    void shouldResolveProviderFromExecutionHints() {
        SearchProvider provider = new InMemorySearchProvider();
        DefaultSearchProviderRegistry registry =
                new DefaultSearchProviderRegistry(List.of(provider));
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(registry);

        ExecutionContext context = new ExecutionContext("iphone");
        context.store(
                ContextKeys.EXECUTION_HINTS,
                new ExecutionHints("inmemory", Duration.ofSeconds(2), true, false)
        );

        SearchProvider resolved = resolver.resolve(context);

        assertThat(resolved).isSameAs(provider);
    }

    @Test
    void shouldFailWhenProviderDoesNotExist() {
        DefaultSearchProviderRegistry registry =
                new DefaultSearchProviderRegistry(List.of());
        DefaultSearchProviderResolver resolver =
                new DefaultSearchProviderResolver(registry);

        assertThatThrownBy(() -> resolver.resolve(new ExecutionContext("iphone")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown search provider: inmemory");
    }
}