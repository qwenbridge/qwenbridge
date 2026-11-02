package io.qwenbridge.ranking.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.ranking.BusinessRule;
import io.qwenbridge.core.ranking.RankingPolicy;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The configured ranking policy (QB-117, QB-121). It is read once at startup and validated by the
 * Core {@link RankingPolicy}; an invalid policy or rule stops startup instead of ranking with
 * something other than what was configured.
 *
 * <p>Business rules are configured as JSON: {@code [{"id":"promote-stock","field":"inStock",
 * "value":true,"boost":0.5}]}. A rule matches when the field equals the value (text, number or
 * boolean). Rules only reorder results that passed authorization and every HARD constraint.
 */
@Singleton
public final class RankingPolicies {
  private final RankingPolicy policy;

  @Inject
  public RankingPolicies(
      @ConfigProperty(name = "qwenbridge.ranking.policy.id", defaultValue = "qwenbridge-baseline")
          String id,
      @ConfigProperty(name = "qwenbridge.ranking.policy.version", defaultValue = "1.0")
          String version,
      @ConfigProperty(name = "qwenbridge.ranking.policy.retrieval-weight", defaultValue = "1.0")
          double retrievalWeight,
      @ConfigProperty(name = "qwenbridge.ranking.policy.preference-weight", defaultValue = "0.5")
          double preferenceWeight,
      @ConfigProperty(name = "qwenbridge.ranking.policy.business-weight", defaultValue = "0.0")
          double businessWeight,
      @ConfigProperty(name = "qwenbridge.ranking.policy.rerank-window", defaultValue = "20")
          int rerankWindow,
      @ConfigProperty(name = "qwenbridge.ranking.business-rules") Optional<String> rules) {
    this.policy =
        new RankingPolicy(
            id,
            version,
            retrievalWeight,
            preferenceWeight,
            businessWeight,
            rules.map(RankingPolicies::parseRules).orElse(List.of()),
            rerankWindow);
  }

  public RankingPolicy policy() {
    return policy;
  }

  static List<BusinessRule> parseRules(String json) {
    JsonNode root;
    try {
      root = new ObjectMapper().readTree(json);
    } catch (java.io.IOException ex) {
      throw new IllegalArgumentException("qwenbridge.ranking.business-rules is not valid JSON", ex);
    }
    if (root == null || !root.isArray() || root.size() > 64)
      throw new IllegalArgumentException("qwenbridge.ranking.business-rules must be an array");
    List<BusinessRule> out = new ArrayList<>();
    for (JsonNode rule : root) {
      String field = rule.path("field").asText("");
      if (!field.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
        throw new IllegalArgumentException("Invalid business rule field");
      JsonNode value = rule.path("value");
      FieldValue typed =
          value.isBoolean()
              ? new FieldValue.Bool(value.booleanValue())
              : value.isNumber()
                  ? new FieldValue.Number(value.decimalValue())
                  : value.isTextual() ? FieldValue.text(value.textValue()) : null;
      if (typed == null) throw new IllegalArgumentException("Invalid business rule value");
      if (!rule.path("boost").isNumber())
        throw new IllegalArgumentException("Invalid business rule boost");
      out.add(
          new BusinessRule(
              rule.path("id").asText(""),
              new Filter.Compare(field, Filter.Compare.Op.EQ, typed),
              rule.path("boost").doubleValue()));
    }
    return List.copyOf(out);
  }
}
