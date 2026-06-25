package io.qwenbridge.rewrite;

import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RewriteService {

    private static final Logger log = LoggerFactory.getLogger(RewriteService.class);

    private final AIRewriteService aiRewriteService;

    public RewriteService(AIRewriteService aiRewriteService) {
        this.aiRewriteService = aiRewriteService;
    }

    public List<String> rewrite(String query, String language, String intent) {
        try {
            String rewrittenQuery = aiRewriteService.rewrite(query);

            if (rewrittenQuery == null || rewrittenQuery.isBlank()) {
                return List.of(query);
            }

            return List.of(rewrittenQuery);
        } catch (RuntimeException ex) {
            log.warn(
                    "AI rewrite failed. Falling back to original query. language={} intent={} query={}",
                    language,
                    intent,
                    query,
                    ex
            );

            return List.of(query);
        }
    }
}
