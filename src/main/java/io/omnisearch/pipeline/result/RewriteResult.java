package io.omnisearch.pipeline.result;

import java.util.List;

public record RewriteResult(
        boolean performed,
        String provider,
        List<String> rewrites
) {
    public static RewriteResult none() {
        return new RewriteResult(false, "none", List.of());
    }
}
