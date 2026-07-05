package io.qwenbridge.reranking;

import io.qwenbridge.execution.provider.model.SearchResultSet;

public interface Reranker {

  SearchResultSet rerank(String query, SearchResultSet resultSet);
}
