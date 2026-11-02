package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.connectors.CommerceBackends;
import io.qwenbridge.platform.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.*;
import java.util.*;

/** Owned durable conversation state. Its lifetime is independent of an SSE connection. */
@ApplicationScoped
public final class CommerceSessions {
  public record Create(Integer ttlSeconds, String backend, List<String> lockedFields) {
    public Create {
      ttlSeconds = ttlSeconds == null ? Integer.valueOf(1800) : ttlSeconds;
      lockedFields = lockedFields == null ? List.of() : List.copyOf(lockedFields);
      if (ttlSeconds < 60 || ttlSeconds > 3600)
        throw new IllegalArgumentException("Session TTL must be 60..3600 seconds");
    }
  }

  public record TurnRequest(
      String query, Integer expectedRevision, boolean resetContext, String mode, boolean rerank) {
    public TurnRequest {
      if (query == null || query.isBlank() || query.length() > 4096)
        throw new IllegalArgumentException("Invalid conversation turn");
      mode = mode == null ? "KEYWORD" : mode;
    }
  }

  public record Turn(String requestId, String query, String status, Instant at) {}

  public record Session(
      String id,
      String tenant,
      String subject,
      String backend,
      List<String> lockedFields,
      Instant createdAt,
      Instant expiresAt,
      int revision,
      String busy,
      Instant busySince,
      SearchResponse last,
      List<Turn> turns) {
    public Session {
      lockedFields = List.copyOf(lockedFields);
      turns = List.copyOf(turns);
    }
  }

  private final PlatformStore store;
  private final CommerceSearchService search;
  private final CommerceBackends backends;
  private final Entitlements entitlements;
  private final ZeroResultRecovery recovery;

  public CommerceSessions(
      PlatformStore store,
      CommerceSearchService search,
      CommerceBackends backends,
      Entitlements entitlements,
      ZeroResultRecovery recovery) {
    this.store = store;
    this.search = search;
    this.backends = backends;
    this.entitlements = entitlements;
    this.recovery = recovery;
  }

  public Session create(ApiIdentity.Principal caller, Create input) {
    caller.require("search");
    entitlements.require(caller.tenant(), "sessions");
    if (!CommerceSchemaRegistry.defaults().fields().keySet().containsAll(input.lockedFields()))
      throw new IllegalArgumentException("Unknown locked field");
    String backend = backends.backend(caller.tenant(), input.backend()).id();
    Instant now = Instant.now();
    var session =
        new Session(
            UUID.randomUUID().toString(),
            caller.tenant(),
            caller.subject(),
            backend,
            input.lockedFields(),
            now,
            now.plusSeconds(input.ttlSeconds()),
            0,
            null,
            null,
            null,
            List.of());
    return store.update(
        caller.tenant(),
        "sessions",
        session.id(),
        Session.class,
        Duration.ofSeconds(input.ttlSeconds()),
        previous -> {
          if (previous != null) throw new IllegalStateException("Session identity collision");
          return session;
        });
  }

  private Session owned(ApiIdentity.Principal caller, Session session) {
    if (session == null
        || !session.subject().equals(caller.subject())
        || !session.tenant().equals(caller.tenant()))
      throw new jakarta.ws.rs.NotFoundException("Session expired or unavailable");
    if (!Instant.now().isBefore(session.expiresAt()))
      throw new jakarta.ws.rs.ClientErrorException("Session expired", 410);
    return session;
  }

  public Session get(ApiIdentity.Principal caller, String id) {
    caller.require("search");
    entitlements.require(caller.tenant(), "sessions");
    return owned(caller, store.read(caller.tenant(), "sessions", id, Session.class));
  }

  private Session claim(ApiIdentity.Principal caller, String id, Integer revision, String nonce) {
    var before = get(caller, id);
    return store.update(
        caller.tenant(),
        "sessions",
        id,
        Session.class,
        Duration.between(Instant.now(), before.expiresAt()),
        previous -> {
          var s = owned(caller, previous);
          if (revision != null && revision != s.revision())
            throw new jakarta.ws.rs.ClientErrorException("Conversation revision changed", 409);
          if (s.busy() != null && s.busySince().plusSeconds(300).isAfter(Instant.now()))
            throw new jakarta.ws.rs.ClientErrorException("A turn is already running", 409);
          return new Session(
              s.id(),
              s.tenant(),
              s.subject(),
              s.backend(),
              s.lockedFields(),
              s.createdAt(),
              s.expiresAt(),
              s.revision(),
              nonce,
              Instant.now(),
              s.last(),
              s.turns());
        });
  }

  private Session finish(
      ApiIdentity.Principal caller, Session claimed, String query, SearchResponse response) {
    return store.update(
        caller.tenant(),
        "sessions",
        claimed.id(),
        Session.class,
        Duration.between(Instant.now(), claimed.expiresAt()),
        previous -> {
          var s = owned(caller, previous);
          if (!claimed.busy().equals(s.busy()))
            throw new jakarta.ws.rs.ClientErrorException("Conversation turn lease changed", 409);
          var turns = new ArrayList<>(s.turns());
          turns.add(new Turn(response.requestId(), query, response.status(), Instant.now()));
          if (turns.size() > 50)
            turns = new ArrayList<>(turns.subList(turns.size() - 50, turns.size()));
          return new Session(
              s.id(),
              s.tenant(),
              s.subject(),
              s.backend(),
              s.lockedFields(),
              s.createdAt(),
              s.expiresAt(),
              s.revision() + 1,
              null,
              null,
              response,
              turns);
        });
  }

  private void release(ApiIdentity.Principal caller, Session claimed, RuntimeException original) {
    if (!Instant.now().isBefore(claimed.expiresAt())) return;
    try {
      store.update(
          caller.tenant(),
          "sessions",
          claimed.id(),
          Session.class,
          Duration.between(Instant.now(), claimed.expiresAt()),
          s -> {
            if (s == null) throw new jakarta.ws.rs.NotFoundException("Session expired");
            if (!claimed.busy().equals(s.busy())) return s;
            return new Session(
                s.id(),
                s.tenant(),
                s.subject(),
                s.backend(),
                s.lockedFields(),
                s.createdAt(),
                s.expiresAt(),
                s.revision(),
                null,
                null,
                s.last(),
                s.turns());
          });
    } catch (RuntimeException cleanup) {
      original.addSuppressed(cleanup);
    }
  }

  public Session turn(ApiIdentity.Principal caller, String id, TurnRequest request) {
    Session current = claim(caller, id, request.expectedRevision(), UUID.randomUUID().toString());
    try {
      if (current.last() != null
          && !Set.of("OK", "CLARIFICATION_REQUIRED").contains(current.last().status())
          && !request.resetContext())
        throw new jakarta.ws.rs.ClientErrorException(
            "Rephrase the full request and explicitly start a new search to resolve the previous"
                + " clarification",
            409);
      boolean clarification =
          current.last() != null
              && current.last().status().equals("CLARIFICATION_REQUIRED")
              && !request.resetContext();
      String text =
          clarification
              ? current.last().translation().original() + "\n" + request.query()
              : request.query();
      var query =
          new SearchRequest(
              null,
              text,
              "en",
              "commerce",
              1,
              request.mode(),
              List.of(),
              request.rerank(),
              current.backend(),
              10,
              null,
              current.lockedFields());
      var response =
          current.last() == null || request.resetContext() || clarification
              ? search.search(query, caller)
              : search.refineVerified(current.last(), query, caller);
      return finish(caller, current, request.query(), response);
    } catch (RuntimeException ex) {
      release(caller, current, ex);
      throw ex;
    }
  }

  public Session accept(
      ApiIdentity.Principal caller, String id, String proposal, Integer revision) {
    var offer = recovery.offer(caller, proposal);
    var before = get(caller, id);
    if (offer.result() != null
        && before.last() != null
        && before.last().requestId().equals(offer.result().requestId())) return before;
    Session current = claim(caller, id, revision, UUID.randomUUID().toString());
    try {
      if (current.last() == null
          || !offer.parentRequestId().equals(current.last().requestId())
          || !Objects.equals(offer.backend(), current.backend()))
        throw new jakarta.ws.rs.ClientErrorException(
            "Proposal belongs to another conversation revision", 409);
      var result = recovery.accept(caller, proposal);
      return finish(caller, current, offer.description(), result);
    } catch (RuntimeException ex) {
      release(caller, current, ex);
      throw ex;
    }
  }
}
