package io.qwenbridge.execution.provider.spi;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;

public interface SearchProvider {

    String name();

    SearchResponse search(SearchRequest request);
}