package io.qwenbridge.execution;

public enum ExecutionOperation {
  REWRITE_QUERY,
  KEYWORD_SEARCH,
  VECTOR_SEARCH,
  HYBRID_SEARCH,
  APPLY_FACETS,
  RERANK_RESULTS,
  DIRECT_ANSWER,
  RETURN_RESULTS
}
