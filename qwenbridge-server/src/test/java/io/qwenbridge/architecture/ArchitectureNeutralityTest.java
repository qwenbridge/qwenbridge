package io.qwenbridge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.platform.PlatformStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * QB-293 regression guards: QwenBridge stays a domain-neutral engine with no mandatory database or
 * search engine. PostgreSQL, MongoDB, Elasticsearch and OpenSearch are optional connectors only; no
 * connector module and no Shopify code exist (QB-287); Spring Boot does not come back.
 */
class ArchitectureNeutralityTest {

  static final Path ROOT = Path.of("..");
  static final Pattern DATA_TECH = Pattern.compile("(?i)postgres|mongo|elasticsearch|opensearch");
  static final List<String> RUNTIME_MODULES =
      List.of(
          "qwenbridge-core",
          "qwenbridge-plugin-sdk",
          "qwenbridge-commerce-pack",
          "qwenbridge-library-pack",
          "qwenbridge-server");

  static List<Path> sources(String module) throws IOException {
    Path main = ROOT.resolve(module).resolve("src/main/java");
    if (!Files.isDirectory(main)) return List.of();
    try (Stream<Path> files = Files.walk(main)) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  static String pom(String module) throws IOException {
    return Files.readString(ROOT.resolve(module).resolve("pom.xml"));
  }

  @Test
  void coreHasNoFrameworkDriverOrNetworkServiceDependency() throws IOException {
    var artifacts = new ArrayList<String>();
    var m =
        Pattern.compile(
                "<artifactId>([^<]+)</artifactId>(?:\\s*<version>[^<]*</version>)?(\\s*<scope>([^<]+)</scope>)?")
            .matcher(pom("qwenbridge-core"));
    while (m.find())
      if (m.group(3) == null || !m.group(3).equals("test")) artifacts.add(m.group(1));
    // The parent reference and the module itself, then exactly one runtime dependency: Jackson.
    assertThat(artifacts)
        .containsExactly("qwenbridge-parent", "qwenbridge-core", "jackson-databind");
    // Assembled from parts so this guard does not itself match the Quarkus-only source scan.
    String other = "spr" + "ing";
    var forbidden =
        Pattern.compile(
            "import (static )?(io\\.quarkus|jakarta\\.|javax\\.persistence|org\\.hibernate|org\\."
                + other
                + "framework"
                + "|org\\.postgresql|com\\.mongodb|org\\.bson|co\\.elastic|org\\.opensearch|java\\.sql"
                + "|java\\.net\\.http|io\\.qwenbridge\\.(server|commerce|connectors|connector|platform))");
    for (Path f : sources("qwenbridge-core"))
      assertThat(forbidden.matcher(Files.readString(f)).find()).as(f.toString()).isFalse();
  }

  @Test
  void noModuleMakesADatasourceOrmOrSearchClientMandatory() throws IOException {
    var mandatory =
        Pattern.compile(
            "<artifactId>(quarkus-jdbc-[a-z]+|quarkus-agroal|quarkus-hibernate-orm[a-z-]*|quarkus-mongodb[a-z-]*"
                + "|quarkus-elasticsearch[a-z-]*|quarkus-opensearch[a-z-]*|[a-z-]*panache[a-z-]*)</artifactId>");
    for (String module : RUNTIME_MODULES)
      assertThat(mandatory.matcher(pom(module)).find()).as(module).isFalse();
    assertThat(mandatory.matcher(Files.readString(ROOT.resolve("pom.xml"))).find()).isFalse();
    String config = Files.readString(Path.of("src/main/resources/application.yaml"));
    assertThat(config)
        .doesNotContainPattern("(?m)^\\s+datasource:")
        .doesNotContain("hibernate-orm");
    // Commerce search is database-free unless a connector is selected explicitly.
    assertThat(config).containsPattern("(?m)^  commerce:\\n    backend: memory$");
    assertThat(config).containsPattern("(?m)^    backend: in-memory$");
  }

  @Test
  void redisIsNeverRequiredForPlatformState() {
    jakarta.enterprise.inject.Instance<io.quarkus.redis.datasource.RedisDataSource> redis =
        org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
    var unconfigured =
        new PlatformStore(
            redis,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            Optional.empty(),
            Optional.empty(),
            4096);
    assertThat(unconfigured.adapter()).isEqualTo("memory");
    org.mockito.Mockito.verifyNoInteractions(redis);
  }

  @SuppressWarnings("unchecked")
  @Test
  void theDefaultComposeStackStartsNoDatabaseOrSearchEngine() throws IOException {
    Map<String, Object> compose =
        new Yaml().load(Files.readString(ROOT.resolve("docker-compose.yml")));
    var services = (Map<String, Map<String, Object>>) compose.get("services");
    var optional = new java.util.HashSet<String>();
    services.forEach(
        (name, service) -> {
          String image = String.valueOf(service.getOrDefault("image", ""));
          boolean profiled = service.containsKey("profiles");
          if (DATA_TECH.matcher(image).find() || DATA_TECH.matcher(name).find()) {
            assertThat(profiled).as(name + " must be in an explicit optional profile").isTrue();
            optional.add(name);
          }
        });
    // Nothing waits on an optional data service unless that profile is enabled.
    services.forEach(
        (name, service) -> {
          if (service.get("depends_on") instanceof Map<?, ?> deps)
            deps.forEach(
                (dep, condition) -> {
                  if (optional.contains(String.valueOf(dep)))
                    assertThat(((Map<String, Object>) condition).get("required"))
                        .as(name + " -> " + dep)
                        .isEqualTo(false);
                });
        });
    var app = services.get("qwenbridge-app");
    // QB-287: Redis is an optional cache; it never gates the application's startup.
    var appDeps = (Map<String, Map<String, Object>>) app.get("depends_on");
    assertThat(appDeps.get("qwenbridge-redis").get("required")).isEqualTo(false);
    var env = (Map<String, Object>) app.get("environment");
    assertThat(String.valueOf(env.get("QWENBRIDGE_COMMERCE_BACKEND"))).endsWith(":-memory}");
    assertThat(String.valueOf(env.get("QWENBRIDGE_PLATFORM_STORE"))).doesNotContain("redis");
    // No database or other search engine is a Compose service (QB-287).
    assertThat(services.keySet())
        .noneMatch(n -> n.matches("(?i).*(postgres|mongo|elasticsearch).*"));
  }

  @Test
  void noModuleContainsShopifyCodeOrDependsOnAShopifyModule() throws IOException {
    // QB-287: the Shopify connector module was removed; Shopify is only an optional external
    // end-to-end target (e2e/dual-sdk), never a dependency of any module.
    assertThat(Files.readString(ROOT.resolve("pom.xml"))).doesNotContain("shopify");
    for (String module : RUNTIME_MODULES) {
      assertThat(pom(module).toLowerCase(Locale.ROOT)).as(module).doesNotContain("shopify");
      for (Path f : sources(module))
        assertThat(Files.readString(f).toLowerCase(Locale.ROOT))
            .as(f.toString())
            .doesNotContain("shopify");
      Path services = ROOT.resolve(module).resolve("src/main/resources/META-INF/services");
      if (Files.isDirectory(services))
        try (Stream<Path> files = Files.list(services)) {
          for (Path f : files.toList())
            assertThat(Files.readString(f).toLowerCase(Locale.ROOT))
                .as(f.toString())
                .doesNotContain("shopify");
        }
    }
  }

  @Test
  void springBootIsNotReintroduced() throws IOException {
    // Assembled from parts so this guard does not itself match the Section 1 text scan.
    String framework = "spring";
    var spring = Pattern.compile("org\\." + framework + "framework|" + framework + "-boot");
    assertThat(spring.matcher(Files.readString(ROOT.resolve("pom.xml"))).find()).isFalse();
    for (String module : RUNTIME_MODULES) {
      assertThat(spring.matcher(pom(module)).find()).as(module).isFalse();
      for (Path f : sources(module))
        assertThat(spring.matcher(Files.readString(f)).find()).as(f.toString()).isFalse();
    }
  }
}
