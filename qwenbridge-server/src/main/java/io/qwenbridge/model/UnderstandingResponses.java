package io.qwenbridge.model;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.confidence.ConfidenceReport;
import io.qwenbridge.core.evidence.EvidenceRecord;
import io.qwenbridge.core.explain.ExplanationSet;
import java.util.List;

/** Public views of the confidence report and explanations (QB-078–QB-082). */
public final class UnderstandingResponses {

  private UnderstandingResponses() {}

  public record ConfidenceView(String kind, Double value) {
    static ConfidenceView of(Confidence c) {
      return new ConfidenceView(
          c.kind().name(), c.value().isPresent() ? c.value().getAsDouble() : null);
    }
  }

  public record Component(String component, String source, ConfidenceView confidence) {}

  /** Per-component confidence; {@code overall} has a value only when every component is a score. */
  public record ConfidenceReportResponse(
      String method, boolean calibrated, ConfidenceView overall, List<Component> components) {
    public ConfidenceReportResponse {
      components = List.copyOf(components);
    }

    public static ConfidenceReportResponse from(ConfidenceReport report) {
      if (report == null) {
        return null;
      }
      return new ConfidenceReportResponse(
          report.method(),
          report.calibrated(),
          ConfidenceView.of(report.overall()),
          report.components().stream()
              .map(
                  c ->
                      new Component(
                          c.component().name(), c.source(), ConfidenceView.of(c.confidence())))
              .toList());
    }
  }

  /** Evidence reference: offsets or a digest, never copied content. */
  public record Evidence(
      String id,
      String kind,
      String sourceType,
      String sourceId,
      Integer spanStart,
      Integer spanEnd,
      String contentSha256,
      String parentId) {}

  public record Item(
      String subject,
      String status,
      String statement,
      ConfidenceView confidence,
      List<Evidence> evidence) {
    public Item {
      evidence = List.copyOf(evidence);
    }
  }

  public record ExplanationsResponse(String evidenceVersion, List<Item> items) {
    public ExplanationsResponse {
      items = List.copyOf(items);
    }

    public static ExplanationsResponse from(ExplanationSet set) {
      if (set == null) {
        return null;
      }
      return new ExplanationsResponse(
          EvidenceRecord.VERSION,
          set.explanations().stream()
              .map(
                  e ->
                      new Item(
                          e.subject(),
                          e.status().name(),
                          e.statement().orElse(null),
                          ConfidenceView.of(e.confidence()),
                          e.evidenceIds().stream()
                              .map(id -> set.evidence().find(id).orElseThrow())
                              .map(UnderstandingResponses::evidence)
                              .toList()))
              .toList());
    }
  }

  private static Evidence evidence(EvidenceRecord r) {
    return new Evidence(
        r.id(),
        r.kind().name(),
        r.sourceType(),
        r.sourceId(),
        r.span().map(s -> s.start()).orElse(null),
        r.span().map(s -> s.end()).orElse(null),
        r.contentSha256().orElse(null),
        r.parentId().orElse(null));
  }
}
