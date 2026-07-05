package io.qwenbridge.pipeline;

import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.pipeline.context.ContextKey;
import io.qwenbridge.pipeline.result.*;
import io.qwenbridge.threat.ThreatResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {

  private final RequestContext request;
  private final Map<Class<?>, Object> results = new ConcurrentHashMap<>();
  private final Map<ContextKey<?>, Object> extensions = new ConcurrentHashMap<>();
  private final List<PipelineTraceItem> trace = new ArrayList<>();

  public ExecutionContext(String originalQuery) {
    this.request = RequestContext.of(originalQuery);
    initializeDefaults();
  }

  public ExecutionContext(String requestId, String originalQuery) {
    this.request = RequestContext.of(requestId, originalQuery);
    initializeDefaults();
  }

  private void initializeDefaults() {
    store(AIAnalysisCacheTrace.class, AIAnalysisCacheTrace.disabled());
    store(LanguageResult.class, LanguageResult.unknown());
    store(IntentResult.class, IntentResult.unknown());
    store(ThreatResult.class, ThreatResult.noThreat());
    store(RewriteResult.class, RewriteResult.none());
    store(SemanticResult.class, SemanticResult.notValidated());
    store(PolicyResult.class, PolicyResult.allow());
    store(ConfidenceResult.class, ConfidenceResult.zero());
    store(DecisionResult.class, DecisionResult.none());
    store(ExecutionPlanResult.class, ExecutionPlanResult.none());
    store(ExecutionResultResult.class, ExecutionResultResult.none());
  }

  public RequestContext request() {
    return request;
  }

  public <T> void store(Class<T> type, T result) {
    results.put(type, result);
  }

  public <T> T get(Class<T> type) {
    return type.cast(results.get(type));
  }

  public <T> void store(ContextKey<T> key, T value) {
    extensions.put(key, key.type().cast(value));
  }

  public <T> T get(ContextKey<T> key) {
    return key.type().cast(extensions.get(key));
  }

  public void addTrace(PipelineTraceItem item) {
    trace.add(item);
  }

  public List<PipelineTraceItem> trace() {
    return List.copyOf(trace);
  }

  public boolean stopped() {
    ThreatResult threat = get(ThreatResult.class);
    PolicyResult policy = get(PolicyResult.class);
    DecisionResult decision = get(DecisionResult.class);

    return !threat.safe() || !policy.passed() || decision.type() == DecisionType.BLOCK;
  }
}
