package io.qwenbridge.execution.provider.opensearch.query;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenSearchQueryFactory {

    private static final String EMBEDDING_FIELD = "embedding";

    private final OpenSearchProperties properties;

    public OpenSearchSearchRequest from(SearchRequest request) {
        return switch (request.searchMode()) {
            case "VECTOR", "SEMANTIC" -> vectorSearch(request);
            case "HYBRID" -> hybridSearch(request);
            default -> keywordSearch(request);
        };
    }

    private OpenSearchSearchRequest keywordSearch(SearchRequest request) {
        return new OpenSearchSearchRequest(
                keywordQuery(request.query()),
                properties.defaultSize()
        );
    }

    private OpenSearchSearchRequest vectorSearch(SearchRequest request) {
        return new OpenSearchSearchRequest(
                request.embedding()
                        .map(this::vectorQuery)
                        .orElseGet(() -> keywordQuery(request.query())),
                properties.defaultSize()
        );
    }

    private OpenSearchSearchRequest hybridSearch(SearchRequest request) {
        return new OpenSearchSearchRequest(
                request.embedding()
                        .map(embedding -> Map.<String, Object>of(
                                "hybrid",
                                Map.of(
                                        "queries",
                                        List.of(
                                                keywordQuery(request.query()),
                                                vectorQuery(embedding)
                                        )
                                )
                        ))
                        .orElseGet(() -> keywordQuery(request.query())),
                properties.defaultSize()
        );
    }

    private Map<String, Object> keywordQuery(String query) {
        return Map.of(
                "multi_match",
                Map.of(
                        "query", query,
                        "fields", List.of("title^3", "brand^2", "category", "description")
                )
        );
    }

    private Map<String, Object> vectorQuery(List<Double> embedding) {
        return Map.of(
                "knn",
                Map.of(
                        EMBEDDING_FIELD,
                        Map.of(
                                "vector", embedding,
                                "k", properties.defaultSize()
                        )
                )
        );
    }
}
