package io.qwenbridge.catalog.mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The QwenBridge JSON feed shape: product fields at the top level, optional {@code variants},
 * optional {@code attributes} object and well-known option fields. A record without {@code
 * variants} is a single-variant product.
 */
public final class JsonFeedMapping implements CatalogMapping {
  public static final String ID = "http-json";

  /** Option fields that may appear at the top level of a product or a variant. */
  static final List<String> OPTION_FIELDS =
      List.of("color", "size", "condition", "material", "location");

  @Override
  public String id() {
    return ID;
  }

  @Override
  public MappingResult map(SourceRecord record) {
    var raw = record.fields();
    RawValue id =
        raw.get("productId") != null
            ? new RawValue("productId", raw.get("productId"))
            : new RawValue("$recordId", record.recordId());
    RawValue brand =
        raw.containsKey("brand") ? RawValue.of("brand", raw.get("brand")) : vendor(raw);
    RawValue category =
        raw.containsKey("category")
            ? RawValue.of("category", raw.get("category"))
            : RawValue.of("productType", raw.get("productType"));
    var attributes = attributes(raw, "");
    if (attributes == null)
      return MappingResult.reject("attributes", "Attributes must be an object");
    List<ProductDraft.VariantDraft> variants = new ArrayList<>();
    if (raw.containsKey("variants")) {
      if (!(raw.get("variants") instanceof List<?> rows) || rows.isEmpty())
        return MappingResult.reject("variants", "Variants must be a nonempty array");
      for (int i = 0; i < rows.size(); i++) {
        String prefix = "variants[" + i + "].";
        if (!(rows.get(i) instanceof Map<?, ?> map))
          return MappingResult.reject("variants[" + i + "]", "Invalid variant");
        Map<String, Object> data = new LinkedHashMap<>();
        map.forEach((k, v) -> data.put(String.valueOf(k), v));
        var options = attributes(data, prefix);
        if (options == null)
          return MappingResult.reject(prefix + "attributes", "Attributes must be an object");
        RawValue currency =
            data.containsKey("currency")
                ? RawValue.of(prefix + "currency", data.get("currency"))
                : RawValue.of("currency", raw.get("currency"));
        var variant =
            variant(data, prefix, RawValue.of(prefix + "id", data.get("id")), currency, options);
        if (variant instanceof MappingResult.Rejected rejected) return rejected;
        variants.add(((Holder) variant).draft);
      }
    } else {
      RawValue variantId =
          raw.get("variantId") != null
              ? new RawValue("variantId", raw.get("variantId"))
              : new RawValue("$recordId", record.recordId());
      var variant =
          variant(raw, "", variantId, RawValue.of("currency", raw.get("currency")), Map.of());
      if (variant instanceof MappingResult.Rejected rejected) return rejected;
      variants.add(((Holder) variant).draft);
    }
    List<ProductDraft.RelationDraft> relations = new ArrayList<>();
    if (raw.get("relations") instanceof List<?> rows)
      for (int i = 0; i < rows.size(); i++) {
        if (!(rows.get(i) instanceof Map<?, ?> r))
          return MappingResult.reject("relations[" + i + "]", "Invalid relationship");
        relations.add(
            new ProductDraft.RelationDraft(
                RawValue.of("relations[" + i + "].type", r.get("type")),
                RawValue.of("relations[" + i + "].targetProductId", r.get("targetProductId"))));
      }
    return new MappingResult.Mapped(
        new ProductDraft(
            id,
            RawValue.of("title", raw.get("title")),
            RawValue.of("description", raw.get("description")),
            brand,
            category,
            attributes,
            variants,
            relations));
  }

  private static RawValue vendor(Map<String, Object> raw) {
    return RawValue.of("vendor", raw.get("vendor"));
  }

  /** The {@code attributes} object followed by top-level option fields, which take precedence. */
  private static Map<String, RawValue> attributes(Map<String, Object> raw, String prefix) {
    Map<String, RawValue> out = new LinkedHashMap<>();
    Object nested = raw.get("attributes");
    if (nested instanceof Map<?, ?> a) {
      for (var entry : a.entrySet())
        if (entry.getValue() != null)
          out.put(
              String.valueOf(entry.getKey()),
              new RawValue(prefix + "attributes." + entry.getKey(), entry.getValue()));
    } else if (nested != null) return null;
    for (String key : OPTION_FIELDS)
      if (raw.get(key) != null) out.put(key, new RawValue(prefix + key, raw.get(key)));
    return out;
  }

  private record Holder(ProductDraft.VariantDraft draft) {}

  private static Object variant(
      Map<String, Object> raw,
      String prefix,
      RawValue id,
      RawValue currency,
      Map<String, RawValue> options) {
    RawValue weightValue = null;
    RawValue weightUnit = null;
    if (raw.get("weightKg") != null) {
      weightValue = new RawValue(prefix + "weightKg", raw.get("weightKg"));
      weightUnit = new RawValue(prefix + "weightKg", "kg");
    } else if (raw.get("weightGrams") != null) {
      weightValue = new RawValue(prefix + "weightGrams", raw.get("weightGrams"));
      weightUnit = new RawValue(prefix + "weightGrams", "g");
    } else if (raw.get("weight") instanceof Map<?, ?> w) {
      weightValue = new RawValue(prefix + "weight.value", w.get("value"));
      weightUnit = new RawValue(prefix + "weight.unit", w.get("unit"));
    } else if (raw.containsKey("weight"))
      return MappingResult.reject(prefix + "weight", "Weight requires value and unit");
    return new Holder(
        new ProductDraft.VariantDraft(
            id,
            RawValue.of(prefix + "sku", raw.get("sku")),
            RawValue.of(prefix + "price", raw.get("price")),
            RawValue.of(prefix + "compareAtPrice", raw.get("compareAtPrice")),
            currency,
            RawValue.of(prefix + "quantity", raw.get("quantity")),
            RawValue.of(prefix + "inStock", raw.get("inStock")),
            RawValue.of(prefix + "availability", raw.get("availability")),
            weightValue,
            weightUnit,
            RawValue.of(prefix + "availableAt", raw.get("availableAt")),
            options));
  }
}
