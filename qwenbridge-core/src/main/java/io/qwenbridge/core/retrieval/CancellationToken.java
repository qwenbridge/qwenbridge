package io.qwenbridge.core.retrieval;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation for a retrieval call (QB-107). */
public final class CancellationToken {

  private final AtomicBoolean cancelled = new AtomicBoolean();

  public static CancellationToken create() {
    return new CancellationToken();
  }

  public void cancel() {
    cancelled.set(true);
  }

  public boolean cancelled() {
    return cancelled.get();
  }
}
