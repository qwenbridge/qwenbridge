package io.qwenbridge.analysis.cache.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AIAnalysisCacheTraceTest {

  @Test
  void shouldCreateHitTrace() {
    AIAnalysisCacheTrace trace = AIAnalysisCacheTrace.hit("key", "ollama", "qwen2.5", "v4");

    assertThat(trace.enabled()).isTrue();
    assertThat(trace.hit()).isTrue();
    assertThat(trace.miss()).isFalse();
    assertThat(trace.key()).isEqualTo("key");
    assertThat(trace.provider()).isEqualTo("ollama");
    assertThat(trace.model()).isEqualTo("qwen2.5");
    assertThat(trace.version()).isEqualTo("v4");
  }

  @Test
  void shouldCreateMissTrace() {
    AIAnalysisCacheTrace trace = AIAnalysisCacheTrace.miss("key", "ollama", "qwen2.5", "v4");

    assertThat(trace.enabled()).isTrue();
    assertThat(trace.hit()).isFalse();
    assertThat(trace.miss()).isTrue();
  }

  @Test
  void shouldCreateDisabledTrace() {
    AIAnalysisCacheTrace trace = AIAnalysisCacheTrace.disabled();

    assertThat(trace.enabled()).isFalse();
    assertThat(trace.hit()).isFalse();
    assertThat(trace.miss()).isTrue();
  }
}
