package io.qwenbridge.core.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * QB-203: the domain-neutral library dataset, reproduced on the Core in-memory index alone. The
 * expected results are written in the dataset file with the reason for each; this test only runs
 * them.
 */
class LibraryDatasetTest {

  static JsonNode dataset() throws Exception {
    try (var in = LibraryDatasetTest.class.getResourceAsStream("/datasets/library-1.0.json")) {
      return new ObjectMapper().readTree(in);
    }
  }

  static FieldValue value(JsonNode node) {
    if (node.isBoolean()) return new FieldValue.Bool(node.booleanValue());
    if (node.isNumber()) return new FieldValue.Number(new BigDecimal(node.asText()));
    return FieldValue.text(node.asText());
  }

  static InMemoryKeywordIndex index(JsonNode root) {
    var index = new InMemoryKeywordIndex("library", 1_000, 100);
    for (JsonNode record : root.path("records")) {
      var text = new StringBuilder();
      for (JsonNode field : root.path("textFields"))
        if (record.has(field.asText()))
          text.append(record.get(field.asText()).asText()).append(' ');
      var fields = new HashMap<String, FieldValue>();
      record
          .properties()
          .forEach(
              e -> {
                if (!Set.of("tenant", "id").contains(e.getKey()))
                  fields.put(e.getKey(), value(e.getValue()));
              });
      index.put(
          new IndexedDocument(
              record.get("tenant").asText(),
              record.get("id").asText(),
              text.toString().trim(),
              fields));
    }
    return index;
  }

  static Filter filter(JsonNode clauses) {
    var parts = new ArrayList<Filter>();
    for (JsonNode c : clauses)
      parts.add(
          new Filter.Compare(
              c.get(0).asText(), Filter.Compare.Op.valueOf(c.get(1).asText()), value(c.get(2))));
    return parts.isEmpty()
        ? Filter.ALL
        : parts.size() == 1 ? parts.getFirst() : new Filter.And(parts);
  }

  @TestFactory
  Stream<DynamicTest> everyCaseReproducesItsExpectedResult() throws Exception {
    JsonNode root = dataset();
    var index = index(root);
    String tenant = root.path("tenant").asText();
    var tests = new ArrayList<DynamicTest>();
    for (JsonNode c : root.path("cases"))
      tests.add(
          DynamicTest.dynamicTest(
              c.path("name").asText(),
              () -> {
                var outcome =
                    index.search(
                        SearchRequest.keyword(
                            tenant, c.path("text").asText(), filter(c.path("filter")), 50),
                        CancellationToken.create());
                List<String> ids =
                    ((SearchOutcome.Success) outcome)
                        .hits().stream().map(ScoredDocument::id).toList();
                var expected = new TreeSet<String>();
                c.path("expected").forEach(e -> expected.add(e.asText()));
                assertEquals(expected, new TreeSet<>(ids), c.path("why").asText());
                if (c.has("expectedTop"))
                  assertEquals(c.path("expectedTop").asText(), ids.getFirst());
              }));
    return tests.stream();
  }
}
