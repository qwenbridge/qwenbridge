package io.qwenbridge.reranking.service;

import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.reranking.Reranker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class DefaultRerankingService implements RerankingService {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  private final Reranker reranker;
  private final Duration timeout;

  @Inject
  public DefaultRerankingService(Reranker reranker) {
    this(reranker, DEFAULT_TIMEOUT);
  }

  DefaultRerankingService(Reranker reranker, Duration timeout) {
    this.reranker = Objects.requireNonNull(reranker, "reranker must not be null");
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");

    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be greater than zero");
    }
  }

  @Override
  public SearchResultSet rerank(String query, SearchResultSet resultSet) {
    Objects.requireNonNull(query, "query must not be null");
    Objects.requireNonNull(resultSet, "resultSet must not be null");

    if (resultSet.isEmpty()) {
      return resultSet;
    }

    CompletableFuture<SearchResultSet> task =
        CompletableFuture.supplyAsync(() -> reranker.rerank(query, resultSet));
    try {
      SearchResultSet reranked = task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

      return reranked == null ? resultSet : reranked;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return resultSet;
    } catch (java.util.concurrent.ExecutionException
        | java.util.concurrent.TimeoutException
        | RuntimeException ignored) {
      return resultSet;
    } finally {
      task.cancel(true);
    }
  }
}
