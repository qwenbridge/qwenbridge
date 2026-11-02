package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.connectors.*;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.platform.*;
import io.qwenbridge.security.ApiIdentity;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class ConversationAcceptanceTest {
  final ApiIdentity.Principal caller =
      new ApiIdentity.Principal("alice", "tenant1", Set.of("search"));
  final PlatformStore store =
      new PlatformStore(null, new ObjectMapper().findAndRegisterModules(), Optional.of(false));
  final CommerceSearchService search = mock(CommerceSearchService.class);
  final CommerceEvidenceStore evidence = mock(CommerceEvidenceStore.class);
  final CommerceAnalytics analytics = mock(CommerceAnalytics.class);
  final CommerceBackends backends = mock(CommerceBackends.class);
  final Entitlements license = new Entitlements(store, new ObjectMapper(), "{}");
  final ZeroResultRecovery recovery =
      new ZeroResultRecovery(store, analytics, evidence, search, license);
  final CommerceSessions sessions =
      new CommerceSessions(store, search, backends, license, recovery);

  Constraint c(String f, Operator op, String v) {
    return new Constraint(f, op, v, new Evidence("original", 0, 1, "x"));
  }

  SearchResponse response(String id, List<String> locked) {
    var plan =
        CommerceCompiler.validate(
            CommerceSchemaRegistry.defaults(),
            caller.tenant(),
            List.of(
                c("price", Operator.LT, "100"),
                c("currency", Operator.EQ, "EUR"),
                c("color", Operator.EQ, "black"),
                c("brand", Operator.NE, "Adidas"),
                c("inStock", Operator.EQ, "true")));
    var source =
        new Attribution(
            "a".repeat(64),
            caller.tenant(),
            id,
            "urn:test",
            Instant.now(),
            Instant.now(),
            "Original request",
            List.of(),
            null);
    return new SearchResponse(
        2,
        id,
        "OK",
        new Translation(1, "x", "en", "en", "x", true, List.of()),
        "SEARCH",
        plan,
        List.of(),
        new SearchResultSet(List.of(), 0, 0),
        null,
        List.of(source),
        List.of(),
        "none",
        0,
        null,
        Map.of(),
        List.of(),
        locked);
  }

  @BeforeEach
  void setup() {
    var backend = mock(CommerceBackend.class);
    when(backend.id()).thenReturn("opensearch");
    when(backends.backend(anyString(), nullable(String.class))).thenReturn(backend);
    when(evidence.recordQuery(anyString(), anyString(), anyString(), anyList(), anyString()))
        .thenAnswer(
            i ->
                new Attribution(
                    "b".repeat(64),
                    i.getArgument(0),
                    i.getArgument(1),
                    "urn:proposal",
                    Instant.now(),
                    Instant.now(),
                    "Explicit proposal",
                    i.getArgument(3),
                    i.getArgument(4)));
    when(search.search(any(), eq(caller))).thenReturn(response("first", List.of()));
    when(search.refineVerified(any(), any(), eq(caller))).thenReturn(response("second", List.of()));
    when(search.executeApproved(eq(caller), any(), any(), anyString(), nullable(String.class)))
        .thenAnswer(
            i -> {
              var parent = (SearchResponse) i.getArgument(1);
              return new SearchResponse(
                  2,
                  "accepted",
                  "OK",
                  parent.translation(),
                  "SEARCH",
                  i.getArgument(2),
                  List.of(),
                  new SearchResultSet(List.of(), 0, 0),
                  null,
                  parent.evidence(),
                  List.of(),
                  "none",
                  0,
                  null,
                  Map.of(),
                  List.of(),
                  parent.lockedFields());
            });
  }

  @Test
  void conversationPersistsBetweenIndependentServiceInstancesAndRequiresOwnerRevision() {
    var s = sessions.create(caller, new CommerceSessions.Create(1800, null, List.of("price")));
    var first =
        sessions.turn(
            caller, s.id(), new CommerceSessions.TurnRequest("x", 0, false, "KEYWORD", false));
    assertThat(first.revision()).isEqualTo(1);
    assertThat(first.expiresAt()).isEqualTo(s.expiresAt());
    var reconnected = new CommerceSessions(store, search, backends, license, recovery);
    var second =
        reconnected.turn(
            caller,
            s.id(),
            new CommerceSessions.TurnRequest("make it blue", 1, false, "KEYWORD", false));
    verify(search)
        .refineVerified(
            eq(first.last()),
            argThat(q -> q.query().equals("make it blue") && q.lockedFields().contains("price")),
            eq(caller));
    assertThat(second.turns()).hasSize(2);
    assertThat(second.revision()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                sessions.turn(
                    caller, s.id(), new CommerceSessions.TurnRequest("x", 1, false, null, false)))
        .hasMessageContaining("revision");
    for (var other :
        List.of(
            new ApiIdentity.Principal("bob", "tenant1", Set.of("search")),
            new ApiIdentity.Principal("alice", "tenant2", Set.of("search"))))
      assertThatThrownBy(() -> sessions.get(other, s.id()))
          .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
  }

  @Test
  void expiredSessionCannotExecuteAndConcurrentTurnsCannotOverwriteContext() throws Exception {
    var s = sessions.create(caller, new CommerceSessions.Create(60, null, List.of()));
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(search.search(any(), eq(caller)))
        .thenAnswer(
            i -> {
              started.countDown();
              if (!finish.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test release missing");
              return response("first", List.of());
            });
    try (var executor = Executors.newSingleThreadExecutor()) {
      var running =
          executor.submit(
              () ->
                  sessions.turn(
                      caller,
                      s.id(),
                      new CommerceSessions.TurnRequest("x", 0, false, null, false)));
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(
                () ->
                    sessions.turn(
                        caller,
                        s.id(),
                        new CommerceSessions.TurnRequest("x", 0, false, null, false)))
            .hasMessageContaining("already running");
      } finally {
        finish.countDown();
      }
      assertThat(running.get(5, TimeUnit.SECONDS).revision()).isEqualTo(1);
    }
    store.update(
        caller.tenant(),
        "sessions",
        s.id(),
        CommerceSessions.Session.class,
        Duration.ofMinutes(1),
        old ->
            new CommerceSessions.Session(
                old.id(),
                old.tenant(),
                old.subject(),
                old.backend(),
                old.lockedFields(),
                old.createdAt(),
                Instant.now().minusSeconds(1),
                old.revision(),
                null,
                null,
                old.last(),
                old.turns()));
    assertThatThrownBy(
            () ->
                sessions.turn(
                    caller,
                    s.id(),
                    new CommerceSessions.TurnRequest("x", null, false, null, false)))
        .isInstanceOf(jakarta.ws.rs.ClientErrorException.class)
        .extracting(e -> ((jakarta.ws.rs.ClientErrorException) e).getResponse().getStatus())
        .isEqualTo(410);
  }

  @Test
  void zeroResultsNeverBroadenBeforeAcceptanceAndLockedOrNegativeConditionsRemain() {
    var prior = response("zero", List.of("price"));
    var offers = recovery.proposeVerified(caller, prior, "opensearch");
    assertThat(offers)
        .extracting(ZeroResultRecovery.Proposal::changedField)
        .containsExactly("color");
    verifyNoInteractions(search);
    var held = recovery.offer(caller, offers.getFirst().id());
    assertThat(held.constraints())
        .anySatisfy(
            c -> {
              assertThat(c.field()).isEqualTo("price");
              assertThat(c.value()).isEqualTo("100");
            });
    assertThat(held.constraints())
        .anySatisfy(
            c -> {
              assertThat(c.field()).isEqualTo("brand");
              assertThat(c.operator()).isEqualTo(Operator.NE);
            });
    var accepted = recovery.accept(caller, held.id());
    assertThat(accepted.queryIR().constraints()).noneMatch(c -> c.field().equals("color"));
    assertThat(recovery.accept(caller, held.id())).isEqualTo(accepted);
    verify(search, times(1))
        .executeApproved(eq(caller), eq(prior), any(), anyString(), eq("opensearch"));
    assertThatThrownBy(
            () ->
                recovery.accept(
                    new ApiIdentity.Principal("bob", "tenant1", Set.of("search")), held.id()))
        .isInstanceOf(jakarta.ws.rs.NotFoundException.class);
  }

  @Test
  void budgetProposalPreservesOriginalOperatorCurrencyAndProofAndExpires() {
    var prior = response("budget", List.of());
    var price =
        recovery.proposeVerified(caller, prior, "opensearch").stream()
            .filter(o -> o.changedField().equals("price"))
            .findFirst()
            .orElseThrow();
    var held = recovery.offer(caller, price.id());
    assertThat(held.constraints())
        .anySatisfy(
            c -> {
              assertThat(c.field()).isEqualTo("price");
              assertThat(c.operator()).isEqualTo(Operator.LT);
              assertThat(c.value()).isEqualTo("110.00");
              assertThat(c.evidence().source()).isEqualTo("evidence:" + "b".repeat(64));
            });
    assertThat(held.constraints())
        .anySatisfy(
            c -> {
              assertThat(c.field()).isEqualTo("currency");
              assertThat(c.value()).isEqualTo("EUR");
            });
    store.update(
        caller.tenant(),
        "relaxations",
        held.id(),
        ZeroResultRecovery.Offer.class,
        Duration.ofMinutes(1),
        o ->
            new ZeroResultRecovery.Offer(
                o.id(),
                o.tenant(),
                o.subject(),
                o.parentRequestId(),
                o.backend(),
                o.description(),
                o.field(),
                o.constraints(),
                o.parent(),
                Instant.now().minusSeconds(1),
                o.status(),
                null,
                null,
                null));
    assertThatThrownBy(() -> recovery.accept(caller, held.id()))
        .isInstanceOf(jakarta.ws.rs.ClientErrorException.class);
    verifyNoInteractions(search);
  }

  @Test
  void acceptedProposalBecomesConversationContextAndStaleProposalCannotReplaceNewerTurn() {
    var s = sessions.create(caller, new CommerceSessions.Create(1800, null, List.of()));
    var first =
        sessions.turn(caller, s.id(), new CommerceSessions.TurnRequest("x", 0, false, null, false));
    var offer = recovery.proposeVerified(caller, first.last(), first.backend()).getFirst();
    var accepted = sessions.accept(caller, s.id(), offer.id(), 1);
    assertThat(accepted.revision()).isEqualTo(2);
    assertThat(accepted.last().requestId()).isEqualTo("accepted");
    assertThat(sessions.accept(caller, s.id(), offer.id(), 1)).isEqualTo(accepted);
    sessions.turn(
        caller, s.id(), new CommerceSessions.TurnRequest("make it blue", 2, false, null, false));
    verify(search).refineVerified(eq(accepted.last()), any(), eq(caller));
    assertThatThrownBy(() -> sessions.accept(caller, s.id(), offer.id(), 3))
        .hasMessageContaining("another conversation revision");
  }
}
