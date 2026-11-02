package io.qwenbridge.core.context;

import io.qwenbridge.core.retrieval.CancellationToken;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * The request a piece of work belongs to (QB-182, QB-184): its request ID, an optional W3C trace
 * parent, an optional deadline and a cancellation flag. API handlers open a scope for the thread
 * that does the work; Core stages, connector guards and connectors read it to stop early, to bound
 * their own timeouts by the time left and to tag outbound calls for correlation.
 *
 * <p>The scope is held in a thread-local and is never inherited implicitly: work handed to another
 * thread carries it only through {@link #propagate(Callable)}. A closed scope is never current, so
 * a reference that outlives its request cannot affect later work.
 */
public final class RequestScope {

  /** Why a request stopped before it finished. */
  public enum Interruption {
    CANCELLED,
    DEADLINE_EXCEEDED
  }

  /** Thrown at a checkpoint when the current request was cancelled or ran out of time. */
  public static final class Interrupted extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Interruption reason;

    public Interrupted(Interruption reason) {
      super(
          reason == Interruption.CANCELLED ? "Request was cancelled" : "Request deadline exceeded");
      this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Interruption reason() {
      return reason;
    }
  }

  /** Restores the previous scope of the thread when closed. */
  public interface Handle extends AutoCloseable {
    @Override
    void close();
  }

  private static final ThreadLocal<RequestScope> CURRENT = new ThreadLocal<>();
  private static final Pattern TRACEPARENT =
      Pattern.compile("00-(?!0{32})[0-9a-f]{32}-(?!0{16})[0-9a-f]{16}-[0-9a-f]{2}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final String requestId;
  private final String traceparent;
  private final Instant deadline;
  private final Clock clock;
  private final CancellationToken cancellation = CancellationToken.create();
  private volatile boolean closed;

  private RequestScope(String requestId, String traceparent, Instant deadline, Clock clock) {
    if (!RequestIds.isValid(requestId)) {
      throw new IllegalArgumentException("requestId is not a valid identifier");
    }
    this.requestId = requestId;
    // An invalid trace parent is dropped rather than forwarded to backends.
    this.traceparent = traceparent != null && validTraceparent(traceparent) ? traceparent : null;
    this.deadline = deadline;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * @param traceparent W3C {@code traceparent} of the incoming request, or null
   * @param deadline when the request must be finished, or null for none
   */
  public static RequestScope create(
      String requestId, String traceparent, Instant deadline, Clock clock) {
    return new RequestScope(requestId, traceparent, deadline, clock);
  }

  public static boolean validTraceparent(String value) {
    return value != null && TRACEPARENT.matcher(value).matches();
  }

  /** The open scope of the calling thread. */
  public static Optional<RequestScope> current() {
    RequestScope scope = CURRENT.get();
    return scope == null || scope.closed ? Optional.empty() : Optional.of(scope);
  }

  /** Throws when the calling thread's request was cancelled or ran out of time. */
  public static void checkpointCurrent() {
    current().ifPresent(RequestScope::checkpoint);
  }

  /** Makes this scope current for the calling thread until the handle is closed. */
  public Handle enter() {
    RequestScope previous = CURRENT.get();
    CURRENT.set(this);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  /** Ends the scope everywhere: it is no longer current on any thread that still holds it. */
  public void close() {
    closed = true;
  }

  public String requestId() {
    return requestId;
  }

  public Optional<String> traceparent() {
    return Optional.ofNullable(traceparent);
  }

  public Optional<Instant> deadline() {
    return Optional.ofNullable(deadline);
  }

  public void cancel() {
    cancellation.cancel();
  }

  /** The token retrieval providers poll (QB-107); cancelled together with this scope. */
  public CancellationToken cancellation() {
    return cancellation;
  }

  public boolean cancelled() {
    return cancellation.cancelled();
  }

  public boolean expired() {
    return deadline != null && !clock.instant().isBefore(deadline);
  }

  /** Cancellation wins over the deadline: a cancelled request is reported as cancelled. */
  public Optional<Interruption> interruption() {
    if (cancelled()) {
      return Optional.of(Interruption.CANCELLED);
    }
    return expired() ? Optional.of(Interruption.DEADLINE_EXCEEDED) : Optional.empty();
  }

  public void checkpoint() {
    Optional<Interruption> reason = interruption();
    if (reason.isPresent()) {
      throw new Interrupted(reason.get());
    }
  }

  /** Time left before the deadline (never negative), or empty without a deadline. */
  public Optional<Duration> remaining() {
    if (deadline == null) {
      return Optional.empty();
    }
    Duration left = Duration.between(clock.instant(), deadline);
    return Optional.of(left.isNegative() ? Duration.ZERO : left);
  }

  /** {@code timeout}, shortened to the time left when the deadline comes first. */
  public Duration bound(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout must not be null");
    return remaining().filter(left -> left.compareTo(timeout) < 0).orElse(timeout);
  }

  /** {@code timeout} bounded by the calling thread's request, if any. */
  public static Duration boundCurrent(Duration timeout) {
    return current().map(scope -> scope.bound(timeout)).orElse(timeout);
  }

  /** Runs {@code work} inside this scope on whatever thread executes the returned callable. */
  public <T> Callable<T> propagate(Callable<T> work) {
    Objects.requireNonNull(work, "work must not be null");
    return () -> {
      try (Handle ignored = enter()) {
        return work.call();
      }
    };
  }

  /**
   * A {@code traceparent} for an outbound call: the same trace, a new span ID, the same flags.
   * Empty when the request carried no trace parent.
   */
  public Optional<String> outboundTraceparent() {
    if (traceparent == null) {
      return Optional.empty();
    }
    byte[] span = new byte[8];
    String spanId;
    do {
      RANDOM.nextBytes(span);
      spanId = HexFormat.of().formatHex(span);
    } while (spanId.equals("0000000000000000"));
    return Optional.of(traceparent.substring(0, 36) + spanId + traceparent.substring(52));
  }
}
