package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.execution.provider.support.AbstractSearchProvider;
import org.springframework.stereotype.Component;

@Component
public class InMemorySearchProvider extends AbstractSearchProvider {

    public InMemorySearchProvider() {
        super("inmemory");
    }

    @Override
    protected SearchResponse doSearch(SearchRequest request) {

        return new SearchResponse(
                SearchResultSet.empty()
        );
    }
}