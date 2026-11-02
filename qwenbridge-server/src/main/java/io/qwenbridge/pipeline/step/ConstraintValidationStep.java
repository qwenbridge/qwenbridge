package io.qwenbridge.pipeline.step;

import io.qwenbridge.core.validation.ConstraintValidator;
import io.qwenbridge.core.validation.ValidationReport;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.BoundSchema;
import io.qwenbridge.pipeline.result.ConstraintValidationResult;
import io.qwenbridge.pipeline.result.QueryIrResult;
import io.qwenbridge.schema.DomainMappings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Runs the single Core constraint validation gate (QB-050–QB-056) on the assembled QueryIR against
 * the bound domain schema and the pack's approved mapping rules. Invalid HARD constraints and
 * contradictions are reported as blocking, never removed; SOFT ones are classified.
 *
 * <p>In v1 the result is reported, not enforced: execution still uses the legacy analysis. Query
 * compilers introduced in Section 2 accept only the resulting {@code ValidatedQuery}.
 */
@ApplicationScoped
public class ConstraintValidationStep implements PipelineStep<ConstraintValidationResult> {

  private final DomainMappings mappings;

  @Inject
  public ConstraintValidationStep(DomainMappings mappings) {
    this.mappings = mappings;
  }

  @Override
  public PipelineStage stage() {
    return PipelineStage.INTENT;
  }

  @Override
  public String name() {
    return "ConstraintValidationStep";
  }

  @Override
  public int order() {
    return 32;
  }

  @Override
  public boolean publishEvents() {
    return false;
  }

  @Override
  public Class<ConstraintValidationResult> resultType() {
    return ConstraintValidationResult.class;
  }

  @Override
  public ConstraintValidationResult execute(ExecutionContext context) {
    QueryIrResult ir = context.get(QueryIrResult.class);
    if (ir == null || ir.ir().isEmpty()) {
      return ConstraintValidationResult.without(ConstraintValidationResult.Status.NO_QUERY_IR);
    }
    BoundSchema bound = context.get(BoundSchema.class);
    if (bound == null) {
      return ConstraintValidationResult.without(ConstraintValidationResult.Status.NO_SCHEMA);
    }
    ValidationReport report =
        ConstraintValidator.validate(
            ir.ir().get(), bound.schema(), mappings.forSchema(bound.schema().id()));
    return ConstraintValidationResult.of(bound.schema().key(), report);
  }
}
