package io.qwenbridge.pipeline.result;

import io.qwenbridge.core.schema.DomainSchema;
import java.util.Objects;

/** The domain schema the request's QueryIR was bound to (first enabled hinted domain). */
public record BoundSchema(DomainSchema schema) {
  public BoundSchema {
    Objects.requireNonNull(schema, "schema must not be null");
  }
}
