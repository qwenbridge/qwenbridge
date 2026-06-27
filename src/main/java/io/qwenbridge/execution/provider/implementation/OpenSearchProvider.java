package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.support.AbstractSearchProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchProvider extends AbstractSearchProvider {

    private final OpenSearchProperties properties;

    public OpenSearchProvider(OpenSearchProperties properties) {
        super("opensearch");
        this.properties = properties;
    }

    @Override
    protected SearchResponse doSearch(SearchRequest request) {
        return SearchResponse.empty();
    }

    public OpenSearchProperties properties() {
        return properties;
    }
}
