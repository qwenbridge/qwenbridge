package io.qwenbridge.core.schema;

import io.qwenbridge.core.queryir.Names;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A versioned domain schema (QB-044): the fields that may be constrained, the entity types that may
 * be extracted and the capabilities the domain offers. Commerce, DevOps and Security all describe
 * themselves with this one contract; domain specifics live in field and entity names.
 *
 * @param id schema identifier, e.g. {@code commerce}, {@code devops}
 * @param fields queryable fields keyed by name (dotted lowercase, as used by QueryIR)
 * @param entityTypes entity type names that may appear in QueryIR entities
 * @param capabilities domain capabilities, e.g. {@code search}, {@code analyze}
 */
public record DomainSchema(
    String id,
    SchemaVersion version,
    Map<String, FieldDefinition> fields,
    Set<String> entityTypes,
    Set<String> capabilities) {

  public static final int MAX_FIELDS = 128;
  public static final int MAX_ENTITY_TYPES = 64;

  static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

  public DomainSchema {
    Objects.requireNonNull(id, "id must not be null");
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException("schema id must match " + ID.pattern());
    }
    Objects.requireNonNull(version, "version must not be null");
    fields = fields == null ? Map.of() : Map.copyOf(fields);
    entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    if (fields.isEmpty() && entityTypes.isEmpty()) {
      throw new IllegalArgumentException("a schema needs fields or entity types");
    }
    if (fields.size() > MAX_FIELDS || entityTypes.size() > MAX_ENTITY_TYPES) {
      throw new IllegalArgumentException("schema exceeds size limits");
    }
    fields.forEach(
        (name, field) -> {
          if (!Names.valid(name) || !name.equals(field.name())) {
            throw new IllegalArgumentException("field keys must be valid names matching the field");
          }
        });
    entityTypes.forEach(
        type -> {
          if (!Names.valid(type)) {
            throw new IllegalArgumentException("entity types must be valid names");
          }
        });
  }

  public SchemaKey key() {
    return new SchemaKey(id, version);
  }
}
