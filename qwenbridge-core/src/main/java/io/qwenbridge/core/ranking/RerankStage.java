package io.qwenbridge.core.ranking;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Applies an optional reranker to the top of a baseline ranking (QB-119, QB-120).
 *
 * <ul>
 *   <li>Only the first {@code window} items are offered; the rest keep their baseline order after
 *       them.
 *   <li>The answer must be a complete permutation of the offered ids; otherwise it is ignored.
 *   <li>A timeout or an exception keeps the baseline. Every outcome says what happened.
 *   <li>Scores are preserved: each item keeps its baseline score, retrieval score and components;
 *       the reranker's position (and score, if any) is added as a RERANK component.
 * </ul>
 */
public final class RerankStage {

  public enum Status {
    APPLIED,
    DISABLED,
    TOO_FEW,
    TIMEOUT,
    FAILED,
    INVALID_PERMUTATION
  }

  public record Result(List<RankedItem> items, Status status, Optional<String> reranker) {
    public Result {
      items = List.copyOf(items);
      Objects.requireNonNull(status, "status must not be null");
      reranker = reranker == null ? Optional.empty() : reranker;
    }
  }

  private RerankStage() {}

  public static Result apply(
      Optional<Reranker> reranker,
      String query,
      List<RankedItem> ranked,
      int window,
      Duration timeout) {
    if (reranker.isEmpty() || window == 0) {
      return new Result(ranked, Status.DISABLED, Optional.empty());
    }
    Reranker r = reranker.get();
    // QB-245: never offer more candidates than the reranker accepts; the rest keep their order.
    int candidates = Math.min(Math.min(window, r.maxCandidates()), ranked.size());
    List<RankedItem> head = ranked.subList(0, Math.max(candidates, 0));
    if (head.size() < 2) {
      return new Result(ranked, Status.TOO_FEW, Optional.of(r.id()));
    }
    List<Reranker.Item> items =
        head.stream().map(i -> new Reranker.Item(i.id(), i.fields())).toList();
    Reranker.Response response;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Reranker.Response> call = executor.submit(() -> r.rerank(query, items));
      try {
        response = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        call.cancel(true);
        return new Result(ranked, Status.TIMEOUT, Optional.of(r.id()));
      } catch (ExecutionException e) {
        return new Result(ranked, Status.FAILED, Optional.of(r.id()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        call.cancel(true);
        return new Result(ranked, Status.FAILED, Optional.of(r.id()));
      }
    }
    if (response == null || !permutation(response, head)) {
      return new Result(ranked, Status.INVALID_PERMUTATION, Optional.of(r.id()));
    }
    Map<String, RankedItem> byId = new LinkedHashMap<>();
    head.forEach(i -> byId.put(i.id(), i));
    List<RankedItem> out = new ArrayList<>(ranked.size());
    for (int p = 0; p < response.order().size(); p++) {
      RankedItem item = byId.get(response.order().get(p));
      double raw =
          response
              .scores()
              .map(s -> s.get(response.order().indexOf(item.id())))
              .orElse((double) p + 1);
      List<ScoreComponent> parts = new ArrayList<>(item.components());
      parts.add(new ScoreComponent("rerank", ScoreComponent.Kind.RERANK, raw, 0, r.id()));
      out.add(
          new RankedItem(
              item.id(),
              p + 1,
              item.score(),
              item.retrievalRank(),
              item.retrievalScore(),
              parts,
              item.fields(),
              OptionalInt.of(p + 1)));
    }
    for (int i = head.size(); i < ranked.size(); i++) {
      out.add(ranked.get(i).at(i + 1));
    }
    return new Result(out, Status.APPLIED, Optional.of(r.id()));
  }

  private static boolean permutation(Reranker.Response response, List<RankedItem> head) {
    List<String> order = response.order();
    if (order.size() != head.size() || new HashSet<>(order).size() != order.size()) {
      return false;
    }
    if (!new HashSet<>(order).equals(new HashSet<>(head.stream().map(RankedItem::id).toList()))) {
      return false;
    }
    return response
        .scores()
        .map(s -> s.size() == order.size() && s.stream().allMatch(Double::isFinite))
        .orElse(true);
  }
}
