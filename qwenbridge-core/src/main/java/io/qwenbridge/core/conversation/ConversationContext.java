package io.qwenbridge.core.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The versioned, bounded conversation context contract (QB-139). It carries what a follow-up turn
 * needs: the constraints in force with their provenance, the locked fields, results the user marked
 * as not relevant, and a short turn history. It never carries result documents, user text beyond
 * the stated values, or credentials.
 *
 * <p>Whoever holds it (a server session, or a client between stateless turns) cannot widen it:
 * {@link ContextValidator} re-checks owner, expiry, limits and schema every time it is used.
 */
public record ConversationContext(
    String version,
    String conversationId,
    String tenant,
    String subject,
    int revision,
    Instant issuedAt,
    Instant expiresAt,
    List<ContextConstraint> constraints,
    List<String> lockedFields,
    List<String> suppressed,
    List<Turn> turns) {

  public static final String VERSION = "conversation-context/1.0";

  /** A past turn: its request id and outcome. */
  public record Turn(String requestId, String status) {
    public Turn {
      Objects.requireNonNull(requestId, "requestId must not be null");
      Objects.requireNonNull(status, "status must not be null");
    }
  }

  public ConversationContext {
    Objects.requireNonNull(version, "version must not be null");
    Objects.requireNonNull(conversationId, "conversationId must not be null");
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    constraints = constraints == null ? List.of() : List.copyOf(constraints);
    lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
    suppressed = suppressed == null ? List.of() : List.copyOf(suppressed);
    turns = turns == null ? List.of() : List.copyOf(turns);
    if (revision < 0) {
      throw new IllegalArgumentException("revision must not be negative");
    }
  }
}
