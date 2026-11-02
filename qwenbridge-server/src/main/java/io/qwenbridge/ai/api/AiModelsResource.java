package io.qwenbridge.ai.api;

import io.quarkus.arc.All;
import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.ai.routing.ChatRouting;
import io.qwenbridge.core.ranking.Reranker;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model and capability discovery for chat, embedding and reranking (QB-246, QB-247): which provider
 * and model serve each capability, what they support, and when vectors must be reindexed. Public
 * metadata only; credentials are never part of it.
 */
@Path("/api/v2/ai/models")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AiModelsResource {

  @Inject ChatRouting chat;
  @Inject EmbeddingStages embeddings;
  @Inject @All List<Reranker> rerankers;
  @Context ContainerRequestContext context;

  @GET
  public Map<String, Object> models() {
    ApiIdentity.caller(context).require("search");
    var out = new LinkedHashMap<String, Object>();
    var chatProviders =
        chat.router().providers().stream()
            .map(
                p -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", p.id());
                  m.put("model", p.model());
                  m.put("capabilities", p.capabilities());
                  return m;
                })
            .toList();
    out.put(
        "chat",
        Map.of(
            "primary",
            chatProviders.getFirst().get("id"),
            "fallbacks",
            chat.fallbacks(),
            "fallbackPolicy",
            chat.active() ? "ON_PROVIDER_FAULT" : "NONE",
            "providers",
            chatProviders));
    var stage = embeddings.stage();
    var embedding = new LinkedHashMap<String, Object>();
    embedding.put("enabled", stage.model().isPresent());
    stage.providerId().ifPresent(id -> embedding.put("provider", id));
    stage
        .model()
        .ifPresent(
            m -> {
              embedding.put(
                  "space",
                  Map.of(
                      "model", m.space().model(),
                      "version", m.space().version(),
                      "dimensions", m.space().dimensions()));
              embedding.put("maxBatch", m.maxBatch());
              embedding.put("maxInputChars", m.maxInputChars());
            });
    embedding.put("fallback", "none: a fallback must embed into the same space");
    embedding.put("reindexRequiredWhen", List.of("model", "version", "dimensions"));
    out.put("embedding", embedding);
    out.put(
        "reranking",
        rerankers.stream()
            .map(
                r -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("id", r.id());
                  m.put("maxCandidates", r.maxCandidates());
                  r.model().ifPresent(d -> m.put("model", d));
                  return m;
                })
            .toList());
    return out;
  }
}
