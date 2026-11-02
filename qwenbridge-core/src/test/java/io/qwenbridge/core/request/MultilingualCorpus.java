package io.qwenbridge.core.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the shared multilingual corpus from test resources. */
final class MultilingualCorpus {

  record Case(
      String id,
      Integer contractVersion,
      String query,
      String declaredLanguage,
      String responseLanguage,
      String locale,
      Map<String, String> context,
      List<String> domainHints,
      String expect,
      String direction) {

    RawRequest toRaw() {
      return new RawRequest(
          contractVersion,
          null,
          query,
          declaredLanguage,
          responseLanguage,
          locale,
          context,
          domainHints);
    }

    boolean expectValid() {
      return "VALID".equals(expect);
    }

    @Override
    public String toString() {
      return id;
    }
  }

  private MultilingualCorpus() {}

  static List<Case> load() {
    try (InputStream in =
        MultilingualCorpus.class.getResourceAsStream("/corpus/multilingual-input-corpus.json")) {
      if (in == null) {
        throw new IllegalStateException("corpus resource missing");
      }
      JsonNode root = new ObjectMapper().readTree(in);
      List<Case> cases = new ArrayList<>();
      for (JsonNode node : root.get("cases")) {
        cases.add(
            new Case(
                node.get("id").asText(),
                node.has("contractVersion") ? node.get("contractVersion").asInt() : null,
                text(node, "query"),
                text(node, "declaredLanguage"),
                text(node, "responseLanguage"),
                text(node, "locale"),
                map(node.get("context")),
                list(node.get("domainHints")),
                node.get("expect").asText(),
                text(node, "direction")));
      }
      return List.copyOf(cases);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String text(JsonNode node, String field) {
    return node.has(field) ? node.get(field).asText() : null;
  }

  private static Map<String, String> map(JsonNode node) {
    if (node == null) {
      return null;
    }
    Map<String, String> map = new LinkedHashMap<>();
    node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
    return map;
  }

  private static List<String> list(JsonNode node) {
    if (node == null) {
      return null;
    }
    List<String> list = new ArrayList<>();
    node.forEach(n -> list.add(n.asText()));
    return list;
  }
}
