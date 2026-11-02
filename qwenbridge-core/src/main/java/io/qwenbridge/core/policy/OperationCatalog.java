package io.qwenbridge.core.policy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The known operations (QB-072, QB-074). An operation that is not listed is denied. */
public final class OperationCatalog {

  private final Map<String, OperationSpec> operations;

  public OperationCatalog(Collection<OperationSpec> operations) {
    this.operations =
        Map.copyOf(
            operations.stream()
                .collect(Collectors.toMap(OperationSpec::name, Function.identity())));
  }

  public Optional<OperationSpec> find(String operation) {
    return Optional.ofNullable(operations.get(operation));
  }

  public Collection<OperationSpec> all() {
    return operations.values();
  }
}
