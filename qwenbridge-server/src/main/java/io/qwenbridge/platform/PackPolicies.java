package io.qwenbridge.platform;

import io.qwenbridge.pack.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public final class PackPolicies {
  public record Trace(
      String pack, String version, int order, String rule, String effect, String reason) {}

  public record Outcome(String effect, boolean conflict, List<Trace> decisions) {
    public Outcome {
      decisions = List.copyOf(decisions);
    }

    public boolean allowed() {
      return effect.equals("ALLOW");
    }

    /** QB-252: the policy pack versions that took part in this decision, as {@code id@version}. */
    public List<String> versions() {
      return decisions.stream()
          .filter(t -> !t.pack().equals("platform-authorization"))
          .map(t -> t.pack() + "@" + t.version())
          .distinct()
          .toList();
    }
  }

  private final ManagedPacks packs;

  private record Ordered(ManagedPacks.View view, int order) {}

  public PackPolicies(ManagedPacks packs) {
    this.packs = packs;
  }

  public Outcome evaluate(
      ApiIdentity.Principal caller,
      String domain,
      String operation,
      Map<String, Object> attributes) {
    if (!caller.roles().contains(operation)) {
      // Audit S2: pack evaluation never stands in for authorization. Without the operation's
      // scope the outcome is DENY, whatever packs are installed.
      return new Outcome(
          "DENY",
          false,
          List.of(
              new Trace(
                  "platform-authorization",
                  "1.0.0",
                  0,
                  "no-scope",
                  "DENY",
                  "The caller holds no scope for this operation")));
    }
    var input =
        new PolicyPack.Input(
            caller.tenant(), caller.subject(), caller.roles(), domain, operation, attributes);
    List<Trace> traces = new ArrayList<>();
    List<Ordered> ordered = new ArrayList<>();
    for (var view : packs.list(caller.tenant()))
      if (view.active() && view.manifest().kind() == PackManifest.Kind.POLICY) {
        try {
          int order =
              packs.withPack(caller.tenant(), view.manifest().id(), p -> ((PolicyPack) p).order());
          ordered.add(new Ordered(view, order));
        } catch (RuntimeException ex) {
          traces.add(
              new Trace(
                  view.manifest().id(),
                  view.manifest().version(),
                  Integer.MIN_VALUE,
                  "policy-unavailable",
                  "DENY",
                  "Policy evaluation failed; execution was stopped"));
        }
      }
    ordered.sort(
        Comparator.comparingInt(Ordered::order).thenComparing(o -> o.view().manifest().id()));
    for (var entry : ordered) {
      var view = entry.view();
      if (view.active() && view.manifest().kind() == PackManifest.Kind.POLICY) {
        try {
          Trace trace =
              packs.withPack(
                  caller.tenant(),
                  view.manifest().id(),
                  pack -> {
                    PolicyPack policy = (PolicyPack) pack;
                    if (!policy.manifest().version().equals(view.manifest().version()))
                      throw new IllegalStateException("Policy changed during evaluation");
                    if (!policy.domains().contains(domain) && !policy.domains().contains("*"))
                      return null;
                    if (!policy.operations().contains(operation)
                        && !policy.operations().contains("*")) return null;
                    var decision = policy.evaluate(input);
                    return new Trace(
                        view.manifest().id(),
                        view.manifest().version(),
                        policy.order(),
                        decision.rule(),
                        decision.effect().name(),
                        decision.reason());
                  });
          if (trace != null) traces.add(trace);
        } catch (RuntimeException ex) {
          traces.add(
              new Trace(
                  view.manifest().id(),
                  view.manifest().version(),
                  Integer.MIN_VALUE,
                  "policy-unavailable",
                  "DENY",
                  "Policy evaluation failed; execution was stopped"));
        }
      }
    }
    traces.sort(Comparator.comparingInt(Trace::order).thenComparing(Trace::pack));
    boolean deny = traces.stream().anyMatch(t -> t.effect().equals("DENY"));
    boolean review = traces.stream().anyMatch(t -> t.effect().equals("REVIEW"));
    boolean allow = traces.stream().anyMatch(t -> t.effect().equals("ALLOW"));
    if (traces.isEmpty())
      traces.add(
          new Trace(
              "platform-authorization",
              "1.0.0",
              0,
              "scope",
              "ALLOW",
              "Allowed by the caller's scope; no domain pack policy applies"));
    return new Outcome(
        deny ? "DENY" : review ? "REVIEW" : "ALLOW", allow && (deny || review), traces);
  }

  public Outcome require(
      ApiIdentity.Principal caller,
      String domain,
      String operation,
      Map<String, Object> attributes) {
    Outcome result = evaluate(caller, domain, operation, attributes);
    if (!result.allowed())
      throw new jakarta.ws.rs.ForbiddenException(
          "Policy stopped execution: " + result.decisions().stream().map(Trace::reason).toList());
    return result;
  }
}
