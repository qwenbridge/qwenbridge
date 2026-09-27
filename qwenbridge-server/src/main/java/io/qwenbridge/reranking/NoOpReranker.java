package io.qwenbridge.reranking;

import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoOpReranker implements Reranker {

  @Override
  public SearchResultSet rerank(String query, SearchResultSet resultSet) {
    Objects.requireNonNull(query, "query must not be null");
    return Objects.requireNonNull(resultSet, "resultSet must not be null");
  }
}
