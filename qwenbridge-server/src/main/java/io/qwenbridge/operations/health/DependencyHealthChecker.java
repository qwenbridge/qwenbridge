package io.qwenbridge.operations.health;

public interface DependencyHealthChecker {
  DependencyHealth check();

  /**
   * Whether the dependency is in use with the current configuration (QB-287). An optional
   * dependency that is not selected is neither probed nor reported.
   */
  default boolean active() {
    return true;
  }
}
