package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client binding for the Ollama HTTP API. Transport concerns only; retry,
 * metrics, and error normalization remain in {@link OllamaClient}.
 */
@RegisterRestClient(configKey = "ollama")
public interface OllamaApiClient {

  @POST
  @Path("/api/chat")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  OllamaChatResponse chat(OllamaChatRequest request);

  @POST
  @Path("/api/embed")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  OllamaEmbeddingResponse embed(OllamaEmbeddingRequest request);

  @POST
  @Path("/api/chat")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces("application/x-ndjson")
  Multi<OllamaStreamingChatResponse> streamChat(OllamaChatRequest request);

  @GET
  @Path("/api/tags")
  Response tags();
}
