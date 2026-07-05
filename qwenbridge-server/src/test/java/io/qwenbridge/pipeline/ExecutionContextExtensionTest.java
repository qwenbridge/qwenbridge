package io.qwenbridge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.pipeline.context.ContextKey;
import io.qwenbridge.pipeline.context.ContextKeys;
import io.qwenbridge.pipeline.context.ExecutionHints;
import io.qwenbridge.pipeline.context.LocaleContext;
import io.qwenbridge.pipeline.context.TenantContext;
import io.qwenbridge.pipeline.result.LanguageResult;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ExecutionContextExtensionTest {

  @Test
  void shouldKeepClassBasedStateCompatibility() {
    ExecutionContext context = new ExecutionContext("iphone");

    LanguageResult result = context.get(LanguageResult.class);

    assertThat(result).isNotNull();
  }

  @Test
  void shouldStoreAndGetTenantByContextKey() {
    ExecutionContext context = new ExecutionContext("iphone");
    TenantContext tenant = new TenantContext("tenant-a", "test", "qwenbridge");

    context.store(ContextKeys.TENANT, tenant);

    assertThat(context.get(ContextKeys.TENANT)).isEqualTo(tenant);
  }

  @Test
  void shouldStoreAndGetLocaleByContextKey() {
    ExecutionContext context = new ExecutionContext("iphone");
    LocaleContext locale = new LocaleContext(Locale.ENGLISH, "SE", "SEK");

    context.store(ContextKeys.LOCALE, locale);

    assertThat(context.get(ContextKeys.LOCALE)).isEqualTo(locale);
  }

  @Test
  void shouldStoreAndGetExecutionHintsByContextKey() {
    ExecutionContext context = new ExecutionContext("iphone");
    ExecutionHints hints = new ExecutionHints("inmemory", Duration.ofSeconds(2), true, false);

    context.store(ContextKeys.EXECUTION_HINTS, hints);

    assertThat(context.get(ContextKeys.EXECUTION_HINTS)).isEqualTo(hints);
  }

  @Test
  void shouldOverwriteExtensionValue() {
    ExecutionContext context = new ExecutionContext("iphone");

    TenantContext first = new TenantContext("tenant-a", "test", "qwenbridge");
    TenantContext second = new TenantContext("tenant-b", "prod", "qwenbridge");

    context.store(ContextKeys.TENANT, first);
    context.store(ContextKeys.TENANT, second);

    assertThat(context.get(ContextKeys.TENANT)).isEqualTo(second);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void shouldRejectValueWithWrongRuntimeType() {
    ExecutionContext context = new ExecutionContext("iphone");
    ContextKey rawKey = ContextKeys.TENANT;

    assertThatThrownBy(() -> context.store(rawKey, new LocaleContext(Locale.ENGLISH, "SE", "SEK")))
        .isInstanceOf(ClassCastException.class);
  }
}
