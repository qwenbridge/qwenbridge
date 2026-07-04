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
    void shouldReturnMatchingProduct() {
        InMemorySearchProvider provider = new InMemorySearchProvider();

        SearchResponse response = provider.search(SearchRequest.of("iphone"));

        assertThat(response).isNotNull();
        assertThat(response.results()).isNotNull();
        assertThat(response.results().isEmpty()).isFalse();
        assertThat(response.results().totalHits()).isEqualTo(1);
        assertThat(response.results().hits().getFirst().id()).isEqualTo("product-1");
        assertThat(response.results().hits().getFirst().document())
                .containsEntry("title", "iPhone 16 Pro");
    }

    @Test
    void shouldReturnEmptyResponseWhenNothingMatches() {
        InMemorySearchProvider provider = new InMemorySearchProvider();

        SearchResponse response = provider.search(SearchRequest.of("non-existing-product"));

        assertThat(response.results().isEmpty()).isTrue();
        assertThat(response.results().totalHits()).isZero();
    }
}