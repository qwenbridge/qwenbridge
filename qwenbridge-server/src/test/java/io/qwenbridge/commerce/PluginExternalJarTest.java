package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.plugins.PluginRegistry;
import io.qwenbridge.security.ApiIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginExternalJarTest {
  @TempDir Path directory;

  @Test
  void loadsAnIndependentlyCompiledJarAndEnforcesItsManifest() throws Exception {
    Path source = directory.resolve("ExamplePlugin.java");
    Files.writeString(
        source,
        """
        import io.qwenbridge.plugin.QwenBridgePlugin;
        import java.util.*;
        public class ExamplePlugin implements QwenBridgePlugin {
          public Manifest manifest(){ return new Manifest("example","1.0.0",1,Set.of("rerank")); }
          public Map<String,Object> invoke(String capability,Map<String,Object> input){return Map.of("ids",List.of("b","a"));}
        }
        """);
    String sdk =
        Path.of(
                io.qwenbridge.plugin.QwenBridgePlugin.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    int result =
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
                directory.toString(),
                source.toString());
    assertThat(result).isZero();
    try (var jar = new JarOutputStream(Files.newOutputStream(directory.resolve("example.jar")))) {
      jar.putNextEntry(new JarEntry("ExamplePlugin.class"));
      jar.write(Files.readAllBytes(directory.resolve("ExamplePlugin.class")));
      jar.closeEntry();
      jar.putNextEntry(new JarEntry("META-INF/services/io.qwenbridge.plugin.QwenBridgePlugin"));
      jar.write("ExamplePlugin\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      jar.closeEntry();
    }
    var registry = new PluginRegistry(directory.toString());
    try {
      assertThat(registry.manifests()).hasSize(1);
      var caller = new ApiIdentity.Principal("alice", "acme", java.util.Set.of("search"));
      assertThat(registry.invoke(caller, "search", "example", "rerank", Map.of()).get("ids"))
          .isEqualTo(java.util.List.of("b", "a"));
      assertThatThrownBy(
              () -> registry.invoke(caller, "search", "example", "catalog:write", Map.of()))
          .hasMessageContaining("unavailable");
      // QB-076: a plugin cannot be used to bypass the central policy.
      var noSearch = new ApiIdentity.Principal("bob", "acme", java.util.Set.of("catalog:write"));
      assertThatThrownBy(() -> registry.invoke(noSearch, "search", "example", "rerank", Map.of()))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    } finally {
      registry.close();
    }
  }
}
