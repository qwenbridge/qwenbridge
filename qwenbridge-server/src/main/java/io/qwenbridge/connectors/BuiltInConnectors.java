package io.qwenbridge.connectors;

import io.qwenbridge.core.connector.CircuitBreaker;
import io.qwenbridge.core.connector.ConfigSchema;
import io.qwenbridge.core.connector.Connector;
import io.qwenbridge.core.connector.ConnectorCapabilities;
import io.qwenbridge.core.connector.ConnectorCapabilities.Access;
import io.qwenbridge.core.connector.ConnectorConfig;
import io.qwenbridge.core.connector.ConnectorRegistry;
import io.qwenbridge.core.connector.ConnectorResult;
import io.qwenbridge.core.connector.ConnectorRuntime;
import io.qwenbridge.core.connector.EnvironmentSecrets;
import io.qwenbridge.core.connector.RetryPolicy;
import io.qwenbridge.core.retrieval.RetrievalMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The built-in connectors as Core connectors (QB-153 – QB-158). Each backend gets a lifecycle
 * (DISABLED when it is not configured), a capability record, and a guard for its calls: a timeout,
 * one retry for transient read failures, and a circuit breaker that stops calling a backend after
 * repeated failures and tries again after a pause.
 *
 * <p>Caller errors (invalid requests, unsupported modes) are not failures of the connector: they
 * pass through unchanged and never open the circuit.
 */
public final class BuiltInConnectors {

  /** A backend call's outcome: a value, or a caller error to rethrow unchanged. */
  private record Outcome<T>(T value, RuntimeException callerError) {}

  public record View(
      String id,
      String state,
      String reason,
      List<String> access,
      List<String> modes,
      String circuit,
      String configuration) {
    public View {
      access = List.copyOf(access);
      modes = List.copyOf(modes);
    }
  }

  static final RetryPolicy READ_RETRY =
      new RetryPolicy(2, Duration.ofMillis(50), BuiltInConnectors::transient_);

  private final ConnectorRegistry registry = new ConnectorRegistry();

  public BuiltInConnectors(
      List<CommerceBackend> backends, int failureThreshold, Duration openFor, Duration timeout) {
    var clock = Clock.systemUTC();
    for (CommerceBackend backend : backends)
      registry.register(
          new ConnectorRuntime(
              adapter(backend),
              clock,
              new CircuitBreaker(failureThreshold, openFor, clock),
              timeout,
              d -> Thread.sleep(d.toMillis())));
    var configs = new java.util.LinkedHashMap<String, ConnectorConfig>();
    for (CommerceBackend backend : backends)
      if (backend.configured())
        configs.put(
            backend.id(),
            new ConnectorConfig(ConnectorConfig.VERSION, backend.id(), true, Map.of(), Map.of()));
    registry.startAll(configs, EnvironmentSecrets.system(Optional.empty()));
  }

  private static Connector adapter(CommerceBackend backend) {
    var modes = EnumSet.noneOf(RetrievalMode.class);
    for (String m : backend.modes())
      switch (m) {
        case "KEYWORD" -> modes.add(RetrievalMode.KEYWORD);
        case "VECTOR" -> modes.add(RetrievalMode.VECTOR);
        case "HYBRID" -> {
          modes.add(RetrievalMode.KEYWORD);
          modes.add(RetrievalMode.VECTOR);
        }
        default -> {}
      }
    var capabilities =
        new ConnectorCapabilities(
            backend.readOnly() ? EnumSet.of(Access.READ) : EnumSet.allOf(Access.class), modes);
    return new Connector() {
      @Override
      public String id() {
        return backend.id();
      }

      @Override
      public ConfigSchema configSchema() {
        // Built-in connectors are configured through qwenbridge.connectors.* properties, which
        // their constructors validate; the contract configuration carries nothing more.
        return ConfigSchema.NONE;
      }

      @Override
      public ConnectorCapabilities capabilities() {
        return capabilities;
      }

      @Override
      public void start(ConnectorConfig config, io.qwenbridge.core.connector.SecretResolver s) {}
    };
  }

  /** Transient failures worth one retry: I/O and timeouts, never caller errors. */
  static boolean transient_(Throwable t) {
    return t instanceof IOException
        || t instanceof UncheckedIOException
        || t instanceof java.util.concurrent.TimeoutException
        || t instanceof jakarta.ws.rs.ProcessingException
        || t instanceof jakarta.ws.rs.ServiceUnavailableException;
  }

  static boolean callerError(RuntimeException e) {
    return e instanceof IllegalArgumentException || e instanceof jakarta.ws.rs.ClientErrorException;
  }

  /** Runs a read through the backend's guard. */
  public <T> T read(String backend, Supplier<T> call) {
    return guarded(backend, Access.READ, READ_RETRY, call);
  }

  /** Runs a write or delete; writes are not retried (they may have happened). */
  public <T> T write(String backend, Access access, Supplier<T> call) {
    return guarded(backend, access, RetryPolicy.NONE, call);
  }

  private <T> T guarded(String backend, Access access, RetryPolicy retry, Supplier<T> call) {
    var runtime = registry.get(backend);
    if (runtime.isEmpty()) return call.get();
    var errors = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
    ConnectorResult<Outcome<T>> result =
        runtime
            .get()
            .execute(
                access,
                retry,
                () -> {
                  try {
                    return new Outcome<>(call.get(), null);
                  } catch (RuntimeException e) {
                    if (callerError(e)) return new Outcome<T>(null, e);
                    errors.set(e);
                    throw e;
                  }
                });
    return switch (result) {
      case ConnectorResult.Success<Outcome<T>> s -> {
        if (s.value().callerError() != null) throw s.value().callerError();
        yield s.value().value();
      }
      case ConnectorResult.Failed<Outcome<T>> f -> {
        if (f.failure() == ConnectorResult.Failure.FAILED && errors.get() != null)
          throw errors.get();
        // QB-182: the caller stopped (cancelled or out of time); the connector is not to blame.
        if (f.failure() == ConnectorResult.Failure.CANCELLED)
          throw new io.qwenbridge.core.context.RequestScope.Interrupted(
              io.qwenbridge.core.context.RequestScope.Interruption.CANCELLED);
        if (f.failure() == ConnectorResult.Failure.DEADLINE_EXCEEDED)
          throw new io.qwenbridge.core.context.RequestScope.Interrupted(
              io.qwenbridge.core.context.RequestScope.Interruption.DEADLINE_EXCEEDED);
        if (f.failure() == ConnectorResult.Failure.ACCESS_DENIED)
          throw new jakarta.ws.rs.ForbiddenException(
              "Connector "
                  + backend
                  + " does not allow "
                  + access.name().toLowerCase(java.util.Locale.ROOT));
        throw new jakarta.ws.rs.ServiceUnavailableException(
            "Connector " + backend + " unavailable: " + f.failure());
      }
    };
  }

  public List<View> views() {
    return registry.all().stream()
        .map(
            r ->
                new View(
                    r.id(),
                    r.state().name(),
                    r.reason(),
                    r.capabilities().access().stream().map(Enum::name).sorted().toList(),
                    r.capabilities().modes().stream().map(Enum::name).sorted().toList(),
                    r.circuit().name(),
                    ConnectorConfig.VERSION))
        .toList();
  }

  public void close() {
    registry.stopAll();
  }
}
