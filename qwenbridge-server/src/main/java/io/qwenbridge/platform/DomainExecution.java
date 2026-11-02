package io.qwenbridge.platform;

import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.commerce.*;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.connectors.*;
import io.qwenbridge.pack.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

/** Domain-neutral execution. No product or book rules live in this engine. */
@ApplicationScoped
public final class DomainExecution {
  public record Request(
      String query,
      String backend,
      String connectorPack,
      String mode,
      Integer limit,
      String cursor) {
    public Request {
      if (query == null || query.isBlank() || query.length() > 4096)
        throw new IllegalArgumentException("Invalid domain query");
      mode = mode == null ? "KEYWORD" : mode;
      limit = limit == null ? Integer.valueOf(10) : limit;
      if (limit < 1 || limit > 100 || cursor != null && cursor.length() > 4096)
        throw new IllegalArgumentException("Invalid page");
    }
  }

  public record Document(String id, Map<String, Object> data, String backend) {
    public Document {
      data = Map.copyOf(data);
    }
  }

  public record Response(
      String status,
      DomainPack.Understanding understanding,
      Plan queryIR,
      List<Map<String, Object>> documents,
      String nextCursor,
      PackPolicies.Outcome policy) {
    public Response {
      documents = List.copyOf(documents.stream().map(Map::copyOf).toList());
    }
  }

  private final ManagedPacks packs;
  private final CommerceBackends backends;
  private final PackPolicies policies;

  public DomainExecution(ManagedPacks packs, CommerceBackends backends, PackPolicies policies) {
    this.packs = packs;
    this.backends = backends;
    this.policies = policies;
  }

  public static Schema schema(DomainPack.Definition definition) {
    Map<String, Field> fields = new HashMap<>();
    definition
        .fields()
        .forEach(
            (name, field) ->
                fields.put(
                    name,
                    new Field(
                        ValueType.valueOf(field.type().name()),
                        field.operators().stream()
                            .map(o -> Operator.valueOf(o.name()))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                        field.backendField(),
                        field.caseFold() ? Normalization.LOWERCASE : Normalization.NONE)));
    return new Schema(definition.id(), definition.schemaVersion(), fields);
  }

  private DomainPack domain(QwenBridgePack pack) {
    if (!(pack instanceof DomainPack d))
      throw new IllegalArgumentException("A domain pack is required");
    return d;
  }

  public List<Map<String, Object>> normalize(
      ApiIdentity.Principal caller, String id, Document input) {
    caller.require("catalog:write");
    io.qwenbridge.security.Authorization.requireDomainPolicies(
        caller, id, "catalog:write", Map.of());
    if (input.id() == null || !input.id().matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid document identity");
    return packs.withPack(
        caller.tenant(),
        id,
        p -> {
          var documents = domain(p).documents(input.id(), input.data());
          if (documents.isEmpty() || documents.size() > 1000)
            throw new IllegalArgumentException("Invalid normalized document count");
          return documents.stream()
              .map(
                  doc -> {
                    var normalized = new HashMap<>(doc);
                    normalized.put("domain", id);
                    normalized.put("tenantId", caller.tenant());
                    return Map.<String, Object>copyOf(normalized);
                  })
              .toList();
        });
  }

  public List<String> ingest(ApiIdentity.Principal caller, String id, Document input) {
    var documents = normalize(caller, id, input);
    backends.backend(caller.tenant(), input.backend());
    List<String> ids = new ArrayList<>();
    for (var doc : documents) {
      String key =
          doc.containsKey("variantId")
              ? ApiIdentity.digest(input.id() + "\n" + Objects.toString(doc.get("variantId")))
              : Objects.toString(doc.getOrDefault("id", input.id()));
      backends.upsert(caller.tenant(), input.backend(), id + "-" + key, doc);
      ids.add(id + "-" + key);
    }
    return List.copyOf(ids);
  }

  public Response search(ApiIdentity.Principal caller, String id, Request input) {
    caller.require("search");
    var policy =
        io.qwenbridge.security.Authorization.requireDomainPolicies(
            caller, id, "search", Map.of("mode", input.mode()));
    return packs.withPack(
        caller.tenant(),
        id,
        p -> {
          DomainPack d = domain(p);
          var understood = d.understand(input.query());
          if (!understood.domain().equals(id) || !understood.original().equals(input.query()))
            throw new IllegalArgumentException(
                "Domain understanding is not grounded in this request");
          if (!understood.questions().isEmpty())
            return new Response("CLARIFICATION", understood, null, List.of(), null, policy);
          var schema = schema(d.definition());
          if (!schema.fields().containsKey("domain"))
            throw new IllegalArgumentException("Domain schema must declare the domain field");
          List<Constraint> constraints = new ArrayList<>();
          for (var c : understood.clauses())
            constraints.add(
                new Constraint(
                    c.field(),
                    Operator.valueOf(c.operator().name()),
                    c.value(),
                    new Evidence("original", c.start(), c.end(), c.quote())));
          constraints.add(
              new Constraint(
                  "domain",
                  Operator.EQ,
                  id,
                  new Evidence("domain-pack:" + p.manifest().id(), 0, id.length(), id)));
          var plan = CommerceCompiler.validate(schema, caller.tenant(), constraints);
          if (input.connectorPack() != null)
            return packs.withPack(
                caller.tenant(),
                input.connectorPack(),
                connector -> {
                  if (!(connector instanceof ConnectorPack c))
                    throw new IllegalArgumentException("A connector pack is required");
                  if (!c.modes().contains(input.mode()))
                    throw new jakarta.ws.rs.ClientErrorException(
                        "Connector does not support mode", 422);
                  var page =
                      c.search(
                          id,
                          d.searchText(understood),
                          understood.clauses(),
                          input.mode(),
                          input.cursor(),
                          input.limit());
                  for (var doc : page.documents())
                    if (!CommerceCompiler.matches(plan, doc))
                      throw new IllegalStateException(
                          "Connector violated domain, tenant or query constraints");
                  if (page.documents().size() > input.limit())
                    throw new IllegalStateException("Connector exceeded page size");
                  return new Response(
                      "OK", understood, plan, page.documents(), page.nextCursor(), policy);
                });
          if (!input.mode().equals("KEYWORD"))
            throw new jakarta.ws.rs.ClientErrorException(
                "Generic domain execution currently supports KEYWORD", 422);
          var page =
              backends.search(
                  caller,
                  input.backend(),
                  d.searchText(understood),
                  plan,
                  input.mode(),
                  List.of(),
                  input.limit(),
                  input.cursor());
          return new Response(
              "OK",
              understood,
              plan,
              page.results().hits().stream().map(h -> h.document()).toList(),
              page.nextCursor(),
              policy);
        });
  }
}
