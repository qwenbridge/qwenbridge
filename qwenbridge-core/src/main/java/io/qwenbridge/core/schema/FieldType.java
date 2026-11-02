package io.qwenbridge.core.schema;

import io.qwenbridge.core.queryir.IrValue;

/** Semantic type of a schema field and the QueryIR values it accepts (QB-044, QB-045). */
public enum FieldType {
  /** Free text; accepts text values. */
  TEXT,
  /** Exact identifiers (SKUs, IP addresses, pod names); accepts text values, matched exactly. */
  IDENTIFIER,
  NUMBER,
  /** A measured amount with a unit; accepts quantity values with the field's unit. */
  QUANTITY,
  /** An amount of money; accepts money values. */
  MONEY,
  BOOLEAN,
  /** A point in time; accepts instant/date values and relative windows. */
  TEMPORAL;

  public boolean numeric() {
    return this == NUMBER || this == QUANTITY || this == MONEY;
  }

  /** Whether a scalar QueryIR value has the right type for this field. */
  public boolean accepts(IrValue value) {
    return switch (this) {
      case TEXT, IDENTIFIER -> value instanceof IrValue.Text;
      case NUMBER -> value instanceof IrValue.Number;
      case QUANTITY -> value instanceof IrValue.Quantity;
      case MONEY -> value instanceof IrValue.Money;
      case BOOLEAN -> value instanceof IrValue.Bool;
      case TEMPORAL ->
          value instanceof IrValue.Temporal t && t.kind() != IrValue.Temporal.Kind.DURATION;
    };
  }
}
