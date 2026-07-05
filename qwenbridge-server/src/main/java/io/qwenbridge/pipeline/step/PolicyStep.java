package io.qwenbridge.pipeline.step;

import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.PolicyResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PolicyStep implements PipelineStep<PolicyResult> {

    @Override
    public PipelineStage stage() {
        return PipelineStage.POLICY;
    }

    @Override
    public String name() {
        return "PolicyStep";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public Class<PolicyResult> resultType() {
        return PolicyResult.class;
    }

    @Override
    public PolicyResult execute(ExecutionContext context) {

        List<String> violations = new ArrayList<>();

        for (String rewrite : context.get(RewriteResult.class).rewrites()) {

            String normalized = rewrite.toLowerCase();

            if (normalized.contains("adult")) {
                violations.add("ADULT_CONTENT");
            }

            if (normalized.split("\\s+").length > 40) {
                violations.add("BROAD_EXPANSION");
            }
        }

        return new PolicyResult(
                violations.isEmpty(),
                violations
        );
    }
}