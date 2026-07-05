package io.qwenbridge.intent;

import io.qwenbridge.intent.ai.AIIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntentService {

  private final AIIntentService aiIntentService;

  public IntentAnalysis analyze(String query) {
    try {
      return aiIntentService.analyze(query);
    } catch (Exception exception) {
      log.warn(
          "AI intent analysis failed. Falling back to product search intent. query={}",
          query,
          exception);
      return IntentAnalysis.productSearch();
    }
  }

  public String detect(String query) {
    return analyze(query).type().name();
  }
}
