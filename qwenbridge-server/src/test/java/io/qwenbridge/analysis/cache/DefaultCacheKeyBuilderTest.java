package io.qwenbridge.analysis.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultCacheKeyBuilderTest {

  private final DefaultCacheKeyBuilder builder = new DefaultCacheKeyBuilder();

  @Test
  void shouldBuildStableKeyForSameInput() {
    CacheKey first = builder.build("best ergonomic chair", "ollama", "qwen2.5", "v4");

    CacheKey second = builder.build("best ergonomic chair", "ollama", "qwen2.5", "v4");

    assertThat(first).isEqualTo(second);
    assertThat(first.value()).hasSize(64);
  }

  @Test
  void shouldIncludeModelProviderAndVersionInKey() {
    CacheKey first = builder.build("desk", "ollama", "qwen2.5", "v4");
    CacheKey second = builder.build("desk", "ollama", "qwen3", "v4");
    CacheKey third = builder.build("desk", "openai", "qwen2.5", "v4");
    CacheKey fourth = builder.build("desk", "ollama", "qwen2.5", "v5");

    assertThat(first).isNotEqualTo(second);
    assertThat(first).isNotEqualTo(third);
    assertThat(first).isNotEqualTo(fourth);
  }

  @Test
  void shouldNormalizeWhitespaceAndCaseForKeyInput() {
    CacheKey first = builder.build(" Desk ", " Ollama ", " Qwen2.5 ", " V4 ");
    CacheKey second = builder.build("desk", "ollama", "qwen2.5", "v4");

    assertThat(first).isEqualTo(second);
  }
}
