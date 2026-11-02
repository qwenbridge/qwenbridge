package io.qwenbridge.normalization.service;

import io.quarkus.arc.All;
import io.qwenbridge.normalization.model.NormalizationTraceItem;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.normalization.rule.InputNormalizationRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class DefaultInputNormalizer implements InputNormalizer {

  private final List<InputNormalizationRule> rules;

  @Inject
  public DefaultInputNormalizer(@All List<InputNormalizationRule> rules) {
    this.rules =
        rules.stream()
            .sorted(
                Comparator.comparingInt(InputNormalizationRule::order)
                    .thenComparing(InputNormalizationRule::name))
            .toList();
  }

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
