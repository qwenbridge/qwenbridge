package io.qwenbridge.extensions;

import io.qwenbridge.agent.AgentResult;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/** Plugin SDK 1.0 extensions and agent runs (QB-225 – QB-230). */
@Path("/api/v2")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ExtensionsResource {

  public record RunRequest(Map<String, Object> input) {
    public RunRequest {
      input = input == null ? Map.of() : Map.copyOf(input);
    }
  }

  @Inject AgentRuntime agents;
  @Inject PluginHost plugins;
  @Context ContainerRequestContext context;

  @GET
  @Path("/extensions")
  public List<PluginHost.Status> extensions() {
    var caller = ApiIdentity.caller(context);
    caller.require("plugins:read", Resource.of("plugin", caller.tenant()));
    return plugins.statuses();
  }

  @GET
  @Path("/agents")
  public Map<String, Object> catalog() {
    return agents.catalog(ApiIdentity.caller(context));
  }

  @POST
  @Path("/agents/{agentId}/runs")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response start(@PathParam("agentId") String agentId, RunRequest request) {
    AgentResult run =
        agents.start(
            ApiIdentity.caller(context), agentId, request == null ? Map.of() : request.input());
    return Response.accepted(run).build();
  }

  @GET
  @Path("/agents/runs/{runId}")
  public AgentResult inspect(@PathParam("runId") String runId) {
    return agents.inspect(ApiIdentity.caller(context), runId);
  }

  @DELETE
  @Path("/agents/runs/{runId}")
  public AgentResult cancel(@PathParam("runId") String runId) {
    return agents.cancel(ApiIdentity.caller(context), runId);
  }
}
