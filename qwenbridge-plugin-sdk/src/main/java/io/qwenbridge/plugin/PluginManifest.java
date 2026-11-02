package io.qwenbridge.plugin;

import io.qwenbridge.pack.PackManifest;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A plugin's declaration (QB-228): identity, publisher, version, the SDK and QwenBridge runtime
 * versions it supports, its entry point, dependencies on other plugins, and every tool and agent it
 * may register. It is read from {@value PluginSdk#MANIFEST_ENTRY} and validated before any plugin
 * class is loaded; a plugin can register only what it declares here.
 */
public record PluginManifest(
    String id,
    String publisher,
    String version,
    VersionRange sdk,
    VersionRange runtime,
    String entryPoint,
    List<PackManifest.Dependency> dependencies,
    Set<String> tools,
    Set<String> agents) {

  public PluginManifest {
    PackManifest.validId(id);
    PackManifest.validId(publisher);
    PackManifest.versionParts(version);
    Objects.requireNonNull(sdk, "sdk range is required");
    Objects.requireNonNull(runtime, "runtime range is required");
    if (entryPoint == null
        || !entryPoint.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
      throw new IllegalArgumentException("Invalid plugin entry point");
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    tools = tools == null ? Set.of() : Set.copyOf(tools);
    agents = agents == null ? Set.of() : Set.copyOf(agents);
    for (String name : tools) validExtensionId(name);
    for (String name : agents) validExtensionId(name);
    if (tools.isEmpty() && agents.isEmpty())
      throw new IllegalArgumentException("A plugin must declare at least one tool or agent");
    if (dependencies.stream().anyMatch(d -> d.id().equals(id))
        || dependencies.stream().map(PackManifest.Dependency::id).distinct().count()
            != dependencies.size())
      throw new IllegalArgumentException("Invalid plugin dependencies");
  }

  /** Tool and agent ids: lower-case, dotted, at most 64 characters. */
  public static void validExtensionId(String id) {
    if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}"))
      throw new IllegalArgumentException("Invalid extension identifier: " + id);
  }

  /** Whether this plugin may run on the given SDK and runtime versions. */
  public boolean compatibleWith(String sdkVersion, String runtimeVersion) {
    return sdk.includes(sdkVersion) && runtime.includes(runtimeVersion);
  }
}
