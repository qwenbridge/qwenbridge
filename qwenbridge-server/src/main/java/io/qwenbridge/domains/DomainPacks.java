package io.qwenbridge.domains;

import io.qwenbridge.core.policy.PolicyRequest;
import io.qwenbridge.core.policy.PolicyRule;
import io.qwenbridge.core.policy.Scope;
import io.qwenbridge.core.queryir.Extension;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.schema.SchemaKey;
import io.qwenbridge.core.schema.SchemaRegistry;
import io.qwenbridge.pack.DomainPack;
import io.qwenbridge.pack.PolicyPack;
import io.qwenbridge.schema.DomainPackSchemas;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Domain Pack lifecycle against the Core contracts (QB-232 – QB-236). Activating a pack registers,
 * as one unit:
 *
 * <ul>
 *   <li>its schema in the in-memory {@link SchemaRegistry}, owned by {@code pack:<pack id>} and
 *       recorded with the pack version;
 *   <li>its QueryIR extension namespace (the pack's domain id) with the versions it can read;
 *   <li>its policy rules, which run in the central policy engine for resources in its domain and
 *       can only deny or ask for approval.
 * </ul>
 *
 * <p>Any conflict (a domain or namespace another pack owns, an invalid definition) refuses the
 * activation and leaves nothing behind. Deactivation removes exactly what the pack registered.
 * Packs are independent: no pack depends on another being active, and the Core needs none.
 */
public final class DomainPacks {

  /** What one active pack registered. */
  public record Registration(
      String packId,
      String packVersion,
      String domain,
      List<SchemaKey> schemas,
      Optional<DomainPack.QueryExtension> extension,
      List<String> rules) {
    public Registration {
      schemas = List.copyOf(schemas);
      rules = List.copyOf(rules);
    }
  }

  private record Active(DomainPack pack, Registration registration) {}

  private final SchemaRegistry schemas;
  private final Map<String, Active> active = new LinkedHashMap<>();

  public DomainPacks(SchemaRegistry schemas) {
    this.schemas = schemas;
  }

  public SchemaRegistry schemas() {
    return schemas;
  }

  public static String owner(DomainPack pack) {
    return "pack:" + pack.manifest().id();
  }

  /** Activates a pack; on any conflict nothing is registered and the reason is thrown. */
  public synchronized Registration activate(DomainPack pack) {
    var manifest = pack.manifest();
    if (active.containsKey(manifest.id()))
      throw new IllegalArgumentException("Domain pack " + manifest.id() + " is already active");
    DomainPack.Definition definition = pack.definition();
    String domain = definition.id();
    for (Active other : active.values())
      if (other.registration().domain().equals(domain))
        throw new IllegalArgumentException(
            "Domain " + domain + " is owned by pack " + other.registration().packId());
    Optional<DomainPack.QueryExtension> extension = pack.queryExtension();
    if (extension.isPresent() && !extension.get().namespace().equals(domain))
      throw new IllegalArgumentException(
          "The QueryIR extension namespace of pack " + manifest.id() + " must be its domain");
    List<DomainPack.Rule> rules = List.copyOf(pack.policies());
    if (rules.stream().map(DomainPack.Rule::id).distinct().count() != rules.size())
      throw new IllegalArgumentException("Duplicate policy rule ids in pack " + manifest.id());
    var schema = schemas.register(owner(pack), DomainPackSchemas.toSchema(definition));
    var registration =
        new Registration(
            manifest.id(),
            manifest.version(),
            domain,
            List.of(schema.key()),
            extension,
            rules.stream().map(r -> manifest.id() + "/" + r.id()).toList());
    active.put(manifest.id(), new Active(pack, registration));
    return registration;
  }

  /** Deactivates a pack: removes only the schemas, namespace and rules it registered. */
  public synchronized Optional<Registration> deactivate(String packId) {
    Active removed = active.remove(packId);
    if (removed == null) return Optional.empty();
    schemas.unregisterAll("pack:" + packId);
    return Optional.of(removed.registration());
  }

  public synchronized List<Registration> registrations() {
    return active.values().stream().map(Active::registration).toList();
  }

  public synchronized Optional<DomainPack> forDomain(String domain) {
    return active.values().stream()
        .filter(a -> a.registration().domain().equals(domain))
        .map(Active::pack)
        .findFirst();
  }

  /**
   * QB-234: problems with a query's domain extensions; empty when every payload belongs to an
   * active pack, is in a version that pack can read and passes its validation. The universal fields
   * are not touched.
   */
  public List<String> validate(QueryIR query) {
    return validate(query.extensions());
  }

  public List<String> validate(Map<String, Extension> extensions) {
    List<String> problems = new ArrayList<>();
    for (var entry : new TreeMap<>(extensions).entrySet()) {
      String namespace = entry.getKey();
      Extension payload = entry.getValue();
      Optional<DomainPack> owner;
      synchronized (this) {
        owner = forDomain(namespace);
      }
      if (owner.isEmpty() || owner.get().queryExtension().isEmpty()) {
        problems.add(namespace + ": no active domain pack owns this extension");
        continue;
      }
      var spec = owner.get().queryExtension().get();
      if (!spec.readableVersions().contains(payload.version())) {
        problems.add(
            namespace
                + ": incompatible extension version "
                + payload.version()
                + " (readable: "
                + spec.readableVersions().stream().sorted().toList()
                + ")");
        continue;
      }
      try {
        owner.get().validateExtension(payload.version(), payload.data()).stream()
            .map(p -> namespace + ": " + p)
            .forEach(problems::add);
      } catch (RuntimeException e) {
        problems.add(namespace + ": extension validation failed");
      }
    }
    return problems;
  }

  /**
   * QB-235: the active packs' rules as central policy rules. Each applies only to resources in its
   * pack's domain; ALLOW and ABSTAIN never grant (they are not applicable), DENY denies and REVIEW
   * asks for an approval. Exceptions propagate, and the engine turns them into a denial.
   */
  public synchronized List<PolicyRule> rules() {
    List<PolicyRule> out = new ArrayList<>();
    for (Active a : active.values()) {
      String domain = a.registration().domain();
      for (DomainPack.Rule rule : a.pack().policies())
        out.add(new DomainRule(a.registration().packId() + "/" + rule.id(), domain, rule));
    }
    return List.copyOf(out);
  }

  private record DomainRule(String id, String domain, DomainPack.Rule rule) implements PolicyRule {
    @Override
    public Verdict evaluate(PolicyRequest request) {
      if (!request.resource().domain().map(domain::equals).orElse(false))
        return Verdict.NOT_APPLICABLE;
      var input =
          new PolicyPack.Input(
              request.subject().tenant(),
              request.subject().id(),
              request.subject().scopes().stream().map(Scope::operation).collect(Collectors.toSet()),
              domain,
              request.operation(),
              Map.<String, Object>copyOf(request.context()));
      PolicyPack.Effect effect = rule.evaluate(input);
      if (effect == null) throw new IllegalStateException("A domain rule returned no effect");
      return switch (effect) {
        case DENY -> Verdict.DENY;
        case REVIEW -> Verdict.REQUIRE_APPROVAL;
        case ALLOW, ABSTAIN -> Verdict.NOT_APPLICABLE;
      };
    }
  }

  /** Activates every pack in order; a pack that conflicts is skipped and reported. */
  public Map<String, String> activateAll(Collection<DomainPack> packs) {
    Map<String, String> skipped = new LinkedHashMap<>();
    for (DomainPack pack : packs)
      try {
        activate(pack);
      } catch (RuntimeException e) {
        String id;
        try {
          id = pack.manifest().id();
        } catch (RuntimeException unreadable) {
          id = pack.getClass().getName();
        }
        skipped.put(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      }
    return skipped;
  }
}
