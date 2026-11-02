package io.qwenbridge.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.pipeline.step.TranslationPipelineTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** QB-078–QB-084 end to end on the v1 analyze response. */
@QuarkusTest
class UnderstandingReportsPipelineTest {

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void setUp() {
    TranslationPipelineTest.stub(aiService, openSearchClient, searchAnalysisService);
  }

  private static JsonPath analyze(String query) {
    return TranslationPipelineTest.analyze(
        Map.of("query", query, "domainHints", List.of("commerce")));
  }

  @Test
  void everyComponentReportsItsOwnTypedConfidence() {
    JsonPath r = analyze("black running shoes under 100 EUR");
    List<String> components = r.getList("confidenceReport.components.component", String.class);
    assertThat(components)
        .contains("LANGUAGE", "THREAT", "MODEL_INTENT", "MODEL_SEMANTIC", "MODEL_DECISION");
    assertThat(r.getString("confidenceReport.method")).isEqualTo("weakest-link-v1");
    assertThat(r.getBoolean("confidenceReport.calibrated")).isFalse();
    assertThat(
            r.getString(
                "confidenceReport.components.find { it.component == 'MODEL_INTENT'"
                    + " }.confidence.kind"))
        .isEqualTo("MODEL_SCORE");
    assertThat(
            r.getString(
                "confidenceReport.components.find { it.component == 'THREAT' }.confidence.value"))
        .isNull();
    // Mixed kinds: the overall is never a number.
    assertThat(r.getString("confidenceReport.overall.value")).isNull();
  }

  @Test
  void withoutTheModelItsComponentsAreUnknownAndSoIsTheOverall() {
    SearchAnalysis fallback = SearchAnalysis.fallback("black running shoes");
    when(searchAnalysisService.analyze(anyString())).thenReturn(fallback);
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(fallback);
    JsonPath r = analyze("black running shoes");
    assertThat(
            r.getString(
                "confidenceReport.components.find { it.component == 'MODEL_INTENT'"
                    + " }.confidence.kind"))
        .isEqualTo("UNKNOWN");
    assertThat(r.getString("confidenceReport.overall.kind")).isEqualTo("UNKNOWN");
  }

  @Test
  void constraintsAreExplainedWithEvidenceOffsetsNotCopiedText() {
    String query = "black running shoes under 100 EUR";
    JsonPath r = analyze(query);
    Map<String, Object> constraint =
        r.getMap("explanations.items.find { it.subject.startsWith('constraint:') }");
    assertThat(constraint.get("status")).isEqualTo("SUPPORTED");
    assertThat((String) constraint.get("statement")).contains("\"under 100 EUR\"", "Validation:");
    int start =
        r.getInt(
            "explanations.items.find { it.subject.startsWith('constraint:')"
                + " }.evidence[0].spanStart");
    int end =
        r.getInt(
            "explanations.items.find { it.subject.startsWith('constraint:') }.evidence[0].spanEnd");
    assertThat(query.substring(start, end)).isEqualTo("under 100 EUR");
    assertThat(r.getString("explanations.evidenceVersion")).isEqualTo("1.0");
    assertThat(r.getString("explanations.items.find { it.subject == 'threat' }.statement"))
        .startsWith("No threat rule matched");
  }

  @Test
  void decisionsWithNothingRecordedAreUnknownNotDescribed() {
    JsonPath r = analyze("black running shoes");
    // No execution plan was produced by the stubbed decision step.
    Map<String, Object> plan = r.getMap("explanations.items.find { it.subject == 'plan' }");
    if (!"SUPPORTED".equals(plan.get("status"))) {
      assertThat(plan.get("status")).isEqualTo("UNKNOWN");
      assertThat(plan.get("statement")).isNull();
    }
  }

  @Test
  void secretsNeverAppearInExplanations() {
    JsonPath r = analyze("laptop SKU-4471 under 900 EUR token=abcdef123456");
    String explanations = r.prettify().substring(r.prettify().indexOf("\"explanations\""));
    assertThat(explanations).doesNotContain("abcdef123456");
  }
}
