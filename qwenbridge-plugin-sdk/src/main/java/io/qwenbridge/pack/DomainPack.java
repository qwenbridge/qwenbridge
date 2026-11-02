package io.qwenbridge.pack;

import java.util.*;

public interface DomainPack extends QwenBridgePack {
  enum Type {
    TEXT,
    DECIMAL,
    BOOLEAN,
    DATE
  }

  enum Operator {
    EQ,
    NE,
    LT,
    LTE,
    GT,
    GTE
  }

  record Field(Type type, Set<Operator> operators, String backendField, boolean caseFold) {
    public Field {
      Objects.requireNonNull(type);
      operators = Set.copyOf(operators);
      if (operators.isEmpty()
          || backendField == null
          || !backendField.matches("[A-Za-z_][A-Za-z0-9_.]*"))
        throw new IllegalArgumentException("Invalid domain field");
    }
  }

  record Definition(String id, int schemaVersion, Map<String, Field> fields) {
    public Definition {
      PackManifest.validId(id);
      if (schemaVersion < 1) throw new IllegalArgumentException("Invalid domain version");
      fields = Map.copyOf(fields);
    }
  }

  record Clause(String field, Operator operator, String value, int start, int end, String quote) {
    public Clause {
      if (field == null
          || operator == null
          || value == null
          || start < 0
          || end <= start
          || quote == null) throw new IllegalArgumentException("Invalid domain clause");
    }

    public void verify(String original) {
      if (end > original.length() || !original.substring(start, end).equals(quote))
        throw new IllegalArgumentException("Ungrounded domain clause");
    }
  }

  record Understanding(
      String domain, String original, List<Clause> clauses, List<String> questions) {
    public Understanding {
      clauses = List.copyOf(clauses);
      questions = List.copyOf(questions);
      clauses.forEach(c -> c.verify(original));
    }
  }

  /**
   * QB-234: the domain-specific QueryIR payload a pack owns. Its namespace is the pack's domain id,
   * so two packs can never claim the same payload. A payload whose version is not in {@code
   * readableVersions} is incompatible and refused, never guessed at.
   */
  record QueryExtension(String namespace, String version, Set<String> readableVersions) {
    public QueryExtension {
      PackManifest.validId(namespace);
      if (version == null || !version.matches("[0-9]{1,4}\\.[0-9]{1,4}"))
        throw new IllegalArgumentException("Invalid extension version");
      readableVersions = Set.copyOf(readableVersions);
      if (!readableVersions.contains(version))
        throw new IllegalArgumentException("An extension must read its own version");
    }
  }

  /**
   * QB-235: a domain-specific policy rule. It runs inside the central policy engine, after the base
   * checks, only for resources in the pack's domain. It can only restrict: {@code DENY} denies,
   * {@code REVIEW} asks for an approval, and {@code ALLOW} or {@code ABSTAIN} change nothing. A
   * rule that throws denies.
   */
  interface Rule {
    String id();

    PolicyPack.Effect evaluate(PolicyPack.Input input);
  }

  Definition definition();

  default Optional<QueryExtension> queryExtension() {
    return Optional.empty();
  }

  /**
   * Problems with a payload in this pack's namespace, already known to be in a readable version;
   * empty when valid. Messages must not repeat the payload's values.
   */
  default List<String> validateExtension(String version, Map<String, Object> data) {
    return List.of();
  }

  default List<Rule> policies() {
    return List.of();
  }

  Map<String, Object> normalize(String sourceId, Map<String, Object> input);

  /** One source record may expand into independently searchable variants. */
  default List<Map<String, Object>> documents(String sourceId, Map<String, Object> input) {
    return List.of(normalize(sourceId, input));
  }

  Understanding understand(String original);

  default String searchText(Understanding query) {
    return query.original();
  }
}
