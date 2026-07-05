package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.correlation.ThreatCorrelation;
import io.qwenbridge.threat.correlation.ThreatRiskProfile;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatFinding;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultThreatExplanationBuilder implements ThreatExplanationBuilder {

  @Override
  public ThreatExplanation build(ThreatAnalysis analysis) {
    if (analysis == null) {
      return ThreatExplanation.none();
    }

    ThreatRiskProfile profile = analysis.riskProfile();

    List<ThreatExplanationItem> items = analysis.findings().stream().map(this::toItem).toList();

    List<String> matchedRules = profile.correlations().stream().map(ThreatCorrelation::id).toList();

    return new ThreatExplanation(profile.riskLevel(), analysis.decision(), items, matchedRules);
  }

  private ThreatExplanationItem toItem(ThreatFinding finding) {
    return new ThreatExplanationItem(
        finding.type(), finding.severity(), finding.reason(), finding.detector());
  }
}
