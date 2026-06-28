package io.qwenbridge.execution.provider.opensearch.mapper;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpenSearchResponseMapper {

    @SuppressWarnings("unchecked")
    public SearchResponse from(Map<String, Object> response) {
        Map<String, Object> hitsRoot =
                (Map<String, Object>) response.getOrDefault("hits", Map.of());

        Number totalHits = extractTotalHits(hitsRoot);

        List<Map<String, Object>> rawHits =
                (List<Map<String, Object>>) hitsRoot.getOrDefault("hits", List.of());

        List<SearchHit> hits = rawHits.stream()
                .map(this::toSearchHit)
                .toList();

        return new SearchResponse(
                new SearchResultSet(
                        hits,
                        totalHits.longValue(),
                        extractTookMillis(response)
                )
        );
    }

    @SuppressWarnings("unchecked")
    private SearchHit toSearchHit(Map<String, Object> rawHit) {
        String id = rawHit.getOrDefault("_id", "").toString();

        double score = 0.0;
        Object rawScore = rawHit.get("_score");
        if (rawScore instanceof Number number) {
            score = number.doubleValue();
        }

        Map<String, Object> source =
                (Map<String, Object>) rawHit.getOrDefault("_source", Map.of());

        return SearchHit.of(id, score, source);
    }

    @SuppressWarnings("unchecked")
    private Number extractTotalHits(Map<String, Object> hitsRoot) {
        Object total = hitsRoot.get("total");

        if (total instanceof Number number) {
            return number;
        }

        if (total instanceof Map<?, ?> totalMap) {
            Object value = ((Map<String, Object>) totalMap).get("value");
            if (value instanceof Number number) {
                return number;
            }
        }

        return 0;
    }

    private long extractTookMillis(Map<String, Object> response) {
        Object took = response.get("took");

        if (took instanceof Number number) {
            return number.longValue();
        }

        return 0;
    }
}
