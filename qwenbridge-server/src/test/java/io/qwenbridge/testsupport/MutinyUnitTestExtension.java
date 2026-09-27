package io.qwenbridge.testsupport;

import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disables Mutiny's context-propagation interceptors for the duration of a pure unit test class.
 *
 * <p>When a {@code @QuarkusTest} runs earlier in the same JVM fork it leaves a SmallRye Context
 * Propagation provider bound to the Quarkus test classloader. A later plain unit test that
 * subscribes to a Mutiny pipeline then fails while decorating operators with {@code
 * ServiceConfigurationError: MutinyContextManagerExtension not a subtype}. These unit tests do not
 * rely on context propagation, so the interceptors are cleared before the class runs and reloaded
 * afterwards to leave the shared infrastructure untouched for other tests.
 */
public final class MutinyUnitTestExtension implements BeforeAllCallback, AfterAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) {
    Infrastructure.clearInterceptors();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    Infrastructure.reload();
  }
}
