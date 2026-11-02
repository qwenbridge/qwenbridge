package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.core.conversation.ContextConstraint;
import io.qwenbridge.core.conversation.ContextLimits;
import io.qwenbridge.core.conversation.ContextMerge;
import io.qwenbridge.core.conversation.ContextTokens;
import io.qwenbridge.core.conversation.ContextValidator;
import io.qwenbridge.core.conversation.ConversationContext;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.security.ApiIdentity;
import jakarta.inject.Inject;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Conversation context for Commerce (QB-139 – QB-145).
 *
 * <ul>
 *   <li>A server session's context can be read as the versioned contract.
 *   <li>Stateless follow-ups: the client sends back the context it was given, either inside a
 *       signed token (trusted provenance) or as plain JSON (untrusted: re-validated and downgraded
 *       to CONTEXT provenance). Either way it must belong to the caller and stay within limits.
 *   <li>Constraints carried between turns keep their provenance and turn through the Core merge.
 *   <li>Signing is optional: with {@code qwenbridge.context.signing.private-key} and {@code
 *       public-key} (Ed25519, PKCS#8 and X.509, base64) tokens are issued; without them plain
 *       context still works and tokens are refused.
 * </ul>
 */
@jakarta.inject.Singleton
public final class ConversationContexts {
  static final String SOURCE = "context:";

  @Inject CommerceSearchService search;
  @Inject ObjectMapper mapper;

  private final ContextTokens tokens;
  private final Duration ttl;
  private final ContextLimits limits = ContextLimits.DEFAULT;

  @Inject
  public ConversationContexts(
      @ConfigProperty(name = "qwenbridge.context.signing.key-id", defaultValue = "context-1")
          String keyId,
      @ConfigProperty(name = "qwenbridge.context.signing.private-key") Optional<String> privateKey,
      @ConfigProperty(name = "qwenbridge.context.signing.public-key") Optional<String> publicKey,
      @ConfigProperty(name = "qwenbridge.context.ttl", defaultValue = "30m") Duration ttl) {
    if (privateKey.isPresent() && publicKey.isEmpty())
      throw new IllegalArgumentException("Context signing needs the public key as well");
    if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(ContextLimits.DEFAULT.maxTtl()) > 0)
      throw new IllegalArgumentException("qwenbridge.context.ttl must be within 1 hour");
    this.ttl = ttl;
    try {
      var factory = KeyFactory.getInstance("Ed25519");
      Optional<PrivateKey> signing =
          privateKey.map(
              k -> {
                try {
                  return factory.generatePrivate(
                      new PKCS8EncodedKeySpec(Base64.getDecoder().decode(k.strip())));
                } catch (java.security.GeneralSecurityException ex) {
                  throw new IllegalArgumentException("Invalid context signing key", ex);
                }
              });
      Map<String, PublicKey> verify =
          publicKey.isEmpty()
              ? Map.of()
              : Map.of(
                  keyId,
                  factory.generatePublic(
                      new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.get().strip()))));
      this.tokens = new ContextTokens("qwenbridge", "qwenbridge-commerce", keyId, signing, verify);
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalArgumentException("Invalid context verification key", ex);
    }
  }

  /** A context and, when signing is configured, its token. */
  public record ContextView(ConversationContext context, String token) {}

  /** A stateless follow-up: the query and the context the client was given (one of the two). */
  public record TurnRequest(
      String query, JsonNode context, String contextToken, String backend, Integer limit) {}

  public record TurnResult(SearchResponse response, ConversationContext context, String token) {}

  /** Context failures map to HTTP statuses; the reasons are named. */
  static RuntimeException refused(List<String> reasons) {
    int status =
        reasons.contains("FOREIGN")
            ? 403
            : reasons.contains("EXPIRED")
                ? 410
                : reasons.contains("TOO_LARGE") || reasons.contains("TOO_MANY_TURNS") ? 413 : 400;
    // 400 goes through the IllegalArgument mapper, which keeps the (sanitized) reason; the generic
    // 400 mapper would replace it with a JSON-syntax message.
    return status == 400
        ? new IllegalArgumentException("Context refused: " + reasons)
        : new jakarta.ws.rs.ClientErrorException("Context refused: " + reasons, status);
  }

  /** QB-139: the session's context as the versioned contract. */
  public ContextView session(CommerceSessions.Session session) {
    var turns =
        session.turns().stream()
            .map(t -> new ConversationContext.Turn(t.requestId(), t.status()))
            .toList();
    List<ContextConstraint> constraints = new ArrayList<>();
    if (session.last() != null && session.last().queryIR() != null)
      for (var c : session.last().queryIR().constraints())
        constraints.add(context(c, Math.max(1, session.revision())));
    var ctx =
        new ConversationContext(
            ConversationContext.VERSION,
            session.id(),
            session.tenant(),
            session.subject(),
            session.revision(),
            Instant.now(),
            session.expiresAt(),
            constraints,
            session.lockedFields(),
            List.of(),
            turns);
    return new ContextView(ctx, tokens.canIssue() ? tokens.issue(ctx) : null);
  }

  /** QB-140, QB-141, QB-144: one stateless follow-up turn. */
  public TurnResult turn(ApiIdentity.Principal caller, TurnRequest request) {
    caller.require("search");
    if (request == null || request.query() == null)
      throw new IllegalArgumentException("Query required");
    if (request.context() != null && request.contextToken() != null)
      throw new IllegalArgumentException("Send either a context or a context token");
    ConversationContext previous = resolve(caller, request);
    List<Constraint> inherited =
        previous == null
            ? List.of()
            : previous.constraints().stream().map(ConversationContexts::commerce).toList();
    var searchRequest =
        new SearchRequest(
            null,
            request.query(),
            "en",
            "commerce",
            1,
            null,
            List.of(),
            false,
            request.backend(),
            request.limit(),
            null,
            previous == null ? List.of() : previous.lockedFields());
    var response = search.searchInContext(searchRequest, caller, inherited);
    var suppressed = previous == null ? List.<String>of() : previous.suppressed();
    if (!suppressed.isEmpty()) {
      var hidden = new HashSet<>(suppressed);
      var hits = response.results().hits().stream().filter(h -> !hidden.contains(h.id())).toList();
      response =
          response.withResults(
              new SearchResultSet(
                  hits,
                  Math.max(
                      0,
                      response.results().totalHits()
                          - (response.results().hits().size() - hits.size())),
                  response.results().tookMillis()));
    }
    int turn = previous == null ? 1 : previous.turns().size() + 1;
    List<ContextConstraint> constraints = new ArrayList<>();
    if (response.queryIR() != null)
      for (var c : response.queryIR().constraints()) constraints.add(context(c, turn));
    else if (previous != null) constraints.addAll(previous.constraints());
    List<ConversationContext.Turn> turns =
        new ArrayList<>(previous == null ? List.of() : previous.turns());
    turns.add(new ConversationContext.Turn(response.requestId(), response.status()));
    if (turns.size() > limits.maxTurns())
      turns = turns.subList(turns.size() - limits.maxTurns(), turns.size());
    Instant now = Instant.now();
    var next =
        new ConversationContext(
            ConversationContext.VERSION,
            previous == null ? UUID.randomUUID().toString() : previous.conversationId(),
            caller.tenant(),
            caller.subject(),
            previous == null ? 1 : previous.revision() + 1,
            now,
            now.plus(ttl),
            constraints,
            previous == null ? List.of() : previous.lockedFields(),
            suppressed,
            turns);
    return new TurnResult(response, next, tokens.canIssue() ? tokens.issue(next) : null);
  }

  private ConversationContext resolve(ApiIdentity.Principal caller, TurnRequest request) {
    Instant now = Instant.now();
    if (request.contextToken() != null) {
      var verified = tokens.verify(request.contextToken(), now);
      if (verified.failure().isPresent())
        throw verified.failure().get() == ContextTokens.Failure.EXPIRED
            ? new jakarta.ws.rs.ClientErrorException("Context token expired", 410)
            : new IllegalArgumentException("Signed context refused: " + verified.failure().get());
      return checked(caller, verified.context().orElseThrow(), verified.bytes(), true, now);
    }
    if (request.context() == null) return null;
    int bytes;
    ConversationContext raw;
    try {
      bytes = mapper.writeValueAsBytes(request.context()).length;
      if (bytes > limits.maxBytes()) throw refused(List.of("TOO_LARGE"));
      raw = mapper.treeToValue(request.context(), ConversationContext.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid context", ex);
    }
    return checked(caller, raw, bytes, false, now);
  }

  private ConversationContext checked(
      ApiIdentity.Principal caller,
      ConversationContext context,
      int bytes,
      boolean signed,
      Instant now) {
    var schema = CommerceSchemaRegistry.defaults();
    var result =
        ContextValidator.validate(
            context,
            bytes,
            signed,
            caller.tenant(),
            caller.subject(),
            now,
            limits,
            (field, op) -> {
              var f = schema.fields().get(field);
              if (f == null) return false;
              if (op == null) return true;
              try {
                return f.operators().contains(Operator.valueOf(op));
              } catch (IllegalArgumentException ex) {
                return false;
              }
            });
    if (!result.valid()) throw refused(result.reasons().stream().map(Enum::name).toList());
    return result.context().orElseThrow();
  }

  /** A Commerce constraint carried from context; its evidence source records origin and turn. */
  static Constraint commerce(ContextConstraint c) {
    String quote = c.field() + " " + c.operator() + " " + c.value();
    return new Constraint(
        c.field(),
        Operator.valueOf(c.operator()),
        c.value(),
        new Evidence(SOURCE + c.origin() + ":" + c.turn(), 0, quote.length(), quote));
  }

  /** The context form of a Commerce constraint; stated in this turn unless carried from context. */
  static ContextConstraint context(Constraint c, int turn) {
    String source = c.evidence().source();
    if (source.startsWith(SOURCE)) {
      String[] parts = source.substring(SOURCE.length()).split(":");
      try {
        return new ContextConstraint(
            c.field(),
            c.operator().name(),
            c.value(),
            ContextConstraint.Origin.valueOf(parts[0]),
            Integer.parseInt(parts[1]));
      } catch (RuntimeException ex) {
        return new ContextConstraint(
            c.field(), c.operator().name(), c.value(), ContextConstraint.Origin.CONTEXT, turn);
      }
    }
    return new ContextConstraint(
        c.field(),
        c.operator().name(),
        c.value(),
        "original".equals(source)
            ? ContextConstraint.Origin.USER_TEXT
            : ContextConstraint.Origin.CONTEXT,
        turn);
  }

  /**
   * QB-144: the Core merge over Commerce constraints. Carried constraints keep their objects (and
   * so their provenance) unless the new turn clears or replaces them.
   */
  static List<Constraint> merge(
      List<Constraint> inherited, List<Constraint> stated, Set<String> cleared) {
    var back = new IdentityHashMap<ContextConstraint, Constraint>();
    List<ContextConstraint> previous = new ArrayList<>();
    for (Constraint c : inherited) {
      var cc = context(c, 1);
      back.put(cc, c);
      previous.add(cc);
    }
    List<ContextConstraint> now = new ArrayList<>();
    for (Constraint c : stated) {
      var cc =
          new ContextConstraint(
              c.field(), c.operator().name(), c.value(), ContextConstraint.Origin.USER_TEXT, 1);
      back.put(cc, c);
      now.add(cc);
    }
    return ContextMerge.followUp(previous, now, cleared).stream().map(back::get).toList();
  }
}
