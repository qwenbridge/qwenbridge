package io.qwenbridge.core.retrieval.filter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** A typed document or filter value (QB-100). Numbers are exact decimals, never doubles. */
public sealed interface FieldValue {

  record Text(String value) implements FieldValue {
    public Text {
      Objects.requireNonNull(value, "value must not be null");
    }
  }

  record Number(BigDecimal value) implements FieldValue {
    public Number {
      Objects.requireNonNull(value, "value must not be null");
    }
  }

  record Bool(boolean value) implements FieldValue {}

  record TextList(List<String> values) implements FieldValue {
    public TextList {
      values = List.copyOf(values);
    }
  }

  static FieldValue text(String value) {
    return new Text(value);
  }

  static FieldValue number(String value) {
    return new Number(new BigDecimal(value));
  }
}
