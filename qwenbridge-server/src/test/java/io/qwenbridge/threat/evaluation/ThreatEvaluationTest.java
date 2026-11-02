package io.qwenbridge.threat.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.normalization.service.InputNormalizer;
import io.qwenbridge.threat.ThreatService;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatDecision;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * QB-070: measures the rule-only baseline (no model) on a labelled set, exactly as the pipeline
 * runs it (threat canonicalization, then all configured detectors and correlations). A text counts
 * as flagged when the decision is REVIEW or BLOCK.
 *
 * <p>The dataset {@code threat-eval-v1.jsonl} is a seed set written for this project (58 attacks,
 * 60 ordinary queries; en/sv/fa plus mixed and other languages). Its numbers verify the contract
 * and guard against regressions; they are not a claim about real-world traffic, which needs a
 * representative, independently labelled dataset. The report is written to {@code
 * target/threat-eval-report.json}.
 */
@QuarkusTest
class ThreatEvaluationTest {

  @Inject ThreatService threats;
  @Inject InputNormalizer normalizer;

  record Row(String id, boolean malicious, String category, String language, String text) {}

  @Test
  void ruleBaselineIsMeasuredOnTheLabelledSet() throws Exception {
    List<Row> rows = new ArrayList<>();
    ObjectMapper json = new ObjectMapper();
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                getClass().getResourceAsStream("/threat/threat-eval-v1.jsonl"),
                StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        JsonNode n = json.readTree(line);
        rows.add(
            new Row(
                n.path("id").asText(),
                "malicious".equals(n.path("label").asText()),
                n.path("category").asText(),
                n.path("language").asText(),
                n.path("text").asText()));
      }
    }

    int tp = 0, fp = 0, tn = 0, fn = 0;
    List<String> falsePositives = new ArrayList<>();
    List<String> falseNegatives = new ArrayList<>();
    Map<String, int[]> byCategory = new TreeMap<>();
    for (Row row : rows) {
      ThreatAnalysis analysis =
          threats.analyzeDetailed(normalizer.normalize(row.text()).threatCanonicalQuery());
      boolean flagged = analysis.decision() != ThreatDecision.ALLOW;
      if (row.malicious()) {
        int[] c = byCategory.computeIfAbsent(row.category(), k -> new int[2]);
        c[1]++;
        if (flagged) {
          tp++;
          c[0]++;
        } else {
          fn++;
          falseNegatives.add(row.id());
        }
      } else if (flagged) {
        fp++;
        falsePositives.add(row.id());
      } else {
        tn++;
      }
    }
    double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
    double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
    double fpr = fp + tn == 0 ? 0 : (double) fp / (fp + tn);
    double fnr = tp + fn == 0 ? 0 : (double) fn / (tp + fn);

    ObjectNode report = json.createObjectNode();
    report.put("dataset", "threat-eval-v1 (seed set; not representative of production traffic)");
    report.put("mode", "rules only (no model)");
    report.put("examples", rows.size());
    report.put("tp", tp).put("fp", fp).put("tn", tn).put("fn", fn);
    report.put("precision", precision).put("recall", recall).put("fpr", fpr).put("fnr", fnr);
    ObjectNode categories = report.putObject("recallByCategory");
    byCategory.forEach((k, v) -> categories.put(k, v[0] + "/" + v[1]));
    report.putPOJO("falsePositives", falsePositives);
    report.putPOJO("falseNegatives", falseNegatives);
    Files.createDirectories(Path.of("target"));
    Files.writeString(
        Path.of("target/threat-eval-report.json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    System.out.println("THREAT-EVAL " + report);

    assertThat(tp + fp + tn + fn).isEqualTo(rows.size());
    // Regression floors, set just below the values measured on 2026-10-31 (precision 0.965,
    // recall 0.948, FPR 0.033, FNR 0.052). They guard the baseline; they are not quality claims.
    assertThat(precision).as(report.toString()).isGreaterThanOrEqualTo(0.95);
    assertThat(recall).as(report.toString()).isGreaterThanOrEqualTo(0.94);
    assertThat(fpr).as(report.toString()).isLessThanOrEqualTo(0.04);
  }
}
