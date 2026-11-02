package io.qwenbridge.pipeline.step;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrJson;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.intent.IntentType;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-029–QB-036 end to end: the v1 response carries a valid universal QueryIR assembled from
 * language, intents and extraction, and reports what it could not resolve.
 */
@QuarkusTest
class QueryIrPipelineTest {

  @InjectMock AIService aiService;
  @InjectMock OpenSearchClient openSearchClient;
  @InjectMock SearchAnalysisService searchAnalysisService;

  @BeforeEach
  void stub() {
    reset(aiService, openSearchClient, searchAnalysisService);
    SearchAnalysis analysis =
        SearchAnalysis.builder()
            .language("en")
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(0.9)
            .intentReason("test")
            .rewrites(List.of("q"))
            .semanticValidated(true)
            .semanticScore(0.9)
            .semanticMeaning("test")
            .entities(List.of())
            .searchMode(SearchMode.KEYWORD)
            .backend(SearchBackend.OPENSEARCH)
            .keywordSearch(true)
            .vectorSearch(false)
            .hybridSearch(false)
            .facets(false)
            .rerank(false)
            .rewriteAgain(false)
            .answer(false)
            .decisionConfidence(0.9)
            .decisionReason("test")
            .modelThreatDecision("ALLOW")
            .build();
    when(aiService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse("{}"));
    when(searchAnalysisService.analyze(anyString())).thenReturn(analysis);
    when(searchAnalysisService.analyze(anyString(), anyString())).thenReturn(analysis);
    when(openSearchClient.search(anyString(), anyMap()))
        .thenReturn(
            Map.of("took", 0, "hits", Map.of("total", Map.of("value", 0), "hits", List.of())));
  }

  private JsonPath analyze(Map<String, Object> body) {
    String json =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/api/v1/search/analyze")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    return JsonPath.from(json);
  }

  /** The published queryIR must itself be a valid QueryIR document. */
  private static QueryIR reparse(JsonPath response) throws Exception {
    String irJson =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(response.getMap("queryIR"));
    QueryIrJson.Result result = QueryIrJson.read(irJson);
    assertThat(result).isInstanceOf(QueryIrJson.Result.Valid.class);
    return ((QueryIrJson.Result.Valid) result).ir();
  }

  @Test
  void devopsQueryProducesAValidUniversalQueryIr() throws Exception {
    String text = "Find Kubernetes pods with memory usage above 85% during the last hour";
    JsonPath response = analyze(Map.of("query", text));

    QueryIR ir = reparse(response);
    assertThat(ir.originalText()).isEqualTo(text);
    assertThat(ir.intents()).extracting(i -> i.type()).containsExactly("search");
    assertThat(ir.constraints()).extracting(c -> c.field()).contains("memory_usage", "time");
    var window =
        ir.constraints().stream().filter(c -> c.field().equals("time")).findFirst().orElseThrow();
    var temporal = (io.qwenbridge.core.queryir.IrValue.Temporal) window.value().orElseThrow();
    assertThat(temporal.resolved()).as("request time is the explicit reference").isTrue();
    assertThat(response.getList("unresolved.kind", String.class)).contains("UNBOUND_FIELD");
  }

  @Test
  void hintedEnabledDomainBindsFieldsToItsSchema() throws Exception {
    JsonPath response =
        analyze(
            Map.of(
                "query", "black shoes with price under 100", "domainHints", List.of("commerce")));
    QueryIR ir = reparse(response);
    var price = ir.constraints().stream().filter(c -> c.field().equals("price")).findFirst();
    assertThat(price).as("bound by name to the commerce pack's price field").isPresent();
    assertThat(response.getList("unresolved.kind", String.class)).doesNotContain("UNBOUND_FIELD");
  }

  @Test
  void persianKronaIsReportedAsAmbiguousNotGuessed() throws Exception {
    JsonPath response = analyze(Map.of("query", "کفش مشکی زیر ۱۲۰۰ کرون می‌خوام"));
    QueryIR ir = reparse(response);
    var money =
        (io.qwenbridge.core.queryir.IrValue.Money)
            ir.constraints().getFirst().value().orElseThrow();
    assertThat(money.amount()).isEqualByComparingTo("1200");
    assertThat(money.currency()).isEmpty();
    assertThat(response.getList("unresolved.kind", String.class)).contains("AMBIGUOUS_CURRENCY");
  }

  @Test
  void calendarWordsResolveOnlyWithAClientTimeZone() throws Exception {
    JsonPath without = analyze(Map.of("query", "failed logins yesterday"));
    assertThat(reparse(without).constraints()).isEmpty();
    assertThat(without.getList("unresolved.kind", String.class)).contains("MISSING_TIME_ZONE");

    JsonPath with =
        analyze(
            Map.of(
                "query",
                "failed logins yesterday",
                "context",
                Map.of("timezone", "Europe/Stockholm")));
    assertThat(reparse(with).constraints()).hasSize(1);
  }

  @Test
  void exactDecimalsSurviveTheResponse() throws Exception {
    QueryIR ir = reparse(analyze(Map.of("query", "laptops between 899.50 and 1500 EUR")));
    var range =
        (io.qwenbridge.core.queryir.IrValue.Range)
            ir.constraints().getFirst().value().orElseThrow();
    var min = (io.qwenbridge.core.queryir.IrValue.Money) range.min().orElseThrow();
    assertThat(min.amount()).isEqualByComparingTo("899.50");
    assertThat(min.currency()).contains("EUR");
  }

  // --- QB-050–QB-056: constraint validation gate -------------------------------------------------

  @Test
  void commerceMoneyIsValidatedAsPriceAndCurrency() {
    JsonPath response =
        analyze(Map.of("query", "black shoes under 1200 SEK", "domainHints", List.of("commerce")));

    assertThat(response.getString("constraintValidation.status")).isEqualTo("VALIDATED");
    assertThat(response.getString("constraintValidation.schema")).isEqualTo("commerce@1.0");
    assertThat(response.getBoolean("constraintValidation.enforced")).isFalse();
    List<String> statuses =
        response.getList("constraintValidation.assessments.status", String.class);
    assertThat(statuses).isNotEmpty().containsOnly("EXECUTABLE");
    assertThat(response.getList("constraintValidation.assessments.note", String.class))
        .contains("filter");
  }

  @Test
  void requiredConstraintOutsideTheSchemaIsReportedAsBlocking() {
    JsonPath response =
        analyze(
            Map.of(
                "query", "shoes with heel height above 5 cm", "domainHints", List.of("commerce")));

    assertThat(response.getString("constraintValidation.status")).isEqualTo("BLOCKED");
    assertThat(response.getList("constraintValidation.assessments.status", String.class))
        .contains("INVALID_HARD");
    assertThat(response.getList("constraintValidation.assessments.violations.code").toString())
        .contains("UNKNOWN_FIELD");
    // The constraint is reported, not dropped, and no user value is echoed by the gate.
    assertThat(response.getMap("constraintValidation").toString()).doesNotContain("heel");
  }

  @Test
  void withoutADomainNothingIsCompilable() {
    JsonPath response = analyze(Map.of("query", "laptops under 900 EUR"));
    assertThat(response.getString("constraintValidation.status")).isEqualTo("NO_SCHEMA");
    assertThat(response.getList("constraintValidation.assessments")).isEmpty();
  }
}
