package io.qwenbridge.extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.agent.AgentDefinition;
import io.qwenbridge.agent.ToolDefinition;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.plugin.Plugin;
import io.qwenbridge.plugin.PluginManifest;
import io.qwenbridge.plugin.PluginSdk;
import io.qwenbridge.plugin.VersionRange;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** QB-225, QB-228 – QB-230: Plugin SDK 1.0 discovery, validation and lifecycle. */
class PluginHostTest {
  @TempDir Path directory;

  static final VersionRange SDK = new VersionRange("1.0.0", "2.0.0");
  static final VersionRange RUNTIME = new VersionRange("0.9.0", "2.0.0");

  static PluginManifest manifest(
      String id,
      Set<String> tools,
      Set<String> agents,
      List<PackManifest.Dependency> deps,
      String version) {
    return new PluginManifest(id, "acme", version, SDK, RUNTIME, "x.Entry", deps, tools, agents);
  }

  static ToolDefinition read(String id) {
    return new ToolDefinition(
        id, "search", ToolDefinition.Effect.READ, "index", "", Duration.ofSeconds(1));
  }

  /** Compiles {@code source} into a jar with the given manifest JSON. */
  Path jar(String name, String className, String source, String manifestJson) throws Exception {
    Path work = Files.createDirectories(directory.resolve("src-" + name));
    Path file = work.resolve(className + ".java");
    Files.writeString(file, source);
    String sdk =
        Path.of(Plugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
    assertThat(
            ToolProvider.getSystemJavaCompiler()
                .run(
                    null,
                    null,
                    null,
                    "--release",
                    "25",
                    "-cp",
                    sdk,
                    "-d",
                    work.toString(),
                    file.toString()))
        .isZero();
    Path jar = directory.resolve(name + ".jar");
    try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
      for (Path cls : Files.list(work).filter(p -> p.toString().endsWith(".class")).toList()) {
        out.putNextEntry(new JarEntry(cls.getFileName().toString()));
        out.write(Files.readAllBytes(cls));
        out.closeEntry();
      }
      out.putNextEntry(new JarEntry(PluginSdk.MANIFEST_ENTRY));
      out.write(manifestJson.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return jar;
  }

  static String json(String id, String entry, String sdkMin, String tools) {
    return json(id, entry, sdkMin, "2.0.0", tools);
  }

  static String json(String id, String entry, String sdkMin, String sdkMax, String tools) {
    return """
    {"manifestVersion":1,"id":"%s","publisher":"acme","version":"1.2.0","entryPoint":"%s",
     "sdk":{"minimum":"%s","maximumExclusive":"%s"},"runtime":{"minimum":"0.9.0","maximumExclusive":"2.0.0"},
     "tools":[%s],"agents":[]}\
    """
        .formatted(id, entry, sdkMin, sdkMax, tools);
  }

  static final String ECHO =
      """
      import io.qwenbridge.plugin.*; import io.qwenbridge.agent.*; import java.util.*; import java.time.Duration;
      public class Echo implements Plugin {
        public void start(PluginContext c) {
          c.registrar().tool(new ToolDefinition("demo.echo", "search", ToolDefinition.Effect.READ, "index", "echo", Duration.ofSeconds(1)), call -> Map.of("echo", call.input()));
        }
      }\
      """;

  @Test
  void aCompiledPluginJarIsDiscoveredValidatedAndStarted() throws Exception {
    jar("echo", "Echo", ECHO, json("demo", "Echo", "1.0.0", "\"demo.echo\""));
    var registry = new ExtensionRegistry();
    var host = new PluginHost(registry, "0.9.1", Duration.ofSeconds(5));
    host.discover(directory.toString());
    host.activateAll();
    assertThat(host.statuses())
        .extracting(PluginHost.Status::id, PluginHost.Status::state)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("demo", PluginHost.State.STARTED));
    assertThat(registry.tool("demo.echo")).isPresent();
    host.close();
    assertThat(registry.tool("demo.echo")).isEmpty();
  }

  @Test
  void manifestsAreValidatedWithoutRunningPluginCode() throws Exception {
    String trap =
        """
        import io.qwenbridge.plugin.*;
        public class Trap implements Plugin {
          static { System.setProperty("qwenbridge.test.trap", "executed"); }
          public void start(PluginContext c) {}
        }\
        """;
    System.clearProperty("qwenbridge.test.trap");
    // Requires SDK 2.x: incompatible with this host (SDK 1.0.0).
    jar("trap", "Trap", trap, json("trap", "Trap", "2.0.0", "3.0.0", "\"trap.tool\""));
    Files.writeString(directory.resolve("broken.jar"), "not a jar");
    var registry = new ExtensionRegistry();
    var host = new PluginHost(registry, "0.9.1", Duration.ofSeconds(5));
    host.discover(directory.toString());
    host.activateAll();
    var states = host.statuses();
    assertThat(states)
        .anySatisfy(
            s -> {
              assertThat(s.id()).isEqualTo("trap");
              assertThat(s.state()).isEqualTo(PluginHost.State.REJECTED);
              assertThat(s.reason()).contains("incompatible");
            });
    assertThat(states).anySatisfy(s -> assertThat(s.reason()).contains("invalid manifest"));
    assertThat(System.getProperty("qwenbridge.test.trap"))
        .as("no plugin class was loaded")
        .isNull();
  }

  @Test
  void duplicatePluginIdsAreRejectedDeterministically() throws Exception {
    jar("a-first", "Echo", ECHO, json("same", "Echo", "1.0.0", "\"demo.echo\""));
    jar("b-second", "Echo", ECHO, json("same", "Echo", "1.0.0", "\"demo.echo\""));
    var registry = new ExtensionRegistry();
    var host = new PluginHost(registry, "0.9.1", Duration.ofSeconds(5));
    host.discover(directory.toString());
    host.activateAll();
    assertThat(host.statuses())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.state()).isEqualTo(PluginHost.State.REJECTED);
              assertThat(s.reason()).contains("duplicate plugin id in 2 jars");
            });
    assertThat(registry.tools()).isEmpty();
  }

  @Test
  void onlyDeclaredCorrectlyClassifiedExtensionsRegisterAndFailuresRollBack() {
    var registry = new ExtensionRegistry();
    var host = new PluginHost(registry, "0.9.1", Duration.ofSeconds(5));
    host.install(
        manifest("undeclared", Set.of("ok.tool"), Set.of(), List.of(), "1.0.0"),
        () ->
            c -> {
              c.registrar().tool(read("ok.tool"), call -> Map.of());
              c.registrar().tool(read("secret.tool"), call -> Map.of());
            });
    host.install(
        manifest("unknown-op", Set.of("x.tool"), Set.of(), List.of(), "1.0.0"),
        () ->
            c ->
                c.registrar()
                    .tool(
                        new ToolDefinition(
                            "x.tool", "shell:exec", ToolDefinition.Effect.MUTATE, "host", "", null),
                        call -> Map.of()));
    host.install(
        manifest("misclassified", Set.of("y.tool"), Set.of(), List.of(), "1.0.0"),
        () ->
            c ->
                c.registrar()
                    .tool(
                        new ToolDefinition(
                            "y.tool",
                            "catalog:write",
                            ToolDefinition.Effect.READ,
                            "catalog",
                            "",
                            null),
                        call -> Map.of()));
    host.activateAll();
    assertThat(host.statuses())
        .allSatisfy(s -> assertThat(s.state()).isEqualTo(PluginHost.State.FAILED));
    assertThat(host.statuses())
        .extracting(PluginHost.Status::reason)
        .anySatisfy(r -> assertThat(r).contains("not declared"))
        .anySatisfy(r -> assertThat(r).contains("unknown operation"))
        .anySatisfy(r -> assertThat(r).contains("misclassifies"));
    assertThat(registry.tools()).as("a failed plugin leaves nothing registered").isEmpty();
  }

  @Test
  void lifecycleIsBoundedIdempotentAndDependencyAware() {
    var registry = new ExtensionRegistry();
    var host = new PluginHost(registry, "0.9.1", Duration.ofMillis(300));
    host.install(
        manifest("base", Set.of("base.tool"), Set.of(), List.of(), "1.4.0"),
        () -> c -> c.registrar().tool(read("base.tool"), call -> Map.of()));
    host.install(
        manifest(
            "child",
            Set.of("child.tool"),
            Set.of("child.agent"),
            List.of(new PackManifest.Dependency("base", "1.0.0", "2.0.0")),
            "1.0.0"),
        () ->
            c -> {
              c.registrar().tool(read("child.tool"), call -> Map.of());
              c.registrar()
                  .agent(
                      new AgentDefinition(
                          "child.agent", "", Set.of("child.tool"), Set.of(), 5, null),
                      (ctx, in) -> Map.of());
            });
    host.install(
        manifest(
            "too-new",
            Set.of("n.tool"),
            Set.of(),
            List.of(new PackManifest.Dependency("base", "2.0.0", null)),
            "1.0.0"),
        () -> c -> {});
    host.install(
        manifest(
            "missing",
            Set.of("m.tool"),
            Set.of(),
            List.of(new PackManifest.Dependency("nowhere", "1.0.0", null)),
            "1.0.0"),
        () -> c -> {});
    host.install(
        manifest("slow", Set.of("s.tool"), Set.of(), List.of(), "1.0.0"),
        () -> c -> Thread.sleep(5_000));
    host.activateAll();
    var states =
        host.statuses().stream()
            .collect(java.util.stream.Collectors.toMap(PluginHost.Status::id, s -> s));
    assertThat(states.get("base").state()).isEqualTo(PluginHost.State.STARTED);
    assertThat(states.get("child").state()).isEqualTo(PluginHost.State.STARTED);
    assertThat(states.get("too-new").reason()).contains("outside the required range");
    assertThat(states.get("missing").reason()).contains("missing dependency nowhere");
    assertThat(states.get("slow").reason()).contains("timed out");
    // Repeated activation does not duplicate registrations.
    host.activate("child");
    host.activateAll();
    assertThat(registry.tools())
        .extracting(ToolDefinition::id)
        .containsExactlyInAnyOrder("base.tool", "child.tool");
    // Stop removes registrations; a restart registers them again exactly once.
    host.stop("child");
    assertThat(registry.agent("child.agent")).isEmpty();
    assertThat(host.activate("child").state()).isEqualTo(PluginHost.State.STARTED);
    assertThat(registry.tools())
        .extracting(ToolDefinition::id)
        .containsExactlyInAnyOrder("base.tool", "child.tool");
    assertThatThrownBy(
            () ->
                host.install(
                    manifest("base", Set.of("z"), Set.of(), List.of(), "1.0.0"), () -> c -> {}))
        .hasMessageContaining("already installed");
  }

  @Test
  void manifestRulesAreEnforcedBeforeActivation() {
    assertThatThrownBy(() -> new VersionRange("2.0.0", "1.0.0"))
        .hasMessageContaining("Empty version range");
    assertThatThrownBy(() -> manifest("p", Set.of(), Set.of(), List.of(), "1.0.0"))
        .hasMessageContaining("at least one tool or agent");
    assertThatThrownBy(() -> manifest("p", Set.of("Bad Tool"), Set.of(), List.of(), "1.0.0"))
        .hasMessageContaining("Invalid extension identifier");
    assertThatThrownBy(
            () ->
                manifest(
                    "p",
                    Set.of("t"),
                    Set.of(),
                    List.of(new PackManifest.Dependency("p", "1.0.0", null)),
                    "1.0.0"))
        .hasMessageContaining("dependencies");
    assertThatThrownBy(
            () ->
                new PluginManifest(
                    "p", "acme", "1.0", SDK, RUNTIME, "x.E", null, Set.of("t"), null))
        .hasMessageContaining("semantic version");
    assertThat(
            manifest("p", Set.of("t"), Set.of(), List.of(), "1.0.0")
                .compatibleWith("1.0.0", "0.9.1"))
        .isTrue();
    assertThat(
            manifest("p", Set.of("t"), Set.of(), List.of(), "1.0.0")
                .compatibleWith("2.0.0", "0.9.1"))
        .isFalse();
  }
}
