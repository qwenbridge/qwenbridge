package io.qwenbridge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-208: the capability matrix (evaluation/capability-matrix.json) refers only to tests that
 * exist, and every capability has positive and negative cases, plus degradation cases or an
 * explicitly stated gap.
 */
class CapabilityMatrixTest {

  static Map<String, String> testSources() throws IOException {
    var sources = new HashMap<String, String>();
    for (String module : List.of("qwenbridge-core", "qwenbridge-server")) {
      Path root = Path.of("..", module, "src", "test", "java");
      try (Stream<Path> files = Files.walk(root)) {
        for (Path f : files.filter(p -> p.toString().endsWith("Test.java")).toList())
          sources.put(f.getFileName().toString().replace(".java", ""), Files.readString(f));
      }
    }
    return sources;
  }

  @Test
  void everyReferencedTestExistsAndEveryCapabilityIsCovered() throws IOException {
    JsonNode matrix =
        new ObjectMapper().readTree(Path.of("..", "evaluation", "capability-matrix.json").toFile());
    var sources = testSources();
    var missing = new ArrayList<String>();
    var gaps = new ArrayList<String>();
    for (JsonNode capability : matrix.path("capabilities")) {
      String id = capability.path("id").asText();
      assertThat(capability.path("positive").size()).as(id + " positive").isPositive();
      assertThat(capability.path("negative").size()).as(id + " negative").isPositive();
      assertThat(capability.path("degradation").size() + capability.path("gaps").size())
          .as(id + " degradation or stated gap")
          .isPositive();
      capability.path("gaps").forEach(g -> gaps.add(id + ": " + g.asText()));
      for (String kind : List.of("positive", "negative", "degradation"))
        for (JsonNode ref : capability.path(kind)) {
          String[] parts = ref.asText().split("#");
          String source = sources.get(parts[0]);
          // Any return type and parameters (plain, parameterized and @TestFactory tests).
          if (source == null || !source.contains(" " + parts[1] + "("))
            missing.add(id + " " + kind + ": " + ref.asText());
        }
    }
    assertThat(missing).as("matrix references to tests that do not exist").isEmpty();
    gaps.forEach(g -> System.out.println("capability gap — " + g));
  }
}
