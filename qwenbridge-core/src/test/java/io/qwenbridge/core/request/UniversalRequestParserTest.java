package io.qwenbridge.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.request.RequestViolation.Code;
import io.qwenbridge.core.request.UniversalRequestParser.Result;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UniversalRequestParserTest {

  private final UniversalRequestParser parser = new UniversalRequestParser(RequestLimits.DEFAULTS);

  static Stream<MultilingualCorpus.Case> corpus() {
    return MultilingualCorpus.load().stream();
  }

  static Stream<MultilingualCorpus.Case> validCorpus() {
    return corpus().filter(MultilingualCorpus.Case::expectValid);
  }

  @Test
  void corpusHasUniqueIdsAndCoversRequiredCategories() {
    List<MultilingualCorpus.Case> cases = MultilingualCorpus.load();
    assertEquals(cases.size(), cases.stream().map(MultilingualCorpus.Case::id).distinct().count());
    for (String prefix : List.of("en-", "fa-", "sv-", "mixed-", "emoji-", "empty", "invalid-")) {
      assertTrue(cases.stream().anyMatch(c -> c.id().startsWith(prefix)), "corpus lacks " + prefix);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  void corpusCaseHasExpectedOutcome(MultilingualCorpus.Case c) {
    Result result = parser.parse(c.toRaw());
    if (c.expectValid()) {
      Result.Valid valid = assertInstanceOf(Result.Valid.class, result, c.id());
      assertEquals(TextDirection.valueOf(c.direction()), valid.request().direction(), c.id());
    } else {
      Result.Invalid invalid = assertInstanceOf(Result.Invalid.class, result, c.id());
      assertTrue(
          invalid.violations().stream().anyMatch(v -> v.code() == Code.valueOf(c.expect())),
          c.id() + " expected " + c.expect() + " but got " + invalid.violations());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validCorpus")
  void validQueryIsPreservedExactly(MultilingualCorpus.Case c) {
    Result.Valid valid = assertInstanceOf(Result.Valid.class, parser.parse(c.toRaw()));
    String query = valid.request().query();
    assertEquals(c.query(), query, "query must not be normalized or re-encoded at intake");
    assertTrue(
        java.util.Arrays.equals(
            c.query().getBytes(StandardCharsets.UTF_8), query.getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validCorpus")
  void validQuerySurvivesJsonRoundTrip(MultilingualCorpus.Case c) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    byte[] json = mapper.writeValueAsBytes(c.toRaw());
    RawRequest back = mapper.readValue(new String(json, StandardCharsets.UTF_8), RawRequest.class);
    assertEquals(c.query(), back.query());
    Result.Valid valid = assertInstanceOf(Result.Valid.class, parser.parse(back));
    assertEquals(c.query(), valid.request().query());
  }

  @Test
  void queryLengthIsCountedInCodePointsNotUtf16Units() {
    String emoji = "😀"; // one code point, two UTF-16 units
    String atLimit = emoji.repeat(RequestLimits.DEFAULTS.maxQueryCodePoints());
    assertInstanceOf(Result.Valid.class, parser.parse(raw(atLimit)));

    Result.Invalid tooLong =
        assertInstanceOf(Result.Invalid.class, parser.parse(raw(atLimit + emoji)));
    assertEquals(Code.TOO_LONG, tooLong.violations().get(0).code());
  }

  @Test
  void violationsNeverEchoSubmittedValues() {
    String secret = "s3cr3t-value-should-not-leak";
    Map<String, String> context = new LinkedHashMap<>();
    context.put("api_token", secret);
    RawRequest raw =
        new RawRequest(
            null, "bad id with spaces", "q", "english-" + secret, null, null, context, null);
    Result.Invalid invalid = assertInstanceOf(Result.Invalid.class, parser.parse(raw));
    assertEquals(3, invalid.violations().size());
    invalid
        .violations()
        .forEach(
            v -> {
              assertFalse(v.message().contains(secret), v.toString());
              assertFalse(v.message().contains("spaces"), v.toString());
            });
  }

  @Test
  void allViolationsAreReportedTogether() {
    RawRequest raw = new RawRequest(9, null, "", "xx-", null, "sv_SE", null, null);
    Result.Invalid invalid = assertInstanceOf(Result.Invalid.class, parser.parse(raw));
    assertEquals(
        List.of(
            Code.UNSUPPORTED_VERSION,
            Code.BLANK,
            Code.INVALID_LANGUAGE_TAG,
            Code.INVALID_LANGUAGE_TAG),
        invalid.violations().stream().map(RequestViolation::code).toList());
  }

  @Test
  void contextAndDomainHintLimitsAreEnforced() {
    Map<String, String> tooMany = new LinkedHashMap<>();
    IntStream.rangeClosed(0, RequestLimits.DEFAULTS.maxContextEntries())
        .forEach(i -> tooMany.put("k" + i, "v"));
    RawRequest raw =
        new RawRequest(
            null,
            null,
            "q",
            null,
            null,
            null,
            tooMany,
            List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"));
    Result.Invalid invalid = assertInstanceOf(Result.Invalid.class, parser.parse(raw));
    assertEquals(
        List.of(Code.TOO_MANY, Code.TOO_MANY),
        invalid.violations().stream().map(RequestViolation::code).toList());

    String longValue = "ä".repeat(RequestLimits.DEFAULTS.maxContextValueCodePoints() + 1);
    RawRequest longCtx =
        new RawRequest(null, null, "q", null, null, null, Map.of("note", longValue), null);
    assertEquals(
        Code.TOO_LONG,
        assertInstanceOf(Result.Invalid.class, parser.parse(longCtx)).violations().get(0).code());
  }

  @Test
  void domainHintsAreDeduplicatedInOrder() {
    RawRequest raw =
        new RawRequest(
            null, null, "q", null, null, null, null, List.of("security", "devops", "security"));
    Result.Valid valid = assertInstanceOf(Result.Valid.class, parser.parse(raw));
    assertEquals(List.of("security", "devops"), valid.request().domainHints());
  }

  @Test
  void languagePreferencesAreCanonicalizedAndUndIsTreatedAsAbsent() {
    RawRequest raw = new RawRequest(null, " req-1 ", "q", "FA", "und", "sv-se", null, null);
    UniversalRequest request = assertInstanceOf(Result.Valid.class, parser.parse(raw)).request();
    assertEquals("req-1", request.requestId().orElseThrow());
    assertEquals("fa", request.language().declared().orElseThrow().value());
    assertTrue(request.language().response().isEmpty());
    assertEquals("sv-SE", request.language().locale().orElseThrow().value());
    assertTrue(request.initialLanguageMetadata().detected().isUndetermined());
  }

  @Test
  void requestWithoutDomainOrLanguageIsValid() {
    UniversalRequest request =
        assertInstanceOf(Result.Valid.class, parser.parse(raw("pods with high memory"))).request();
    assertEquals(UniversalRequest.CONTRACT_VERSION, request.contractVersion());
    assertTrue(request.domainHints().isEmpty());
    assertTrue(request.context().isEmpty());
  }

  @Test
  void sensitiveKeyDetectionCoversSeparatorVariants() {
    for (String key :
        List.of(
            "password", "api-key", "api_key", "x.auth.token", "client_secret", "session_cookie")) {
      assertTrue(UniversalRequestParser.isSensitiveKey(key), key);
    }
    for (String key : List.of("channel", "client.version", "region", "tenant")) {
      assertFalse(UniversalRequestParser.isSensitiveKey(key), key);
    }
  }

  private static RawRequest raw(String query) {
    return new RawRequest(null, null, query, null, null, null, null, null);
  }
}
