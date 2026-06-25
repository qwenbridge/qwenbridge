package io.qwenbridge.decision;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DecisionService {
    public DecisionType decide(double confidence, List<String> rewrites) {
        if (confidence < 0.50) return DecisionType.CLARIFY;
        if (rewrites.size() > 1) return DecisionType.REWRITE;
        return DecisionType.ALLOW;
    }
}
