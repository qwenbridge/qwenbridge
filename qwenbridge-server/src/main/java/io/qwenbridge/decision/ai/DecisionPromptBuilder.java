package io.qwenbridge.decision.ai;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import io.qwenbridge.pipeline.result.SemanticResult;
import org.springframework.stereotype.Component;

@Component
public class DecisionPromptBuilder {

    public String build(ExecutionContext context) {
        LanguageResult language = context.get(LanguageResult.class);
        IntentResult intent = context.get(IntentResult.class);
        RewriteResult rewrite = context.get(RewriteResult.class);
        SemanticResult semantic = context.get(SemanticResult.class);

        return """
                You are the AI Search Decision Engine for QwenBridge.

                Your task is to decide how the search system should execute the query.
                Stay strictly within search orchestration.
                Do not plan UI, user accounts, agents, external providers, or production databases.

                Allowed search modes:
                - KEYWORD
                - SEMANTIC
                - VECTOR
                - HYBRID
                - DIRECT_ANSWER

                Allowed backends:
                - NONE
                - IN_MEMORY
                - OPENSEARCH
                - CUSTOM

                Return only valid JSON.
                Do not wrap the JSON in markdown.
                Do not include explanations outside JSON.

                JSON schema:
                {
                  "mode": "KEYWORD|SEMANTIC|VECTOR|HYBRID|DIRECT_ANSWER",
                  "backend": "NONE|IN_MEMORY|OPENSEARCH|CUSTOM",
                  "keywordSearch": true,
                  "vectorSearch": false,
                  "hybridSearch": false,
                  "facets": true,
                  "rerank": false,
                  "rewriteAgain": false,
                  "answer": false,
                  "confidence": 0.85,
                  "reason": "short reason"
                }

                Query:
                %s

                Language:
                %s

                Intent:
                %s

                Rewrite candidates:
                %s

                Semantic:
                %s
                """.formatted(
                context.request().originalQuery(),
                language.language(),
                intent.intent(),
                rewrite.rewrites(),
                semantic
        );
    }
}
