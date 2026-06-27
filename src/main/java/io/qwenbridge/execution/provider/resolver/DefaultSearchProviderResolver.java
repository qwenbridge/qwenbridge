package io.qwenbridge.execution.provider.resolver;

import io.qwenbridge.decision.SearchBackend;
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

        return resolveByProviderName(providerName);
    }

    @Override
    public SearchProvider resolve(SearchBackend backend) {
        String providerName = switch (backend) {
            case IN_MEMORY -> "inmemory";
            case OPENSEARCH -> "opensearch";
            case CUSTOM -> "custom";
            case NONE -> throw new IllegalStateException(
                    "No SearchProvider registered for backend: " + backend
            );
        };

        return resolveByProviderName(providerName);
    }

    private SearchProvider resolveByProviderName(String providerName) {
        return registry.find(providerName)
                .orElseThrow(() -> new IllegalStateException(
                        "No SearchProvider registered with name: " + providerName
                ));
    }
}
