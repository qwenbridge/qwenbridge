package io.qwenbridge.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.ranking.RankedItem;
import io.qwenbridge.core.ranking.RerankStage;
import io.qwenbridge.core.ranking.Reranker;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.QueryVector;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * QB-243 – QB-248: chat, embedding and reranking providers are interchangeable behind the Core
 * SPIs; capabilities are checked before any call; fallback is explicit, labelled and never mixes
 * vector spaces or streamed answers.
 */
class AiProviderSwitchingTest {

  static final ChatCapabilities FULL = new ChatCapabilities(true, true, true, 1000, 2048);
  static final ChatCapabilities PLAIN = new ChatCapabilities(false, false, false, 1000, 2048);

  /** A chat provider whose behaviour each test chooses. */
  static class Chat implements ChatProvider {
    final String id;
    final ChatCapabilities capabilities;
    final ModelDescriptor model;
    final AtomicInteger calls = new AtomicInteger();
    volatile RuntimeException failure;
    volatile ChatResult.Failure typed;
    volatile Duration delay = Duration.ZERO;
    volatile boolean streamThenFail;

    Chat(String id, ChatCapabilities capabilities) {
      this.id = id;
      this.capabilities = capabilities;
      this.model = new ModelDescriptor(id, id + "-model", "7", Map.of("temperature", "0"));
    }

    public String id() {
      return id;
    }

    public ModelDescriptor model() {
      return model;
    }

    public ChatCapabilities capabilities() {
      return capabilities;
    }

    public ChatResult complete(ChatCall call, Consumer<String> tokens) {
      calls.incrementAndGet();
      if (!delay.isZero()) {
        try {
          Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new ChatResult.Failed(ChatResult.Failure.CANCELLED, id);
        }
      }
      if (streamThenFail) {
        tokens.accept("partial ");
        throw new IllegalStateException("connection reset");
      }
      if (failure != null) throw failure;
      if (typed != null) return new ChatResult.Failed(typed, id);
      if (call.streaming()) tokens.accept(id + " says");
      return new ChatResult.Completed(id + " says: " + call.prompt(), model);
    }
  }

  static ChatCall call(String prompt) {
    return ChatCall.of(prompt, Duration.ofSeconds(2));
  }

  @Test
  void chatProvidersAreSwitchedByConfigurationAlone() {
    var a = new Chat("alpha", FULL);
    var b = new Chat("beta", FULL);
    for (var primary : List.of(a, b)) {
      var routed =
          new ChatRouter(List.of(primary), ChatRouter.FallbackPolicy.NONE)
              .route(call("hi"), t -> {});
      var done = assertInstanceOf(ChatResult.Completed.class, routed.result());
      assertEquals(primary.id() + " says: hi", done.text());
      assertEquals(primary.model(), done.model());
      assertEquals(primary.id(), routed.servedBy());
      assertFalse(routed.fallbackUsed());
    }
    assertEquals(
        ChatResult.Failure.NO_PROVIDER,
        ((ChatResult.Failed)
                new ChatRouter(List.of(), ChatRouter.FallbackPolicy.NONE)
                    .route(call("x"), t -> {})
                    .result())
            .failure());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChatRouter(List.of(a, a), ChatRouter.FallbackPolicy.NONE));
  }

  @Test
  void unsupportedCapabilitiesAreDetectedBeforeExecution() {
    var plain = new Chat("plain", PLAIN);
    var router = new ChatRouter(List.of(plain), ChatRouter.FallbackPolicy.NONE);
    var streaming =
        new ChatCall("hi", Optional.empty(), true, 100, Duration.ofSeconds(1), () -> false);
    var structured =
        new ChatCall(
            "hi",
            Optional.of(Map.of("type", "object")),
            false,
            100,
            Duration.ofSeconds(1),
            () -> false);
    var tooLong = ChatCall.of("x".repeat(1001), Duration.ofSeconds(1));
    var tooManyTokens =
        new ChatCall("hi", Optional.empty(), false, 4096, Duration.ofSeconds(1), () -> false);
    assertEquals(Optional.of("streaming"), AiCapabilities.unmet(PLAIN, streaming));
    assertEquals(Optional.of("structured output"), AiCapabilities.unmet(PLAIN, structured));
    assertEquals(Optional.of("input length"), AiCapabilities.unmet(PLAIN, tooLong));
    assertEquals(Optional.of("output tokens"), AiCapabilities.unmet(PLAIN, tooManyTokens));
    for (var c : List.of(streaming, structured, tooLong, tooManyTokens)) {
      var routed = router.route(c, t -> {});
      assertEquals(ChatResult.Failure.UNSUPPORTED, ((ChatResult.Failed) routed.result()).failure());
      assertTrue(routed.attempts().getFirst().outcome().startsWith("UNSUPPORTED: "));
    }
    assertEquals(0, plain.calls.get());
    // With a fallback that supports streaming, the streaming call is served there, and says so.
    var full = new Chat("full", FULL);
    var tokens = new ArrayList<String>();
    var routed =
        new ChatRouter(List.of(plain, full), ChatRouter.FallbackPolicy.ON_PROVIDER_FAULT)
            .route(streaming, tokens::add);
    assertEquals("full", routed.servedBy());
    assertTrue(routed.fallbackUsed());
    assertEquals(List.of("full says"), tokens);
    assertEquals(0, plain.calls.get());
  }

  @Test
  void fallbackIsExplicitLabelledAndOnlyAfterProviderFaults() {
    var primary = new Chat("primary", FULL);
    var backup = new Chat("backup", FULL);
    primary.failure = new IllegalStateException("connection refused");
    // Without a fallback policy the failure is reported, not hidden.
    var none =
        new ChatRouter(List.of(primary, backup), ChatRouter.FallbackPolicy.NONE)
            .route(call("q"), t -> {});
    assertEquals(ChatResult.Failure.UNAVAILABLE, ((ChatResult.Failed) none.result()).failure());
    assertEquals(0, backup.calls.get());
    var router =
        new ChatRouter(List.of(primary, backup), ChatRouter.FallbackPolicy.ON_PROVIDER_FAULT);
    var routed = router.route(call("q"), t -> {});
    assertEquals("backup", routed.servedBy());
    assertTrue(routed.fallbackUsed());
    assertEquals(
        List.of(
            new ChatRouter.Attempt("primary", "UNAVAILABLE"),
            new ChatRouter.Attempt("backup", "COMPLETED")),
        routed.attempts());
    assertEquals(backup.model(), ((ChatResult.Completed) routed.result()).model());

    // Timeouts are bounded and count as provider faults.
    primary.failure = null;
    primary.delay = Duration.ofSeconds(5);
    long started = System.nanoTime();
    var slow = router.route(ChatCall.of("q", Duration.ofMillis(200)), t -> {});
    assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
    assertEquals("TIMEOUT", slow.attempts().getFirst().outcome());
    assertEquals("backup", slow.servedBy());

    // A bad request or a cancellation is not a provider fault: no fallback.
    primary.delay = Duration.ZERO;
    primary.typed = ChatResult.Failure.INPUT_TOO_LARGE;
    int before = backup.calls.get();
    var bad = router.route(call("q"), t -> {});
    assertEquals(ChatResult.Failure.INPUT_TOO_LARGE, ((ChatResult.Failed) bad.result()).failure());
    assertFalse(bad.fallbackUsed());
    primary.typed = null;
    primary.delay = Duration.ofSeconds(5);
    var cancelled = new AtomicBoolean();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ignored) {
              }
              cancelled.set(true);
            });
    var stopped =
        router.route(
            new ChatCall("q", Optional.empty(), false, 10, Duration.ofSeconds(3), cancelled::get),
            t -> {});
    assertEquals(ChatResult.Failure.CANCELLED, ((ChatResult.Failed) stopped.result()).failure());
    assertEquals(before, backup.calls.get());

    // A stream that already produced text is never continued by another model.
    primary.delay = Duration.ZERO;
    primary.streamThenFail = true;
    var tokens = new ArrayList<String>();
    var broken =
        router.route(
            new ChatCall("q", Optional.empty(), true, 10, Duration.ofSeconds(1), () -> false),
            tokens::add);
    assertEquals(ChatResult.Failure.UNAVAILABLE, ((ChatResult.Failed) broken.result()).failure());
    assertEquals(List.of("partial "), tokens);
    assertEquals("primary", broken.servedBy());
    assertEquals(before, backup.calls.get());
  }

  @Test
  void aProviderCannotClaimAnotherModel() {
    var liar =
        new Chat("liar", FULL) {
          @Override
          public ChatResult complete(ChatCall call, Consumer<String> tokens) {
            return new ChatResult.Completed(
                "x", new ModelDescriptor("liar", "bigger-model", "9", Map.of()));
          }
        };
    var routed =
        new ChatRouter(List.of(liar), ChatRouter.FallbackPolicy.NONE).route(call("q"), t -> {});
    assertEquals(
        ChatResult.Failure.INVALID_OUTPUT, ((ChatResult.Failed) routed.result()).failure());
    var leak =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ModelDescriptor("p", "m", "1", Map.of("api-key", "sk-1")));
    assertFalse(leak.getMessage().contains("sk-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModelDescriptor("p", "m", "1", Map.of("authToken", "x")));
  }

  static final EmbeddingSpace SPACE = new EmbeddingSpace("bge-m3", "1", 3);

  static EmbeddingProvider embedder(
      String id, EmbeddingSpace space, AtomicBoolean down, double bias) {
    return new EmbeddingProvider() {
      public String id() {
        return id;
      }

      public EmbeddingModel model() {
        return new EmbeddingModel(space, 8, 1000);
      }

      public EmbeddingResult embed(List<String> texts, Duration timeout) {
        if (down.get()) throw new IllegalStateException("down");
        return new EmbeddingResult.Embedded(
            texts.stream()
                .map(t -> new double[] {t.length() + bias, 1, t.contains("tea") ? 5 : 0})
                .toList(),
            space);
      }
    };
  }

  @Test
  void embeddingProvidersAreInterchangeableOnlyWithinOneSpace() {
    var primaryDown = new AtomicBoolean(true);
    var primary = embedder("ollama", SPACE, primaryDown, 0);
    var replica = embedder("replica", SPACE, new AtomicBoolean(false), 0);
    var fallback = new FallbackEmbeddingProvider(List.of(primary, replica));
    var stage = new EmbeddingStage(Optional.of(fallback), Duration.ofSeconds(2));
    var index = new InMemoryVectorIndex("vec", SPACE, 100, 10);
    // Documents embedded by the replica (primary down) and later by the primary share the index.
    var green = assertInstanceOf(EmbeddingResult.Embedded.class, stage.embed(List.of("green tea")));
    index.put("t", "tea", green.vectors().getFirst(), green.space(), Map.of());
    primaryDown.set(false);
    var coffee = assertInstanceOf(EmbeddingResult.Embedded.class, stage.embed(List.of("coffee")));
    index.put("t", "coffee", coffee.vectors().getFirst(), coffee.space(), Map.of());
    var query = assertInstanceOf(EmbeddingResult.Embedded.class, stage.embed(List.of("tea")));
    var hits =
        assertInstanceOf(
            SearchOutcome.Success.class,
            index.search(
                SearchRequest.vector(
                    "t", new QueryVector(query.vectors().getFirst(), query.space()), Filter.ALL, 2),
                CancellationToken.create()));
    assertEquals("tea", hits.hits().getFirst().id());

    // A provider in another space can neither be a fallback nor write into the index.
    var otherModel =
        embedder("other", new EmbeddingSpace("e5-large", "1", 3), new AtomicBoolean(false), 0);
    var mixed =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FallbackEmbeddingProvider(List.of(primary, otherModel)));
    assertTrue(mixed.getMessage().contains("MODEL_CHANGED"));
    var newVersion =
        embedder("v2", new EmbeddingSpace("bge-m3", "2", 3), new AtomicBoolean(false), 0);
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () -> new FallbackEmbeddingProvider(List.of(primary, newVersion)))
            .getMessage()
            .contains("VERSION_CHANGED"));
    assertEquals(
        Optional.of("embedding space (reindex required: [MODEL_CHANGED])"),
        AiCapabilities.unmet(otherModel.model(), SPACE, 1));
    assertEquals(Optional.of("batch size"), AiCapabilities.unmet(primary.model(), SPACE, 9));
    var foreign =
        ((EmbeddingResult.Embedded) otherModel.embed(List.of("tea"), Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> index.put("t", "x", foreign.vectors().getFirst(), foreign.space(), Map.of()));

    // A provider that claims the space but returns other dimensions is caught by the stage.
    var wrongDims =
        new EmbeddingProvider() {
          public String id() {
            return "wrong";
          }

          public EmbeddingModel model() {
            return new EmbeddingModel(SPACE, 8, 1000);
          }

          public EmbeddingResult embed(List<String> texts, Duration timeout) {
            return new EmbeddingResult.Embedded(List.of(new double[] {1, 2}), SPACE);
          }
        };
    assertEquals(
        new EmbeddingResult.Failed(EmbeddingResult.Failure.INVALID_OUTPUT, "wrong"),
        new EmbeddingStage(Optional.of(wrongDims), Duration.ofSeconds(1)).embed(List.of("tea")));
    assertEquals(
        EmbeddingResult.Failure.NO_PROVIDER,
        ((EmbeddingResult.Failed)
                new EmbeddingStage(Optional.empty(), Duration.ofSeconds(1)).embed(List.of("x")))
            .failure());
  }

  static RankedItem item(String id, int position) {
    return new RankedItem(
        id,
        position,
        10 - position,
        position,
        new RetrievalScore(1.0 / position, RetrievalScore.Kind.BACKEND_NATIVE, "t"),
        List.of(),
        Map.of(),
        OptionalInt.empty());
  }

  @Test
  void rerankersReceiveAtMostTheirCandidateLimitThroughTheRankingPipeline() {
    var seen = new AtomicInteger();
    Reranker reverse =
        new Reranker() {
          public String id() {
            return "reverse";
          }

          public int maxCandidates() {
            return 3;
          }

          public Optional<ModelDescriptor> model() {
            return Optional.of(new ModelDescriptor("local", "reverse", "1", Map.of()));
          }

          public Response rerank(String query, List<Item> items) {
            seen.set(items.size());
            return Response.of(items.stream().map(Item::id).toList().reversed());
          }
        };
    var ranked = List.of(item("a", 1), item("b", 2), item("c", 3), item("d", 4), item("e", 5));
    var result = RerankStage.apply(Optional.of(reverse), "q", ranked, 10, Duration.ofSeconds(1));
    assertEquals(RerankStage.Status.APPLIED, result.status());
    assertEquals(3, seen.get());
    assertEquals(
        List.of("c", "b", "a", "d", "e"), result.items().stream().map(RankedItem::id).toList());
    assertEquals(Optional.of("candidates (at most 3)"), AiCapabilities.unmet(reverse, 5));
    assertEquals(Optional.empty(), AiCapabilities.unmet(reverse, 3));
    // Without a reranker the baseline stands and says so.
    assertEquals(
        RerankStage.Status.DISABLED,
        RerankStage.apply(Optional.empty(), "q", ranked, 10, Duration.ofSeconds(1)).status());
  }
}
