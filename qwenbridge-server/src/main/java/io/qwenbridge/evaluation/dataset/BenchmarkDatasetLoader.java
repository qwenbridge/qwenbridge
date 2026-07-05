package io.qwenbridge.evaluation.dataset;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.RelevanceLabel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BenchmarkDatasetLoader {

  public List<EvaluationQuery> load(Reader reader) {
    Map<String, MutableEvaluationQuery> queries = new LinkedHashMap<>();

    try (BufferedReader bufferedReader = new BufferedReader(reader)) {
      String line;
      boolean header = true;

      while ((line = bufferedReader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        if (header) {
          header = false;
          continue;
        }

        String[] columns = line.split(",", -1);

        if (columns.length != 4) {
          throw new IllegalArgumentException("benchmark row must contain exactly 4 columns");
        }

        String queryId = columns[0].trim();
        String query = columns[1].trim();
        String documentId = columns[2].trim();
        int relevance = Integer.parseInt(columns[3].trim());

        queries
            .computeIfAbsent(queryId, ignored -> new MutableEvaluationQuery(queryId, query))
            .labels
            .add(new RelevanceLabel(documentId, relevance));
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("failed to read benchmark dataset", exception);
    }

    return queries.values().stream()
        .map(item -> new EvaluationQuery(item.id, item.query, item.labels))
        .toList();
  }

  private static final class MutableEvaluationQuery {
    private final String id;
    private final String query;
    private final List<RelevanceLabel> labels = new ArrayList<>();

    private MutableEvaluationQuery(String id, String query) {
      this.id = id;
      this.query = query;
    }
  }
}
