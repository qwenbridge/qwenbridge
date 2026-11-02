package io.qwenbridge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-178, QB-287: no module depends on a database or search-engine driver. The Core never did; the
 * connector modules that owned the PostgreSQL and MongoDB drivers were removed in QB-287.
 */
class DriverDependencyTest {
  static final Path ROOT = Path.of("..");
  static final Pattern DRIVER_ARTIFACT =
      Pattern.compile(
          "<artifactId>(postgresql|mongodb-driver-sync|mongodb-driver-core|bson|elasticsearch-java|opensearch-java)</artifactId>");
  static final Pattern DRIVER_IMPORT =
      Pattern.compile(
          "import (static"
              + " )?(org\\.postgresql|com\\.mongodb|org\\.bson|co\\.elastic|org\\.opensearch|java\\.sql)\\.");

  static List<String> artifacts(String module) throws IOException {
    var m = DRIVER_ARTIFACT.matcher(Files.readString(ROOT.resolve(module).resolve("pom.xml")));
    var out = new java.util.ArrayList<String>();
    while (m.find()) out.add(m.group(1));
    return out;
  }

  @Test
  void coreDeclaresAndImportsNoDriver() throws IOException {
    assertThat(artifacts("qwenbridge-core")).isEmpty();
    try (Stream<Path> files = Files.walk(ROOT.resolve("qwenbridge-core/src/main/java"))) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList())
        assertThat(DRIVER_IMPORT.matcher(Files.readString(f)).find()).as(f.toString()).isFalse();
    }
  }

  @Test
  void noModuleDeclaresOrImportsADatabaseOrSearchEngineDriver() throws IOException {
    // QB-287: the connector modules that owned the PostgreSQL and MongoDB drivers were removed; the
    // optional OpenSearch backend speaks HTTP through the Quarkus REST client, no driver.
    for (String module :
        List.of(
            "qwenbridge-core",
            "qwenbridge-plugin-sdk",
            "qwenbridge-commerce-pack",
            "qwenbridge-library-pack",
            "qwenbridge-server",
            "qwenbridge-java-sdk")) {
      assertThat(artifacts(module)).as(module).isEmpty();
      Path main = ROOT.resolve(module).resolve("src/main/java");
      if (!Files.isDirectory(main)) continue;
      try (Stream<Path> files = Files.walk(main)) {
        for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList())
          assertThat(DRIVER_IMPORT.matcher(Files.readString(f)).find()).as(f.toString()).isFalse();
      }
    }
  }
}
