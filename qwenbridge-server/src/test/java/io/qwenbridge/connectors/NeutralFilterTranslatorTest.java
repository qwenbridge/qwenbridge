package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-100, QB-059: translations never put user values into query text and always scope the tenant.
 */
class NeutralFilterTranslatorTest {

  static final String PAYLOAD = "x' OR '1'='1; DROP TABLE t; --";

  static final Filter HOSTILE =
      new Filter.And(
          List.of(
              new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text(PAYLOAD)),
              new Filter.Not(new Filter.Contains("description", PAYLOAD)),
              new Filter.In("color", List.of(FieldValue.text(PAYLOAD)))));

  @Test
  void openSearchKeepsValuesAsLeavesAndScopesTheTenant() {
    var dsl = NeutralFilterTranslator.openSearch("acme", HOSTILE).toString();
    assertThat(dsl).contains("tenantId=acme").contains("brand.keyword");
    var keys = new java.util.ArrayList<String>();
    collectKeys(NeutralFilterTranslator.openSearch("acme", HOSTILE), keys);
    assertThat(keys).doesNotContain(PAYLOAD);
  }

  @Test
  void storedDocumentsBecomeTypedFields() {
    var fields =
        NeutralFilterTranslator.fields(
            Map.of("brand", "Nike", "price", 99.9, "inStock", true, "tags", List.of("a", "b")));
    assertThat(fields.get("brand")).isEqualTo(FieldValue.text("Nike"));
    assertThat(fields.get("price")).isEqualTo(FieldValue.number("99.9"));
    assertThat(fields.get("inStock")).isEqualTo(new FieldValue.Bool(true));
    assertThat(fields.get("tags")).isEqualTo(new FieldValue.TextList(List.of("a", "b")));
  }

  @SuppressWarnings("unchecked")
  private static void collectKeys(Object node, List<String> keys) {
    if (node instanceof Map<?, ?> m)
      m.forEach(
          (k, v) -> {
            keys.add(String.valueOf(k));
            collectKeys(v, keys);
          });
    else if (node instanceof List<?> l) l.forEach(x -> collectKeys(x, keys));
  }
}
