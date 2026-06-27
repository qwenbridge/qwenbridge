package io.qwenbridge.execution.provider.resolver;

import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderRegistry;
import io.qwenbridge.execution.provider.spi.SearchProviderResolver;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.context.ContextKeys;
import org.springframework.stereotype.Component;

@Component
public class DefaultSearchProviderResolver implements SearchProviderResolver {

    private static final String DEFAULT_PROVIDER = "inmemory";

    private final SearchProviderRegistry registry;

    public DefaultSearchProviderResolver(SearchProviderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public SearchProvider resolve(ExecutionContext context) {

        String providerName = DEFAULT_PROVIDER;

        if (context.get(ContextKeys.EXECUTION_HINTS) != null) {
            providerName = context.get(ContextKeys.EXECUTION_HINTS).provider();
        }

        var provider = registry.find(providerName);

        if (provider.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown search provider: " + providerName);
        }

        return provider.get();
    }
}