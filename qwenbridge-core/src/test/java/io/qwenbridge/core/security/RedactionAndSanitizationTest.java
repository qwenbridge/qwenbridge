package io.qwenbridge.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.security.Sanitizer.Sink;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** QB-060, QB-061: secrets never reach logs or errors; output is safe for its sink. */
class RedactionAndSanitizationTest {

  @Nested
  class Redaction {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = {
          "bearer | Authorization: Bearer abcDEF123456789xyz | Authorization: Bearer [REDACTED]",
          "basic | auth Basic dXNlcjpwYXNzd29yZA== ok | auth Basic [REDACTED] ok",
          "jwt | t=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJlLXZhbHVl | t=[REDACTED]",
          "openai | key sk-proj-abcdefghijklmnop1234 used | key [REDACTED] used",
          "github | ghp_abcdefghijklmnopqrstuvwxyz0123 | [REDACTED]",
          "aws | AKIAIOSFODNN7EXAMPLE leaked | [REDACTED] leaked",
          "url | jdbc:postgresql://admin:hunter2@db:5432/x |"
              + " jdbc:postgresql://[REDACTED]@db:5432/x",
          "pair | password=hunter2&user=bob | password=[REDACTED]&user=bob",
          "json | {\"api_key\": \"abc123\"} | {\"api_key\": \"[REDACTED]\"}",
          "header | X-API-Key: k-123456 | X-API-Key: [REDACTED]",
          "card | card 4111 1111 1111 1111 here | card [REDACTED] here"
        })
    void secretsAreMaskedAndContextKept(String name, String input, String expected) {
      assertEquals(expected, Redactor.redact(input));
      assertTrue(Redactor.containsSecret(input));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "black Nike shoes under 1200 SEK",
          "order 1234567812345678 shipped",
          "pods with memory above 80 % on 10.0.0.12",
          "کفش بدون چرم زیر ۱۲۰۰ یورو",
          "token budget exceeded",
          "the password field is required"
        })
    void ordinaryTextIsUntouched(String text) {
      assertEquals(text, Redactor.redact(text));
      assertFalse(Redactor.containsSecret(text));
    }

    @Test
    void privateKeysAreRemovedEvenWhenTruncated() {
      String pem = "x -----BEGIN RSA PRIVATE KEY-----\nMIIEow\nIBAAK";
      assertEquals("x [REDACTED]", Redactor.redact(pem));
    }
  }

  @Nested
  class Sinks {

    @Test
    void logLinesCannotBeForgedOrHidden() {
      String forged = "q\r\n2026-10-31 INFO admin login ok\u202egnp.exe\u0007";
      String safe = Sanitizer.forSink(Sink.LOG, forged);
      assertFalse(safe.contains("\n") || safe.contains("\r") || safe.contains("\u202e"));
      assertEquals("q\\r\\n2026-10-31 INFO admin login ok\\u202egnp.exe\\u0007", safe);
    }

    @Test
    void errorMessagesLoseInternalsAndSecretsAndAreBounded() {
      String message =
          "No enum constant io.qwenbridge.commerce.CommerceContracts.Operator.XX with token=abc";
      assertEquals(
          "No enum constant [internal] with token=[REDACTED]",
          Sanitizer.forSink(Sink.ERROR_MESSAGE, message));
      String bounded = Sanitizer.forSink(Sink.ERROR_MESSAGE, "x".repeat(1000));
      assertEquals(Sink.ERROR_MESSAGE.maxLength(), bounded.length());
      assertTrue(bounded.endsWith(Sanitizer.TRUNCATED));
    }

    @Test
    void headersArePrintableAsciiOnly() {
      assertEquals("a??b???", Sanitizer.forSink(Sink.HEADER, "a\r\nbکفش"));
    }

    @Test
    void promptDataIsAQuotedJsonString() {
      assertEquals(
          "\"ignore \\\"rules\\\"\\n\\u2028done\\\\\"",
          Sanitizer.forSink(Sink.PROMPT_DATA, "ignore \"rules\"\n\u2028done\\"));
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
      String emoji = "😀".repeat(400);
      String bounded = Sanitizer.forSink(Sink.ERROR_MESSAGE, emoji);
      String kept = bounded.substring(0, bounded.length() - Sanitizer.TRUNCATED.length());
      assertFalse(Character.isHighSurrogate(kept.charAt(kept.length() - 1)));
    }
  }
}
