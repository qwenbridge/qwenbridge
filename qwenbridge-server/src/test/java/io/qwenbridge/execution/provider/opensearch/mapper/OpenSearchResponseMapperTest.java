package io.qwenbridge.execution.provider.opensearch.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.provider.model.SearchResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchResponseMapperTest {

  @Test
  void shouldMapOpenSearchResponseToSearchResponse() {
    OpenSearchResponseMapper mapper = new OpenSearchResponseMapper();

    Map<String, Object> response =
        Map.of(
            "took",
            12,
            "hits",
            Map.of(
                "total", Map.of("value", 1),
                "hits",
                    List.of(
                        Map.of(
                            "_id",
                            "product-1",
                            "_score",
                            1.7,
                            "_source",
                            Map.of(
                                "title", "iPhone 16 Pro",
                                "brand", "Apple",
                                "category", "smartphone")))));

    SearchResponse mapped = mapper.from(response);

    assertThat(mapped.results().totalHits()).isEqualTo(1);
    assertThat(mapped.results().tookMillis()).isEqualTo(12);
    assertThat(mapped.results().hits()).hasSize(1);

    assertThat(mapped.results().hits().getFirst().id()).isEqualTo("product-1");
    assertThat(mapped.results().hits().getFirst().score()).isEqualTo(1.7);
    assertThat(mapped.results().hits().getFirst().document())
        .containsEntry("title", "iPhone 16 Pro")
        .containsEntry("brand", "Apple");
  }

  @Test
  void shouldReturnEmptyResponseWhenHitsAreMissing() {
    OpenSearchResponseMapper mapper = new OpenSearchResponseMapper();

    SearchResponse mapped = mapper.from(Map.of());

    assertThat(mapped.results().totalHits()).isZero();
    assertThat(mapped.results().hits()).isEmpty();
  }
}
