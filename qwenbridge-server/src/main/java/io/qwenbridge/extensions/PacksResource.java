package io.qwenbridge.extensions;

import io.qwenbridge.core.connector.pack.ConnectorPacks;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.domains.DomainPacks;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Read-only discovery of the deployment's Domain Packs and Connector Packs (QB-232 – QB-240): what
 * is active, what each pack registered or can do, and its health. Capabilities are reported as
 * data; nothing is executed. Configuration is shown by key name only, never by value.
 */
@Path("/api/v2")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PacksResource {

  @Inject DomainPacks domains;
  @Inject ConnectorPackService connectors;
  @Context ContainerRequestContext context;

  private void requireRead() {
    var caller = ApiIdentity.caller(context);
    caller.require("packs:read", Resource.of("pack", caller.tenant()));
  }

  @GET
  @Path("/domain-packs")
  public List<DomainPacks.Registration> domainPacks() {
    requireRead();
    return domains.registrations();
  }

  @GET
  @Path("/connector-packs")
  public List<ConnectorPacks.PackStatus> connectorPacks() {
    requireRead();
    return connectors.statuses();
  }
}
