package io.qwenbridge.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.plugin.PluginManifest;
import io.qwenbridge.plugin.PluginSdk;
import io.qwenbridge.plugin.VersionRange;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * Reads {@value PluginSdk#MANIFEST_ENTRY} from a plugin jar as data (QB-228). No class in the jar
 * is loaded, so validation never runs plugin code.
 */
public final class PluginManifests {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_BYTES = 64 * 1024;

  private PluginManifests() {}

  /** Empty when the jar carries no Plugin SDK manifest (for example a legacy plugin jar). */
  public static Optional<PluginManifest> read(Path jar) throws IOException {
    try (var file = new JarFile(jar.toFile(), false)) {
      var entry = file.getJarEntry(PluginSdk.MANIFEST_ENTRY);
      if (entry == null) return Optional.empty();
      if (entry.getSize() > MAX_BYTES)
        throw new IllegalArgumentException("Plugin manifest is too large");
      try (var in = file.getInputStream(entry)) {
        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES)
          throw new IllegalArgumentException("Plugin manifest is too large");
        return Optional.of(parse(JSON.readTree(bytes)));
      }
    }
  }

  public static PluginManifest parse(JsonNode root) {
    if (root == null || !root.isObject())
      throw new IllegalArgumentException("Plugin manifest must be a JSON object");
    if (root.path("manifestVersion").asInt(0) != 1)
      throw new IllegalArgumentException("Unsupported plugin manifestVersion");
    var deps = new ArrayList<PackManifest.Dependency>();
    for (JsonNode d : root.path("dependencies"))
      deps.add(
          new PackManifest.Dependency(
              text(d, "id"),
              text(d, "minimum"),
              d.path("maximumExclusive").isTextual() ? d.get("maximumExclusive").asText() : null));
    var tools = new HashSet<String>();
    root.path("tools").forEach(t -> tools.add(t.asText()));
    var agents = new HashSet<String>();
    root.path("agents").forEach(a -> agents.add(a.asText()));
    return new PluginManifest(
        text(root, "id"),
        text(root, "publisher"),
        text(root, "version"),
        range(root.path("sdk")),
        range(root.path("runtime")),
        text(root, "entryPoint"),
        deps,
        tools,
        agents);
  }

  private static VersionRange range(JsonNode node) {
    if (!node.isObject()) throw new IllegalArgumentException("Version range required");
    return new VersionRange(
        text(node, "minimum"),
        node.path("maximumExclusive").isTextual() ? node.get("maximumExclusive").asText() : null);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual())
      throw new IllegalArgumentException("Plugin manifest field '" + field + "' is required");
    return value.asText();
  }
}
