package io.qwenbridge.schema;

import io.qwenbridge.core.validation.DomainMapping;
import io.qwenbridge.core.validation.MappingRule;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/**
 * Approved semantic-to-structured mapping rules per domain schema (QB-052, QB-053).
 *
 * <p>Rules for the bundled packs live here until the pack vocabulary SPI ships them with the pack
 * itself. They only state what each pack's schema already implies; no thresholds or interpretations
 * of subjective words are invented:
 *
 * <ul>
 *   <li>commerce: extraction's generic {@code amount} proposal is the {@code price} field, and a
 *       money value on {@code price} (a NUMBER field) is split into the amount and an equality on
 *       the pack's {@code currency} field.
 * </ul>
 */
@ApplicationScoped
public class DomainMappings {

  private static final Map<String, DomainMapping> BUILT_IN =
      Map.of(
          "commerce",
          new DomainMapping(
              "commerce",
              List.of(
                  new MappingRule.FieldAlias("commerce-amount-is-price", "amount", "price"),
                  new MappingRule.MoneyToNumberAndCurrency(
                      "commerce-price-money", "price", "currency"))));

  public DomainMapping forSchema(String schemaId) {
    return BUILT_IN.getOrDefault(schemaId, DomainMapping.none(schemaId));
  }
}
