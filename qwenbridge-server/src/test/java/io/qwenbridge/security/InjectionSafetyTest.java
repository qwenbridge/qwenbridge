package io.qwenbridge.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.model.Grounded.Constraint;
import io.qwenbridge.analysis.model.Grounded.Evidence;
import io.qwenbridge.analysis.model.Grounded.Operator;
import io.qwenbridge.commerce.CommerceCompiler;
import io.qwenbridge.commerce.CommerceSchemaRegistry;
import io.qwenbridge.connectors.CommerceBackend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * QB-059: user values reach backends only as bound parameters or typed structure values, never as
 * query text, and no code path executes operating-system commands.
 */
class InjectionSafetyTest {

  static final String[] PAYLOADS = {
    "x' OR '1'='1",
    "x'); DROP TABLE catalog;--",
    "\" } , { \"match_all\": {} }",
    "{\"$where\": \"sleep(1000)\"}",
    "${jndi:ldap://x}",
    "x\\' UNION SELECT password FROM users --"
  };

  private static CommerceBackend.Query query(String text, String brand) {
    var plan =
        CommerceCompiler.validate(
            CommerceSchemaRegistry.defaults(),
            "tenant-a",
            List.of(
                new Constraint(
                    "brand",
                    Operator.EQ,
                    brand,
                    new Evidence("original", 0, 1, text.substring(0, 1)))));
    return new CommerceBackend.Query(text, plan, "KEYWORD", List.of(), 10, 0);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5})
  void searchQueriesCarryUserValuesOnlyAsLeafValues(int i) {
    // QB-287: the OpenSearch filter translation (moved from the removed Elasticsearch/OpenSearch
    // connector module) carries user values only as typed leaves, never as keys or query text.
    String payload = PAYLOADS[i];
    var filter =
        new io.qwenbridge.core.retrieval.filter.Filter.And(
            List.of(
                new io.qwenbridge.core.retrieval.filter.Filter.Compare(
                    "brand",
                    io.qwenbridge.core.retrieval.filter.Filter.Compare.Op.EQ,
                    new io.qwenbridge.core.retrieval.filter.FieldValue.Text(payload)),
                new io.qwenbridge.core.retrieval.filter.Filter.Contains("title", payload)));
    var tree =
        new ObjectMapper()
            .valueToTree(
                io.qwenbridge.connectors.NeutralFilterTranslator.openSearch("tenant-a", filter));
    List<String> keys = new ArrayList<>();
    collectKeys(tree, keys);
    assertThat(keys).doesNotContain(payload).noneMatch(k -> k.startsWith("$"));
    assertThat(tree.toString()).contains("tenant-a");
  }

  private static void collectKeys(com.fasterxml.jackson.databind.JsonNode node, List<String> keys) {
    if (node.isObject()) {
      node.properties()
          .forEach(
              e -> {
                keys.add(e.getKey());
                collectKeys(e.getValue(), keys);
              });
    } else if (node.isArray()) {
      node.forEach(child -> collectKeys(child, keys));
    }
  }

  private static final Pattern PROCESS_EXECUTION =
      Pattern.compile(
          "\\bProcessBuilder\\b|Runtime\\.getRuntime\\(\\)\\s*\\.\\s*exec|\\bScriptEngineManager\\b"
              + "|\\bProcessHandle\\.of\\b");

  @Test
  void noModuleExecutesOperatingSystemCommands() throws IOException {
    Path root = Path.of("..").toAbsolutePath().normalize();
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> modules = Files.list(root)) {
      for (Path module :
          modules.filter(p -> p.getFileName().toString().startsWith("qwenbridge-")).toList()) {
        Path main = module.resolve("src/main/java");
        if (!Files.isDirectory(main)) {
          continue;
        }
        try (Stream<Path> files = Files.walk(main)) {
          for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
            if (PROCESS_EXECUTION.matcher(Files.readString(file)).find()) {
              offenders.add(root.relativize(file).toString());
            }
          }
        }
      }
    }
    assertThat(offenders).isEmpty();
  }

  @Test
  void commerceContractsRejectOverlongValuesBeforeAnyBackend() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new io.qwenbridge.analysis.model.Grounded.Constraint(
                    "brand", Operator.EQ, "x".repeat(257), new Evidence("original", 0, 1, "x")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
