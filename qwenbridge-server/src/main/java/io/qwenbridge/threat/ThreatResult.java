package io.qwenbridge.threat;

import java.util.List;

public record ThreatResult(boolean safe, List<String> reasons) {
  public static ThreatResult noThreat() {
    return new ThreatResult(true, List.of());
  }

  public static ThreatResult detected(List<String> reasons) {
    return new ThreatResult(false, reasons);
  }
}
