package io.qwenbridge.core.threat;

import java.util.List;

/**
 * Provider-neutral threat detection SPI (QB-064). Implementations may be pattern rules, classifiers
 * or a model. A detector reports signals; it never decides the action (that is {@link
 * ThreatPolicy}) and never authorizes anything.
 *
 * <p>Detectors must treat the text as data, must not throw for ordinary input, and must not copy
 * the input into {@link ThreatSignal#evidence()}.
 */
public interface ThreatDetector {

  /** Stable identifier recorded in signals. */
  String id();

  /** Whether the detector is a deterministic rule set or a model. */
  ThreatSignal.Source source();

  List<ThreatSignal> detect(ThreatInput input);
}
