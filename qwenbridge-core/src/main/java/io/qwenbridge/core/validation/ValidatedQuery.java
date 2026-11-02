package io.qwenbridge.core.validation;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.schema.SchemaKey;
import java.util.List;
import java.util.Objects;

/**
 * The pre-compilation boundary (QB-054): the only input query compilers may accept. It can only be
 * created by {@link ConstraintValidator}, so every constraint in it is schema-valid for {@code
 * schema}, free of contradictions, and carries the operator, value and negation it was given by the
 * user. No backend syntax is ever produced from anything else, in particular not from free-form
 * model output.
 */
public final class ValidatedQuery {

  private final QueryIR ir;
  private final SchemaKey schema;
  private final List<Constraint> filters;
  private final List<Constraint> preferences;

  ValidatedQuery(
      QueryIR ir, SchemaKey schema, List<Constraint> filters, List<Constraint> preferences) {
    this.ir = Objects.requireNonNull(ir, "ir must not be null");
    this.schema = Objects.requireNonNull(schema, "schema must not be null");
    this.filters = List.copyOf(filters);
    this.preferences = List.copyOf(preferences);
  }

  /** The mapped QueryIR the constraints belong to. */
  public QueryIR ir() {
    return ir;
  }

  public SchemaKey schema() {
    return schema;
  }

  /** HARD constraints: results must satisfy all of them. */
  public List<Constraint> filters() {
    return filters;
  }

  /** SOFT constraints (including uncertain ones): may influence ranking, never exclude. */
  public List<Constraint> preferences() {
    return preferences;
  }
}
