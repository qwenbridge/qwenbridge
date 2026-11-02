package io.qwenbridge.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.exception.ApiError;
import io.qwenbridge.exception.ApiExceptionMappers;
import io.qwenbridge.operations.logging.RedactingLogFilter;
import java.util.logging.Level;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

/** QB-061, QB-062: error bodies and log lines never expose secrets or internals. */
class ErrorAndLogSafetyTest {

  @Test
  void validationMessagesAreSanitizedBeforeTheyReachTheClient() {
    var response =
        new ApiExceptionMappers.IllegalArgumentMapper()
            .toResponse(
                new IllegalArgumentException(
                    "No enum constant io.qwenbridge.analysis.model.Grounded.Operator.X\r\n"
                        + "password=hunter2"));
    ApiError error = (ApiError) response.getEntity();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(error.message())
        .doesNotContain("io.qwenbridge")
        .doesNotContain("hunter2")
        .doesNotContain("\n")
        .contains("[internal]", "password=[REDACTED]");
  }

  @Test
  void upstreamProviderDetailsAreNeverReturned() {
    var response =
        new ApiExceptionMappers.AIExceptionMapper()
            .toResponse(new AIException("POST http://ollama.internal:11434/api/chat failed: 500"));
    ApiError error = (ApiError) response.getEntity();
    assertThat(response.getStatus()).isEqualTo(502);
    assertThat(error.message()).isEqualTo("AI provider failure");
  }

  @Test
  void unexpectedErrorsHaveNoStackTraceOrMessage() {
    var response =
        new ApiExceptionMappers.UnexpectedExceptionMapper()
            .toResponse(new IllegalStateException("db password=hunter2 at Foo.java:12"));
    ApiError error = (ApiError) response.getEntity();
    assertThat(error.message()).isEqualTo("Unexpected server error");
  }

  @Test
  void logMessagesAreRedactedAndCannotForgeLines() {
    ExtLogRecord record =
        new ExtLogRecord(
            Level.INFO, "calling %s with token=%s", ExtLogRecord.FormatStyle.PRINTF, "x");
    record.setParameters(new Object[] {"https://bob:secret@example.test/x", "abc123\nFAKE ENTRY"});

    assertThat(new RedactingLogFilter().isLoggable(record)).isTrue();

    assertThat(record.getFormattedMessage())
        .isEqualTo("calling https://[REDACTED]@example.test/x with token=[REDACTED]\\nFAKE ENTRY")
        .doesNotContain("secret")
        .doesNotContain("abc123");
  }
}
