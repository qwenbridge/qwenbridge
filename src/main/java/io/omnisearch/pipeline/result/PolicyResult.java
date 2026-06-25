package io.omnisearch.pipeline.result;

import java.util.List;

public record PolicyResult(
        boolean passed,
        List<String> violations
) {
    public static PolicyResult allow() {
        return new PolicyResult(true, List.of());
    }
}
