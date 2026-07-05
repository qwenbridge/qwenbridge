package io.qwenbridge.execution.provider.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchRequestTest {

  @Test
  void shouldDefaultToKeywordMode() {
    SearchRequest request = SearchRequest.of("iphone");

    assertThat(request.searchMode()).isEqualTo("KEYWORD");
    assertThat(request.embedding()).isEmpty();
  }

  @Test
  void shouldExposeVectorModeAndEmbedding() {
    SearchRequest request = SearchRequest.vector("gaming mouse", List.of(0.1, 0.2));

    assertThat(request.searchMode()).isEqualTo("VECTOR");
    assertThat(request.embedding()).contains(List.of(0.1, 0.2));
  }

  @Test
  void shouldIgnoreInvalidEmbeddingOption() {
    SearchRequest request =
        SearchRequest.withOptions(
            "gaming mouse", Map.of(SearchRequest.OPTION_EMBEDDING, List.of(0.1, "bad")));

    assertThat(request.embedding()).isEmpty();
  }
}
