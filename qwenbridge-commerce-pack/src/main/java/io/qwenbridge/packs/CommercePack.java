package io.qwenbridge.packs;

import io.qwenbridge.catalog.*;
import io.qwenbridge.pack.*;
import java.util.*;
import java.util.regex.Pattern;

public final class CommercePack implements DomainPack {
  public PackManifest manifest() {
    return new PackManifest(
        "commerce",
        "1.0.0",
        1,
        PackManifest.Kind.DOMAIN,
        "qwenbridge",
        List.of(),
        Set.of("normalize", "understand", "search"),
        Map.of());
  }

  public Definition definition() {
    var terms = Set.of(Operator.EQ, Operator.NE);
    var ordered = Set.of(Operator.values());
    Map<String, Field> fields = new HashMap<>();
    for (String name : List.of("brand", "color", "size", "condition", "location"))
      fields.put(name, new Field(Type.TEXT, terms, name + ".keyword", true));
    for (String name : List.of("category", "domain"))
      fields.put(name, new Field(Type.TEXT, terms, name, true));
    fields.put("currency", new Field(Type.TEXT, Set.of(Operator.EQ), "currency", false));
    for (String name : List.of("price", "weightKg"))
      fields.put(name, new Field(Type.DECIMAL, ordered, name, false));
    fields.put("inStock", new Field(Type.BOOLEAN, terms, "inStock", false));
    fields.put("availableAt", new Field(Type.DATE, ordered, "availableAt", false));
    return new Definition("commerce", 1, fields);
  }

  /** The Commerce QueryIR payload (QB-150, QB-234): the same shape the server writes. */
  @Override
  public Optional<QueryExtension> queryExtension() {
    return Optional.of(new QueryExtension("commerce", "1.0", Set.of("1.0")));
  }

  @Override
  public List<String> validateExtension(String version, Map<String, Object> data) {
    List<String> problems = new ArrayList<>();
    if (!data.keySet().equals(Set.of("schema", "catalogLanguage", "currencies")))
      problems.add("commerce extension keys must be [catalogLanguage, currencies, schema]");
    if (!(data.get("schema") instanceof String schema)
        || !schema.matches("[a-z][a-z0-9-]{0,63}@[0-9]{1,6}"))
      problems.add("schema must be a schema reference id@version");
    if (!(data.get("catalogLanguage") instanceof String language)
        || !language.matches("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*"))
      problems.add("catalogLanguage must be a language tag");
    if (!(data.get("currencies") instanceof List<?> currencies)
        || !currencies.stream().allMatch(c -> c instanceof String code && code.matches("[A-Z]{3}")))
      problems.add("currencies must be a list of ISO 4217 codes");
    return problems;
  }

  public Map<String, Object> normalize(String sourceId, Map<String, Object> input) {
    var normalizer = new CatalogNormalizer();
    var product = normalizer.normalize("commerce-pack", sourceId, input);
    var document = new HashMap<>(documents(sourceId, input).getFirst());
    document.put("variants", documents(sourceId, input));
    document.put("product", product);
    return Map.copyOf(document);
  }

  @Override
  public List<Map<String, Object>> documents(String sourceId, Map<String, Object> input) {
    var normalizer = new CatalogNormalizer();
    var product = normalizer.normalize("commerce-pack", sourceId, input);
    return product.variants().stream()
        .map(
            variant -> {
              var doc = new HashMap<>(normalizer.document(product, variant, "public"));
              doc.remove("tenantId");
              doc.put("domain", "commerce");
              return Map.copyOf(doc);
            })
        .toList();
  }

  public Understanding understand(String original) {
    List<Clause> clauses = new ArrayList<>();
    List<String> questions = new ArrayList<>();
    rule(
        clauses,
        original,
        "(?i)\\b(not\\s+)?(Nike|Adidas|Lenovo|Apple|Samsung|Sony|Bose|Razer|Logitech)\\b",
        "brand",
        null,
        2);
    rule(clauses, original, "(?i)\\b(not\\s+)?(black|white|red|blue)\\b", "color", null, 2);
    rule(clauses, original, "(?i)\\bsize\\s+([0-9]+(?:\\.[0-9]+)?)", "size", Operator.EQ, 1);
    rule(clauses, original, "(?i)\\b(not\\s+)?(refurbished)\\b", "condition", null, 2);
    var price =
        Pattern.compile(
                "(?i)\\b(under|below|at most|over|above|at"
                    + " least)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+([A-Z]{3})\\b")
            .matcher(original);
    while (price.find()) {
      Operator op =
          switch (price.group(1).toLowerCase(Locale.ROOT)) {
            case "over", "above" -> Operator.GT;
            case "at least" -> Operator.GTE;
            case "at most" -> Operator.LTE;
            default -> Operator.LT;
          };
      clauses.add(
          new Clause("price", op, price.group(2), price.start(), price.end(), price.group()));
      clauses.add(
          new Clause(
              "currency",
              Operator.EQ,
              price.group(3).toUpperCase(Locale.ROOT),
              price.start(),
              price.end(),
              price.group()));
    }
    for (String category : List.of("shoes", "laptop", "mouse", "headphones", "smartphone")) {
      var m = Pattern.compile("(?i)\\b" + category + "s?\\b").matcher(original);
      if (m.find())
        clauses.add(new Clause("category", Operator.EQ, category, m.start(), m.end(), m.group()));
    }
    var stock = Pattern.compile("(?i)\\b(out of stock|in stock)\\b").matcher(original);
    if (stock.find())
      clauses.add(
          new Clause(
              "inStock",
              Operator.EQ,
              Boolean.toString(stock.group().equalsIgnoreCase("in stock")),
              stock.start(),
              stock.end(),
              stock.group()));
    if (clauses.isEmpty())
      questions.add("Specify a product category or supported product attributes.");
    var remaining = original.toCharArray();
    for (var clause : clauses) Arrays.fill(remaining, clause.start(), clause.end(), ' ');
    String unsupported =
        new String(remaining)
            .replaceAll(
                "(?i)\\b(find|show|me|i|want|need|please|a|an|the|some|products|and|with|that|are)\\b",
                " ")
            .replaceAll("[^\\p{L}\\p{N}]", "")
            .strip();
    if (!unsupported.isEmpty())
      questions.add(
          "The bundled rule grammar cannot safely interpret the remaining text. Use the"
              + " multilingual Commerce Search API or rephrase with supported attributes.");
    return new Understanding("commerce", original, clauses, questions);
  }

  private void rule(
      List<Clause> clauses, String text, String regex, String field, Operator fixed, int group) {
    var m = Pattern.compile(regex).matcher(text);
    while (m.find())
      clauses.add(
          new Clause(
              field,
              fixed == null ? m.group(1) == null ? Operator.EQ : Operator.NE : fixed,
              m.group(group),
              m.start(),
              m.end(),
              m.group()));
  }
}
