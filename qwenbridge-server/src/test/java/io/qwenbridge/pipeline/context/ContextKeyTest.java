package io.qwenbridge.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ContextKeyTest {

  @Test
  void shouldCreateContextKey() {
    ContextKey<TenantContext> key = ContextKey.of("tenant", TenantContext.class);

    assertThat(key.name()).isEqualTo("tenant");
    assertThat(key.type()).isEqualTo(TenantContext.class);
  }

  @Test
  void shouldCompareKeysByName() {
    ContextKey<TenantContext> first = ContextKey.of("tenant", TenantContext.class);
    ContextKey<LocaleContext> second = ContextKey.of("tenant", LocaleContext.class);

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void shouldRejectNullName() {
    assertThatNullPointerException()
        .isThrownBy(() -> ContextKey.of(null, TenantContext.class))
        .withMessage("name must not be null");
  }

  @Test
  void shouldRejectNullType() {
    assertThatNullPointerException()
        .isThrownBy(() -> ContextKey.of("tenant", null))
        .withMessage("type must not be null");
  }

  @Test
  void shouldRenderReadableToString() {
    ContextKey<TenantContext> key = ContextKey.of("tenant", TenantContext.class);

    assertThat(key.toString()).isEqualTo("ContextKey[name=tenant, type=TenantContext]");
  }
}
