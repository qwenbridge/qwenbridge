package io.qwenbridge.core.ranking;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The single reranking SPI (QB-119, also QB-245). A reranker returns an order for the items it is
 * given: a complete permutation of their ids. It may add its own scores. It cannot add, remove or
 * rescore items: {@link RerankStage} rejects anything but a complete permutation and keeps the
 * baseline when the reranker is slow, fails or misbehaves.
 *
 * <p>Implementations treat item fields as untrusted data.
 */
public interface Reranker {

  String id();

  Response rerank(String query, List<Item> items);

  /** The most items one call may be given (QB-245); the stage reranks only that many. */
  default int maxCandidates() {
    return 100;
  }

  /** The model behind the reranker, when it uses one (QB-247). */
  default Optional<io.qwenbridge.core.ai.ModelDescriptor> model() {
    return Optional.empty();
  }

  /** An item offered for reranking: its id and stored fields, no scores. */
  record Item(String id, Map<String, FieldValue> fields) {
    public Item {
      Objects.requireNonNull(id, "id must not be null");
      fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
  }

  /**
   * @param order item ids, best first
   * @param scores the reranker's own score per position, when it has one
   */
  record Response(List<String> order, Optional<List<Double>> scores) {
    public Response {
      order = List.copyOf(order);
      scores = scores == null ? Optional.empty() : scores.map(List::copyOf);
    }

    public static Response of(List<String> order) {
      return new Response(order, Optional.empty());
    }
  }
}
