package io.qwenbridge.core.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.Extraction;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrJson;
import io.qwenbridge.core.queryir.QueryIrValidator;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.request.RawRequest;
import io.qwenbridge.core.request.RequestLimits;
import io.qwenbridge.core.request.RequestViolation;
import io.qwenbridge.core.request.UniversalRequestParser;
import io.qwenbridge.core.security.Sanitizer;
import io.qwenbridge.core.security.Sanitizer.Sink;
import io.qwenbridge.core.text.SearchTextNormalizer;
import io.qwenbridge.core.translation.PreservationChecker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Seeded fuzz and property tests for the Core entry points that see untrusted text (QB-063).
 *
 * <p>Every run is reproducible: the seed defaults to a fixed value and can be changed with {@code
 * -Dqwenbridge.fuzz.seed=N} (iterations with {@code -Dqwenbridge.fuzz.iterations=N}). A failure
 * reports the seed, the iteration and the escaped input, so it can be replayed exactly.
 */
class CoreFuzzTest {

  static final long SEED = Long.getLong("qwenbridge.fuzz.seed", 20261031L);
  static final int ITERATIONS = Integer.getInteger("qwenbridge.fuzz.iterations", 1500);

  private static final String[] FRAGMENTS = {
    "under",
    "below",
    "at least",
    "between",
    "and",
    "not",
    "without",
    "no",
    "utan",
    "inte",
    "mellan",
    "och",
    "högst",
    "بدون",
    "زیر",
    "بین",
    "تا",
    "حداقل",
    "around",
    "preferably",
    "SEK",
    "EUR",
    "kr",
    "€",
    "$",
    "یورو",
    "kronor",
    "kg",
    "GB",
    "%",
    "cm",
    "today",
    "last 3 days",
    "SKU-4471",
    "10.0.0.12",
    "\"exact phrase\"",
    "Nike",
    "pod-7f9c2",
    "1,200",
    "1.5",
    "۱۲۰۰",
    "٫",
    "１２",
    "-",
    ",",
    ".",
    "(",
    ")",
    "{",
    "}",
    "[",
    "]",
    ":",
    "\\",
    "'",
    "\"",
    "<script>",
    "${jndi:x}",
    "'; DROP TABLE x;--",
    "{{7*7}}",
  };

  private static final String[] ODD = {
    "\u0000", "\u0007", "\r\n", "\t", "\u200b", "\u200c", "\u200d", "\u202e", "\u2066",
    "\u2028", "\ufeff", "😀", "\ud800", "\udc00", "\u0301", "ﷺ", "Ａ", "ﬁ",
  };

  private static String randomText(Random r) {
    StringBuilder b = new StringBuilder();
    int parts = r.nextInt(12);
    for (int i = 0; i < parts; i++) {
      switch (r.nextInt(5)) {
        case 0, 1 -> b.append(FRAGMENTS[r.nextInt(FRAGMENTS.length)]);
        case 2 -> b.append(r.nextInt(100000));
        case 3 -> b.append(ODD[r.nextInt(ODD.length)]);
        default -> b.appendCodePoint(randomCodePoint(r));
      }
      if (r.nextBoolean()) {
        b.append(' ');
      }
    }
    return b.toString();
  }

  private static int randomCodePoint(Random r) {
    return switch (r.nextInt(6)) {
      case 0 -> 0x20 + r.nextInt(0x5F);
      case 1 -> 0x0600 + r.nextInt(0x100); // Arabic script
      case 2 -> 0x4E00 + r.nextInt(0x500); // CJK
      case 3 -> 0x00C0 + r.nextInt(0x40); // Latin-1 letters
      case 4 -> 0x06F0 + r.nextInt(10); // Persian digits
      default -> 0x1F300 + r.nextInt(0x100); // Emoji (supplementary)
    };
  }

  private static void forAll(String property, Consumer<String> check) {
    Random random = new Random(SEED ^ property.hashCode());
    for (int i = 0; i < ITERATIONS; i++) {
      String input = randomText(random);
      try {
        check.accept(input);
      } catch (AssertionError | RuntimeException e) {
        fail(
            property
                + " failed (seed="
                + SEED
                + ", iteration="
                + i
                + ", input="
                + Sanitizer.forSink(Sink.PROMPT_DATA, input)
                + "): "
                + e,
            e);
      }
    }
  }

  private static final Optional<LanguageTag> SV = Optional.of(new LanguageTag("sv"));

  @Test
  void extractionNeverThrowsAndAlwaysProducesAValidGroundedQueryIr() {
    forAll(
        "extraction",
        text -> {
          if (text.isBlank()) {
            return; // A QueryIR requires text; blank requests are rejected by the parser.
          }
          for (Optional<LanguageTag> lang :
              List.of(Optional.<LanguageTag>empty(), SV, Optional.of(new LanguageTag("fa")))) {
            Extraction e =
                ConstraintExtractor.extract(
                    text,
                    new ExtractionContext(
                        lang,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        null));
            QueryIR ir = e.applyTo(QueryIR.of(text, LanguageTag.UNDETERMINED));
            assertEquals(List.of(), QueryIrValidator.validate(ir));
          }
        });
  }

  @Test
  void validQueryIrSurvivesASerializationRoundTrip() {
    forAll(
        "queryir-roundtrip",
        text -> {
          if (text.isBlank()) {
            return;
          }
          Extraction e = ConstraintExtractor.extract(text, ExtractionContext.none());
          QueryIR ir = e.applyTo(QueryIR.of(text, LanguageTag.UNDETERMINED));
          String json = QueryIrJson.write(ir);
          QueryIrJson.Result back = QueryIrJson.read(json);
          assertTrue(back instanceof QueryIrJson.Result.Valid, back::toString);
          assertEquals(json, QueryIrJson.write(((QueryIrJson.Result.Valid) back).ir()));
        });
  }

  @Test
  void malformedQueryIrJsonIsRejectedAsAValueNeverAnException() {
    forAll(
        "queryir-read",
        text -> {
          QueryIrJson.Result result = QueryIrJson.read(text);
          if (result instanceof QueryIrJson.Result.Invalid invalid) {
            assertFalse(invalid.violations().isEmpty());
          }
          QueryIrJson.read(
              "{\"irVersion\":\"1.0\",\"originalText\":"
                  + Sanitizer.forSink(Sink.PROMPT_DATA, text)
                  + ",\"language\":\"und\",\"constraints\":[{\"id\":"
                  + Sanitizer.forSink(Sink.PROMPT_DATA, text)
                  + "}]}");
        });
  }

  @Test
  void searchNormalizationIsTotalAndIdempotent() {
    forAll(
        "normalization",
        text -> {
          String once = SearchTextNormalizer.normalize(text, SV).normalized();
          assertEquals(once, SearchTextNormalizer.normalize(once, SV).normalized());
        });
  }

  @Test
  void requestParserReportsViolationsWithoutEchoingSubmittedValues() {
    UniversalRequestParser parser = new UniversalRequestParser(RequestLimits.DEFAULTS);
    forAll(
        "request-parser",
        text -> {
          String value = text + "-zq";
          UniversalRequestParser.Result result =
              parser.parse(
                  new RawRequest(
                      null,
                      value,
                      value,
                      value,
                      value,
                      value,
                      Map.of(value.toLowerCase(java.util.Locale.ROOT), value),
                      List.of(value)));
          if (result instanceof UniversalRequestParser.Result.Invalid invalid) {
            for (RequestViolation v : invalid.violations()) {
              assertFalse(v.message().contains(value), v::toString);
            }
          }
        });
  }

  @Test
  void sanitizedOutputIsSafeForItsSink() throws Exception {
    ObjectMapper json = new ObjectMapper();
    forAll(
        "sanitizer",
        text -> {
          String log = Sanitizer.forSink(Sink.LOG, text);
          assertTrue(log.chars().noneMatch(c -> c == '\n' || c == '\r' || c == 0x202E), log);
          assertTrue(log.length() <= Sink.LOG.maxLength());
          assertTrue(
              Sanitizer.forSink(Sink.HEADER, text).chars().allMatch(c -> c >= 0x20 && c <= 0x7E));
          try {
            assertEquals(
                text, json.readValue(Sanitizer.forSink(Sink.PROMPT_DATA, text), String.class));
          } catch (java.io.IOException e) {
            throw new AssertionError("prompt data is not a JSON string", e);
          }
        });
  }

  @Test
  void aTextIsAlwaysAFaithfulTranslationOfItself() {
    forAll(
        "preservation-identity",
        text ->
            assertTrue(
                PreservationChecker.check(
                        text,
                        new LanguageTag("sv"),
                        text,
                        new LanguageTag("sv"),
                        io.qwenbridge.core.extraction.EntityDictionary.EMPTY)
                    .preserved()));
  }
}
