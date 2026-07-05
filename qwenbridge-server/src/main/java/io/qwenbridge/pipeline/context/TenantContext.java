package io.qwenbridge.pipeline.context;

import java.util.Objects;

public record TenantContext(String tenantId, String environment, String brand) {

  public TenantContext {

    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(environment, "environment must not be null");
    Objects.requireNonNull(brand, "brand must not be null");
  }
}
