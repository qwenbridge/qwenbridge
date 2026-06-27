package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySearchProviderTest {

    @Test
    void shouldExposeProviderName() {
        InMemorySearchProvider provider = new InMemorySearchProvider();

        assertThat(provider.name()).isEqualTo("inmemory");
    }

    @Test
    void shouldReturnEmptySearchResponse() {
        InMemorySearchProvider provider = new InMemorySearchProvider();

        SearchResponse response = provider.search(SearchRequest.of("iphone"));

        assertThat(response).isNotNull();
        assertThat(response.results()).isNotNull();
        assertThat(response.results().isEmpty()).isTrue();
        assertThat(response.results().totalHits()).isZero();
    }
}