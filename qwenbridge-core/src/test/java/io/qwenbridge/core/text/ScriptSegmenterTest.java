package io.qwenbridge.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.Character.UnicodeScript;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScriptSegmenterTest {

  @Test
  void codeSwitchedTextIsSplitIntoOrderedScriptRuns() {
    String text = "Nike shoes برای winter, not leather.";
    List<ScriptSegment> segments = ScriptSegmenter.segment(text);

    assertEquals(
        List.of(UnicodeScript.LATIN, UnicodeScript.ARABIC, UnicodeScript.LATIN),
        segments.stream().map(ScriptSegment::script).toList());
    assertEquals("Nike shoes ", segments.get(0).text());
    assertEquals("برای ", segments.get(1).text());
    assertEquals("winter, not leather.", segments.get(2).text());
    assertEquals(text.indexOf('ب'), segments.get(1).start());
    assertEquals(Set.of(UnicodeScript.LATIN, UnicodeScript.ARABIC), ScriptSegmenter.scripts(text));
  }

  @Test
  void zwnjAndCombiningMarksStayInsideTheirWord() {
    String text = "می‌خوام löparskor";
    List<ScriptSegment> segments = ScriptSegmenter.segment(text);
    assertEquals(2, segments.size());
    assertEquals("می‌خوام ", segments.get(0).text());
    assertEquals("löparskor", segments.get(1).text());
  }

  @Test
  void japaneseKanaAndKanjiFormOneSegment() {
    List<ScriptSegment> segments = ScriptSegmenter.segment("東京のホテル");
    assertEquals(1, segments.size());
    assertEquals(UnicodeScript.HAN, segments.get(0).script());
  }

  @Test
  void textWithoutLettersIsOneCommonSegment() {
    List<ScriptSegment> segments = ScriptSegmenter.segment("12345 !!! 👟");
    assertEquals(1, segments.size());
    assertEquals(UnicodeScript.COMMON, segments.get(0).script());
    assertTrue(ScriptSegmenter.scripts("12345").isEmpty());
    assertTrue(ScriptSegmenter.segment("").isEmpty());
  }

  @Test
  void propertySegmentsAreContiguousAndCoverTheText() {
    String[] parts = {"a", "b ", "م", "ی ", "ש", "東", "1", " ", ",", "‌", "̈"};
    Random random = new Random(20261031L);
    for (int n = 0; n < 2000; n++) {
      StringBuilder sb = new StringBuilder();
      for (int i = random.nextInt(20); i > 0; i--) {
        sb.append(parts[random.nextInt(parts.length)]);
      }
      String text = sb.toString();
      List<ScriptSegment> segments = ScriptSegmenter.segment(text);
      StringBuilder joined = new StringBuilder();
      int expectedStart = 0;
      for (ScriptSegment segment : segments) {
        assertEquals(expectedStart, segment.start());
        assertEquals(text.substring(segment.start(), segment.end()), segment.text());
        joined.append(segment.text());
        expectedStart = segment.end();
      }
      assertEquals(text, joined.toString());
    }
  }
}
