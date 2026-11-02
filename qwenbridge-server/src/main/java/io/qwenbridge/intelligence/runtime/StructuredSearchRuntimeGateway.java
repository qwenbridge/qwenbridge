package io.qwenbridge.intelligence.runtime;

import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.semantic.SemanticAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Explicit opt-in boundary for structured OpenSearch execution. A caller must first complete the
 * existing threat, authorization and policy pipeline. This service does not expose an HTTP endpoint
 * or bypass the legacy search path.
 */
@ApplicationScoped
public class StructuredSearchRuntimeGateway {
  private final StructuredQueryCoordinator coordinator;
  private final boolean enabled;
  private final String priceField;
  private final String currencyField;

  @Inject
  public StructuredSearchRuntimeGateway(
      StructuredQueryCoordinator coordinator,
      @ConfigProperty(name = "qwenbridge.structured-search.enabled", defaultValue = "false")
          boolean enabled,
      @ConfigProperty(name = "qwenbridge.structured-search.price-field", defaultValue = "price")
          String priceField,
      @ConfigProperty(
              name = "qwenbridge.structured-search.currency-field",
              defaultValue = "currency.keyword")
          String currencyField) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.enabled = enabled;
    this.priceField = validateField(priceField);
    this.currencyField = validateField(currencyField);
    // Stage 26: a price amount and its currency must not resolve to the same index field.
    if (this.priceField.equals(this.currencyField)) {
      throw new IllegalArgumentException("Price and currency mappings must be distinct");
    }
  }

  /** Only invoke after the caller's authorization, threat and policy checks have passed. */
  public SearchResponse executeAfterPolicyApproval(SemanticAnalysis analysis, boolean approved) {
    Objects.requireNonNull(analysis, "analysis");
    if (!enabled) {
      throw new IllegalStateException("Structured search is disabled");
    }
    if (!approved) {
      throw new SecurityException("Structured search requires explicit policy approval");
    }
    return coordinator.execute(analysis, priceField, currencyField);
  }

  private static String validateField(String field) {
    Objects.requireNonNull(field, "field");
    // Mapping paths are configured by the operator, never supplied by the search user.
    // Stage 22: bound operator-controlled mapping paths as defense in depth.
    if (field.length() > 128 || field.split("\\.", -1).length > 8) {
      throw new IllegalArgumentException("Structured mapping field exceeds limits");
    }
    if (!field.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
      throw new IllegalArgumentException("Invalid structured search mapping field");
    }
    return field;
  }
}
