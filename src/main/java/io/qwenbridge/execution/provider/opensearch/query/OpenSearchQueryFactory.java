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

    private final OpenSearchProperties properties;

    public OpenSearchSearchRequest from(SearchRequest request) {
        return new OpenSearchSearchRequest(
                Map.of(
                        "multi_match",
                        Map.of(
                                "query", request.query(),
                                "fields", List.of("title^3", "brand^2", "category", "description")
                        )
                ),
                properties.defaultSize()
        );
    }
}