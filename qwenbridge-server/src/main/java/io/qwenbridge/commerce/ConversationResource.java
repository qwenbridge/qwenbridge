package io.qwenbridge.commerce;

import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/api/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ConversationResource {
  private final java.util.concurrent.Semaphore streams = new java.util.concurrent.Semaphore(25);
  @Inject CommerceSessions sessions;
  @Inject ZeroResultRecovery recovery;
  @Context ContainerRequestContext context;
  @Inject io.qwenbridge.api.requests.ApiRequests requests;

  /** The request ID a turn runs under: the client's X-Request-ID (already validated) or fresh. */
  private String turnId() {
    Object id = context.getProperty(io.qwenbridge.api.header.ApiHeaders.REQUEST_ID);
    return id instanceof String value && io.qwenbridge.core.context.RequestIds.isValid(value)
        ? value
        : UUID.randomUUID().toString();
  }

  @POST
  @Path("/sessions")
  public CommerceSessions.Session create(CommerceSessions.Create request) {
    return sessions.create(ApiIdentity.caller(context), request);
  }

  @GET
  @Path("/sessions/{id}")
  public CommerceSessions.Session get(@PathParam("id") String id) {
    return sessions.get(ApiIdentity.caller(context), id);
  }

  @POST
  @Path("/sessions/{id}/turns")
  public CommerceSessions.Session turn(
      @PathParam("id") String id, CommerceSessions.TurnRequest request) {
    var caller = ApiIdentity.caller(context);
    return requests.run(context, caller, turnId(), () -> sessions.turn(caller, id, request));
  }

  /** One v2 session progress event (QB-180); the 1.0 fields match {@code SearchEvent}. */
  public record SessionEvent(
      int version,
      String kind,
      String requestId,
      CommerceSessions.Session session,
      String message,
      long sequence,
      boolean terminal,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          io.qwenbridge.api.requests.ApiRequests.StreamError error,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String traceId) {}

  @POST
  @Path("/sessions/{id}/turns/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(
      @PathParam("id") String id,
      CommerceSessions.TurnRequest request,
      @Context jakarta.ws.rs.sse.SseEventSink sink,
      @Context jakarta.ws.rs.sse.Sse sse) {
    var caller = ApiIdentity.caller(context);
    sessions.get(caller, id);
    if (!streams.tryAcquire()) throw new ClientErrorException("Stream capacity reached", 429);
    String requestId = turnId();
    try {
      requests.stream(
          context,
          caller,
          requestId,
          "session",
          "Turn could not complete; reload the session before retrying",
          sink,
          sse,
          (kind, sequence, terminal, result, message, error, traceId) ->
              new SessionEvent(
                  2, kind, requestId, result, message, sequence, terminal, error, traceId),
          () -> sessions.turn(caller, id, request));
    } finally {
      streams.release();
    }
  }

  @GET
  @Path("/sessions/{id}/relaxations")
  public List<ZeroResultRecovery.Proposal> offers(@PathParam("id") String id) {
    var caller = ApiIdentity.caller(context);
    var session = sessions.get(caller, id);
    return session.last() == null
        ? List.of()
        : recovery.proposeVerified(caller, session.last(), session.backend());
  }

  @GET
  @Path("/search/{id}/relaxations")
  public List<ZeroResultRecovery.Proposal> proposals(
      @PathParam("id") String id, @QueryParam("backend") String backend) {
    return recovery.propose(ApiIdentity.caller(context), id, backend);
  }

  /**
   * QB-125 – QB-130: the recovery decision for a search: outcome (results, zero results or a
   * provider failure), the relaxation policy, the offers and why every other condition was kept.
   */
  @GET
  @Path("/search/{id}/recovery")
  public ZeroResultRecovery.Recovery recovery(
      @PathParam("id") String id, @QueryParam("backend") String backend) {
    return recovery.recover(ApiIdentity.caller(context), id, backend);
  }

  @Inject ConversationContexts contexts;

  /** QB-139: the session's context as the versioned contract, signed when configured. */
  @GET
  @Path("/sessions/{id}/context")
  public ConversationContexts.ContextView context(@PathParam("id") String id) {
    return contexts.session(sessions.get(ApiIdentity.caller(context), id));
  }

  /**
   * QB-140, QB-141, QB-144: a stateless follow-up. The client sends the context (or its signed
   * token) it was given by the previous turn; the server holds nothing between turns.
   */
  @POST
  @Path("/conversations/turns")
  public ConversationContexts.TurnResult statelessTurn(ConversationContexts.TurnRequest request) {
    var caller = ApiIdentity.caller(context);
    return requests.run(context, caller, turnId(), () -> contexts.turn(caller, request));
  }

  @POST
  @Path("/relaxations/{id}/accept")
  public CommerceContracts.SearchResponse accept(@PathParam("id") String id) {
    return recovery.accept(ApiIdentity.caller(context), id);
  }

  public record Acceptance(Integer expectedRevision) {}

  @POST
  @Path("/sessions/{id}/relaxations/{proposal}/accept")
  public CommerceSessions.Session acceptSession(
      @PathParam("id") String id, @PathParam("proposal") String proposal, Acceptance input) {
    return sessions.accept(
        ApiIdentity.caller(context), id, proposal, input == null ? null : input.expectedRevision());
  }
}
