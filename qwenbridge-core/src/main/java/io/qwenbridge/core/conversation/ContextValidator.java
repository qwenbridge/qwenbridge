package io.qwenbridge.core.conversation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Re-validates a conversation context before it is used (QB-140, QB-143, QB-145).
 *
 * <ul>
 *   <li>It must belong to the caller: same tenant and subject. Anything else is refused as {@code
 *       FOREIGN}, never silently ignored, so context cannot leak across users or tenants.
 *   <li>It must not be expired, issued in the future, or live longer than the limit.
 *   <li>It must be within the count and byte limits.
 *   <li>Every constraint and locked field must be valid for the schema ({@code allowed}).
 *   <li>A context the server did not sign is untrusted: its constraints keep their values but are
 *       downgraded to {@link ContextConstraint.Origin#CONTEXT}, so a client cannot claim the user
 *       stated something.
 * </ul>
 */
public final class ContextValidator {

  public enum Reason {
    WRONG_VERSION,
    FOREIGN,
    EXPIRED,
    NOT_YET_VALID,
    TTL_TOO_LONG,
    TOO_LARGE,
    TOO_MANY_TURNS,
    TOO_MANY_CONSTRAINTS,
    TOO_MANY_LOCKED_FIELDS,
    TOO_MANY_SUPPRESSED,
    UNKNOWN_FIELD
  }

  public record Result(Optional<ConversationContext> context, List<Reason> reasons) {
    public Result {
      context = context == null ? Optional.empty() : context;
      reasons = List.copyOf(reasons);
    }

    public boolean valid() {
      return context.isPresent();
    }
  }

  private ContextValidator() {}

  /**
   * @param serializedBytes the size of the context as received
   * @param signed whether the context came inside a verified server token
   * @param allowed whether (field, operator) is valid for the schema; operator is null for a locked
   *     field
   */
  public static Result validate(
      ConversationContext context,
      int serializedBytes,
      boolean signed,
      String tenant,
      String subject,
      Instant now,
      ContextLimits limits,
      BiPredicate<String, String> allowed) {
    Objects.requireNonNull(context, "context must not be null");
    List<Reason> reasons = new ArrayList<>();
    if (!ConversationContext.VERSION.equals(context.version())) {
      reasons.add(Reason.WRONG_VERSION);
    }
    if (!context.tenant().equals(tenant) || !context.subject().equals(subject)) {
      reasons.add(Reason.FOREIGN);
    }
    if (!now.isBefore(context.expiresAt())) {
      reasons.add(Reason.EXPIRED);
    }
    if (context.issuedAt().isAfter(now.plusSeconds(60))) {
      reasons.add(Reason.NOT_YET_VALID);
    }
    if (context.expiresAt().isAfter(context.issuedAt().plus(limits.maxTtl()))) {
      reasons.add(Reason.TTL_TOO_LONG);
    }
    if (serializedBytes > limits.maxBytes()) {
      reasons.add(Reason.TOO_LARGE);
    }
    if (context.turns().size() > limits.maxTurns()) {
      reasons.add(Reason.TOO_MANY_TURNS);
    }
    if (context.constraints().size() > limits.maxConstraints()) {
      reasons.add(Reason.TOO_MANY_CONSTRAINTS);
    }
    if (context.lockedFields().size() > limits.maxLockedFields()) {
      reasons.add(Reason.TOO_MANY_LOCKED_FIELDS);
    }
    if (context.suppressed().size() > limits.maxSuppressed()) {
      reasons.add(Reason.TOO_MANY_SUPPRESSED);
    }
    boolean fieldsOk =
        context.constraints().stream().allMatch(c -> allowed.test(c.field(), c.operator()))
            && context.lockedFields().stream().allMatch(f -> allowed.test(f, null));
    if (!fieldsOk) {
      reasons.add(Reason.UNKNOWN_FIELD);
    }
    if (!reasons.isEmpty()) {
      return new Result(Optional.empty(), reasons);
    }
    ConversationContext accepted =
        signed
            ? context
            : new ConversationContext(
                context.version(),
                context.conversationId(),
                context.tenant(),
                context.subject(),
                context.revision(),
                context.issuedAt(),
                context.expiresAt(),
                context.constraints().stream()
                    .map(c -> c.withOrigin(ContextConstraint.Origin.CONTEXT))
                    .toList(),
                List.copyOf(new HashSet<>(context.lockedFields())).stream().sorted().toList(),
                context.suppressed(),
                context.turns());
    return new Result(Optional.of(accepted), List.of());
  }

  /** Byte size of a context as JSON (UTF-8). */
  public static int size(String json) {
    return json.getBytes(StandardCharsets.UTF_8).length;
  }
}
