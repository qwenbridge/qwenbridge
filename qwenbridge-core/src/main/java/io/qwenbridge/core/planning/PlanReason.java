package io.qwenbridge.core.planning;

/** Stable reason codes explaining a plan (QB-101). */
public enum PlanReason {
  /** Exact identifiers (codes, IPs, quoted phrases) need lexical matching. */
  EXACT_IDENTIFIERS,
  /** The query asks for terms. */
  LEXICAL_NEED,
  /** Descriptive or subjective wording benefits from meaning-based matching. */
  SEMANTIC_NEED,
  /** Lexical and semantic needs both apply; fusion combines them. */
  BOTH_NEEDS,
  /** Only structured constraints; text matching adds nothing beyond the filter. */
  STRUCTURED_ONLY,
  /** The policy favours latency: a single provider call. */
  POLICY_LATENCY,
  /** The policy favours quality: hybrid whenever available. */
  POLICY_QUALITY,
  /** The budget does not allow every desired call. */
  BUDGET_LIMITED,
  /** Semantic retrieval is wanted but no embedding provider or vector provider can serve it. */
  SEMANTIC_UNAVAILABLE,
  /** The vector index was built for another embedding space. */
  VECTOR_REINDEX_REQUIRED,
  /** A provider lacks a capability the filter needs. */
  CAPABILITY_MISMATCH
}
