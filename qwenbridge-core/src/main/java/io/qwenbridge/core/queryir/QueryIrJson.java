package io.qwenbridge.core.queryir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.queryir.IrViolation.Code;
import io.qwenbridge.core.request.LanguageTag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Strict JSON (de)serialization of {@link QueryIR} (QB-041, QB-042).
 *
 * <ul>
 *   <li>The version is checked first; unsupported versions are rejected before any other field is
 *       interpreted (see {@link IrVersion} for the compatibility rules).
 *   <li>Unknown fields are rejected everywhere except inside {@code extensions}.
 *   <li>Numbers are read exactly as {@link BigDecimal}; JSON nesting is bounded.
 *   <li>Every failure is a typed {@link IrViolation} with a JSON-pointer path; user text is never
 *       echoed.
 *   <li>Output is deterministic: fixed field order, optional values omitted when absent.
 * </ul>
 */
public final class QueryIrJson {

  public static final int MAX_JSON_DEPTH = 32;

  private static final ObjectMapper MAPPER = createMapper();
  private static final JsonNodeFactory NODES = JsonNodeFactory.withExactBigDecimals(true);

  private QueryIrJson() {}

  private static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(MAX_JSON_DEPTH).build());
    mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    return mapper;
  }

  /** Parsing outcome: a valid QueryIR, or every violation found. */
  public sealed interface Result {
    record Valid(QueryIR ir) implements Result {}

    record Invalid(List<IrViolation> violations) implements Result {
      public Invalid {
        violations = List.copyOf(violations);
      }
    }
  }

  // ---------------------------------------------------------------------------------------
  // Writing
  // ---------------------------------------------------------------------------------------

  public static String write(QueryIR ir) {
    try {
      return MAPPER.writeValueAsString(toTree(ir));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("QueryIR could not be serialized", e);
    }
  }

  public static ObjectNode toTree(QueryIR ir) {
    ObjectNode root = NODES.objectNode();
    root.put("irVersion", ir.version().toString());
    ir.queryId().ifPresent(id -> root.put("queryId", id));
    root.put("originalText", ir.originalText());
    root.put("language", ir.language().value());
    array(root, "intents", ir.intents(), QueryIrJson::intent);
    array(root, "entities", ir.entities(), QueryIrJson::entity);
    array(root, "constraints", ir.constraints(), QueryIrJson::constraint);
    array(root, "semanticSignals", ir.semanticSignals(), QueryIrJson::signal);
    if (!ir.retrievalHints().equals(RetrievalHints.NONE)) {
      root.set("retrievalHints", hints(ir.retrievalHints()));
    }
    array(root, "subqueries", ir.subqueries(), QueryIrJson::subquery);
    if (!ir.extensions().isEmpty()) {
      ObjectNode extensions = root.putObject("extensions");
      new TreeMap<>(ir.extensions())
          .forEach(
              (ns, ext) -> {
                ObjectNode node = extensions.putObject(ns);
                node.put("version", ext.version());
                node.set("data", javaToJson(ext.data()));
              });
    }
    ir.correction().ifPresent(c -> root.set("correction", correction(c)));
    ir.expansion().ifPresent(e -> root.set("expansion", expansion(e)));
    return root;
  }

  private static JsonNode expansion(QueryExpansion e) {
    ObjectNode n = NODES.objectNode();
    n.put("status", e.status());
    n.put("policy", e.policy());
    n.put("sourceText", e.sourceText());
    e.sourceLanguage().ifPresent(l -> n.put("sourceLanguage", l.value()));
    strings(n, "configuredTargets", e.configuredTargets());
    if (!e.targets().isEmpty()) {
      ArrayNode items = n.putArray("targets");
      for (QueryExpansion.Target t : e.targets()) {
        ObjectNode item = items.addObject();
        item.put("requested", t.requested());
        t.language().ifPresent(l -> item.put("language", l.value()));
        item.put("status", t.status());
        t.text().ifPresent(x -> item.put("text", x));
        t.provider().ifPresent(x -> item.put("provider", x));
        item.put("confidence", t.confidence());
        t.reason().ifPresent(x -> item.put("reason", x));
        t.preservation().ifPresent(x -> item.put("preservation", x));
        item.put("retrieval", t.retrieval());
      }
    }
    return n;
  }

  private static JsonNode correction(TextCorrection c) {
    ObjectNode n = NODES.objectNode();
    n.put("status", c.status());
    n.put("mode", c.mode());
    n.put("normalizedText", c.normalizedText());
    n.put("correctedText", c.correctedText());
    strings(n, "configuredLanguages", c.configuredLanguages());
    strings(n, "activeLanguages", c.activeLanguages());
    c.detectedLanguage().ifPresent(l -> n.put("detectedLanguage", l.value()));
    if (!c.corrections().isEmpty()) {
      ArrayNode items = n.putArray("corrections");
      for (TextCorrection.Correction x : c.corrections()) {
        ObjectNode item = items.addObject();
        item.put("token", x.token());
        item.put("start", x.start());
        item.put("end", x.end());
        item.put("language", x.language());
        item.put("replacement", x.replacement());
        item.put("confidence", x.confidence());
        item.put("applied", x.applied());
        item.put("reason", x.reason());
      }
    }
    return n;
  }

  private static <T> void array(
      ObjectNode root, String name, List<T> items, Function<T, JsonNode> mapper) {
    if (!items.isEmpty()) {
      ArrayNode array = root.putArray(name);
      items.forEach(item -> array.add(mapper.apply(item)));
    }
  }

  private static JsonNode intent(Intent i) {
    ObjectNode n = NODES.objectNode();
    n.put("id", i.id());
    n.put("type", i.type());
    n.set("confidence", confidence(i.confidence()));
    i.span().ifPresent(s -> n.set("span", span(s)));
    return n;
  }

  private static JsonNode entity(Entity e) {
    ObjectNode n = NODES.objectNode();
    n.put("id", e.id());
    n.put("type", e.type());
    n.put("value", e.value());
    e.normalizedValue().ifPresent(v -> n.put("normalizedValue", v));
    n.set("span", span(e.span()));
    n.set("confidence", confidence(e.confidence()));
    return n;
  }

  private static JsonNode constraint(Constraint c) {
    ObjectNode n = NODES.objectNode();
    n.put("id", c.id());
    n.put("field", c.field());
    n.put("operator", c.operator().name());
    c.value().ifPresent(v -> n.set("value", value(v)));
    n.put("strength", c.strength().name());
    n.put("provenance", c.provenance().name());
    n.put("reason", c.reason());
    c.span().ifPresent(s -> n.set("span", span(s)));
    n.set("confidence", confidence(c.confidence()));
    c.scope().ifPresent(s -> n.put("scope", s));
    return n;
  }

  private static JsonNode signal(SemanticSignal s) {
    ObjectNode n = NODES.objectNode();
    n.put("id", s.id());
    n.put("concept", s.concept());
    n.put("polarity", s.polarity().name());
    s.target().ifPresent(t -> n.put("target", t));
    s.span().ifPresent(sp -> n.set("span", span(sp)));
    n.set("confidence", confidence(s.confidence()));
    return n;
  }

  private static JsonNode hints(RetrievalHints h) {
    ObjectNode n = NODES.objectNode();
    ArrayNode modes = n.putArray("modes");
    EnumSet<RetrievalHints.Mode> sorted =
        h.modes().isEmpty() ? EnumSet.noneOf(RetrievalHints.Mode.class) : EnumSet.copyOf(h.modes());
    sorted.forEach(m -> modes.add(m.name()));
    n.put("crossLanguage", h.crossLanguage());
    if (!h.contentLanguages().isEmpty()) {
      ArrayNode languages = n.putArray("contentLanguages");
      h.contentLanguages().forEach(l -> languages.add(l.value()));
    }
    return n;
  }

  private static JsonNode subquery(SubQuery s) {
    ObjectNode n = NODES.objectNode();
    n.put("id", s.id());
    n.put("objective", s.objective());
    strings(n, "intentIds", s.intentIds());
    strings(n, "constraintIds", s.constraintIds());
    strings(n, "dependsOn", s.dependsOn());
    n.put("inheritsParentConstraints", s.inheritsParentConstraints());
    return n;
  }

  private static void strings(ObjectNode n, String name, List<String> values) {
    if (!values.isEmpty()) {
      ArrayNode array = n.putArray(name);
      values.forEach(array::add);
    }
  }

  private static JsonNode span(Span s) {
    ObjectNode n = NODES.objectNode();
    n.put("start", s.start());
    n.put("end", s.end());
    n.put("text", s.text());
    return n;
  }

  private static JsonNode confidence(Confidence c) {
    ObjectNode n = NODES.objectNode();
    n.put("kind", c.kind().name());
    c.value().ifPresent(v -> n.put("value", v));
    return n;
  }

  private static JsonNode value(IrValue value) {
    ObjectNode n = NODES.objectNode();
    n.put("type", value.type());
    switch (value) {
      case IrValue.Text t -> n.put("value", t.value());
      case IrValue.Number x -> n.put("value", x.value());
      case IrValue.Quantity q -> {
        n.put("value", q.value());
        n.put("unit", q.unit());
      }
      case IrValue.Money m -> {
        n.put("amount", m.amount());
        m.currency().ifPresent(c -> n.put("currency", c));
      }
      case IrValue.Bool b -> n.put("value", b.value());
      case IrValue.Temporal t -> {
        n.put("kind", t.kind().name());
        n.put("value", t.value());
        t.reference().ifPresent(r -> n.put("reference", r));
      }
      case IrValue.Range r -> {
        r.min().ifPresent(min -> n.set("min", value(min)));
        r.max().ifPresent(max -> n.set("max", value(max)));
        n.put("minInclusive", r.minInclusive());
        n.put("maxInclusive", r.maxInclusive());
      }
      case IrValue.ListOf l -> {
        ArrayNode values = n.putArray("values");
        l.values().forEach(v -> values.add(value(v)));
      }
    }
    return n;
  }

  private static JsonNode javaToJson(Object value) {
    if (value instanceof Map<?, ?> map) {
      ObjectNode n = NODES.objectNode();
      new TreeMap<>(map).forEach((k, v) -> n.set(String.valueOf(k), javaToJson(v)));
      return n;
    }
    if (value instanceof List<?> list) {
      ArrayNode n = NODES.arrayNode();
      list.forEach(v -> n.add(javaToJson(v)));
      return n;
    }
    if (value instanceof BigDecimal d) {
      return NODES.numberNode(d);
    }
    if (value instanceof Boolean b) {
      return NODES.booleanNode(b);
    }
    return NODES.textNode(String.valueOf(value));
  }

  // ---------------------------------------------------------------------------------------
  // Reading
  // ---------------------------------------------------------------------------------------

  public static Result read(String json) {
    if (json == null) {
      return new Result.Invalid(List.of(new IrViolation("", Code.MALFORMED_JSON, "no content")));
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      return new Result.Invalid(
          List.of(new IrViolation("", Code.MALFORMED_JSON, "not valid JSON within limits")));
    }
    return read(root);
  }

  public static Result read(JsonNode root) {
    Reader r = new Reader();
    if (root == null || !root.isObject()) {
      return new Result.Invalid(
          List.of(new IrViolation("", Code.MALFORMED_JSON, "root must be an object")));
    }
    String versionText = r.reqText(root, "/irVersion", "irVersion");
    if (versionText == null) {
      return new Result.Invalid(r.violations);
    }
    Optional<IrVersion> version = IrVersion.parse(versionText);
    if (version.isEmpty()) {
      return new Result.Invalid(
          List.of(new IrViolation("/irVersion", Code.INVALID_FORMAT, "must be major.minor")));
    }
    if (!IrVersion.READABLE.canRead(version.get())) {
      return new Result.Invalid(
          List.of(
              new IrViolation(
                  "/irVersion",
                  Code.UNSUPPORTED_VERSION,
                  "supported: " + IrVersion.CURRENT + " to " + IrVersion.READABLE)));
    }
    // The correction field exists from 1.1; in a 1.0 document it is an unknown field.
    boolean v11 = version.get().compareTo(IrVersion.V1_1) >= 0;
    Set<String> rootFields =
        new java.util.HashSet<>(
            Set.of(
                "irVersion",
                "queryId",
                "originalText",
                "language",
                "intents",
                "entities",
                "constraints",
                "semanticSignals",
                "retrievalHints",
                "subqueries",
                "extensions"));
    if (v11) {
      rootFields.add("correction");
      rootFields.add("expansion");
    }
    r.known(root, "", rootFields);

    Optional<String> queryId = r.optText(root, "/queryId", "queryId");
    String text = r.reqText(root, "/originalText", "originalText");
    LanguageTag language = r.language(root, "/language", "language");
    List<Intent> intents = r.list(root, "intents", r::intent);
    List<Entity> entities = r.list(root, "entities", r::entity);
    List<Constraint> constraints = r.list(root, "constraints", r::constraint);
    List<SemanticSignal> signals = r.list(root, "semanticSignals", r::signal);
    RetrievalHints hints =
        root.has("retrievalHints")
            ? r.hints(root.get("retrievalHints"), "/retrievalHints")
            : RetrievalHints.NONE;
    List<SubQuery> subqueries = r.list(root, "subqueries", r::subquery);
    Map<String, Extension> extensions = r.extensions(root.get("extensions"));
    Optional<TextCorrection> correction =
        v11 && root.has("correction")
            ? Optional.ofNullable(r.correction(root.get("correction"), "/correction"))
            : Optional.empty();
    Optional<QueryExpansion> expansion =
        v11 && root.has("expansion")
            ? Optional.ofNullable(r.expansion(root.get("expansion"), "/expansion"))
            : Optional.empty();

    if (!r.violations.isEmpty() || text == null || language == null) {
      return new Result.Invalid(r.violations);
    }
    QueryIR ir =
        new QueryIR(
            version.get(),
            queryId,
            text,
            language,
            intents,
            entities,
            constraints,
            signals,
            hints,
            subqueries,
            extensions,
            correction,
            expansion);
    List<IrViolation> semantic = QueryIrValidator.validate(ir);
    return semantic.isEmpty() ? new Result.Valid(ir) : new Result.Invalid(semantic);
  }

  /** Accumulates violations while reading; every accessor tolerates missing or wrong input. */
  private static final class Reader {
    private final List<IrViolation> violations = new ArrayList<>();

    private interface Element<T> {
      T read(JsonNode node, String path);
    }

    private <T> List<T> list(JsonNode parent, String name, Element<T> element) {
      JsonNode node = parent.get(name);
      if (node == null) {
        return List.of();
      }
      if (!node.isArray()) {
        violations.add(new IrViolation("/" + name, Code.INVALID_VALUE, "must be an array"));
        return List.of();
      }
      List<T> items = new ArrayList<>();
      for (int i = 0; i < node.size(); i++) {
        String path = "/" + name + "/" + i;
        JsonNode item = node.get(i);
        if (!item.isObject()) {
          violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an object"));
          continue;
        }
        T value = guard(path, () -> element.read(item, path));
        if (value != null) {
          items.add(value);
        }
      }
      return items;
    }

    private TextCorrection correction(JsonNode n, String path) {
      if (n == null || !n.isObject()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an object"));
        return null;
      }
      known(
          n,
          path,
          Set.of(
              "status",
              "mode",
              "normalizedText",
              "correctedText",
              "configuredLanguages",
              "activeLanguages",
              "detectedLanguage",
              "corrections"));
      String status = reqText(n, path + "/status", "status");
      String mode = reqText(n, path + "/mode", "mode");
      String normalized = reqText(n, path + "/normalizedText", "normalizedText");
      String corrected = reqText(n, path + "/correctedText", "correctedText");
      List<String> configured = stringList(n, path + "/configuredLanguages", "configuredLanguages");
      List<String> active = stringList(n, path + "/activeLanguages", "activeLanguages");
      Optional<LanguageTag> detected =
          n.has("detectedLanguage")
              ? Optional.ofNullable(language(n, path + "/detectedLanguage", "detectedLanguage"))
              : Optional.empty();
      List<TextCorrection.Correction> items = new ArrayList<>();
      JsonNode array = n.get("corrections");
      if (array != null) {
        if (!array.isArray()) {
          violations.add(
              new IrViolation(path + "/corrections", Code.INVALID_VALUE, "must be an array"));
        } else {
          for (int i = 0; i < array.size(); i++) {
            JsonNode x = array.get(i);
            String p = path + "/corrections/" + i;
            known(
                x,
                p,
                Set.of(
                    "token",
                    "start",
                    "end",
                    "language",
                    "replacement",
                    "confidence",
                    "applied",
                    "reason"));
            String token = reqText(x, p + "/token", "token");
            int start = integer(x, p + "/start", "start");
            int end = integer(x, p + "/end", "end");
            String lang = reqText(x, p + "/language", "language");
            String replacement = reqText(x, p + "/replacement", "replacement");
            BigDecimal confidence = decimal(x, p + "/confidence", "confidence");
            boolean applied = bool(x, p + "/applied", "applied", false);
            String reason = reqText(x, p + "/reason", "reason");
            if (token == null
                || lang == null
                || replacement == null
                || confidence == null
                || reason == null) continue;
            TextCorrection.Correction item =
                guard(
                    p,
                    () ->
                        new TextCorrection.Correction(
                            token, start, end, lang, replacement, confidence, applied, reason));
            if (item != null) items.add(item);
          }
        }
      }
      if (status == null || mode == null || normalized == null || corrected == null) return null;
      return guard(
          path,
          () ->
              new TextCorrection(
                  status, mode, normalized, corrected, configured, active, detected, items));
    }

    private QueryExpansion expansion(JsonNode n, String path) {
      if (n == null || !n.isObject()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an object"));
        return null;
      }
      known(
          n,
          path,
          Set.of(
              "status", "policy", "sourceText", "sourceLanguage", "configuredTargets", "targets"));
      String status = reqText(n, path + "/status", "status");
      String policy = reqText(n, path + "/policy", "policy");
      String sourceText = reqText(n, path + "/sourceText", "sourceText");
      Optional<LanguageTag> source =
          n.has("sourceLanguage")
              ? Optional.ofNullable(language(n, path + "/sourceLanguage", "sourceLanguage"))
              : Optional.empty();
      List<String> configured = stringList(n, path + "/configuredTargets", "configuredTargets");
      List<QueryExpansion.Target> targets = new ArrayList<>();
      JsonNode array = n.get("targets");
      if (array != null) {
        if (!array.isArray()) {
          violations.add(
              new IrViolation(path + "/targets", Code.INVALID_VALUE, "must be an array"));
        } else {
          for (int i = 0; i < array.size(); i++) {
            JsonNode x = array.get(i);
            String p = path + "/targets/" + i;
            if (!x.isObject()) {
              violations.add(new IrViolation(p, Code.INVALID_VALUE, "must be an object"));
              continue;
            }
            known(
                x,
                p,
                Set.of(
                    "requested",
                    "language",
                    "status",
                    "text",
                    "provider",
                    "confidence",
                    "reason",
                    "preservation",
                    "retrieval"));
            String requested = reqText(x, p + "/requested", "requested");
            Optional<LanguageTag> lang =
                x.has("language")
                    ? Optional.ofNullable(language(x, p + "/language", "language"))
                    : Optional.empty();
            String targetStatus = reqText(x, p + "/status", "status");
            Optional<String> text = optText(x, p + "/text", "text");
            Optional<String> provider = optText(x, p + "/provider", "provider");
            String confidence = reqText(x, p + "/confidence", "confidence");
            Optional<String> reason = optText(x, p + "/reason", "reason");
            Optional<String> preservation = optText(x, p + "/preservation", "preservation");
            boolean retrieval = bool(x, p + "/retrieval", "retrieval", false);
            if (requested == null || targetStatus == null || confidence == null) continue;
            QueryExpansion.Target target =
                guard(
                    p,
                    () ->
                        new QueryExpansion.Target(
                            requested,
                            lang,
                            targetStatus,
                            text,
                            provider,
                            confidence,
                            reason,
                            preservation,
                            retrieval));
            if (target != null) targets.add(target);
          }
        }
      }
      if (status == null || policy == null || sourceText == null) return null;
      return guard(
          path, () -> new QueryExpansion(status, policy, sourceText, source, configured, targets));
    }

    private <T> T guard(String path, java.util.function.Supplier<T> supplier) {
      int before = violations.size();
      try {
        T value = supplier.get();
        return violations.size() == before ? value : null;
      } catch (RuntimeException e) {
        // Record constructors reject null or inconsistent parts; already-reported problems
        // explain why, otherwise report the element as invalid.
        if (violations.size() == before) {
          violations.add(new IrViolation(path, Code.INVALID_VALUE, "invalid element"));
        }
        return null;
      }
    }

    private void known(JsonNode node, String path, Set<String> allowed) {
      Iterator<String> names = node.fieldNames();
      while (names.hasNext()) {
        String name = names.next();
        if (!allowed.contains(name)) {
          violations.add(
              new IrViolation(
                  path + "/" + name,
                  Code.UNKNOWN_FIELD,
                  "not part of QueryIR " + IrVersion.CURRENT));
        }
      }
    }

    private String reqText(JsonNode node, String path, String name) {
      JsonNode value = node.get(name);
      if (value == null || value.isNull()) {
        violations.add(new IrViolation(path, Code.REQUIRED, "missing"));
        return null;
      }
      if (!value.isTextual()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be a string"));
        return null;
      }
      return value.asText();
    }

    private Optional<String> optText(JsonNode node, String path, String name) {
      return node.has(name) ? Optional.ofNullable(reqText(node, path, name)) : Optional.empty();
    }

    private boolean bool(JsonNode node, String path, String name, boolean defaultValue) {
      JsonNode value = node.get(name);
      if (value == null) {
        return defaultValue;
      }
      if (!value.isBoolean()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be a boolean"));
        return defaultValue;
      }
      return value.asBoolean();
    }

    private int integer(JsonNode node, String path, String name) {
      JsonNode value = node.get(name);
      if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an integer"));
        return -1;
      }
      return value.asInt();
    }

    private BigDecimal decimal(JsonNode node, String path, String name) {
      JsonNode value = node.get(name);
      if (value == null || !value.isNumber()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be a number"));
        return null;
      }
      return value.decimalValue();
    }

    private <E extends Enum<E>> E enumValue(
        JsonNode node, String path, String name, Class<E> type) {
      String text = reqText(node, path, name);
      if (text == null) {
        return null;
      }
      try {
        return Enum.valueOf(type, text);
      } catch (IllegalArgumentException e) {
        violations.add(
            new IrViolation(path, Code.INVALID_VALUE, "unknown " + type.getSimpleName()));
        return null;
      }
    }

    private List<String> stringList(JsonNode node, String path, String name) {
      JsonNode value = node.get(name);
      if (value == null) {
        return List.of();
      }
      if (!value.isArray()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an array"));
        return List.of();
      }
      List<String> items = new ArrayList<>();
      for (JsonNode item : value) {
        if (!item.isTextual()) {
          violations.add(new IrViolation(path, Code.INVALID_VALUE, "must contain strings"));
          return List.of();
        }
        items.add(item.asText());
      }
      return items;
    }

    private LanguageTag language(JsonNode node, String path, String name) {
      String text = reqText(node, path, name);
      if (text == null) {
        return null;
      }
      Optional<LanguageTag> tag = LanguageTag.parse(text);
      if (tag.isEmpty()) {
        violations.add(new IrViolation(path, Code.INVALID_FORMAT, "must be a BCP 47 tag"));
        return null;
      }
      return tag.get();
    }

    private Confidence confidence(JsonNode parent, String path) {
      JsonNode node = parent.get("confidence");
      if (node == null || !node.isObject()) {
        violations.add(new IrViolation(path, Code.REQUIRED, "confidence object required"));
        return null;
      }
      known(node, path, Set.of("kind", "value"));
      Confidence.Kind kind = enumValue(node, path + "/kind", "kind", Confidence.Kind.class);
      OptionalDouble value = OptionalDouble.empty();
      if (node.has("value")) {
        BigDecimal number = decimal(node, path + "/value", "value");
        value = number == null ? OptionalDouble.empty() : OptionalDouble.of(number.doubleValue());
      }
      if (kind == null) {
        return null;
      }
      try {
        return new Confidence(kind, value);
      } catch (IllegalArgumentException e) {
        violations.add(
            new IrViolation(
                path, Code.INVALID_VALUE, "value only allowed for MODEL_SCORE in [0,1]"));
        return null;
      }
    }

    private Optional<Span> optSpan(JsonNode parent, String path) {
      return parent.has("span")
          ? Optional.ofNullable(span(parent.get("span"), path))
          : Optional.empty();
    }

    private Span span(JsonNode node, String path) {
      if (node == null || !node.isObject()) {
        violations.add(new IrViolation(path, Code.REQUIRED, "span object required"));
        return null;
      }
      known(node, path, Set.of("start", "end", "text"));
      int start = integer(node, path + "/start", "start");
      int end = integer(node, path + "/end", "end");
      String text = reqText(node, path + "/text", "text");
      if (text == null || start < 0 || end < 0) {
        return null;
      }
      try {
        return new Span(start, end, text);
      } catch (IllegalArgumentException e) {
        violations.add(
            new IrViolation(path, Code.INVALID_SPAN, "offsets do not fit the span text"));
        return null;
      }
    }

    private Intent intent(JsonNode n, String path) {
      known(n, path, Set.of("id", "type", "confidence", "span"));
      return new Intent(
          reqText(n, path + "/id", "id"),
          reqText(n, path + "/type", "type"),
          confidence(n, path + "/confidence"),
          optSpan(n, path + "/span"));
    }

    private Entity entity(JsonNode n, String path) {
      known(n, path, Set.of("id", "type", "value", "normalizedValue", "span", "confidence"));
      return new Entity(
          reqText(n, path + "/id", "id"),
          reqText(n, path + "/type", "type"),
          reqText(n, path + "/value", "value"),
          optText(n, path + "/normalizedValue", "normalizedValue"),
          span(n.get("span"), path + "/span"),
          confidence(n, path + "/confidence"));
    }

    private Constraint constraint(JsonNode n, String path) {
      known(
          n,
          path,
          Set.of(
              "id",
              "field",
              "operator",
              "value",
              "strength",
              "provenance",
              "reason",
              "span",
              "confidence",
              "scope"));
      return new Constraint(
          reqText(n, path + "/id", "id"),
          reqText(n, path + "/field", "field"),
          enumValue(n, path + "/operator", "operator", Constraint.Operator.class),
          n.has("value")
              ? Optional.ofNullable(value(n.get("value"), path + "/value"))
              : Optional.empty(),
          enumValue(n, path + "/strength", "strength", Constraint.Strength.class),
          enumValue(n, path + "/provenance", "provenance", Constraint.Provenance.class),
          reqText(n, path + "/reason", "reason"),
          optSpan(n, path + "/span"),
          confidence(n, path + "/confidence"),
          optText(n, path + "/scope", "scope"));
    }

    private SemanticSignal signal(JsonNode n, String path) {
      known(n, path, Set.of("id", "concept", "polarity", "target", "span", "confidence"));
      return new SemanticSignal(
          reqText(n, path + "/id", "id"),
          reqText(n, path + "/concept", "concept"),
          enumValue(n, path + "/polarity", "polarity", SemanticSignal.Polarity.class),
          optText(n, path + "/target", "target"),
          optSpan(n, path + "/span"),
          confidence(n, path + "/confidence"));
    }

    private SubQuery subquery(JsonNode n, String path) {
      known(
          n,
          path,
          Set.of(
              "id",
              "objective",
              "intentIds",
              "constraintIds",
              "dependsOn",
              "inheritsParentConstraints"));
      return new SubQuery(
          reqText(n, path + "/id", "id"),
          reqText(n, path + "/objective", "objective"),
          stringList(n, path + "/intentIds", "intentIds"),
          stringList(n, path + "/constraintIds", "constraintIds"),
          stringList(n, path + "/dependsOn", "dependsOn"),
          bool(n, path + "/inheritsParentConstraints", "inheritsParentConstraints", true));
    }

    private RetrievalHints hints(JsonNode n, String path) {
      if (!n.isObject()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an object"));
        return RetrievalHints.NONE;
      }
      known(n, path, Set.of("modes", "crossLanguage", "contentLanguages"));
      EnumSet<RetrievalHints.Mode> modes = EnumSet.noneOf(RetrievalHints.Mode.class);
      for (String mode : stringList(n, path + "/modes", "modes")) {
        try {
          modes.add(RetrievalHints.Mode.valueOf(mode));
        } catch (IllegalArgumentException e) {
          violations.add(
              new IrViolation(path + "/modes", Code.INVALID_VALUE, "unknown retrieval mode"));
        }
      }
      List<LanguageTag> languages = new ArrayList<>();
      for (String tag : stringList(n, path + "/contentLanguages", "contentLanguages")) {
        LanguageTag.parse(tag)
            .ifPresentOrElse(
                languages::add,
                () ->
                    violations.add(
                        new IrViolation(
                            path + "/contentLanguages",
                            Code.INVALID_FORMAT,
                            "must be BCP 47 tags")));
      }
      return new RetrievalHints(
          modes, bool(n, path + "/crossLanguage", "crossLanguage", false), languages);
    }

    private Map<String, Extension> extensions(JsonNode node) {
      if (node == null) {
        return Map.of();
      }
      if (!node.isObject()) {
        violations.add(new IrViolation("/extensions", Code.INVALID_VALUE, "must be an object"));
        return Map.of();
      }
      Map<String, Extension> result = new LinkedHashMap<>();
      node.fields()
          .forEachRemaining(
              entry -> {
                String path = "/extensions/" + entry.getKey();
                JsonNode ext = entry.getValue();
                if (!ext.isObject()) {
                  violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be an object"));
                  return;
                }
                known(ext, path, Set.of("version", "data"));
                String version = reqText(ext, path + "/version", "version");
                JsonNode data = ext.get("data");
                if (data != null && !data.isObject()) {
                  violations.add(
                      new IrViolation(path + "/data", Code.INVALID_VALUE, "must be an object"));
                  return;
                }
                Object converted = data == null ? Map.of() : jsonToJava(data, path + "/data");
                if (version != null && converted instanceof Map<?, ?>) {
                  @SuppressWarnings("unchecked")
                  Map<String, Object> map = (Map<String, Object>) converted;
                  result.put(entry.getKey(), new Extension(version, map));
                }
              });
      return result;
    }

    private Object jsonToJava(JsonNode node, String path) {
      if (node.isObject()) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields()
            .forEachRemaining(
                e -> map.put(e.getKey(), jsonToJava(e.getValue(), path + "/" + e.getKey())));
        return map;
      }
      if (node.isArray()) {
        List<Object> list = new ArrayList<>();
        node.forEach(item -> list.add(jsonToJava(item, path)));
        return list;
      }
      if (node.isNumber()) {
        return node.decimalValue();
      }
      if (node.isBoolean()) {
        return node.asBoolean();
      }
      if (node.isTextual()) {
        return node.asText();
      }
      violations.add(
          new IrViolation(path, Code.INVALID_VALUE, "null is not allowed in extension data"));
      return "";
    }

    private IrValue value(JsonNode n, String path) {
      if (n == null || !n.isObject()) {
        violations.add(new IrViolation(path, Code.INVALID_VALUE, "must be a typed value object"));
        return null;
      }
      String type = reqText(n, path + "/type", "type");
      if (type == null) {
        return null;
      }
      return switch (type) {
        case "text" -> {
          known(n, path, Set.of("type", "value"));
          String v = reqText(n, path + "/value", "value");
          yield v == null ? null : new IrValue.Text(v);
        }
        case "number" -> {
          known(n, path, Set.of("type", "value"));
          BigDecimal v = decimal(n, path + "/value", "value");
          yield v == null ? null : new IrValue.Number(v);
        }
        case "quantity" -> {
          known(n, path, Set.of("type", "value", "unit"));
          BigDecimal v = decimal(n, path + "/value", "value");
          String unit = reqText(n, path + "/unit", "unit");
          yield v == null || unit == null ? null : new IrValue.Quantity(v, unit);
        }
        case "money" -> {
          known(n, path, Set.of("type", "amount", "currency"));
          BigDecimal amount = decimal(n, path + "/amount", "amount");
          yield amount == null
              ? null
              : new IrValue.Money(amount, optText(n, path + "/currency", "currency"));
        }
        case "boolean" -> {
          known(n, path, Set.of("type", "value"));
          JsonNode v = n.get("value");
          if (v == null || !v.isBoolean()) {
            violations.add(
                new IrViolation(path + "/value", Code.INVALID_VALUE, "must be a boolean"));
            yield null;
          }
          yield new IrValue.Bool(v.asBoolean());
        }
        case "temporal" -> {
          known(n, path, Set.of("type", "kind", "value", "reference"));
          IrValue.Temporal.Kind kind =
              enumValue(n, path + "/kind", "kind", IrValue.Temporal.Kind.class);
          String v = reqText(n, path + "/value", "value");
          yield kind == null || v == null
              ? null
              : new IrValue.Temporal(kind, v, optText(n, path + "/reference", "reference"));
        }
        case "range" -> {
          known(n, path, Set.of("type", "min", "max", "minInclusive", "maxInclusive"));
          Optional<IrValue> min =
              n.has("min")
                  ? Optional.ofNullable(value(n.get("min"), path + "/min"))
                  : Optional.empty();
          Optional<IrValue> max =
              n.has("max")
                  ? Optional.ofNullable(value(n.get("max"), path + "/max"))
                  : Optional.empty();
          yield new IrValue.Range(
              min,
              max,
              bool(n, path + "/minInclusive", "minInclusive", true),
              bool(n, path + "/maxInclusive", "maxInclusive", true));
        }
        case "list" -> {
          known(n, path, Set.of("type", "values"));
          JsonNode values = n.get("values");
          if (values == null || !values.isArray()) {
            violations.add(
                new IrViolation(path + "/values", Code.INVALID_VALUE, "must be an array"));
            yield null;
          }
          List<IrValue> items = new ArrayList<>();
          for (int i = 0; i < values.size(); i++) {
            IrValue item = value(values.get(i), path + "/values/" + i);
            if (item != null) {
              items.add(item);
            }
          }
          yield new IrValue.ListOf(items);
        }
        default -> {
          violations.add(new IrViolation(path + "/type", Code.INVALID_VALUE, "unknown value type"));
          yield null;
        }
      };
    }
  }
}
