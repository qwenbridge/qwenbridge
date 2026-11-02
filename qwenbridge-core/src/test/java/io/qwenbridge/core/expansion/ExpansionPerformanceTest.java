package io.qwenbridge.core.expansion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.GlossaryTranslationProvider;
import io.qwenbridge.core.translation.TranslationProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Latency and memory of the engine (QB-286 test 35). Writes {@code
 * target/benchmarks/expansion-performance.json}. Numbers are from the machine running the build;
 * the fake provider measures orchestration overhead only, the glossary run measures the real local
 * provider on the fixtures.
 */
class ExpansionPerformanceTest {

  static long[] run(List<TranslationProvider> providers, List<String> targets, int n) {
    long[] samples = new long[n];
    var settings = new ExpansionSettings(true, targets, ExpansionPolicy.ALL_TARGETS, Map.of(), 0.5);
    for (int i = 0; i < n; i++) {
      String q = "I need a red winter jacket under 100 euros";
      long t0 = System.nanoTime();
      QueryExpander.expand(
          new QueryExpander.Request(q, q, SourceLanguage.of(new LanguageTag("en")), settings, null),
          providers,
          ExpansionLimits.DEFAULT,
          Optional.empty(),
          () -> false);
      samples[i] = System.nanoTime() - t0;
    }
    return samples;
  }

  static double pct(long[] s, double p) {
    long[] c = s.clone();
    Arrays.sort(c);
    return c[(int) Math.min(c.length - 1, Math.ceil(p * c.length) - 1)] / 1_000_000.0;
  }

  @Test
  void latencyAndMemory() throws Exception {
    List<String> ten = List.of("fa", "sv", "nb", "nn", "de", "fr", "es", "it", "ja", "ko");
    run(List.of(FakeProvider.labelling("warm")), ten, 200); // warm-up
    long[] fake10 = run(List.of(FakeProvider.labelling("fake")), ten, 500);
    var glossary = GlossaryTranslationProvider.load("glossary", GlossaryExpansionTest.GLOSSARY);
    run(List.of(glossary), List.of("fa", "sv", "nb", "nn"), 200);
    long[] glossary4 = run(List.of(glossary), List.of("fa", "sv", "nb", "nn"), 500);

    Runtime rt = Runtime.getRuntime();
    System.gc();
    long before = rt.totalMemory() - rt.freeMemory();
    List<ExpansionResult> keep = new ArrayList<>();
    var settings = new ExpansionSettings(true, ten, ExpansionPolicy.ALL_TARGETS, Map.of(), 0.5);
    for (int i = 0; i < 1000; i++) {
      String q = "query " + i + " red winter jacket";
      keep.add(
          QueryExpander.expand(
              new QueryExpander.Request(
                  q, q, SourceLanguage.of(new LanguageTag("en")), settings, null),
              List.of(FakeProvider.labelling("m")),
              ExpansionLimits.DEFAULT,
              Optional.empty(),
              () -> false));
    }
    System.gc();
    long perResult = Math.max(0, (rt.totalMemory() - rt.freeMemory() - before) / keep.size());

    String json =
        String.format(
            Locale.ROOT,
            "{\"orchestration_10_targets_ms\":{\"p50\":%.3f,\"p95\":%.3f,\"p99\":%.3f},"
                + "\"glossary_4_targets_ms\":{\"p50\":%.3f,\"p95\":%.3f,\"p99\":%.3f},"
                + "\"retained_bytes_per_10_target_result\":%d,\"samples\":500,\"note\":\"orchestration"
                + " uses a fake provider; glossary uses the test fixtures\"}",
            pct(fake10, .5),
            pct(fake10, .95),
            pct(fake10, .99),
            pct(glossary4, .5),
            pct(glossary4, .95),
            pct(glossary4, .99),
            perResult);
    Path out = Path.of("target/benchmarks/expansion-performance.json");
    Files.createDirectories(out.getParent());
    Files.writeString(out, json + "\n");
    System.out.println("QB-286 expansion performance: " + json);
    assertTrue(pct(fake10, .95) < 250, "10-target orchestration p95 " + pct(fake10, .95) + " ms");
    assertTrue(pct(glossary4, .95) < 250, "glossary p95 " + pct(glossary4, .95) + " ms");
  }
}
