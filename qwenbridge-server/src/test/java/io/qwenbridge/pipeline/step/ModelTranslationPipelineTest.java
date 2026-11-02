package io.qwenbridge.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-015–QB-019 with the model provider configured. The model is stubbed: these tests check the
 * contract (typed outcome, preservation enforcement, representations, honest confidence), not
 * translation quality.
 */
@QuarkusTest
@TestProfile(ModelTranslationPipelineTest.ModelProvider.class)
class ModelTranslationPipelineTest {

  public static class ModelProvider implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qwenbridge.translation.provider", "model");
    }
  }

  static final String QUERY = "کفش مشکی بدون چرم زیر ۱۲۰۰ یورو";

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void setUp() {
    TranslationPipelineTest.stub(aiService, openSearchClient, searchAnalysisService);
  }

  private void modelAnswers(String json) {
    when(aiService.chat(argThat(r -> r != null && r.prompt().startsWith("Translate"))))
        .thenReturn(new ChatResponse(json));
  }

  private static JsonPath analyze() {
    return TranslationPipelineTest.analyze(
        Map.of("query", QUERY, "context", Map.of("content_language", "en")));
  }

  @Test
  void faithfulTranslationIsAcceptedAndKeptBesideTheOriginal() {
    modelAnswers("{\"text\":\"black shoes without leather under 1200 EUR\"}");
    JsonPath r = analyze();

    assertThat(r.getString("translation.status")).isEqualTo("TRANSLATED");
    assertThat(r.getString("translation.provider")).isEqualTo("model-chat");
    assertThat(r.getString("translation.confidence.kind")).isEqualTo("MODEL_DECISION");
    assertThat(r.getString("translation.confidence.value")).isNull();
    assertThat(r.getString("translation.preservation.status")).isEqualTo("PRESERVED");
    assertThat(r.getString("textRepresentations.original")).isEqualTo(QUERY);
    assertThat(r.getString("textRepresentations.translated"))
        .isEqualTo("black shoes without leather under 1200 EUR");
    assertThat(r.getList("textRepresentations.transformations.kind", String.class))
        .containsExactly("NORMALIZATION", "TRANSLATION");
    assertThat(r.getString("textRepresentations.transformations[1].actor")).isEqualTo("model-chat");
    // Evidence still points into the original text, never the translation.
    assertThat(r.getString("queryIR.originalText")).isEqualTo(QUERY);
  }

  @Test
  void translationThatDropsAnExclusionIsRejected() {
    modelAnswers("{\"text\":\"black leather shoes under 1200 EUR\"}");
    JsonPath r = analyze();

    assertThat(r.getString("translation.status")).isEqualTo("FAILED");
    assertThat(r.getString("translation.reason")).isEqualTo("NOT_PRESERVED");
    assertThat(r.getString("translation.preservation.status")).isEqualTo("NOT_PRESERVED");
    assertThat(r.getString("translation.preservation.issues[0].kind")).isEqualTo("NEGATION");
    assertThat(r.getString("translation.preservation.issues[0].direction")).isEqualTo("LOST");
    assertThat(r.getString("textRepresentations.translated")).isNull();
    assertThat(r.prettify()).doesNotContain("black leather shoes");
  }

  @Test
  void unusableModelOutputIsATypedFailure() {
    modelAnswers("no json at all");
    JsonPath r = analyze();

    assertThat(r.getString("translation.status")).isEqualTo("FAILED");
    assertThat(r.getString("translation.reason")).isEqualTo("INVALID_OUTPUT");
  }
}
