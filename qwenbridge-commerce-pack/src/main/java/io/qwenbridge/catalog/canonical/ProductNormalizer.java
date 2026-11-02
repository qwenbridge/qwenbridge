package io.qwenbridge.catalog.canonical;

import io.qwenbridge.catalog.CatalogProduct.RelationType;
import io.qwenbridge.catalog.canonical.AttributeValue.Certainty;
import io.qwenbridge.catalog.canonical.AttributeValue.Kind;
import io.qwenbridge.catalog.canonical.Provenance.Origin;
import io.qwenbridge.catalog.mapping.CatalogMapping;
import io.qwenbridge.catalog.mapping.MappingResult;
import io.qwenbridge.catalog.mapping.ProductDraft;
import io.qwenbridge.catalog.mapping.ProductDraft.VariantDraft;
import io.qwenbridge.catalog.mapping.RawValue;
import io.qwenbridge.catalog.mapping.SourceRecord;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a mapped draft into a {@link CanonicalProduct} (QB-092 – QB-096).
 *
 * <ul>
 *   <li>Text is Unicode NFKC with whitespace collapsed; digits in any script are read as ASCII.
 *   <li>Brands, categories, attribute names and values go through the vocabulary. The source value
 *       is kept in the provenance whenever it changes.
 *   <li>Prices are exact decimals in an ISO currency; nothing is converted.
 *   <li>Missing stock is {@link Availability#UNKNOWN}, never assumed. Contradictions are rejected.
 *   <li>Attributes the source does not state may be inferred from the title, marked uncertain or
 *       ambiguous. An inferred value never replaces a stated one.
 * </ul>
 */
public final class ProductNormalizer {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final int MAX_ATTRIBUTES = 256;

  private final CatalogVocabulary vocabulary;

  public ProductNormalizer(CatalogVocabulary vocabulary) {
    this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
  }

  public CatalogVocabulary vocabulary() {
    return vocabulary;
  }

  public CanonicalProduct normalize(SourceRecord record, CatalogMapping mapping) {
    return switch (mapping.map(record)) {
      case MappingResult.Rejected rejected -> throw new InvalidCatalogRecord(rejected.issues());
      case MappingResult.Mapped mapped -> normalize(record, mapping.id(), mapped.draft());
    };
  }

  public CanonicalProduct normalize(SourceRecord record, String mappingId, ProductDraft draft) {
    var title = sourced(draft.title());
    if (title == null) throw new InvalidCatalogRecord("title", "Catalog requires title");
    var attributes = attributes(draft.attributes());
    var variants = new ArrayList<CanonicalVariant>();
    for (VariantDraft variant : draft.variants()) variants.add(variant(variant));
    infer(title, attributes, variants);
    var relations = new ArrayList<Relation>();
    for (var relation : draft.relations()) {
      String type = text(value(relation.type()));
      String target = text(value(relation.target()));
      RelationType parsed;
      try {
        parsed = RelationType.valueOf(type);
      } catch (IllegalArgumentException ex) {
        throw new InvalidCatalogRecord(path(relation.type(), "relations"), "Invalid relationship");
      }
      relations.add(
          new Relation(
              parsed, target, Provenance.source(path(relation.target(), "relations"), target)));
    }
    return new CanonicalProduct(
        CanonicalProduct.SCHEMA_VERSION,
        text(value(draft.id())),
        title,
        sourced(draft.description()),
        brand(draft.brand()),
        category(draft.category()),
        attributes,
        variants,
        relations,
        new SourceRef(record.connector(), record.recordId(), mappingId, record.observedAt()));
  }

  private Term brand(RawValue raw) {
    String source = raw == null ? "" : text(raw.value());
    if (source.isBlank()) return null;
    var canonical = vocabulary.brand(source);
    if (canonical.isEmpty()) return new Term(source, false, Provenance.source(raw.path(), source));
    return new Term(canonical.get(), true, mapped(raw.path(), source, canonical.get(), "brand"));
  }

  private CategoryRef category(RawValue raw) {
    String source = raw == null ? "" : text(raw.value());
    if (source.isBlank()) return null;
    var canonical = vocabulary.category(source);
    if (canonical.isPresent())
      return new CategoryRef(
          canonical.get(),
          vocabulary.categoryPath(canonical.get()),
          true,
          mapped(raw.path(), source, canonical.get(), "category"));
    String id = CatalogVocabulary.key(source);
    return new CategoryRef(
        id,
        List.of(id),
        false,
        id.equals(source)
            ? Provenance.source(raw.path(), source)
            : new Provenance(Origin.NORMALIZED, raw.path(), source, "lowercase"));
  }

  private Provenance mapped(String path, String source, String canonical, String kind) {
    return canonical.equals(source)
        ? Provenance.source(path, source)
        : new Provenance(Origin.NORMALIZED, path, source, vocabulary.version() + ":" + kind);
  }

  private Map<String, AttributeValue> attributes(Map<String, RawValue> raw) {
    if (raw.size() > MAX_ATTRIBUTES)
      throw new InvalidCatalogRecord("attributes", "Too many attributes");
    var out = new LinkedHashMap<String, AttributeValue>();
    var exact = new HashSet<String>();
    for (var entry : raw.entrySet()) {
      String sourceName = text(entry.getKey());
      if (sourceName.isBlank())
        throw new InvalidCatalogRecord(entry.getValue().path(), "Attribute name required");
      String name = vocabulary.attributeName(sourceName).orElse(sourceName);
      boolean isExact = CatalogVocabulary.key(sourceName).equals(CatalogVocabulary.key(name));
      // A source field named exactly like the canonical attribute wins over an alias.
      if (out.containsKey(name) && exact.contains(name) && !isExact) continue;
      if (isExact) exact.add(name);
      out.put(name, attribute(name, entry.getValue()));
    }
    return out;
  }

  private AttributeValue attribute(String name, RawValue raw) {
    Object value = raw.value();
    if (value instanceof Boolean bool)
      return AttributeValue.certain(
          name, Kind.BOOLEAN, bool.toString(), Provenance.source(raw.path(), bool.toString()));
    if (value instanceof Number number) {
      String plain = decimal(number, raw.path()).toPlainString();
      return AttributeValue.certain(
          name, Kind.NUMBER, plain, Provenance.source(raw.path(), text(number)));
    }
    String source = text(value);
    var canonical = vocabulary.attributeValue(name, source);
    if (canonical.isPresent())
      return AttributeValue.certain(
          name, Kind.TEXT, canonical.get(), mapped(raw.path(), source, canonical.get(), name));
    if (vocabulary.lowercases(name)) {
      String lower = source.toLowerCase(Locale.ROOT);
      return AttributeValue.certain(
          name,
          Kind.TEXT,
          lower,
          lower.equals(source)
              ? Provenance.source(raw.path(), source)
              : new Provenance(Origin.NORMALIZED, raw.path(), source, "lowercase"));
    }
    return AttributeValue.certain(name, Kind.TEXT, source, Provenance.source(raw.path(), source));
  }

  /** QB-096: fill inferable attributes the source left out, from the title, marked uncertain. */
  private void infer(
      SourcedText title, Map<String, AttributeValue> attributes, List<CanonicalVariant> variants) {
    for (String name : vocabulary.inferable().stream().sorted().toList()) {
      if (attributes.containsKey(name)
          || variants.stream().anyMatch(v -> v.options().containsKey(name))) continue;
      var found = vocabulary.mentions(name, title.value());
      if (found.isEmpty()) continue;
      var provenance =
          new Provenance(
              Origin.INFERRED,
              title.provenance().sourceField(),
              title.value(),
              vocabulary.version() + ":" + name + ":title");
      attributes.put(
          name,
          found.size() == 1
              ? new AttributeValue(
                  name, Kind.TEXT, found.first(), Certainty.UNCERTAIN, List.of(), provenance)
              : new AttributeValue(
                  name, Kind.TEXT, null, Certainty.AMBIGUOUS, List.copyOf(found), provenance));
    }
  }

  private CanonicalVariant variant(VariantDraft raw) {
    String id = text(value(raw.id()));
    if (id.isBlank()) throw new InvalidCatalogRecord(path(raw.id(), "id"), "Catalog requires id");
    if (raw.price() == null)
      throw new InvalidCatalogRecord("price", "Numeric catalog value required");
    String currency = text(value(raw.currency()));
    if (currency.isBlank())
      throw new InvalidCatalogRecord(path(raw.currency(), "currency"), "Catalog requires currency");
    var price = new Price(decimal(raw.price().value(), raw.price().path()), currency);
    Price compareAt =
        raw.compareAtPrice() == null
            ? null
            : new Price(
                decimal(raw.compareAtPrice().value(), raw.compareAtPrice().path()), currency);
    return new CanonicalVariant(
        id,
        sourced(raw.sku()),
        attributes(raw.options()),
        price,
        Provenance.source(raw.price().path(), text(raw.price().value())),
        compareAt,
        inventory(raw),
        weight(raw),
        availableAt(raw.availableAt()));
  }

  private Inventory inventory(VariantDraft raw) {
    Integer quantity = null;
    if (raw.quantity() != null) {
      try {
        quantity = decimal(raw.quantity().value(), raw.quantity().path()).intValueExact();
      } catch (ArithmeticException ex) {
        throw new InvalidCatalogRecord(raw.quantity().path(), "Invalid catalog number");
      }
    }
    Availability fromFlag = null;
    if (raw.inStock() != null) {
      String flag = text(raw.inStock().value()).toLowerCase(Locale.ROOT);
      if (!Set.of("true", "false").contains(flag))
        throw new InvalidCatalogRecord(raw.inStock().path(), "Inventory must be boolean");
      fromFlag = Boolean.parseBoolean(flag) ? Availability.IN_STOCK : Availability.OUT_OF_STOCK;
    }
    Availability stated = null;
    if (raw.availability() != null) stated = availability(raw.availability());
    if (stated != null
        && fromFlag != null
        && (stated == Availability.IN_STOCK) != (fromFlag == Availability.IN_STOCK))
      throw new InvalidCatalogRecord(raw.availability().path(), "Contradictory inventory");
    if (stated != null)
      return new Inventory(
          stated,
          quantity,
          new Provenance(
              stated.name().equals(text(raw.availability().value()))
                  ? Origin.SOURCE
                  : Origin.NORMALIZED,
              raw.availability().path(),
              text(raw.availability().value()),
              stated.name().equals(text(raw.availability().value())) ? null : "availability"));
    if (fromFlag != null)
      return new Inventory(
          fromFlag,
          quantity,
          new Provenance(
              Origin.DERIVED, raw.inStock().path(), text(raw.inStock().value()), "inStock"));
    if (quantity != null)
      return new Inventory(
          quantity > 0 ? Availability.IN_STOCK : Availability.OUT_OF_STOCK,
          quantity,
          new Provenance(Origin.DERIVED, raw.quantity().path(), quantity.toString(), "quantity>0"));
    return new Inventory(Availability.UNKNOWN, null, null);
  }

  private static Availability availability(RawValue raw) {
    String key =
        CatalogVocabulary.key(text(raw.value()))
            .replaceFirst("^https?://schema\\.org/", "")
            .replaceAll("[\\s_-]", "");
    return switch (key) {
      case "instock", "available", "limitedavailability", "onlineonly", "instoreonly" ->
          Availability.IN_STOCK;
      case "outofstock", "soldout", "unavailable" -> Availability.OUT_OF_STOCK;
      case "preorder", "presale" -> Availability.PREORDER;
      case "backorder" -> Availability.BACKORDER;
      case "discontinued" -> Availability.DISCONTINUED;
      case "unknown" -> Availability.UNKNOWN;
      default -> throw new InvalidCatalogRecord(raw.path(), "Unsupported availability");
    };
  }

  private static Weight weight(VariantDraft raw) {
    if (raw.weightValue() == null) return null;
    BigDecimal value = decimal(raw.weightValue().value(), raw.weightValue().path());
    String unit = text(value(raw.weightUnit())).toLowerCase(Locale.ROOT);
    String source = text(raw.weightValue().value());
    String path = raw.weightValue().path();
    return switch (unit) {
      case "kg", "kilogram", "kilograms" -> new Weight(value, Provenance.source(path, source));
      case "g", "gram", "grams" ->
          new Weight(
              value.movePointLeft(3), new Provenance(Origin.DERIVED, path, source + " g", "g->kg"));
      case "lb", "lbs" ->
          new Weight(
              value.multiply(new BigDecimal("0.45359237")),
              new Provenance(Origin.DERIVED, path, source + " lb", "lb->kg:0.45359237"));
      default ->
          throw new InvalidCatalogRecord(path(raw.weightUnit(), path), "Unsupported weight unit");
    };
  }

  private static LocalDate availableAt(RawValue raw) {
    if (raw == null) return null;
    String date = text(raw.value());
    if (date.isBlank()) throw new InvalidCatalogRecord(raw.path(), "Catalog requires availableAt");
    try {
      return LocalDate.parse(date);
    } catch (DateTimeParseException ex) {
      throw new InvalidCatalogRecord(raw.path(), "Invalid availability date");
    }
  }

  private static SourcedText sourced(RawValue raw) {
    if (raw == null) return null;
    String value = text(raw.value());
    return value.isBlank() ? null : new SourcedText(value, Provenance.source(raw.path(), value));
  }

  private static Object value(RawValue raw) {
    return raw == null ? null : raw.value();
  }

  private static String path(RawValue raw, String fallback) {
    return raw == null ? fallback : raw.path();
  }

  /** NFKC, trimmed, whitespace collapsed; at most 16384 characters. */
  static String text(Object value) {
    String s =
        value == null
            ? ""
            : WHITESPACE
                .matcher(Normalizer.normalize(value.toString(), Normalizer.Form.NFKC).strip())
                .replaceAll(" ");
    if (s.length() > 16384) throw new IllegalArgumentException("Catalog value too long");
    return s;
  }

  /** Digits in any script, and the Arabic decimal separator, read as an exact decimal. */
  static BigDecimal decimal(Object raw, String path) {
    if (raw == null) throw new InvalidCatalogRecord(path, "Numeric catalog value required");
    String s = Normalizer.normalize(raw.toString(), Normalizer.Form.NFKC);
    var out = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      int n = Character.digit(c, 10);
      out.append(n >= 0 ? (char) ('0' + n) : c == '٫' ? '.' : c);
    }
    try {
      return new BigDecimal(out.toString());
    } catch (NumberFormatException ex) {
      throw new InvalidCatalogRecord(path, "Invalid catalog number");
    }
  }
}
