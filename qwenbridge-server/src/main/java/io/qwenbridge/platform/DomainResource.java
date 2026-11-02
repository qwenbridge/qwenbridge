package io.qwenbridge.platform;

import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/api/v2/domains/{domain}")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DomainResource {
  @Inject DomainExecution execution;
  @Context ContainerRequestContext context;
  @Inject io.qwenbridge.api.requests.ApiRequests requests;

  @POST
  @Path("/normalize")
  public List<Map<String, Object>> normalize(
      @PathParam("domain") String domain, DomainExecution.Document input) {
    return execution.normalize(ApiIdentity.caller(context), domain, input);
  }

  @POST
  @Path("/documents")
  public List<String> ingest(@PathParam("domain") String domain, DomainExecution.Document input) {
    return execution.ingest(ApiIdentity.caller(context), domain, input);
  }

  @POST
  @Path("/search")
  public DomainExecution.Response search(
      @PathParam("domain") String domain, DomainExecution.Request input) {
    var caller = ApiIdentity.caller(context);
    if (input != null) requests.requirePageSize(input.limit());
    Object id = context.getProperty(io.qwenbridge.api.header.ApiHeaders.REQUEST_ID);
    String requestId =
        id instanceof String value && io.qwenbridge.core.context.RequestIds.isValid(value)
            ? value
            : UUID.randomUUID().toString();
    return requests.run(context, caller, requestId, () -> execution.search(caller, domain, input));
  }
}
