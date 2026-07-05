package io.qwenbridge.execution;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.execution.executor.ExecutionOperationExecutor;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchRequestFactory;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderResolver;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.ranking.service.SearchResultRanker;
import io.qwenbridge.reranking.service.RerankingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultExecutionEngine implements ExecutionEngine {

  private final Map<ExecutionOperation, ExecutionOperationExecutor> executors;
  private final SearchProviderResolver searchProviderResolver;
  private final AIService aiService;
  private final SearchResultRanker searchResultRanker;
  private final RerankingService rerankingService;

  @Autowired
  public DefaultExecutionEngine(
      List<ExecutionOperationExecutor> executors,
      SearchProviderResolver searchProviderResolver,
      AIService aiService,
      SearchResultRanker searchResultRanker,
      RerankingService rerankingService) {
    this.executors = new HashMap<>();
    this.searchProviderResolver = searchProviderResolver;
    this.aiService = aiService;
    this.searchResultRanker = searchResultRanker;
    this.rerankingService = rerankingService;

    for (ExecutionOperationExecutor executor : executors) {
      this.executors.put(executor.operation(), executor);
    }
  }

  @Override
  public ExecutionResult execute(ExecutionPlan plan) {
    List<String> results =
        plan.steps().stream()
            .filter(step -> executors.containsKey(step.operation()))
            .flatMap(step -> executors.get(step.operation()).execute(step).stream())
            .toList();

    return ExecutionResult.completed(
        plan.steps().stream().map(ExecutionStep::operation).toList(),
        results,
        "Execution plan executed successfully.");
  }

  @Override
  public ExecutionResult execute(ExecutionPlan plan, ExecutionContext context) {
    if (plan.backend() == io.qwenbridge.decision.SearchBackend.NONE) {
      return execute(plan);
    }

    SearchRequest searchRequest = searchRequestFor(plan, context);
    SearchProvider provider = searchProviderResolver.resolve(plan.backend());

    SearchResponse rawResponse = provider.search(searchRequest);

    var rankedResults = searchResultRanker.rank(rawResponse.results());

    if (plan.contains(ExecutionOperation.RERANK_RESULTS)) {
      rankedResults = rerankingService.rerank(searchRequest.query(), rankedResults);
    }

    SearchResponse finalResponse = new SearchResponse(rankedResults);

    context.store(SearchResponse.class, finalResponse);

    List<String> results =
        finalResponse.results().hits().stream().map(hit -> hit.document().toString()).toList();

    return ExecutionResult.completed(
        plan.steps().stream().map(ExecutionStep::operation).toList(),
        results,
        "Execution plan executed successfully using search provider: " + provider.name());
  }

  private SearchRequest searchRequestFor(ExecutionPlan plan, ExecutionContext context) {
    SearchRequest baseRequest = SearchRequestFactory.from(context);

    if (plan.contains(ExecutionOperation.HYBRID_SEARCH)) {
      return SearchRequest.hybrid(baseRequest.query(), embeddingFor(baseRequest.query()).vector());
    }

    if (plan.contains(ExecutionOperation.VECTOR_SEARCH)) {
      return SearchRequest.vector(baseRequest.query(), embeddingFor(baseRequest.query()).vector());
    }

    return SearchRequest.keyword(baseRequest.query());
  }

  private EmbeddingResponse embeddingFor(String query) {
    if (aiService == null) {
      throw new AIException("AI embedding service is required for vector search execution");
    }

    EmbeddingResponse response = aiService.embed(new EmbeddingRequest(query));

    if (response == null || response.vector() == null || response.vector().isEmpty()) {
      throw new AIException("AI embedding response was empty");
    }

    return response;
  }
}
