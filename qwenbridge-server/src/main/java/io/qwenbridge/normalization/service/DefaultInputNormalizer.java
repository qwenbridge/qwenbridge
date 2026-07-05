package io.qwenbridge.normalization.service;

import io.qwenbridge.normalization.model.NormalizationTraceItem;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.rule.InputNormalizationRule;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultInputNormalizer implements InputNormalizer {

  private final List<InputNormalizationRule> rules;

  @Override
  public NormalizedInput normalize(String input) {
    String original = input == null ? "" : input;
    String current = original;
    List<NormalizationTraceItem> trace = new ArrayList<>();

    for (InputNormalizationRule rule : rules) {
      String before = current;
      String after = safe(rule.normalize(before));

      trace.add(new NormalizationTraceItem(rule.name(), before, after, !before.equals(after)));

      current = after;
    }

    return new NormalizedInput(original, current, trace);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
