package io.qwenbridge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-148: Commerce is separate from the universal parts.
 *
 * <ul>
 *   <li>The Core module never refers to Commerce, its catalog, its pack or the connectors.
 *   <li>In the server, only the Commerce layer (commerce, catalog, connectors) and the listed
 *       composition points may refer to them. The list is exact: a new dependency fails this test,
 *       and so does removing one without updating the list.
 * </ul>
 */
class CommerceSeparationTest {
  static final Pattern COMMERCE =
      Pattern.compile("io\\.qwenbridge\\.(commerce|connectors|catalog|packs)\\.");

  /** Remaining server couplings, each with the work that removes it. */
  static final Map<String, String> ALLOWED =
      Map.of(
          "analysis/parser/CompactAnalysisParser.java",
          "model-output parser canonicalizes Commerce fields; moves with the pack vocabulary SPI"
              + " (QB-053)",
          "execution/provider/model/SearchRequestFactory.java",
          "v1 pipeline compiles a Commerce plan; removed when v1 runs on the Core planner",
          "execution/provider/opensearch/query/OpenSearchQueryFactory.java",
          "connector layer: the OpenSearch provider compiles the Commerce plan (allowed)",
          "platform/DomainExecution.java",
          "domain platform uses the generic Schema/Plan types still in CommerceContracts",
          "platform/ManagedPacks.java",
          "composition root: registers the bundled Commerce and Library packs (allowed)");

  static TreeSet<String> referencing(Path root, boolean skipCommerceLayer) throws IOException {
    var found = new TreeSet<String>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (skipCommerceLayer
            && (relative.startsWith("commerce/")
                || relative.startsWith("connectors/")
                || relative.startsWith("catalog/"))) continue;
        if (COMMERCE.matcher(Files.readString(file)).find()) found.add(relative);
      }
    }
    return found;
  }

  @Test
  void coreNeverRefersToCommerce() throws IOException {
    Path core = Path.of("..", "qwenbridge-core", "src", "main", "java");
    assertThat(Files.isDirectory(core)).isTrue();
    assertThat(referencing(core, false)).isEmpty();
  }

  @Test
  void onlyTheCommerceLayerAndListedCompositionPointsReferToCommerce() throws IOException {
    Path server = Path.of("src", "main", "java", "io", "qwenbridge");
    assertThat(referencing(server, true)).containsExactlyInAnyOrderElementsOf(ALLOWED.keySet());
  }

  @Test
  void securityPlatformAndPipelineCodeIsCommerceFree() throws IOException {
    Path server = Path.of("src", "main", "java", "io", "qwenbridge");
    for (String generic :
        java.util.List.of("security", "pipeline", "plugins", "policy", "intelligence"))
      if (Files.isDirectory(server.resolve(generic)))
        assertThat(referencing(server.resolve(generic), false)).as(generic).isEmpty();
  }
}
