package io.qwenbridge.streaming.session;

/**
 * Abstraction over the transport used to push server-sent events to a client. Decouples {@link
 * StreamingSession} from the concrete JAX-RS SSE types so the registry remains unit-testable.
 */
public interface SessionSink {

  void send(String eventId, String eventName, Object payload);

  void close();

  boolean isClosed();

  /** No-op sink used for programmatic/test sessions with no attached client connection. */
  final class NoOp implements SessionSink {

    private volatile boolean closed;

    @Override
    public void send(String eventId, String eventName, Object payload) {
      // Intentionally does nothing.
    }

    @Override
    public void close() {
      this.closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
