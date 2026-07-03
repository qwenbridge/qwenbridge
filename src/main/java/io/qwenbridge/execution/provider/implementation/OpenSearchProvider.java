package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.exception.SearchProviderException;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import io.qwenbridge.execution.provider.opensearch.query.OpenSearchQueryFactory;
import io.qwenbridge.execution.provider.support.AbstractSearchProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenSearchProvider extends AbstractSearchProvider {

    private final OpenSearchProperties properties;
    private final OpenSearchClient client;
    private final OpenSearchQueryFactory queryFactory;
    private final OpenSearchResponseMapper responseMapper;

    public OpenSearchProvider(
            OpenSearchProperties properties,
            OpenSearchClient client,
            OpenSearchQueryFactory queryFactory,
            OpenSearchResponseMapper responseMapper
    ) {
        super("opensearch");
        this.properties = properties;
        this.client = client;
        this.queryFactory = queryFactory;
        this.responseMapper = responseMapper;
    }

    @Override
    protected SearchResponse doSearch(SearchRequest request) {
        OpenSearchSearchRequest searchRequest = queryFactory.from(request);

        try {
            Map<String, Object> rawResponse = client.search(
                    properties.index(),
                    Map.of(
                            "query", searchRequest.query(),
                            "size", searchRequest.size()
                    )
            );

            return responseMapper.from(rawResponse);
        } catch (RuntimeException ex) {
            throw new SearchProviderException("OpenSearch provider failure", ex);
        }
    }

    public OpenSearchProperties properties() {
        return properties;
    }
}