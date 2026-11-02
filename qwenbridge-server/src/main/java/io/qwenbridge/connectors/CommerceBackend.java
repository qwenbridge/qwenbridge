package io.qwenbridge.connectors;

import io.qwenbridge.commerce.CommerceContracts.Plan;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.util.*;

public interface CommerceBackend extends AutoCloseable {
  record Query(String text, Plan plan, String mode, List<Double> embedding, int limit, int offset) {
    public Query {
      embedding = embedding == null ? List.of() : List.copyOf(embedding);
      if (limit < 1 || limit > 100 || offset < 0 || offset + limit > 10000)
        throw new IllegalArgumentException("Invalid page bounds");
    }
  }

  String id();

  Set<String> modes();

  boolean configured();

  SearchResultSet search(Query query);

  void upsert(String tenant, String id, Map<String, Object> document);

  void delete(String tenant, String id);

  default void initialize() {}

  /** QB-176: whether this backend refuses writes, deletes and initialization. */
  default boolean readOnly() {
    return false;
  }

  @Override
  default void close() {}

  default void validate(Query query) {
    if (!modes().contains(query.mode()))
      throw new jakarta.ws.rs.ClientErrorException(
          "Backend " + id() + " does not support " + query.mode() + "; supported: " + modes(), 422);
    io.qwenbridge.commerce.CommerceCompiler.filters(query.plan());
    if (!configured())
      throw new jakarta.ws.rs.ServiceUnavailableException("Backend " + id() + " is not configured");
  }
}
