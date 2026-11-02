package io.qwenbridge.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the domain-neutral multilingual corpus owned by qwenbridge-core
 * (qwenbridge-core/src/test/resources/corpus/multilingual-input-corpus.json), so server tests and
 * Core tests exercise exactly the same inputs (QB-007).
 */
public final class SharedMultilingualCorpus {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** One corpus case; {@code body} is the JSON request body for the v1 analyze endpoint. */
  public record Case(String id, String query, String expect, ObjectNode body) {
    public boolean expectValid() {
      return "VALID".equals(expect);
    }

    @Override
    public String toString() {
      return id;
    }
  }

  private SharedMultilingualCorpus() {}

  public static List<Case> load() {
    Path path = locate();
    try {
      JsonNode root = JSON.readTree(Files.readAllBytes(path));
      List<Case> cases = new ArrayList<>();
      for (JsonNode node : root.get("cases")) {
        ObjectNode body = JSON.createObjectNode();
        for (String field :
            List.of(
                "contractVersion",
                "query",
                "declaredLanguage",
                "responseLanguage",
                "locale",
                "context",
                "domainHints")) {
          if (node.has(field)) {
            body.set(field, node.get(field));
          }
        }
        cases.add(
            new Case(
                node.get("id").asText(),
                node.has("query") ? node.get("query").asText() : null,
                node.get("expect").asText(),
                body));
      }
      return List.copyOf(cases);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path locate() {
    Path relative = Path.of("src/test/resources/corpus/multilingual-input-corpus.json");
    for (Path candidate :
        List.of(
            Path.of("..", "qwenbridge-core").resolve(relative),
            Path.of("qwenbridge-core").resolve(relative))) {
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Shared corpus not found; run tests from the module directory");
  }
}
