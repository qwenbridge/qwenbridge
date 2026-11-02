package io.qwenbridge.core.security;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.intent.IntentAnalyzer;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.spelling.ProtectedTokens;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regular expressions that run on user text stay linear on adversarial input (CodeQL
 * java/polynomial-redos). Each input is a long run of the character that made the former patterns
 * rescan the run from every position; with run-start anchoring every run is scanned once.
 */
class RegexLinearityTest {

  static final int N = 200_000;
  static final Duration LIMIT = Duration.ofSeconds(5);

  @Test
  void protectedTokensAreLinearOnLongRunsWithoutAMatch() {
    for (String run : List.of("%".repeat(N), "-".repeat(N), "a-".repeat(N / 2), "x.".repeat(N / 2)))
      assertTimeoutPreemptively(LIMIT, () -> ProtectedTokens.tokenize(run));
  }

  @Test
  void protectedTokensStillProtectEmailsAndHosts() {
    var tokens =
        ProtectedTokens.tokenize("mail bob.smith@example.com about redis03-pt1.example.org");
    assertTrue(
        tokens.stream().anyMatch(t -> t.text().equals("smith") && "email".equals(t.reason())));
    assertTrue(
        tokens.stream()
            .anyMatch(
                t -> t.text().equals("example") && t.kind() == ProtectedTokens.Kind.PROTECTED));
  }

  @Test
  void intentDecompositionIsLinearOnLongWhitespaceRuns() {
    var analyzer = new IntentAnalyzer();
    for (String text :
        List.of(
            "shoes" + " ".repeat(N) + "x",
            "shoes," + " ".repeat(N) + "x",
            ",both " + " ".repeat(N) + "\nx",
            "shoes" + " ".repeat(N) + "and" + " ".repeat(N) + "x"))
      assertTimeoutPreemptively(
          LIMIT,
          () ->
              analyzer.analyze(
                  text, LanguageTag.parse("en"), java.util.Set.of("commerce"), List.of()));
  }

  /** Long runs of groups no longer recurse the regex engine into a StackOverflowError. */
  @Test
  void numbersAndClassNamesSurviveLongGroupRuns() {
    var number = java.util.regex.Pattern.compile(io.qwenbridge.core.extraction.Numerals.TOKEN);
    for (String run : List.of("1.".repeat(N / 2), "1,".repeat(N / 2), "12.3".repeat(N / 4)))
      assertTimeoutPreemptively(
          LIMIT,
          () -> {
            var m = number.matcher(run);
            while (m.find()) {}
          });
    assertTimeoutPreemptively(
        LIMIT, () -> Sanitizer.forSink(Sanitizer.Sink.ERROR_MESSAGE, "x.".repeat(N / 2) + "Y"));
    assertTrue(
        Sanitizer.forSink(Sanitizer.Sink.ERROR_MESSAGE, "failed at com.example.Foo.bar()")
            .contains("[internal]"));
  }
}
