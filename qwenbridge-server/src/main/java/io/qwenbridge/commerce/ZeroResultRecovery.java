package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.platform.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.*;
import java.time.*;
import java.util.*;

/** No query is broadened until its authenticated owner accepts a specific server-held proposal. */
@ApplicationScoped
public final class ZeroResultRecovery {
  public record Offer(
      String id,
      String tenant,
      String subject,
      String parentRequestId,
      String backend,
      String description,
      String field,
      List<Constraint> constraints,
      SearchResponse parent,
      Instant expiresAt,
      String status,
      String acceptedBy,
      Instant acceptedAt,
      SearchResponse result,
      String kind) {
    public Offer(
        String id,
        String tenant,
        String subject,
        String parentRequestId,
        String backend,
        String description,
        String field,
        List<Constraint> constraints,
        SearchResponse parent,
        Instant expiresAt,
        String status,
        String acceptedBy,
        Instant acceptedAt,
        SearchResponse result) {
      this(
          id,
          tenant,
          subject,
          parentRequestId,
          backend,
          description,
          field,
          constraints,
          parent,
          expiresAt,
          status,
          acceptedBy,
          acceptedAt,
          result,
          null);
    }

    public Offer {
      constraints = List.copyOf(constraints);
    }
  }

  /**
   * An offered relaxation. {@code kind} is the policy rule applied; {@code requiresConsent} is true
   * for a HARD constraint, which is changed only when the user accepts (QB-128).
   */
  public record Proposal(
      String id,
      String description,
      String changedField,
      Instant expiresAt,
      String status,
      String kind,
      boolean requiresConsent) {}

  /** Skipped constraint and the reason (QB-130). */
  public record Kept(String field, String operator, String reason) {}

  /**
   * The recovery decision for a search (QB-125 – QB-130): what the result was, which policy
   * decided, the offers and, for every constraint left alone, why.
   */
  public record Recovery(
      String requestId,
      String outcome,
      int found,
      String policy,
      List<String> reasons,
      List<Proposal> proposals,
      List<Kept> kept) {
    public Recovery {
      reasons = List.copyOf(reasons);
      proposals = List.copyOf(proposals);
      kept = List.copyOf(kept);
    }
  }

  /** The Commerce relaxation policy before this change, as the configurable default. */
  public static final io.qwenbridge.core.recovery.RelaxationPolicy DEFAULT_POLICY =
      new io.qwenbridge.core.recovery.RelaxationPolicy(
          "commerce-relaxation",
          "1.0",
          List.of(
              io.qwenbridge.core.recovery.RelaxationRule.raise("price", "1.10"),
              io.qwenbridge.core.recovery.RelaxationRule.drop("size"),
              io.qwenbridge.core.recovery.RelaxationRule.drop("color")),
          Set.of("currency"),
          3,
          0);

  private final PlatformStore store;
  private final CommerceAnalytics analytics;
  private final CommerceEvidenceStore evidence;
  private final CommerceSearchService search;
  private final Entitlements entitlements;

  private final io.qwenbridge.core.recovery.RelaxationPolicy policy;

  public ZeroResultRecovery(
      PlatformStore store,
      CommerceAnalytics analytics,
      CommerceEvidenceStore evidence,
      CommerceSearchService search,
      Entitlements entitlements) {
    this(store, analytics, evidence, search, entitlements, DEFAULT_POLICY);
  }

  /**
   * QB-126: the policy is configured as {@code field:raise:factor}, {@code field:lower:factor} or
   * {@code field:drop} rules, tried in order; an invalid policy stops startup.
   */
  @jakarta.inject.Inject
  public ZeroResultRecovery(
      PlatformStore store,
      CommerceAnalytics analytics,
      CommerceEvidenceStore evidence,
      CommerceSearchService search,
      Entitlements entitlements,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "qwenbridge.commerce.relaxation.rules",
              defaultValue = "price:raise:1.10,size:drop,color:drop")
          List<String> rules,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "qwenbridge.commerce.relaxation.protected-fields",
              defaultValue = "currency")
          List<String> protectedFields,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "qwenbridge.commerce.relaxation.max-proposals",
              defaultValue = "3")
          int maxProposals) {
    this(
        store,
        analytics,
        evidence,
        search,
        entitlements,
        new io.qwenbridge.core.recovery.RelaxationPolicy(
            "commerce-relaxation",
            "1.0",
            rules.stream().map(ZeroResultRecovery::rule).toList(),
            Set.copyOf(protectedFields),
            maxProposals,
            0));
  }

  ZeroResultRecovery(
      PlatformStore store,
      CommerceAnalytics analytics,
      CommerceEvidenceStore evidence,
      CommerceSearchService search,
      Entitlements entitlements,
      io.qwenbridge.core.recovery.RelaxationPolicy policy) {
    this.policy = policy;
    this.store = store;
    this.analytics = analytics;
    this.evidence = evidence;
    this.search = search;
    this.entitlements = entitlements;
  }

  public List<Proposal> propose(ApiIdentity.Principal caller, String requestId, String backend) {
    return proposeVerified(caller, analytics.previous(caller, requestId), backend);
  }

  static io.qwenbridge.core.recovery.RelaxationRule rule(String spec) {
    String[] part = spec.strip().split(":");
    return switch (part.length == 0 ? "" : part.length >= 2 ? part[1] : "") {
      case "raise" -> io.qwenbridge.core.recovery.RelaxationRule.raise(part[0], part[2]);
      case "lower" -> io.qwenbridge.core.recovery.RelaxationRule.lower(part[0], part[2]);
      case "drop" -> io.qwenbridge.core.recovery.RelaxationRule.drop(part[0]);
      default -> throw new IllegalArgumentException("Invalid relaxation rule: " + spec);
    };
  }

  public io.qwenbridge.core.recovery.RelaxationPolicy policy() {
    return policy;
  }

  public List<Proposal> proposeVerified(
      ApiIdentity.Principal caller, SearchResponse previous, String backend) {
    return recoverVerified(caller, previous, backend).proposals();
  }

  public Recovery recover(ApiIdentity.Principal caller, String requestId, String backend) {
    return recoverVerified(caller, analytics.previous(caller, requestId), backend);
  }

  /** The universal form of a Commerce plan's constraints, ids {@code m1..}. */
  static List<io.qwenbridge.core.queryir.Constraint> universal(SearchResponse previous) {
    String original =
        previous.translation() == null || previous.translation().original() == null
            ? ""
            : previous.translation().original();
    var constraints = previous.queryIR().constraints();
    List<io.qwenbridge.core.queryir.Constraint> out = new ArrayList<>();
    for (int i = 0; i < constraints.size(); i++)
      out.add(CommerceInterpretation.universal("m" + (i + 1), original, constraints.get(i)));
    return out;
  }

  /**
   * QB-125: zero results from a working provider are recoverable; a provider that failed or did not
   * run its plan is not, and is never answered by broadening the query.
   */
  static io.qwenbridge.core.recovery.ResultAssessment assess(SearchResponse previous) {
    boolean executed = "OK".equals(previous.status()) && previous.queryIR() != null;
    var retrieval = previous.retrieval();
    boolean failed =
        retrieval != null
            && ("NONE".equals(retrieval.executed())
                || retrieval.degradations().stream().anyMatch(d -> d.contains("PROVIDER_FAILED")));
    return io.qwenbridge.core.recovery.ResultAssessment.of(
        executed, failed, (int) Math.min(Integer.MAX_VALUE, previous.results().totalHits()), 0);
  }

  public Recovery recoverVerified(
      ApiIdentity.Principal caller, SearchResponse previous, String backend) {
    caller.require("search");
    entitlements.require(caller.tenant(), "relaxation");
    if (previous.queryIR() != null && !previous.queryIR().tenant().equals(caller.tenant()))
      throw new jakarta.ws.rs.ForbiddenException();
    var assessment = assess(previous);
    if (!assessment.recoverable() || previous.queryIR() == null) {
      var plan =
          io.qwenbridge.core.recovery.RelaxationPlanner.plan(
              policy, assessment, List.of(), Set.of(), c -> 0);
      return view(previous, plan, List.of());
    }
    var commerce = previous.queryIR().constraints();
    String currency =
        commerce.stream()
            .filter(c -> c.field().equals("currency"))
            .map(Constraint::value)
            .findFirst()
            .orElse(null);
    int digits =
        currency == null
            ? 2
            : Math.max(0, java.util.Currency.getInstance(currency).getDefaultFractionDigits());
    var plan =
        io.qwenbridge.core.recovery.RelaxationPlanner.plan(
            policy,
            assessment,
            universal(previous),
            Set.copyOf(previous.lockedFields()),
            c -> c.field().equals("price") ? digits : 2);
    List<Proposal> proposals = new ArrayList<>();
    for (var relaxation : plan.proposals()) {
      int index = Integer.parseInt(relaxation.constraintId().substring(1)) - 1;
      var offer = offer(caller, previous, backend, commerce.get(index), relaxation, currency);
      if (offer != null) proposals.add(view(offer));
    }
    return view(previous, plan, proposals);
  }

  private Recovery view(
      SearchResponse previous,
      io.qwenbridge.core.recovery.RecoveryPlan plan,
      List<Proposal> proposals) {
    var universal =
        previous.queryIR() == null
            ? List.<io.qwenbridge.core.queryir.Constraint>of()
            : universal(previous);
    List<Kept> kept = new ArrayList<>();
    for (var skipped : plan.skipped()) {
      var c = universal.stream().filter(u -> u.id().equals(skipped.constraintId())).findFirst();
      kept.add(
          new Kept(
              skipped.field(),
              c.map(x -> x.operator().name()).orElse(""),
              skipped.reason().name()));
    }
    return new Recovery(
        previous.requestId(),
        plan.assessment().outcome().name(),
        plan.assessment().found(),
        plan.policy(),
        plan.reasons().stream().map(Enum::name).toList(),
        List.copyOf(proposals),
        List.copyOf(kept));
  }

  /** Stores (or finds) the server-held offer for one relaxation; null when it would be expired. */
  private Offer offer(
      ApiIdentity.Principal caller,
      SearchResponse previous,
      String backend,
      Constraint old,
      io.qwenbridge.core.recovery.Relaxation relaxation,
      String currency) {
    String field = old.field();
    String value =
        relaxation
            .after()
            .flatMap(io.qwenbridge.core.recovery.RelaxationPlanner::number)
            .map(BigDecimal::toPlainString)
            .orElse(null);
    String description =
        switch (relaxation.kind()) {
          case RAISE_UPPER_BOUND ->
              "Raise the maximum "
                  + (field.equals("price") ? "budget" : field)
                  + " from "
                  + old.value()
                  + " to "
                  + value
                  + (field.equals("price") && currency != null ? " " + currency : "")
                  + "; preserve every other condition.";
          case LOWER_LOWER_BOUND ->
              "Lower the minimum "
                  + field
                  + " from "
                  + old.value()
                  + " to "
                  + value
                  + "; preserve every other condition.";
          case DROP_EQUALITY ->
              "Remove only the "
                  + field
                  + " requirement '"
                  + old.value()
                  + "'; preserve every other condition.";
        };
    String id =
        ApiIdentity.digest(
            caller.subject() + "|" + previous.requestId() + "|" + field + "|" + description);
    Offer existing = store.read(caller.tenant(), "relaxations", id, Offer.class);
    if (existing != null) return existing;
    Instant expiry = Instant.now().plusSeconds(300);
    Instant sourceExpiry = previous.evidence().getFirst().observedAt().plusSeconds(3600);
    if (expiry.isAfter(sourceExpiry)) expiry = sourceExpiry;
    if (!expiry.isAfter(Instant.now())) return null;
    var span = new Evidence("original", 0, description.length(), description);
    var source =
        evidence.recordQuery(
            caller.tenant(),
            previous.requestId(),
            description,
            List.of(span),
            previous.evidence().getFirst().id());
    evidence.bindOwner(caller.tenant(), source.id(), caller.subject());
    List<Constraint> changed = new ArrayList<>();
    for (Constraint c : previous.queryIR().constraints())
      if (c != old) {
        var provenance =
            c.evidence().source().equals("original")
                ? new Evidence(
                    "evidence:" + previous.evidence().getFirst().id(),
                    c.evidence().start(),
                    c.evidence().end(),
                    c.evidence().quote())
                : c.evidence();
        changed.add(new Constraint(c.field(), c.operator(), c.value(), provenance));
      }
    if (value != null)
      changed.add(
          new Constraint(
              field,
              old.operator(),
              value,
              new Evidence("evidence:" + source.id(), 0, description.length(), description)));
    var offer =
        new Offer(
            id,
            caller.tenant(),
            caller.subject(),
            previous.requestId(),
            backend,
            description,
            field,
            changed,
            previous,
            expiry,
            "OFFERED",
            null,
            null,
            null,
            relaxation.kind().name());
    return store.update(
        caller.tenant(),
        "relaxations",
        id,
        Offer.class,
        Duration.between(Instant.now(), expiry),
        oldOffer -> oldOffer == null ? offer : oldOffer);
  }

  private Proposal view(Offer offer) {
    return new Proposal(
        offer.id(),
        offer.description(),
        offer.field(),
        offer.expiresAt(),
        offer.status(),
        offer.kind(),
        true);
  }

  private Offer owned(ApiIdentity.Principal caller, Offer offer) {
    if (offer == null
        || !offer.tenant().equals(caller.tenant())
        || !offer.subject().equals(caller.subject()))
      throw new jakarta.ws.rs.NotFoundException("Proposal unavailable");
    if (!Instant.now().isBefore(offer.expiresAt()))
      throw new jakarta.ws.rs.ClientErrorException("Proposal expired", 410);
    return offer;
  }

  public Offer offer(ApiIdentity.Principal caller, String id) {
    return owned(caller, store.read(caller.tenant(), "relaxations", id, Offer.class));
  }

  public SearchResponse accept(ApiIdentity.Principal caller, String id) {
    caller.require("search");
    entitlements.require(caller.tenant(), "relaxation");
    var before = owned(caller, store.read(caller.tenant(), "relaxations", id, Offer.class));
    if (before.result() != null) return before.result();
    // QB-129: a held alternative is re-validated against the original query and the policy before
    // it runs; anything but a permitted relaxation of one condition is refused.
    var parentConstraints = universal(before.parent());
    var proposed =
        universal(
            new SearchResponse(
                before.parent().apiVersion(),
                before.parent().requestId(),
                before.parent().status(),
                before.parent().translation(),
                before.parent().intent(),
                new Plan(
                    1,
                    before.parent().queryIR().schemaId(),
                    before.parent().queryIR().schemaVersion(),
                    before.parent().queryIR().tenant(),
                    before.constraints(),
                    before.parent().queryIR().fields()),
                List.of(),
                before.parent().results(),
                before.parent().confidence(),
                before.parent().evidence(),
                List.of(),
                before.parent().reranking(),
                0));
    var validation =
        io.qwenbridge.core.recovery.AlternativeValidator.validate(
            policy, parentConstraints, proposed, Set.copyOf(before.parent().lockedFields()));
    if (!validation.valid())
      throw new jakarta.ws.rs.ClientErrorException(
          "Proposal no longer satisfies the relaxation policy: " + validation.reasons(), 409);
    var claimed =
        store.update(
            caller.tenant(),
            "relaxations",
            id,
            Offer.class,
            Duration.between(Instant.now(), before.expiresAt()),
            old -> {
              var o = owned(caller, old);
              if (o.status().equals("CLAIMED") || o.status().equals("ACCEPTED"))
                throw new jakarta.ws.rs.ClientErrorException(
                    "Proposal is already being accepted", 409);
              return new Offer(
                  o.id(),
                  o.tenant(),
                  o.subject(),
                  o.parentRequestId(),
                  o.backend(),
                  o.description(),
                  o.field(),
                  o.constraints(),
                  o.parent(),
                  o.expiresAt(),
                  "CLAIMED",
                  caller.subject(),
                  Instant.now(),
                  null,
                  o.kind());
            });
    try {
      var prior = claimed.parent().queryIR();
      var plan =
          CommerceCompiler.validate(
              new Schema(prior.schemaId(), prior.schemaVersion(), prior.fields()),
              caller.tenant(),
              claimed.constraints());
      var result =
          search.executeApproved(
              caller,
              claimed.parent(),
              plan,
              "Accepted proposal " + id + ": " + claimed.description(),
              claimed.backend());
      store.update(
          caller.tenant(),
          "relaxations",
          id,
          Offer.class,
          Duration.between(Instant.now(), claimed.expiresAt()),
          o ->
              new Offer(
                  o.id(),
                  o.tenant(),
                  o.subject(),
                  o.parentRequestId(),
                  o.backend(),
                  o.description(),
                  o.field(),
                  o.constraints(),
                  o.parent(),
                  o.expiresAt(),
                  "ACCEPTED",
                  o.acceptedBy(),
                  o.acceptedAt(),
                  result,
                  o.kind()));
      return result;
    } catch (RuntimeException ex) {
      store.update(
          caller.tenant(),
          "relaxations",
          id,
          Offer.class,
          Duration.between(Instant.now(), claimed.expiresAt()),
          o ->
              new Offer(
                  o.id(),
                  o.tenant(),
                  o.subject(),
                  o.parentRequestId(),
                  o.backend(),
                  o.description(),
                  o.field(),
                  o.constraints(),
                  o.parent(),
                  o.expiresAt(),
                  "FAILED",
                  o.acceptedBy(),
                  o.acceptedAt(),
                  null,
                  o.kind()));
      throw ex;
    }
  }
}
