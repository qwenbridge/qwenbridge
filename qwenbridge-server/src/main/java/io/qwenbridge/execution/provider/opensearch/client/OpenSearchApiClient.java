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
@org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders(OpenSearchAuthHeaders.class)
public interface OpenSearchApiClient {

  @POST
  @Path("/{index}/_search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Map<String, Object> search(@PathParam("index") String index, Map<String, Object> query);

  @GET
  @Path("/")
  Response ping();

  @jakarta.ws.rs.PUT
  @Path("/{index}")
  @Consumes(MediaType.APPLICATION_JSON)
  Map<String, Object> createIndex(@PathParam("index") String index, Map<String, Object> mapping);

  @jakarta.ws.rs.PUT
  @Path("/{index}/_doc/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  Map<String, Object> upsert(
      @PathParam("index") String index, @PathParam("id") String id, Map<String, Object> document);

  @jakarta.ws.rs.DELETE
  @Path("/{index}/_doc/{id}")
  Map<String, Object> delete(@PathParam("index") String index, @PathParam("id") String id);
}
