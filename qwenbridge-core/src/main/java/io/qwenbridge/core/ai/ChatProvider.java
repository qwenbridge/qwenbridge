package io.qwenbridge.core.ai;

import java.util.function.Consumer;

/**
 * The provider-neutral chat SPI (QB-243), separate from embedding (QB-110) and reranking (QB-119).
 * Implementations may live in any module; the Core depends on none. Callers go through {@link
 * ChatRouter}, which checks capabilities first and enforces timeouts and cancellation.
 */
public interface ChatProvider {

  String id();

  ModelDescriptor model();

  ChatCapabilities capabilities();

  /**
   * Answers a call. When {@code call.streaming()} is true, partial text goes to {@code tokens} as
   * it arrives and the full text is returned at the end. May throw; the router maps exceptions to
   * {@code UNAVAILABLE}.
   */
  ChatResult complete(ChatCall call, Consumer<String> tokens);
}
