package io.qwenbridge.rewrite;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.rewrite.ai.AIRewriteService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

@Slf4j
public class RewriteService {

    private final AIRewriteService aiRewriteService;

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
