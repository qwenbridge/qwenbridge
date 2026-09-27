package io.qwenbridge.confidence;

import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfidenceService {
  public double calculate(String query, List<String> rewrites) {
    return rewrites.size() > 1 ? 0.94 : 0.80;
  }
}
