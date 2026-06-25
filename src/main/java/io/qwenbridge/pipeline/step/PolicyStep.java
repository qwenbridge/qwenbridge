package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.PolicyResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyStep implements PipelineStep<PolicyResult> {

    public String name() { return "PolicyStep"; }
    public int order() { return 60; }
    public Class<PolicyResult> resultType() { return PolicyResult.class; }

    public PolicyResult execute(ExecutionContext context) {
        List<String> violations = new ArrayList<>();

        for (String rewrite : context.get(RewriteResult.class).rewrites()) {
            String normalized = rewrite.toLowerCase();

            if (normalized.contains("adult")) {
                violations.add("ADULT_CONTENT");
            }

            if (normalized.split("\\s+").length > 6) {
                violations.add("BROAD_EXPANSION");
            }
        }

        return new PolicyResult(violations.isEmpty(), violations);
    }
}
