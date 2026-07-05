package io.qwenbridge.execution.provider.support;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import java.util.Objects;

public abstract class AbstractSearchProvider implements SearchProvider {

  private final String name;

  protected AbstractSearchProvider(String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final SearchResponse search(SearchRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return doSearch(request);
  }

  protected abstract SearchResponse doSearch(SearchRequest request);
}
