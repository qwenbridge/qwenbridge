package io.qwenbridge.execution.provider.model;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;

import java.util.Objects;

public final class SearchRequestFactory {

    private SearchRequestFactory() {
    }

    public static SearchRequest from(ExecutionContext context) {
        Objects.requireNonNull(context, "context must not be null");

        RewriteResult rewrite = context.get(RewriteResult.class);

        if (rewrite != null && rewrite.performed() && !rewrite.rewrites().isEmpty()) {
            return SearchRequest.of(rewrite.rewrites().getFirst());
        }

        return SearchRequest.of(context.request().originalQuery());
    }
}