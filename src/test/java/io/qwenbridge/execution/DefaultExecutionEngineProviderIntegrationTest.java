package io.qwenbridge.execution;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.executor.ExecutionOperationExecutor;
import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.resolver.DefaultSearchProviderResolver;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderResolver;
import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class DefaultExecutionEngineProviderIntegrationTest {

    @Test
    void shouldExecuteUsingDefaultSearchProvider() {
        SearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(
                                List.of(new InMemorySearchProvider())
                        )
                );

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(List.<ExecutionOperationExecutor>of(), resolver);

        ExecutionPlan plan = ExecutionPlan.builder()
                .mode(SearchMode.KEYWORD)
                .backend(SearchBackend.IN_MEMORY)
                .steps(List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.KEYWORD_SEARCH,
                                "keyword search"
                        )
                ))
                .reason("keyword search plan")
                .build();

        ExecutionResult result =
                engine.execute(plan, new ExecutionContext("iphone"));

        assertThat(result.executed()).isTrue();
        assertThat(result.operations()).containsExactly(ExecutionOperation.KEYWORD_SEARCH);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst()).contains("iPhone 16 Pro");
        assertThat(result.reason())
                .isEqualTo("Execution plan executed successfully using search provider: inmemory");
    }

    @Test
    void shouldStoreStructuredSearchResponseInExecutionContext() {
        SearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(
                                List.of(new InMemorySearchProvider())
                        )
                );

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(List.<ExecutionOperationExecutor>of(), resolver);

        ExecutionPlan plan = ExecutionPlan.builder()
                .mode(SearchMode.KEYWORD)
                .backend(SearchBackend.IN_MEMORY)
                .steps(List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.KEYWORD_SEARCH,
                                "keyword search"
                        )
                ))
                .reason("keyword search plan")
                .build();

        ExecutionContext context = new ExecutionContext("iphone");

        engine.execute(plan, context);

        SearchResponse response = context.get(SearchResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.results().hits()).hasSize(1);
        assertThat(response.results().hits().getFirst().id()).isEqualTo("product-1");
    }
    @Test
    void shouldCreateVectorSearchRequestWithGeneratedEmbedding() {
        SearchProvider provider = mock(SearchProvider.class);
        when(provider.name()).thenReturn("opensearch");
        when(provider.search(any(SearchRequest.class))).thenReturn(SearchResponse.empty());

        SearchProviderResolver resolver = mock(SearchProviderResolver.class);
        when(resolver.resolve(SearchBackend.OPENSEARCH)).thenReturn(provider);

        AIService aiService = mock(AIService.class);
        when(aiService.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3)));

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(
                        List.<ExecutionOperationExecutor>of(),
                        resolver,
                        aiService
                );

        ExecutionPlan plan = ExecutionPlan.builder()
                .mode(SearchMode.VECTOR)
                .backend(SearchBackend.OPENSEARCH)
                .steps(List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.VECTOR_SEARCH,
                                "vector search"
                        )
                ))
                .reason("vector search plan")
                .build();

        engine.execute(plan, new ExecutionContext("semantic gaming mouse"));

        ArgumentCaptor<EmbeddingRequest> embeddingCaptor =
                ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(aiService).embed(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().text()).isEqualTo("semantic gaming mouse");

        ArgumentCaptor<SearchRequest> searchRequestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(provider).search(searchRequestCaptor.capture());

        SearchRequest searchRequest = searchRequestCaptor.getValue();
        assertThat(searchRequest.query()).isEqualTo("semantic gaming mouse");
        assertThat(searchRequest.searchMode()).isEqualTo("VECTOR");
        assertThat(searchRequest.embedding()).contains(List.of(0.1, 0.2, 0.3));
    }

    @Test
    void shouldCreateHybridSearchRequestWithGeneratedEmbedding() {
        SearchProvider provider = mock(SearchProvider.class);
        when(provider.name()).thenReturn("opensearch");
        when(provider.search(any(SearchRequest.class))).thenReturn(SearchResponse.empty());

        SearchProviderResolver resolver = mock(SearchProviderResolver.class);
        when(resolver.resolve(SearchBackend.OPENSEARCH)).thenReturn(provider);

        AIService aiService = mock(AIService.class);
        when(aiService.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(0.4, 0.5, 0.6)));

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(
                        List.<ExecutionOperationExecutor>of(),
                        resolver,
                        aiService
                );

        ExecutionPlan plan = ExecutionPlan.builder()
                .mode(SearchMode.HYBRID)
                .backend(SearchBackend.OPENSEARCH)
                .steps(List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.HYBRID_SEARCH,
                                "hybrid search"
                        )
                ))
                .reason("hybrid search plan")
                .build();

        engine.execute(plan, new ExecutionContext("premium smartphone camera"));

        ArgumentCaptor<SearchRequest> searchRequestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(provider).search(searchRequestCaptor.capture());

        SearchRequest searchRequest = searchRequestCaptor.getValue();
        assertThat(searchRequest.query()).isEqualTo("premium smartphone camera");
        assertThat(searchRequest.searchMode()).isEqualTo("HYBRID");
        assertThat(searchRequest.embedding()).contains(List.of(0.4, 0.5, 0.6));
    }

    @Test
    void shouldRankSearchProviderResultsBeforeStoringResponseInContext() {
        SearchProvider provider = mock(SearchProvider.class);
        when(provider.name()).thenReturn("opensearch");
        when(provider.search(any(SearchRequest.class))).thenReturn(
                new SearchResponse(
                        new io.qwenbridge.execution.provider.model.SearchResultSet(
                                List.of(
                                        new SearchHit(
                                                "doc-low",
                                                0.2,
                                                Map.of("title", "Low score"),
                                                Map.of(
                                                        "lexicalScore", 0.2,
                                                        "vectorScore", 0.2
                                                )
                                        ),
                                        new SearchHit(
                                                "doc-high",
                                                0.9,
                                                Map.of("title", "High score"),
                                                Map.of(
                                                        "lexicalScore", 0.9,
                                                        "vectorScore", 0.9
                                                )
                                        )
                                ),
                                2,
                                9
                        )
                )
        );

        SearchProviderResolver resolver = mock(SearchProviderResolver.class);
        when(resolver.resolve(SearchBackend.OPENSEARCH)).thenReturn(provider);

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(
                        List.<ExecutionOperationExecutor>of(),
                        resolver,
                        null
                );

        ExecutionPlan plan = ExecutionPlan.builder()
                .mode(SearchMode.KEYWORD)
                .backend(SearchBackend.OPENSEARCH)
                .steps(List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.KEYWORD_SEARCH,
                                "keyword search"
                        )
                ))
                .reason("keyword search plan")
                .build();

        ExecutionContext context = new ExecutionContext("desk");

        engine.execute(plan, context);

        SearchResponse response = context.get(SearchResponse.class);

        assertThat(response.results().hits()).extracting(SearchHit::id)
                .containsExactly("doc-high", "doc-low");

        SearchHit topHit = response.results().hits().getFirst();

        assertThat(topHit.metadata()).containsKeys("rankingScore", "finalScore");
        assertThat(topHit.score()).isEqualTo(topHit.metadata().get("finalScore"));
    }


}