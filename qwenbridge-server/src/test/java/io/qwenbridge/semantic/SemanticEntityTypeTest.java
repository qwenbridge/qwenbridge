package io.qwenbridge.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SemanticEntityTypeTest {

  @Test
  void shouldResolveValidEntityType() {
    assertThat(SemanticEntityType.from("PRODUCT")).isEqualTo(SemanticEntityType.PRODUCT);
    assertThat(SemanticEntityType.from("category")).isEqualTo(SemanticEntityType.CATEGORY);
  }

  @Test
  void shouldFallbackToUnknownForInvalidEntityType() {
    assertThat(SemanticEntityType.from("PRODUCT|CATEGORY")).isEqualTo(SemanticEntityType.UNKNOWN);
    assertThat(SemanticEntityType.from("INVALID")).isEqualTo(SemanticEntityType.UNKNOWN);
    assertThat(SemanticEntityType.from(null)).isEqualTo(SemanticEntityType.UNKNOWN);
    assertThat(SemanticEntityType.from(" ")).isEqualTo(SemanticEntityType.UNKNOWN);
  }
}
