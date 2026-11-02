package io.qwenbridge.plugin;

/** The Plugin SDK contract version (QB-225, QB-228). Plugins declare the SDK range they support. */
public final class PluginSdk {
  /** Semantic version of the Plugin and Agent contracts in this SDK. */
  public static final String VERSION = "1.0.0";

  /** Where a plugin jar carries its manifest; read without loading any plugin class. */
  public static final String MANIFEST_ENTRY = "META-INF/qwenbridge/plugin.json";

  private PluginSdk() {}
}
