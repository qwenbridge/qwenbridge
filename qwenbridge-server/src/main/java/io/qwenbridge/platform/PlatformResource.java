package io.qwenbridge.platform;

import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/api/v2/platform")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PlatformResource {
  @Inject ManagedPacks packs;
  @Inject Entitlements entitlements;
  @Inject Marketplace marketplace;
  @Inject PackPolicies policies;
  @Inject io.qwenbridge.security.ApprovalStore approvals;

  @org.eclipse.microprofile.config.inject.ConfigProperty(
      name = "qwenbridge.policy.approvals-enforced",
      defaultValue = "false")
  boolean approvalsEnforced;

  @Context ContainerRequestContext context;

  /** Request to approve one high-risk operation (QB-075). */
  public record ApprovalRequest(
      String operation, String resourceType, String resourceId, Long validitySeconds) {}

  /** An issued approval; send its id as the {@code X-QwenBridge-Approval} header. */
  public record ApprovalView(
      String id, String operation, String resourceType, String expiresAt, boolean enforced) {}

  @POST
  @Path("/approvals")
  public ApprovalView approve(ApprovalRequest request) {
    if (request == null
        || request.operation() == null
        || request.resourceType() == null
        || !request.resourceType().matches("[a-z][a-z0-9_-]{0,31}"))
      throw new IllegalArgumentException("Invalid approval request");
    long seconds = request.validitySeconds() == null ? 900 : request.validitySeconds();
    if (seconds < 1 || seconds > 86_400) throw new IllegalArgumentException("Invalid validity");
    var approval =
        approvals.issue(
            ApiIdentity.caller(context),
            request.operation(),
            request.resourceType(),
            Optional.ofNullable(request.resourceId()),
            java.time.Duration.ofSeconds(seconds));
    return new ApprovalView(
        approval.id(),
        approval.operation(),
        approval.resource().type(),
        approval.expiresAt().toString(),
        approvalsEnforced);
  }

  /**
   * The tenant's license status (QB-261, QB-265) and the enabled packs it no longer covers
   * (QB-272); those packs are kept, not deleted, and resume when the entitlement returns.
   */
  public record LicenseView(
      Entitlements.Edition edition,
      Set<String> features,
      String licenseId,
      java.time.Instant expiresAt,
      Entitlements.State state,
      Entitlements.Problem problem,
      List<String> suspendedPacks) {
    public LicenseView {
      features = Set.copyOf(features);
      suspendedPacks = List.copyOf(suspendedPacks);
    }

    static LicenseView of(Entitlements.Status s, List<String> suspended) {
      return new LicenseView(
          s.edition(),
          s.features(),
          s.licenseId(),
          s.expiresAt(),
          s.state(),
          s.problem(),
          suspended);
    }
  }

  public record Activation(String version, Map<String, String> configuration) {
    public Activation {
      configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }
  }

  @GET
  @Path("/editions")
  public Map<Entitlements.Edition, Set<String>> editions() {
    ApiIdentity.caller(context).require("search");
    return Entitlements.MATRIX;
  }

  @GET
  @Path("/license")
  public LicenseView license() {
    var c = ApiIdentity.caller(context);
    c.require("search");
    return LicenseView.of(entitlements.status(c.tenant()), packs.suspended(c.tenant()));
  }

  @POST
  @Path("/license")
  public LicenseView license(Entitlements.SignedLicense license) {
    var c = ApiIdentity.caller(context);
    return LicenseView.of(entitlements.install(c, license), packs.suspended(c.tenant()));
  }

  @DELETE
  @Path("/license")
  public LicenseView downgrade() {
    var c = ApiIdentity.caller(context);
    return LicenseView.of(entitlements.downgrade(c), packs.suspended(c.tenant()));
  }

  @GET
  @Path("/packs")
  public List<ManagedPacks.View> packs() {
    var c = ApiIdentity.caller(context);
    c.require("packs:read");
    return packs.list(c.tenant());
  }

  @POST
  @Path("/packs")
  public ManagedPacks.View install(ManagedPacks.SignedPack pack) {
    return packs.install(ApiIdentity.caller(context), pack);
  }

  @POST
  @Path("/packs/{id}/activate")
  public Map<String, String> activate(@PathParam("id") String id, Activation request) {
    packs.activate(ApiIdentity.caller(context), id, request.version(), request.configuration());
    return Map.of("status", "ACTIVE");
  }

  @POST
  @Path("/packs/{id}/disable")
  public Map<String, String> disable(@PathParam("id") String id) {
    packs.disable(ApiIdentity.caller(context), id);
    return Map.of("status", "DISABLED");
  }

  @POST
  @Path("/packs/{id}/rollback")
  public Map<String, Object> rollback(@PathParam("id") String id) {
    var result = packs.rollback(ApiIdentity.caller(context), id);
    return Map.of(
        "status",
        "ROLLED_BACK",
        "from",
        result.from(),
        "to",
        result.to(),
        "configurationRestored",
        result.configurationRestored(),
        "notReverted",
        result.notReverted());
  }

  @GET
  @Path("/marketplace")
  public List<Marketplace.Listing> market() {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    entitlements.require(caller.tenant(), "marketplace-read");
    return marketplace.list();
  }

  @POST
  @Path("/marketplace")
  public Marketplace.Listing publish(ManagedPacks.SignedPack artifact) {
    return marketplace.publish(ApiIdentity.caller(context), artifact);
  }

  @GET
  @Path("/marketplace/{id}/{version}")
  public ManagedPacks.SignedPack download(
      @PathParam("id") String id, @PathParam("version") String version) {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    entitlements.require(caller.tenant(), "marketplace-read");
    return marketplace.download(id, version);
  }
}
