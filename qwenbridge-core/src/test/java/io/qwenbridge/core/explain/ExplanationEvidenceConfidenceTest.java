package io.qwenbridge.core.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Calibration;
import io.qwenbridge.core.confidence.ComponentConfidence;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.confidence.ConfidenceComponent;
import io.qwenbridge.core.confidence.ConfidenceReport;
import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.evidence.EvidenceRecord;
import io.qwenbridge.core.evidence.EvidenceSet;
import io.qwenbridge.core.evidence.InMemoryEvidenceStore;
import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.Intent;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.validation.ConstraintPreservationTest;
import io.qwenbridge.core.validation.ConstraintValidator;
import io.qwenbridge.core.validation.ValidationReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** QB-078–QB-084. */
class ExplanationEvidenceConfidenceTest {

  static final Instant AT = Instant.parse("2026-11-01T10:00:00Z");

  static ComponentConfidence c(ConfidenceComponent component, Confidence confidence) {
    return new ComponentConfidence(component, confidence, "fixture");
  }

  @Nested
  class ConfidenceModel {

    @Test
    void anyUnknownComponentMakesTheOverallUnknown() {
      ConfidenceReport r =
          ConfidenceReport.of(
              List.of(
                  c(ConfidenceComponent.MODEL_INTENT, Confidence.modelScore(0.9)),
                  c(ConfidenceComponent.LANGUAGE, Confidence.unknown())),
              false);
      assertEquals(Confidence.Kind.UNKNOWN, r.overall().kind());
      assertTrue(r.overall().value().isEmpty());
    }

    @Test
    void onlyAllScoresGiveANumberAndItIsTheWeakest() {
      ConfidenceReport scores =
          ConfidenceReport.of(
              List.of(
                  c(ConfidenceComponent.MODEL_INTENT, Confidence.modelScore(0.9)),
                  c(ConfidenceComponent.MODEL_SEMANTIC, Confidence.modelScore(0.6))),
              false);
      assertEquals(0.6, scores.overall().value().getAsDouble());
      assertFalse(scores.calibrated());
      ConfidenceReport mixed =
          ConfidenceReport.of(
              List.of(
                  c(ConfidenceComponent.MODEL_INTENT, Confidence.modelScore(0.9)),
                  c(ConfidenceComponent.INTENT, Confidence.heuristic())),
              true);
      assertEquals(Confidence.Kind.HEURISTIC, mixed.overall().kind());
      assertFalse(mixed.calibrated(), "a heuristic cannot be calibrated");
      assertEquals(Confidence.Kind.UNKNOWN, ConfidenceReport.of(List.of(), false).overall().kind());
    }

    @Test
    void retrievalScoresAreASeparateTypeAndOnlyComparableWithinOneScorer() {
      RetrievalScore bm25 = new RetrievalScore(12.7, RetrievalScore.Kind.BM25, "opensearch");
      RetrievalScore cosine =
          new RetrievalScore(0.83, RetrievalScore.Kind.VECTOR_SIMILARITY, "opensearch");
      assertFalse(bm25.comparableWith(cosine));
      assertTrue(
          bm25.comparableWith(new RetrievalScore(3.1, RetrievalScore.Kind.BM25, "opensearch")));
      assertThrows(
          IllegalArgumentException.class,
          () -> new RetrievalScore(Double.NaN, RetrievalScore.Kind.BM25, "x"));
    }
  }

  @Nested
  class CalibrationMeasurement {

    @Test
    void perfectlyCalibratedScoresHaveZeroError() {
      List<Calibration.Observation> data = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        data.add(new Calibration.Observation(0.75, i % 4 != 0));
      }
      Calibration.Report r = Calibration.report(data);
      assertTrue(r.meaningful());
      assertEquals(0.0, r.expectedCalibrationError().getAsDouble(), 1e-9);
      assertEquals(0.1875, r.brierScore(), 1e-9);
    }

    @Test
    void overconfidentScoresAreMeasured() {
      List<Calibration.Observation> data = new ArrayList<>();
      for (int i = 0; i < 60; i++) {
        data.add(new Calibration.Observation(0.95, i % 2 == 0));
      }
      assertEquals(0.45, Calibration.report(data).expectedCalibrationError().getAsDouble(), 1e-9);
    }

    @Test
    void tooFewObservationsReportNoEce() {
      Calibration.Report r = Calibration.report(List.of(new Calibration.Observation(0.9, true)));
      assertFalse(r.meaningful());
      assertTrue(r.expectedCalibrationError().isEmpty());
      assertTrue(r.note().contains("not reported"));
    }
  }

  @Nested
  class EvidenceContract {

    @Test
    void evidenceReferencesContentByDigestNeverByCopy() {
      EvidenceRecord doc =
          EvidenceRecord.of(
              "ev-doc-1",
              EvidenceRecord.Kind.RETRIEVED_CONTENT,
              "document",
              "sku-1",
              "secret product notes",
              AT);
      assertEquals(EvidenceRecord.VERSION, doc.version());
      assertEquals(64, doc.contentSha256().orElseThrow().length());
      assertFalse(doc.toString().contains("secret product notes"));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new EvidenceRecord(
                  "1.0",
                  "ev-x",
                  EvidenceRecord.Kind.USER_TEXT,
                  "query",
                  "original",
                  Optional.empty(),
                  Optional.empty(),
                  AT,
                  Optional.empty(),
                  Map.of()));
    }

    @Test
    void provenanceMustBeAcyclicAndComplete() {
      EvidenceRecord a = EvidenceRecord.userText("ev-a", Span.of("shoes", 0, 5), AT);
      EvidenceRecord orphan =
          new EvidenceRecord(
              "1.0",
              "ev-b",
              EvidenceRecord.Kind.POLICY_DECISION,
              "validation",
              "EXECUTABLE",
              Optional.empty(),
              Optional.empty(),
              AT,
              Optional.of("ev-missing"),
              Map.of());
      assertThrows(IllegalArgumentException.class, () -> new EvidenceSet(List.of(a, orphan)));
      assertThrows(IllegalArgumentException.class, () -> new EvidenceSet(List.of(a, a)));
      assertTrue(new EvidenceSet(List.of(a)).groundedIn("shoes"));
      assertFalse(new EvidenceSet(List.of(a)).groundedIn("boots"));
    }

    @Test
    void storageIsOptionalAndTenantScoped() {
      InMemoryEvidenceStore store = new InMemoryEvidenceStore();
      EvidenceRecord root = EvidenceRecord.userText("ev-root", Span.of("shoes", 0, 5), AT);
      EvidenceRecord child =
          new EvidenceRecord(
              "1.0",
              "ev-child",
              EvidenceRecord.Kind.POLICY_DECISION,
              "validation",
              "EXECUTABLE",
              Optional.empty(),
              Optional.empty(),
              AT,
              Optional.of("ev-root"),
              Map.of());
      store.put("acme", root);
      store.put("acme", child);
      assertEquals(List.of(child, root), store.provenance("acme", "ev-child"));
      assertTrue(store.get("globex", "ev-root").isEmpty());
    }
  }

  @Nested
  class Explanations {

    static QueryIR extracted(String text, String language) {
      Optional<LanguageTag> tag = LanguageTag.parse(language);
      return ConstraintExtractor.extract(
              text,
              new ExtractionContext(
                  tag,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.of(ConstraintPreservationTest.SHOP),
                  ConstraintPreservationTest.TERMS))
          .applyTo(QueryIR.of(text, tag.orElse(LanguageTag.UNDETERMINED)));
    }

    @Test
    void constraintsAreExplainedFromRecordedDecisionsAndCiteEvidence() {
      QueryIR ir = extracted("skor utan läder under 1200 SEK", "sv");
      ValidationReport report =
          ConstraintValidator.validate(
              ir, ConstraintPreservationTest.SHOP, ConstraintPreservationTest.SHOP_MAPPING);
      ExplanationSet set = QueryIrExplainer.explain(ir, Optional.of(report), AT);

      Explanation exclusion =
          set.explanations().stream()
              .filter(e -> e.subject().startsWith("constraint:"))
              .filter(e -> e.statement().map(s -> s.contains("material")).orElse(false))
              .findFirst()
              .orElseThrow();
      assertEquals(Explanation.Status.SUPPORTED, exclusion.status());
      assertTrue(exclusion.statement().get().contains("\"utan läder\""), exclusion::toString);
      assertTrue(exclusion.statement().get().contains("Validation: EXECUTABLE"));
      for (String id : exclusion.evidenceIds()) {
        assertTrue(set.evidence().find(id).isPresent());
      }
      assertTrue(set.evidence().groundedIn(ir.originalText()));
    }

    @Test
    void ungroundedDecisionsAreNotGivenAStory() {
      Constraint inferred =
          new Constraint(
              "c1",
              "price",
              Constraint.Operator.LT,
              Optional.of(new IrValue.Number(java.math.BigDecimal.TEN)),
              Constraint.Strength.SOFT,
              Constraint.Provenance.INFERRED,
              "budget guess",
              Optional.empty(),
              Confidence.heuristic(),
              Optional.empty());
      Intent noSpan = new Intent("i1", "search", Confidence.unknown(), Optional.empty());
      QueryIR ir =
          new QueryIR(
              io.qwenbridge.core.queryir.IrVersion.CURRENT,
              Optional.empty(),
              "cheap shoes",
              LanguageTag.UNDETERMINED,
              List.of(noSpan),
              List.of(),
              List.of(inferred),
              List.of(),
              io.qwenbridge.core.queryir.RetrievalHints.NONE,
              List.of(),
              Map.of());
      ExplanationSet set = QueryIrExplainer.explain(ir, Optional.empty(), AT);
      assertEquals(Explanation.Status.UNKNOWN, set.explanations().get(0).status());
      assertEquals(Explanation.Status.INSUFFICIENT_EVIDENCE, set.explanations().get(1).status());
      assertTrue(set.explanations().stream().allMatch(e -> e.statement().isEmpty()));
    }

    @Test
    void aSupportedClaimWithoutEvidenceCannotBeConstructedOrSurviveACheck() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new Explanation(
                  "x",
                  Explanation.Status.SUPPORTED,
                  Optional.of("trust me"),
                  List.of(),
                  Confidence.heuristic()));
      Explanation claim =
          Explanation.supported("x", "from \"boots\"", List.of("ev-a"), Confidence.heuristic());
      EvidenceSet wrongSpan =
          new EvidenceSet(List.of(EvidenceRecord.userText("ev-a", Span.of("boots", 0, 5), AT)));
      ExplanationSet checked = ExplanationSet.checked(List.of(claim), wrongSpan, "shoes");
      assertEquals(
          Explanation.Status.INSUFFICIENT_EVIDENCE, checked.explanations().getFirst().status());
      ExplanationSet missing = ExplanationSet.checked(List.of(claim), EvidenceSet.EMPTY, "boots");
      assertEquals(
          Explanation.Status.INSUFFICIENT_EVIDENCE, missing.explanations().getFirst().status());
    }

    @Test
    void secretsInQuotedTextNeverAppearInExplanations() {
      QueryIR ir = extracted("token=abc123secret laptop under 900 EUR", "en");
      ExplanationSet set = QueryIrExplainer.explain(ir, Optional.empty(), AT);
      assertFalse(set.explanations().isEmpty());
      assertTrue(
          set.explanations().stream()
              .flatMap(e -> e.statement().stream())
              .noneMatch(s -> s.contains("abc123secret")),
          set.toString());
    }
  }
}
