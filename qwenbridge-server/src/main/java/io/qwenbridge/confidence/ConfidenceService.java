package io.qwenbridge.confidence;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConfidenceService {
    public double calculate(String query, List<String> rewrites) {
        return rewrites.size() > 1 ? 0.94 : 0.80;
    }
}
