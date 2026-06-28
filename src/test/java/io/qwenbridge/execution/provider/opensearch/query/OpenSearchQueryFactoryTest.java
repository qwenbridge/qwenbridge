package io.qwenbridge.execution.provider.opensearch.query;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchQueryFactoryTest {

    @Test
    void shouldCreateMultiMatchQuery() {
        OpenSearchProperties properties = new OpenSearchProperties(
                "http://localhost:9200",
                "qwenbridge-products",
                25,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );

        OpenSearchQueryFactory factory = new OpenSearchQueryFactory(properties);

        OpenSearchSearchRequest request = factory.from(SearchRequest.of("iphone"));

        assertThat(request).isNotNull();
        assertThat(request.size()).isEqualTo(25);
        assertThat(request.query()).containsKey("multi_match");

        Map<?, ?> multiMatch = (Map<?, ?>) request.query().get("multi_match");

        assertThat(multiMatch.get("query")).isEqualTo("iphone");
        assertThat(multiMatch.get("fields").toString())
                .contains("title^3")
                .contains("brand^2")
                .contains("category")
                .contains("description");
    }
}