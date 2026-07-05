package io.qwenbridge.analysis.cache.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AIAnalysisCacheTraceHolderTest {

  private final AIAnalysisCacheTraceHolder holder = new AIAnalysisCacheTraceHolder();

  @Test
  void shouldReturnDisabledTraceWhenEmpty() {
    assertThat(holder.get().enabled()).isFalse();
  }

  @Test
  void shouldStoreAndClearTrace() {
    holder.set(AIAnalysisCacheTrace.hit("key", "ollama", "qwen2.5", "v4"));

    assertThat(holder.get().hit()).isTrue();

    holder.clear();

    assertThat(holder.get().enabled()).isFalse();
  }
}
