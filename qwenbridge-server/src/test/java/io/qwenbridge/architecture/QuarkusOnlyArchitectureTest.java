package io.qwenbridge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-294 regression guards: Quarkus is the only application framework. Every module of the Maven
 * reactor (main and test sources, resources and POMs), the Dockerfile, Compose files and the
 * environment template are checked for the other framework's dependencies, annotations, startup
 * code, configuration keys and executable-jar packaging. Forbidden names are assembled from parts
 * so this guard does not match itself. The resolved dependency graph is checked by the master
 * verification step (scripts/verification/23-quarkus-only.sh).
 */
class QuarkusOnlyArchitectureTest {

  static final Path ROOT = Path.of("..");
  static final String W = "spr" + "ing";
  static final String CAP = "Spr" + "ing";

  static final Pattern FRAMEWORK =
      Pattern.compile("(?i)org\\." + W + "framework|" + W + "-boot|" + W + "framework");
  static final Pattern ANNOTATION =
      Pattern.compile(
          "(?m)^\\s*@("
              + CAP
              + "BootApplication|"
              + CAP
              + "BootTest|RestController|RequestMapping|GetMapping|PostMapping|PutMapping|"
              + "DeleteMapping|PatchMapping|Autowired|MockBean|SpyBean|WebMvcTest|"
              + "EnableAutoConfiguration|ConfigurationProperties|Component|Service|Configuration|"
              + "Bean|Controller)\\b");
  static final Pattern STARTUP = Pattern.compile("\\b" + CAP + "Application\\b");
  static final Pattern CONFIG_KEY =
      Pattern.compile("(?mi)^\\s*(" + W + "|management)\\s*[.:]|^\\s*server\\.port\\s*[=:]");
  static final Pattern ENV_KEY = Pattern.compile("\\b(" + W.toUpperCase() + "|MANAGEMENT)_[A-Z]");
  static final Pattern PACKAGING =
      Pattern.compile(
          "BOOT-INF|JarLauncher|PropertiesLauncher|" + W + "-boot-maven|<goal>\\s*repackage");

  static List<String> modules() throws IOException {
    List<String> out = new ArrayList<>();
    Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(read(ROOT.resolve("pom.xml")));
    while (m.find()) out.add(m.group(1));
    return out;
  }

  static String read(Path p) throws IOException {
    return Files.readString(p);
  }

  static List<Path> files(Path dir, String... suffixes) throws IOException {
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> s = Files.walk(dir)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> !p.toString().contains("/target/"))
          .filter(
              p -> {
                for (String x : suffixes) if (p.toString().endsWith(x)) return true;
                return false;
              })
          .toList();
    }
  }

  @Test
  void theReactorHasModulesToCheck() throws IOException {
    assertThat(modules()).contains("qwenbridge-server", "qwenbridge-core", "qwenbridge-java-sdk");
  }

  @Test
  void noModuleDependsOnOrPackagesTheOtherFramework() throws IOException {
    assertThat(FRAMEWORK.matcher(read(ROOT.resolve("pom.xml"))).find()).isFalse();
    for (String module : modules()) {
      String pom = read(ROOT.resolve(module).resolve("pom.xml"));
      assertThat(FRAMEWORK.matcher(pom).find()).as(module + " pom").isFalse();
      assertThat(PACKAGING.matcher(pom).find()).as(module + " packaging").isFalse();
    }
    String server = read(ROOT.resolve("qwenbridge-server/pom.xml"));
    assertThat(server).contains("<artifactId>quarkus-maven-plugin</artifactId>");
  }

  @Test
  void noSourceUsesItsAnnotationsStartupOrTestSupport() throws IOException {
    int checked = 0;
    for (String module : modules())
      for (Path f : files(ROOT.resolve(module).resolve("src"), ".java", ".kt")) {
        String text = read(f);
        assertThat(FRAMEWORK.matcher(text).find()).as(f.toString()).isFalse();
        assertThat(ANNOTATION.matcher(text).find()).as(f.toString()).isFalse();
        assertThat(STARTUP.matcher(text).find()).as(f.toString()).isFalse();
        checked++;
      }
    assertThat(checked).as("source files checked").isGreaterThan(500);
  }

  @Test
  void noConfigurationUsesItsKeysOrEnvironment() throws IOException {
    List<Path> config = new ArrayList<>();
    for (String module : modules())
      config.addAll(files(ROOT.resolve(module).resolve("src"), ".yaml", ".yml", ".properties"));
    config.add(ROOT.resolve(".env.example"));
    try (Stream<Path> top = Files.list(ROOT)) {
      top.filter(p -> p.getFileName().toString().startsWith("docker-compose")).forEach(config::add);
    }
    config.add(ROOT.resolve("qwenbridge-server/Dockerfile"));
    assertThat(config).anyMatch(p -> p.endsWith("application.yaml"));
    for (Path f : config) {
      String text = read(f);
      assertThat(CONFIG_KEY.matcher(text).find()).as(f.toString()).isFalse();
      assertThat(ENV_KEY.matcher(text).find()).as(f.toString()).isFalse();
      assertThat(FRAMEWORK.matcher(text).find()).as(f.toString()).isFalse();
    }
  }

  @Test
  void dockerRunsTheQuarkusFastJarOnJava25() throws IOException {
    String docker = read(ROOT.resolve("qwenbridge-server/Dockerfile"));
    assertThat(docker).contains("quarkus-run.jar").contains(":25");
    assertThat(PACKAGING.matcher(docker).find()).isFalse();
  }
}
