package io.qwenbridge.pack;

import java.util.*;

public interface PolicyPack extends QwenBridgePack {
  enum Effect {
    ABSTAIN,
    ALLOW,
    DENY,
    REVIEW
  }

  record Input(
      String tenant,
      String subject,
      Set<String> roles,
      String domain,
      String operation,
      Map<String, Object> attributes) {
    public Input {
      roles = Set.copyOf(roles);
      attributes = Map.copyOf(attributes);
    }
  }

  record Decision(Effect effect, String rule, String reason) {
    public Decision {
      Objects.requireNonNull(effect);
      if (rule == null || reason == null || reason.isBlank())
        throw new IllegalArgumentException("Policy evidence required");
    }
  }

  /** The policy evaluation contract this runtime implements (QB-249, QB-252). */
  int CONTRACT = 1;

  /** The evaluation contract the pack was written for; any other value is refused at activation. */
  default int contract() {
    return CONTRACT;
  }

  /**
   * The operations the pack's rules apply to (QB-249, QB-251), or {@code "*"} for all. Every named
   * operation must be one the central policy catalog knows; otherwise activation is refused.
   */
  default Set<String> operations() {
    return Set.of("*");
  }

  /** Lower first; ties are broken by pack id (QB-250). Must be within -10000..10000. */
  int order();

  Set<String> domains();

  Decision evaluate(Input input);
}
