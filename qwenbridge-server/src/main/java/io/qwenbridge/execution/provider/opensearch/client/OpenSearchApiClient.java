package io.qwenbridge.execution.provider.opensearch.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client binding for the OpenSearch HTTP API. Transport concerns only; metrics
 * and error handling remain in {@link OpenSearchClient}.
 */
@RegisterRestClient(configKey = "opensearch")
public interface OpenSearchApiClient {

  @POST
  @Path("/{index}/_search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Map<String, Object> search(@PathParam("index") String index, Map<String, Object> query);

  @GET
  @Path("/")
  Response ping();
}
