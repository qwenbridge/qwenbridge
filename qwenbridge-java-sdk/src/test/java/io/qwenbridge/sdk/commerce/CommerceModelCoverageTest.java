package io.qwenbridge.sdk.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * QB-189: the typed models cover every field of real server responses. The fixtures are captured
 * from the running server by {@code CommerceApiTest}, which fails when the server returns a field
 * they lack; this test fails when a fixture field has no record component, so no field is silently
 * dropped. Maps ({@code document}, {@code metadata}, {@code commerce}, {@code timings}, {@code
 * signals}) are open by design; {@code fields} is a map of typed {@code FieldSpec} values.
 */
class CommerceModelCoverageTest {

  static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

  /** Which record models the object found under each field (lists: their elements). */
  static final Map<String, Class<?>> NESTED =
      Map.ofEntries(
          Map.entry("translation", CommerceContracts.Translation.class),
          Map.entry("queryIR", CommerceContracts.Plan.class),
          Map.entry("constraints@Plan", CommerceContracts.Constraint.class),
          Map.entry("constraints@SubQuery", CommerceContracts.Constraint.class),
          Map.entry("constraints@Interpretation", CommerceContracts.ConstraintStatus.class),
          Map.entry("evidence@Constraint", CommerceContracts.Span.class),
          Map.entry("results", CommerceContracts.Results.class),
          Map.entry("hits", CommerceContracts.Hit.class),
          Map.entry("confidence", CommerceContracts.Confidence.class),
          Map.entry("evidence@Response", CommerceContracts.Attribution.class),
          Map.entry("spans", CommerceContracts.Span.class),
          Map.entry("policies", CommerceContracts.Policy.class),
          Map.entry("retrieval", CommerceContracts.Retrieval.class),
          Map.entry("interpretation", CommerceContracts.Interpretation.class),
          Map.entry("semanticSignals", CommerceContracts.Signal.class),
          Map.entry("subqueries", CommerceContracts.SubQuery.class),
          Map.entry("clarifications", CommerceContracts.Clarification.class));

  static final Set<String> OPEN_MAPS =
      Set.of("document", "metadata", "commerce", "timings", "signals", "fields");

  static JsonNode fixture(String name) throws Exception {
    try (InputStream in =
        CommerceModelCoverageTest.class.getResourceAsStream("/contract/" + name)) {
      return JSON.readTree(in);
    }
  }

  static void covered(JsonNode node, Class<?> type, String path, List<String> missing) {
    if (node == null || node.isNull()) return;
    if (node.isArray()) {
      for (JsonNode element : node) covered(element, type, path + "[]", missing);
      return;
    }
    Set<String> components =
        Arrays.stream(type.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(Collectors.toSet());
    node.properties()
        .forEach(
            field -> {
              String name = field.getKey();
              if (!components.contains(name)) {
                missing.add(path + "." + name + " (" + type.getSimpleName() + ")");
                return;
              }
              if (OPEN_MAPS.contains(name)) return;
              Class<?> nested = NESTED.get(name + "@" + type.getSimpleName());
              if (nested == null) nested = NESTED.get(name);
              if (nested != null) covered(field.getValue(), nested, path + "." + name, missing);
            });
  }

  @Test
  void everyFieldOfRealServerResponsesIsModelled() throws Exception {
    for (String name : List.of("v2-search-response.json", "v2-search-response-planned.json")) {
      JsonNode fixture = fixture(name);
      var missing = new ArrayList<String>();
      covered(fixture, CommerceContracts.Response.class, "$", missing);
      assertEquals(List.of(), missing, name + ": fields without a typed model");
      var response = JSON.treeToValue(fixture, CommerceContracts.Response.class);
      assertEquals(2, response.apiVersion());
      assertEquals(fixture.get("status").asText(), response.status());
    }
    var planned =
        JSON.treeToValue(
            fixture("v2-search-response-planned.json"), CommerceContracts.Response.class);
    assertEquals(CommerceContracts.Request.CONTRACT_V1, planned.contract());
    assertEquals("1.0", planned.interpretation().queryIrVersion());
    assertTrue(planned.retrieval().degraded());
    assertEquals("size", planned.clarifications().getFirst().field());
    assertEquals("q1", planned.subqueries().getFirst().id());
  }

  @Test
  void plannedRequestsSerializeTheContractAndLegacyRequestsOmitIt() throws Exception {
    var planned = JSON.readTree(JSON.writeValueAsString(CommerceContracts.Request.planned("x")));
    assertEquals("commerce-search/1.0", planned.get("contract").asText());
    assertTrue(planned.get("mode").isNull(), "the planner chooses the strategy");
    var legacy = JSON.readTree(JSON.writeValueAsString(new CommerceContracts.Request("x")));
    assertTrue(!legacy.has("contract"));
  }
}
