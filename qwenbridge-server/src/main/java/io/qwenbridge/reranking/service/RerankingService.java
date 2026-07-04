package io.qwenbridge.reranking.service;

import io.qwenbridge.execution.provider.model.SearchResultSet;

public interface RerankingService {

    SearchResultSet rerank(String query, SearchResultSet resultSet);
}
