package io.qwenbridge.core.expansion;

import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.RankFusion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyword retrieval over the expansion's variants (QB-286). The source text and each distinct
 * usable variant are searched once with the same tenant and hard filter, and the lists are fused
 * with reciprocal rank fusion, so a document matched by several variants is counted once and keeps
 * each variant's rank. The filter carries the hard constraints, which therefore apply identically
 * to every variant and are never duplicated. A variant whose search fails is skipped and named in
 * the result.
 */
public final class ExpansionRetrieval {

  private ExpansionRetrieval() {}

  public record Result(List<FusedDocument> documents, List<String> searched, List<String> failed) {
    public Result {
      documents = List.copyOf(documents);
      searched = List.copyOf(searched);
      failed = List.copyOf(failed);
    }
  }

  public static Result keyword(
      SearchProvider provider,
      String tenant,
      Filter filter,
      ExpansionResult expansion,
      int topK,
      Duration timeout,
      CancellationToken cancellation) {
    List<RankFusion.SourceList> lists = new ArrayList<>();
    List<String> searched = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    List<String> texts = expansion.retrievalTexts();
    for (int i = 0; i < texts.size(); i++) {
      if (cancellation.cancelled()) break;
      String source = i == 0 ? "source" : "variant-" + i;
      SearchRequest request = SearchRequest.keyword(tenant, texts.get(i), filter, topK);
      SearchOutcome outcome = provider.search(request.withTimeout(timeout), cancellation);
      if (outcome instanceof SearchOutcome.Success s) {
        lists.add(new RankFusion.SourceList(source, s.hits()));
        searched.add(texts.get(i));
      } else {
        failed.add(texts.get(i));
      }
    }
    return new Result(RankFusion.rrf(lists, RankFusion.DEFAULT_K, topK), searched, failed);
  }
}
