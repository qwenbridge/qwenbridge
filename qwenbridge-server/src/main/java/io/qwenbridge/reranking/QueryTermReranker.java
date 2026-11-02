package io.qwenbridge.reranking;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic query-term baseline, not a semantic/model reranker. */
public class QueryTermReranker implements Reranker, io.qwenbridge.core.ranking.Reranker {

  @Override
  public String id() {
    return "query-terms-v1";
  }

  /**
   * The same baseline on the Core reranking SPI (QB-119): order by the share of query terms found
   * in the item's text fields; ties keep the given order.
   */
  @Override
  public io.qwenbridge.core.ranking.Reranker.Response rerank(
      String query, java.util.List<io.qwenbridge.core.ranking.Reranker.Item> items) {
    Set<String> terms = terms(query);
    record Scored(String id, int index, double coverage) {}
    java.util.List<Scored> scored = new java.util.ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      var fields = items.get(i).fields();
      String text =
          java.util.List.of("title", "brand", "category", "description").stream()
              .map(fields::get)
              .filter(io.qwenbridge.core.retrieval.filter.FieldValue.Text.class::isInstance)
              .map(v -> ((io.qwenbridge.core.retrieval.filter.FieldValue.Text) v).value())
              .collect(Collectors.joining(" "));
      Set<String> documentTerms = terms(text);
      double coverage =
          terms.isEmpty()
              ? 0
              : terms.stream().filter(documentTerms::contains).count() / (double) terms.size();
      scored.add(new Scored(items.get(i).id(), i, coverage));
    }
    scored.sort(
        Comparator.comparingDouble(Scored::coverage).reversed().thenComparingInt(Scored::index));
    return new io.qwenbridge.core.ranking.Reranker.Response(
        scored.stream().map(Scored::id).toList(),
        java.util.Optional.of(scored.stream().map(Scored::coverage).toList()));
  }

  @Override
  public SearchResultSet rerank(String query, SearchResultSet resultSet) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(resultSet, "resultSet");
    Set<String> terms = terms(query);
    if (terms.isEmpty() || resultSet.isEmpty()) {
      return resultSet;
    }
    var hits = resultSet.hits().stream().map(hit -> rerank(hit, terms)).toList();
    if (hits.stream().noneMatch(hit -> ((Double) hit.metadata().get("queryTermCoverage")) > 0)) {
      return resultSet;
    }
    return new SearchResultSet(
        hits.stream()
            .sorted(
                Comparator.comparingDouble(SearchHit::score)
                    .reversed()
                    .thenComparing(SearchHit::id))
            .toList(),
        resultSet.totalHits(),
        resultSet.tookMillis());
  }

  private SearchHit rerank(SearchHit hit, Set<String> queryTerms) {
    String text =
        java.util.List.of("title", "brand", "category", "description").stream()
            .map(hit.document()::get)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.joining(" "));
    Set<String> documentTerms = terms(text);
    double coverage =
        queryTerms.stream().filter(documentTerms::contains).count() / (double) queryTerms.size();
    double original = Double.isFinite(hit.score()) ? Math.clamp(hit.score(), 0.0, 1.0) : 0.0;
    double score = 0.8 * coverage + 0.2 * original;
    var metadata = new LinkedHashMap<>(hit.metadata());
    metadata.put("preRerankScore", hit.score());
    metadata.put("queryTermCoverage", coverage);
    metadata.put("reranker", "query-terms-v1");
    metadata.put("finalScore", score);
    return new SearchHit(hit.id(), score, hit.document(), metadata);
  }

  private Set<String> terms(String value) {
    return Arrays.stream(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+"))
        .filter(term -> !term.isBlank())
        .collect(Collectors.toSet());
  }
}
