package io.qwenbridge.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationContextTest {

  private static final Supplier<String> FIXED = () -> "generated-1";

  @Test
  void validClientIdsArePreserved() {
    CorrelationContext context = CorrelationContext.resolve(" client-req.1 ", "flow:42", FIXED);
    assertEquals("client-req.1", context.requestId());
    assertEquals("flow:42", context.correlationId());
    assertFalse(context.requestIdGenerated());
    assertFalse(context.requestIdReplaced());
  }

  @Test
  void missingIdsAreGeneratedAndCorrelationDefaultsToRequestId() {
    CorrelationContext context = CorrelationContext.resolve(null, "  ", FIXED);
    assertEquals("generated-1", context.requestId());
    assertEquals("generated-1", context.correlationId());
    assertTrue(context.requestIdGenerated());
    assertFalse(context.requestIdReplaced());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"has space", "line\nbreak", "<script>", "میز", "-leading-dash", "x\u0000y"})
  void unsafeClientIdsAreReplacedNotEchoed(String unsafe) {
    CorrelationContext context = CorrelationContext.resolve(unsafe, unsafe, FIXED);
    assertEquals("generated-1", context.requestId());
    assertEquals("generated-1", context.correlationId());
    assertTrue(context.requestIdReplaced());
  }

  @Test
  void overlongIdsAreReplaced() {
    String tooLong = "a".repeat(RequestIds.MAX_LENGTH + 1);
    assertTrue(RequestIds.isValid("a".repeat(RequestIds.MAX_LENGTH)));
    assertEquals("generated-1", CorrelationContext.resolve(tooLong, null, FIXED).requestId());
  }

  @Test
  void generatorMustProduceValidIds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CorrelationContext.resolve(null, null, () -> "bad id"));
  }
}
