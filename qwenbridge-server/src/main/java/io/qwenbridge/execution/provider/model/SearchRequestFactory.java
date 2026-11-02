package io.qwenbridge.execution.provider.model;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;
import java.util.Objects;

public final class SearchRequestFactory {

  private SearchRequestFactory() {}

  public static SearchRequest from(ExecutionContext context) {
    Objects.requireNonNull(context, "context must not be null");

    RewriteResult rewrite = context.get(RewriteResult.class);

    if (rewrite != null && rewrite.performed() && !rewrite.rewrites().isEmpty()) {
      return constrained(context, rewrite.rewrites().getFirst());
    }

    return constrained(context, context.request().originalQuery());
  }

  private static SearchRequest constrained(ExecutionContext context, String query) {
    var analysis = context.get(io.qwenbridge.analysis.model.SearchAnalysis.class);
    var constraints = new java.util.ArrayList<io.qwenbridge.analysis.model.Grounded.Constraint>();
    if (analysis != null) constraints.addAll(analysis.constraints());
    for (var c :
        io.qwenbridge.commerce.CommerceExtraction.explicitPrices(
            context.request().originalQuery())) {
      if (constraints.stream()
          .noneMatch(
              x ->
                  x.field().equals(c.field())
                      && x.operator() == c.operator()
                      && x.value().equals(c.value()))) constraints.add(c);
    }
    if (constraints.isEmpty()) return SearchRequest.of(query);
    // Legacy v1 has a public catalog. Authenticated deployments use the tenant-scoped v2 API.
    var plan =
        io.qwenbridge.commerce.CommerceCompiler.validate(
            io.qwenbridge.commerce.CommerceSchemaRegistry.defaults(), "public", constraints);
    return new SearchRequest(query, java.util.Map.of("commercePlan", plan), java.util.Map.of());
  }
}
