package io.qwenbridge.extensions;

import io.qwenbridge.agent.AgentContext;
import io.qwenbridge.agent.AgentResult;
import io.qwenbridge.agent.ToolResult;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.security.ApiIdentity;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs agents (QB-226, QB-227, QB-229, QB-230).
 *
 * <ul>
 *   <li>A run executes as its caller: starting it needs {@code agents:run}, and every tool call is
 *       authorized by {@link ToolExecutor} with the caller's permissions. The agent's tool list
 *       only narrows those permissions.
 *   <li>Delegation runs another approved agent inside the same run, with the intersection of both
 *       agents' tools, at most {@value #MAX_DEPTH} levels deep: it can never widen permissions.
 *   <li>Every run is bounded by its agent's timeout and step limit, can be cancelled by its owner,
 *       and records each step for inspection. Only the owner (same tenant and subject) can inspect
 *       or cancel it.
 * </ul>
 */
@jakarta.inject.Singleton
public final class AgentRuntime {
  static final int MAX_DEPTH = 3;
  private static final int MAX_RUNS = 1_000;

  private final ExtensionRegistry registry;
  private final ToolExecutor tools;
  private final Map<String, Run> runs = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final ScheduledExecutorService timers =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("qwenbridge-agent-timers").daemon().factory());

  @jakarta.inject.Inject
  public AgentRuntime(ExtensionRegistry registry, ToolExecutor tools) {
    this.registry = registry;
    this.tools = tools;
  }

  private static final class Run {
    final String id;
    final String agentId;
    final ApiIdentity.Principal caller;
    final Instant deadline;
    final int maxSteps;
    final AtomicBoolean cancelled = new AtomicBoolean();
    final AtomicInteger stepCount = new AtomicInteger();
    final List<AgentResult.Step> steps = new ArrayList<>();
    volatile AgentResult.Status status = AgentResult.Status.RUNNING;
    volatile Map<String, Object> output = Map.of();
    volatile String error;
    volatile Future<?> future;

    Run(String id, String agentId, ApiIdentity.Principal caller, Instant deadline, int maxSteps) {
      this.id = id;
      this.agentId = agentId;
      this.caller = caller;
      this.deadline = deadline;
      this.maxSteps = maxSteps;
    }

    synchronized void step(String kind, String name, String status, List<String> reasons, long ms) {
      steps.add(new AgentResult.Step(steps.size() + 1, kind, name, status, reasons, ms));
    }

    synchronized AgentResult snapshot() {
      return new AgentResult(id, agentId, status, output, steps, error);
    }

    boolean stopped() {
      return cancelled.get() || !Instant.now().isBefore(deadline);
    }
  }

  /** Starts a run for the caller; returns at once with status RUNNING (or a refusal). */
  public AgentResult start(
      ApiIdentity.Principal caller, String agentId, Map<String, Object> input) {
    caller.require("agents:run", Resource.of("agent", caller.tenant()).named(agentId));
    var agent =
        registry
            .agent(agentId)
            .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("No agent " + agentId));
    var definition = agent.definition();
    if (runs.size() >= MAX_RUNS)
      runs.values().removeIf(r -> r.status != AgentResult.Status.RUNNING);
    var run =
        new Run(
            UUID.randomUUID().toString(),
            agentId,
            caller,
            Instant.now().plus(definition.timeout()),
            definition.maxSteps());
    runs.put(run.id, run);
    run.future =
        executor.submit(
            () -> execute(run, agent, definition.tools(), input == null ? Map.of() : input));
    timers.schedule(() -> expire(run), definition.timeout().toMillis(), TimeUnit.MILLISECONDS);
    return run.snapshot();
  }

  /** Runs and waits for the result (tests and synchronous callers). */
  public AgentResult run(ApiIdentity.Principal caller, String agentId, Map<String, Object> input)
      throws InterruptedException {
    var started = start(caller, agentId, input);
    var run = runs.get(started.runId());
    try {
      run.future.get(
          registry.agent(agentId).orElseThrow().definition().timeout().toMillis() + 5_000,
          TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.ExecutionException
        | java.util.concurrent.TimeoutException
        | java.util.concurrent.CancellationException ignored) {
      // the run's own status says what happened
    }
    return run.snapshot();
  }

  private void execute(
      Run run,
      ExtensionRegistry.RegisteredAgent agent,
      Set<String> allowed,
      Map<String, Object> input) {
    try {
      Map<String, Object> output =
          agent
              .agent()
              .run(
                  context(run, agent.definition().id(), allowed, agent.definition().delegates(), 0),
                  Map.copyOf(input));
      synchronized (run) {
        if (run.status == AgentResult.Status.RUNNING) {
          run.output = output == null ? Map.of() : Map.copyOf(output);
          run.status =
              run.cancelled.get() ? AgentResult.Status.CANCELLED : AgentResult.Status.COMPLETED;
        }
      }
    } catch (Exception e) {
      synchronized (run) {
        if (run.status == AgentResult.Status.RUNNING) {
          run.status =
              run.cancelled.get() ? AgentResult.Status.CANCELLED : AgentResult.Status.FAILED;
          run.error = e.getClass().getSimpleName();
        }
      }
    }
  }

  private void expire(Run run) {
    synchronized (run) {
      if (run.status != AgentResult.Status.RUNNING) return;
      run.status = AgentResult.Status.TIMED_OUT;
      run.error = "deadline exceeded";
    }
    run.cancelled.set(true);
    if (run.future != null) run.future.cancel(true);
  }

  private AgentContext context(
      Run run, String agentId, Set<String> allowedTools, Set<String> delegates, int depth) {
    return new AgentContext() {
      public String runId() {
        return run.id;
      }

      public String tenant() {
        return run.caller.tenant();
      }

      public String subject() {
        return run.caller.subject();
      }

      public ToolResult tool(String toolId, Map<String, Object> input) {
        long started = System.nanoTime();
        ToolResult result;
        if (run.stopped())
          result = new ToolResult(ToolResult.Status.CANCELLED, Map.of(), List.of("CANCELLED"));
        else if (run.stepCount.incrementAndGet() > run.maxSteps)
          result = new ToolResult(ToolResult.Status.DENIED, Map.of(), List.of("MAX_STEPS"));
        else if (!allowedTools.contains(toolId))
          result =
              new ToolResult(ToolResult.Status.DENIED, Map.of(), List.of("NOT_ALLOWED_FOR_AGENT"));
        else
          result =
              tools.invoke(
                  run.caller,
                  run.id,
                  toolId,
                  input == null ? Map.of() : input,
                  run::stopped,
                  run.deadline);
        run.step(
            "TOOL",
            agentId + "/" + toolId,
            result.status().name(),
            result.reasons(),
            (System.nanoTime() - started) / 1_000_000);
        return result;
      }

      public AgentResult delegate(String delegateId, Map<String, Object> input) {
        long started = System.nanoTime();
        var target = registry.agent(delegateId);
        AgentResult result;
        if (!delegates.contains(delegateId) || target.isEmpty() || depth + 1 > MAX_DEPTH) {
          String reason =
              !delegates.contains(delegateId)
                  ? "NOT_ALLOWED_FOR_AGENT"
                  : target.isEmpty() ? "UNKNOWN_AGENT" : "MAX_DEPTH";
          run.step("DELEGATE", agentId + "->" + delegateId, "DENIED", List.of(reason), 0);
          return new AgentResult(
              run.id, delegateId, AgentResult.Status.FAILED, Map.of(), List.of(), reason);
        }
        // Never wider than the delegating agent: both allow-lists must contain a tool.
        Set<String> narrowed = new HashSet<>(target.get().definition().tools());
        narrowed.retainAll(allowedTools);
        Set<String> nextDelegates = new HashSet<>(target.get().definition().delegates());
        try {
          var output =
              target
                  .get()
                  .agent()
                  .run(
                      context(run, delegateId, narrowed, nextDelegates, depth + 1),
                      input == null ? Map.of() : Map.copyOf(input));
          result =
              new AgentResult(
                  run.id, delegateId, AgentResult.Status.COMPLETED, output, List.of(), null);
        } catch (Exception e) {
          result =
              new AgentResult(
                  run.id,
                  delegateId,
                  run.stopped() ? AgentResult.Status.CANCELLED : AgentResult.Status.FAILED,
                  Map.of(),
                  List.of(),
                  e.getClass().getSimpleName());
        }
        run.step(
            "DELEGATE",
            agentId + "->" + delegateId,
            result.status().name(),
            List.of(),
            (System.nanoTime() - started) / 1_000_000);
        return result;
      }

      public void note(String name, String detail) {
        run.step(
            "NOTE",
            name == null ? "" : name.length() > 80 ? name.substring(0, 80) : name,
            "RECORDED",
            List.of(),
            0);
      }

      public boolean cancelled() {
        return run.stopped();
      }
    };
  }

  /** The caller's own run; other tenants' or subjects' runs are reported as absent. */
  public AgentResult inspect(ApiIdentity.Principal caller, String runId) {
    return owned(caller, runId).snapshot();
  }

  public AgentResult cancel(ApiIdentity.Principal caller, String runId) {
    var run = owned(caller, runId);
    synchronized (run) {
      if (run.status == AgentResult.Status.RUNNING) {
        run.status = AgentResult.Status.CANCELLED;
        run.error = "cancelled by owner";
      }
    }
    run.cancelled.set(true);
    if (run.future != null) run.future.cancel(true);
    return run.snapshot();
  }

  private Run owned(ApiIdentity.Principal caller, String runId) {
    caller.require("agents:read", Resource.of("agent", caller.tenant()));
    var run = runId == null ? null : runs.get(runId);
    if (run == null
        || !run.caller.tenant().equals(caller.tenant())
        || !run.caller.subject().equals(caller.subject()))
      throw new jakarta.ws.rs.NotFoundException("No agent run with that id");
    return run;
  }

  public Map<String, Object> catalog(ApiIdentity.Principal caller) {
    caller.require("agents:read", Resource.of("agent", caller.tenant()));
    var out = new LinkedHashMap<String, Object>();
    out.put("sdkVersion", io.qwenbridge.plugin.PluginSdk.VERSION);
    out.put("agents", registry.agents());
    out.put("tools", registry.tools());
    return out;
  }

  @PreDestroy
  void shutdown() {
    runs.values().forEach(r -> r.cancelled.set(true));
    executor.shutdownNow();
    timers.shutdownNow();
  }
}
