package io.qwenbridge.plugin;

/**
 * The Plugin SDK 1.0 contract (QB-225). A plugin contributes capabilities only through the
 * registrar it receives in {@link #start}: tools and agents it declared in its manifest. It never
 * receives Core or server objects.
 *
 * <p>Plugins are trusted, administrator-installed code that runs in the server's JVM. There is no
 * sandbox for untrusted code (see docs/security/plugin-isolation.md).
 */
public interface Plugin {

  /** Registers this plugin's tools and agents. Runs once per activation, with a time limit. */
  void start(PluginContext context) throws Exception;

  /** Releases resources. Registrations are removed by the host whether or not this succeeds. */
  default void stop() {}
}
