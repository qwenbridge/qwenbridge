package io.qwenbridge.e2e.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.sdk.commerce.CommerceClient;
import io.qwenbridge.sdk.commerce.CommerceClientConfig;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeIncompatibleServerException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.retry.RetryPolicy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * QB-295 independent Java consumer. Executes the shared scenarios through the public Java SDK
 * against a running QwenBridge server and writes one normalized observation per scenario. It
 * judges nothing itself: the shared evaluator applies the same expectations to both SDKs.
 *
 * <p>Usage: --base URL --scenarios FILE --keys FILE --tag offline|provider --out FILE [--perf N]
 */
public final class JavaConsumer {

  static final ObjectMapper JSON =
      new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
  static final Pattern LEAK =
      Pattern.compile(
          "(?i)(\\bat [a-z]+\\.[a-z.]+\\(|Exception:|stack ?trace|/Users/|/home/|e2e-[0-9a-f]{32})");

  private final URI base;
  private final JsonNode keys;

  JavaConsumer(URI base, JsonNode keys) {
    this.base = base;
    this.keys = keys;
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> a = arguments(args);
    var consumer = new JavaConsumer(URI.create(a.get("--base")), JSON.readTree(Path.of(a.get("--keys")).toFile()));
    JsonNode scenarios = JSON.readTree(Path.of(a.get("--scenarios")).toFile()).get("scenarios");
    String tag = a.get("--tag");
    ObjectNode out = JSON.createObjectNode();
    out.put("sdk", "java");
    out.put("sdkArtifact", "io.qwenbridge:qwenbridge-java-sdk:" + sdkVersion());
    out.put("runtime", "Java " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")");
    out.put("tag", tag);
    ObjectNode results = out.putObject("results");
    for (JsonNode s : scenarios) {
      boolean tagged = false;
      for (JsonNode t : s.get("tags")) tagged |= t.asText().equals(tag);
      if (!tagged) continue;
      long t0 = System.nanoTime();
      ObjectNode observation;
      try {
        observation = consumer.run(s);
      } catch (Exception e) {
        observation = JSON.createObjectNode();
        observation.put("ok", false);
        observation.set("error", error(e));
      }
      observation.put("wallMs", (System.nanoTime() - t0) / 1_000_000);
      results.set(s.get("id").asText(), observation);
      System.out.println(s.get("id").asText() + " " + (observation.path("ok").asBoolean() ? "ok" : "error"));
    }
    int perf = Integer.parseInt(a.getOrDefault("--perf", "0"));
    if (perf > 0 && tag.equals("offline")) out.set("performance", consumer.performance(perf));
    Files.writeString(Path.of(a.get("--out")), JSON.writeValueAsString(out));
  }

  static Map<String, String> arguments(String[] args) {
    var map = new java.util.HashMap<String, String>();
    for (int i = 0; i + 1 < args.length; i += 2) map.put(args[i], args[i + 1]);
    for (String required : List.of("--base", "--scenarios", "--keys", "--tag", "--out"))
      if (!map.containsKey(required)) throw new IllegalArgumentException("missing " + required);
    return map;
  }

  /** The version of the SDK JAR actually on the class path, from its Maven metadata. */
  static String sdkVersion() {
    String v = CommerceClient.class.getPackage().getImplementationVersion();
    if (v != null) return v;
    try (var in =
        CommerceClient.class.getResourceAsStream(
            "/META-INF/maven/io.qwenbridge/qwenbridge-java-sdk/pom.properties")) {
      if (in == null) return "unknown";
      var properties = new java.util.Properties();
      properties.load(in);
      return properties.getProperty("version", "unknown");
    } catch (java.io.IOException e) {
      return "unknown";
    }
  }

  String token(JsonNode scenario) {
    JsonNode p = scenario.get("principal");
    if (p == null || p.isNull()) return "";
    if (p.asText().equals("invalid")) return "e2e-invalid-" + UUID.randomUUID();
    return keys.path(p.asText()).asText();
  }

  CommerceClient client(JsonNode scenario, URI target) {
    int timeout = scenario.path("timeoutMs").asInt(120_000);
    return new CommerceClient(
        new CommerceClientConfig(
            target, token(scenario), Duration.ofSeconds(5), Duration.ofMillis(timeout),
            scenario.has("timeoutMs") ? RetryPolicy.disabled() : RetryPolicy.defaultPolicy()));
  }

  static CommerceContracts.Request request(JsonNode r) {
    return new CommerceContracts.Request(
        r.hasNonNull("requestId") ? r.get("requestId").asText().replace("{sdk}", "java") : null,
        r.get("query").asText().replace("{sdk}", "java"),
        r.hasNonNull("catalogLanguage") ? r.get("catalogLanguage").asText() : "en",
        "commerce",
        1,
        r.hasNonNull("mode") ? r.get("mode").asText() : null,
        false,
        null,
        r.hasNonNull("limit") ? r.get("limit").asInt() : null,
        null,
        List.of(),
        r.hasNonNull("contract") ? r.get("contract").asText() : null);
  }

  ObjectNode run(JsonNode s) throws Exception {
    String op = s.get("operation").asText();
    JsonNode r = s.path("request");
    return switch (op) {
      case "search" -> search(s, r);
      case "version" -> version(s);
      case "stream" -> stream(s, r);
      case "stream-abort" -> streamAbort(s, r);
      case "stream-server-cancel" -> streamServerCancel(s, r);
      case "concurrent-streams" -> concurrentStreams(s, r);
      case "concurrent-search" -> concurrentSearch(s, r);
      case "unreachable" -> unreachable(s, r);
      case "v1-analyze" -> v1Analyze(r, false);
      case "v1-analyze-async" -> v1Analyze(r, true);
      case "v1-typed-stream" -> v1Stream(r);
      default -> throw new IllegalArgumentException("unknown operation " + op);
    };
  }

  ObjectNode search(JsonNode s, JsonNode r) {
    ObjectNode o = JSON.createObjectNode();
    long t0 = System.nanoTime();
    try (var client = client(s, base)) {
      var response = client.search(request(r));
      o.put("ok", true);
      String requested = r.hasNonNull("requestId") ? r.get("requestId").asText().replace("{sdk}", "java") : null;
      o.set("response", observe(response, r.get("query").asText(), requested));
    } catch (Exception e) {
      o.put("ok", false);
      o.set("error", error(e));
    }
    o.put("latencyMs", (System.nanoTime() - t0) / 1e6);
    return o;
  }

  ObjectNode version(JsonNode s) {
    ObjectNode o = JSON.createObjectNode();
    try (var client = client(s, base)) {
      var v = client.requireCompatibleServer();
      o.put("ok", true);
      ObjectNode version = o.putObject("version");
      version.put("name", v.name());
      version.put("version", v.version());
      version.set("apiVersions", JSON.valueToTree(v.apiVersions()));
    }
    return o;
  }

  ObjectNode stream(JsonNode s, JsonNode r) {
    ObjectNode o = JSON.createObjectNode();
    var events = new CopyOnWriteArrayList<CommerceContracts.Event>();
    try (var client = client(s, base)) {
      client.stream(request(r), events::add);
      o.put("ok", true);
    } catch (Exception e) {
      o.put("ok", false);
      o.set("error", error(e));
    }
    o.set("events", events(events, r));
    return o;
  }

  ObjectNode streamAbort(JsonNode s, JsonNode r) throws Exception {
    ObjectNode o = JSON.createObjectNode();
    var events = new CopyOnWriteArrayList<CommerceContracts.Event>();
    try (var client = client(s, base)) {
      long t0 = System.nanoTime();
      CompletableFuture<Void> future = client.streamAsync(request(r), events::add);
      Thread.sleep(s.path("abortAfterMs").asLong(800));
      future.cancel(true);
      long elapsed = (System.nanoTime() - t0) / 1_000_000;
      Thread.sleep(500);
      o.put("ok", true);
      o.put("cancelled", future.isCancelled());
      o.put("elapsedMs", elapsed);
      o.set("events", events(events, r));
    }
    return o;
  }

  ObjectNode streamServerCancel(JsonNode s, JsonNode r) throws Exception {
    ObjectNode o = JSON.createObjectNode();
    var events = new CopyOnWriteArrayList<CommerceContracts.Event>();
    String id = "e2e-java-cancel-" + UUID.randomUUID();
    var req = request(r).withRequestId(id);
    try (var client = client(s, base)) {
      long t0 = System.nanoTime();
      CompletableFuture<Void> future = client.streamAsync(req, events::add);
      Thread.sleep(s.path("cancelAfterMs").asLong(800));
      boolean accepted = client.cancel(id);
      try {
        future.get(30, TimeUnit.SECONDS);
        o.put("ok", true);
      } catch (Exception e) {
        o.put("ok", false);
        o.set("error", error(e.getCause() == null ? e : e.getCause()));
      }
      o.put("cancelAccepted", accepted);
      o.put("elapsedMs", (System.nanoTime() - t0) / 1_000_000);
      o.set("events", events(events, r));
    }
    return o;
  }

  ObjectNode concurrentStreams(JsonNode s, JsonNode r) throws Exception {
    int n = s.path("concurrency").asInt(8);
    ObjectNode o = JSON.createObjectNode();
    ArrayNode streams = o.putArray("streams");
    try (var client = client(s, base);
        ExecutorService pool = Executors.newFixedThreadPool(n)) {
      List<Future<List<CommerceContracts.Event>>> futures = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        String id = "e2e-java-stream-" + i + "-" + UUID.randomUUID();
        futures.add(
            pool.submit(
                () -> {
                  var events = new CopyOnWriteArrayList<CommerceContracts.Event>();
                  client.stream(request(r).withRequestId(id), events::add);
                  return (List<CommerceContracts.Event>) events;
                }));
      }
      for (var f : futures) streams.add(events(f.get(60, TimeUnit.SECONDS), r));
    }
    o.put("ok", true);
    return o;
  }

  ObjectNode concurrentSearch(JsonNode s, JsonNode r) throws Exception {
    int n = s.path("concurrency").asInt(16);
    ObjectNode o = JSON.createObjectNode();
    ArrayNode responses = o.putArray("responses");
    try (var client = client(s, base);
        ExecutorService pool = Executors.newFixedThreadPool(n)) {
      List<Future<ObjectNode>> futures = new ArrayList<>();
      for (int i = 0; i < n; i++)
        futures.add(
            pool.submit(
                () -> {
                  ObjectNode x = JSON.createObjectNode();
                  try {
                    x.put("ok", true);
                    x.set("response", observe(client.search(request(r)), r.get("query").asText(), null));
                  } catch (Exception e) {
                    x.put("ok", false);
                    x.set("error", error(e));
                  }
                  return x;
                }));
      for (var f : futures) responses.add(f.get(60, TimeUnit.SECONDS));
    }
    o.put("ok", true);
    return o;
  }

  ObjectNode unreachable(JsonNode s, JsonNode r) {
    ObjectNode o = JSON.createObjectNode();
    long t0 = System.nanoTime();
    try (var client = client(s, URI.create("http://127.0.0.1:9"))) {
      client.search(request(r));
      o.put("ok", true);
    } catch (Exception e) {
      o.put("ok", false);
      o.set("error", error(e));
    }
    o.put("latencyMs", (System.nanoTime() - t0) / 1e6);
    return o;
  }

  io.qwenbridge.sdk.config.QwenBridgeClientConfig v1Config() {
    return new io.qwenbridge.sdk.config.QwenBridgeClientConfig(
        base, Duration.ofSeconds(5), Duration.ofSeconds(60));
  }

  static String sdkId(JsonNode r) {
    return r.get("requestId").asText().replace("{sdk}", "java");
  }

  /** The legacy v1 analyze API, synchronous or asynchronous (what the removed examples showed). */
  ObjectNode v1Analyze(JsonNode r, boolean async) {
    ObjectNode o = JSON.createObjectNode();
    var client = new io.qwenbridge.sdk.QwenBridgeClient(v1Config());
    var request =
        io.qwenbridge.sdk.search.SearchAnalyzeRequest.withRequestId(
            sdkId(r), r.get("query").asText());
    try {
      var response = async ? client.analyzeAsync(request).join() : client.analyze(request);
      o.put("ok", true);
      ObjectNode x = o.putObject("v1");
      x.put("decision", response.decision());
      x.put("intent", response.intent());
      x.put("originalQuery", response.originalQuery());
      x.put("requestIdEchoed", sdkId(r).equals(response.requestId()));
      if (response.spelling() != null) x.put("spellingStatus", response.spelling().status());
      if (response.expansion() != null) {
        x.put("expansionStatus", response.expansion().status());
        x.set("expansionRequested", JSON.valueToTree(response.expansion().requestedLanguages()));
      }
    } catch (Exception e) {
      o.put("ok", false);
      o.set("error", error(e instanceof java.util.concurrent.CompletionException c && c.getCause() != null ? c.getCause() : e));
    }
    return o;
  }

  /** The legacy v1 typed SSE stream, fed by an analysis with the same request id. */
  ObjectNode v1Stream(JsonNode r) throws Exception {
    ObjectNode o = JSON.createObjectNode();
    var names = new CopyOnWriteArrayList<String>();
    var terminal = new java.util.concurrent.CountDownLatch(1);
    var streaming = new io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient(v1Config());
    CompletableFuture<Void> stream =
        streaming.streamTyped(
            sdkId(r),
            event -> {
              names.add(event.eventName());
              if (event.eventName() != null
                  && event.eventName().matches("pipeline\\.(completed|failed|stopped)"))
                terminal.countDown();
            });
    Thread.sleep(500);
    new io.qwenbridge.sdk.QwenBridgeClient(v1Config())
        .analyze(
            io.qwenbridge.sdk.search.SearchAnalyzeRequest.withRequestId(
                sdkId(r), r.get("query").asText()));
    boolean ended = terminal.await(60, TimeUnit.SECONDS);
    stream.cancel(true);
    o.put("ok", ended);
    o.set("eventNames", JSON.valueToTree(new ArrayList<>(names)));
    return o;
  }

  static ArrayNode events(List<CommerceContracts.Event> events, JsonNode r) {
    ArrayNode out = JSON.createArrayNode();
    for (var e : events) {
      ObjectNode x = out.addObject();
      x.put("kind", e.kind());
      x.put("requestId", e.requestId());
      if (e.sequence() != null) x.put("sequence", e.sequence());
      if (e.terminal() != null) x.put("terminal", e.terminal());
      x.put("hasTraceId", e.traceId() != null && e.traceId().matches("[0-9a-f]{32}"));
      if (e.error() != null) {
        x.put("errorCode", e.error().code());
        x.put("errorCategory", e.error().category());
      }
      if (e.response() != null) x.set("response", observe(e.response(), r.get("query").asText(), null));
    }
    return out;
  }

  /** The normalized, SDK-independent view of a response; the TypeScript consumer builds the same. */
  static ObjectNode observe(CommerceContracts.Response r, String query, String requestedId) {
    ObjectNode o = JSON.createObjectNode();
    o.put("apiVersion", r.apiVersion());
    o.put("status", r.status());
    o.put("intent", r.intent());
    o.put("requestIdEchoed", requestedId == null || requestedId.equals(r.requestId()));
    o.put("hasRequestId", r.requestId() != null && !r.requestId().isBlank());
    if (r.translation() != null) {
      o.put("sourceLanguage", r.translation().sourceLanguage());
      o.put("translated", r.translation().translated());
    }
    ArrayNode constraints = o.putArray("constraints");
    boolean grounded = true;
    if (r.queryIR() != null)
      for (var c : r.queryIR().constraints()) {
        constraints.addArray().add(c.field()).add(c.operator()).add(c.value());
        var ev = c.evidence();
        if (ev == null || ev.quote() == null) grounded = false;
        else if ("original".equals(ev.source()) && !query.contains(ev.quote())) grounded = false;
      }
    o.put("evidenceGrounded", grounded);
    ArrayNode hits = o.putArray("hits");
    ArrayNode docs = o.putArray("hitDocs");
    if (r.results() != null) {
      o.put("totalHits", r.results().totalHits());
      for (var h : r.results().hits()) {
        hits.add(h.id());
        ObjectNode d = docs.addObject();
        d.put("id", h.id());
        for (String f : List.of("color", "currency", "category", "condition", "size"))
          if (h.document().get(f) != null) d.put(f, String.valueOf(h.document().get(f)));
        if (h.document().get("price") instanceof Number n) d.put("price", n.doubleValue());
        if (h.document().get("inStock") instanceof Boolean b) d.put("inStock", b);
      }
    }
    if (r.retrieval() != null) {
      ObjectNode x = o.putObject("retrieval");
      x.put("requested", r.retrieval().requested());
      x.put("executed", r.retrieval().executed());
      x.put("outcome", r.retrieval().outcome());
      x.put("degraded", r.retrieval().degraded());
    }
    if (r.interpretation() != null) {
      o.put("queryIrVersion", r.interpretation().queryIrVersion());
      o.set("intents", JSON.valueToTree(r.interpretation().intents()));
      o.put("interpretationConstraints", r.interpretation().constraints().size());
    }
    o.put("clarifications", r.clarificationQuestions().size());
    if (r.spelling() != null) {
      ObjectNode x = o.putObject("spelling");
      x.put("status", r.spelling().status());
      x.put("mode", r.spelling().mode());
      x.put("original", r.spelling().original());
      x.put("normalized", r.spelling().normalized());
      x.put("corrected", r.spelling().corrected());
      x.put("changed", r.spelling().changed());
    }
    if (r.expansion() != null) {
      var e = r.expansion();
      ObjectNode x = o.putObject("expansion");
      x.put("status", e.status());
      x.put("original", e.original());
      x.put("sourceLanguage", e.source() == null ? null : e.source().language());
      x.put("basis", e.source() == null ? null : e.source().basis());
      x.set("requested", JSON.valueToTree(e.requestedLanguages()));
      x.set("unsupported", JSON.valueToTree(e.unsupportedLanguages()));
      x.set("retrievalTexts", JSON.valueToTree(e.retrievalTexts()));
      ObjectNode targets = x.putObject("targets");
      for (var t : e.targets()) {
        ObjectNode y = targets.putObject(t.language() != null ? t.language() : t.requested());
        y.put("requested", t.requested());
        y.put("status", t.status());
        y.put("text", t.text());
        y.put("provider", t.provider());
        y.put("retrieval", t.retrieval());
      }
    }
    if (r.tookMs() > 0) o.put("serverTookMs", r.tookMs());
    return o;
  }

  static ObjectNode error(Throwable e) {
    ObjectNode o = JSON.createObjectNode();
    String message = e.getMessage() == null ? "" : e.getMessage();
    switch (e) {
      case QwenBridgeApiException api -> {
        o.put("kind", "api");
        o.put("status", api.status());
        o.put("code", api.code());
        o.put("category", api.category());
        o.put("retryable", api.apiError() != null && api.apiError().retryable());
      }
      case QwenBridgeTransportException t -> {
        boolean timeout = false;
        for (Throwable c = t; c != null; c = c.getCause())
          timeout |= c instanceof java.net.http.HttpTimeoutException;
        o.put("kind", timeout ? "timeout" : "transport");
      }
      case QwenBridgeIncompatibleServerException i -> o.put("kind", "incompatible");
      case IllegalArgumentException iae -> o.put("kind", "client-validation");
      default -> o.put("kind", "other:" + e.getClass().getSimpleName());
    }
    o.put("messageLeaks", LEAK.matcher(message).find());
    return o;
  }

  ObjectNode performance(int n) throws Exception {
    ObjectNode p = JSON.createObjectNode();
    String query = "black Stride running shoes size 42 under 100 EUR in stock";
    var req = request(JSON.createObjectNode().put("query", query));
    var principal = JSON.createObjectNode().put("principal", "main");
    Runtime rt = Runtime.getRuntime();
    System.gc();
    long heapBefore = rt.totalMemory() - rt.freeMemory();
    double[] latencies = new double[n];
    long[] server = new long[n];
    int errors = 0;
    try (var client = client(principal, base)) {
      for (int i = 0; i < 20; i++) client.search(req);
      long start = System.nanoTime();
      for (int i = 0; i < n; i++) {
        long t0 = System.nanoTime();
        try {
          server[i] = client.search(req).tookMs();
        } catch (Exception e) {
          errors++;
        }
        latencies[i] = (System.nanoTime() - t0) / 1e6;
      }
      double seconds = (System.nanoTime() - start) / 1e9;
      p.set("sequential", stats(latencies, errors, n / seconds));
      p.set("serverTookMs", stats(Arrays.stream(server).asDoubleStream().toArray(), 0, 0));
      int concurrency = 8;
      int total = n;
      double[] concurrent = new double[total];
      var errorCount = new java.util.concurrent.atomic.AtomicInteger();
      long cstart = System.nanoTime();
      try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < total; i++) {
          int k = i;
          fs.add(
              pool.submit(
                  () -> {
                    long t0 = System.nanoTime();
                    try {
                      client.search(req);
                    } catch (Exception e) {
                      errorCount.incrementAndGet();
                    }
                    concurrent[k] = (System.nanoTime() - t0) / 1e6;
                  }));
        }
        for (var f : fs) f.get();
      }
      double cseconds = (System.nanoTime() - cstart) / 1e9;
      ObjectNode c = stats(concurrent, errorCount.get(), total / cseconds);
      c.put("concurrency", concurrency);
      p.set("concurrent", c);
    }
    long heapAfter = rt.totalMemory() - rt.freeMemory();
    p.put("heapUsedBeforeMb", heapBefore / 1_048_576.0);
    p.put("heapUsedAfterMb", heapAfter / 1_048_576.0);
    p.put("query", query);
    p.put("processors", rt.availableProcessors());
    p.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    return p;
  }

  static ObjectNode stats(double[] values, int errors, double throughput) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    ObjectNode o = JSON.createObjectNode();
    o.put("samples", values.length);
    o.put("errors", errors);
    if (throughput > 0) o.put("throughputPerSecond", throughput);
    for (double q : new double[] {0.5, 0.95, 0.99})
      o.put("p" + (int) Math.round(q * 100),
          sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, Math.ceil(q * sorted.length) - 1)]);
    o.put("max", sorted.length == 0 ? 0 : sorted[sorted.length - 1]);
    return o;
  }
}
