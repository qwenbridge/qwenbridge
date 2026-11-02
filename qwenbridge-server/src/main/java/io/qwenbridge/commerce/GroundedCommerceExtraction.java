package io.qwenbridge.commerce;

import static io.qwenbridge.commerce.CommerceContracts.*;

import io.qwenbridge.analysis.model.Grounded.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Source rules corroborate model output; evidence is assigned by code, never invented by a model.
 */
final class GroundedCommerceExtraction {
  private GroundedCommerceExtraction() {}

  private static final Map<String, String> COLORS =
      Map.of(
          "black",
              "black|مشکی|سیاه|svart(?:a)?|schwarz(?:e|es|en|er)?|noir(?:e|es|s)?|negr[oa]s?|أسود|اسود|siyah|zwart(?:e)?|黒|黑|काले|काला|काली|hitam",
          "red", "red|قرمز|سرخ|röd|rot(?:e|es|en|er)?|rouge|rojo|أحمر|kırmızı|rood|赤|红",
          "blue", "blue|آبی|blå|blau|bleu|azul|أزرق|mavi|blauw|青|蓝",
          "white", "white|سفید|vit(?:a)?|weiß|blanc|blanco|أبيض|beyaz|wit(?:te)?|白");
  private static final Map<String, String> BRANDS =
      Map.ofEntries(
          Map.entry("Nike", "nike|نایکی"),
          Map.entry("Adidas", "adidas|آدیداس"),
          Map.entry("Lenovo", "lenovo|لنوو"),
          Map.entry("Apple", "apple|اپل"),
          Map.entry("Samsung", "samsung|سامسونگ"),
          Map.entry("Sony", "sony|سونی"),
          Map.entry("Bose", "bose"),
          Map.entry("Razer", "razer"),
          Map.entry("Logitech", "logitech"));
  private static final String REFURB =
      "refurbish|استوک|بازسازی|renover|generalüberholt|recondition|reacondicion|مجدد|yenilen|gereviseerd|再生品|翻新";
  private static final Pattern NEGATIVE =
      Pattern.compile(
          "(?iu)(\\b(?:not|no|non|without|inte|ej|nicht|kein|sans|pas|sin|niet|geen|değil|except|exclude|bukan|tidak|jangan)\\b|नहीं|बिना|غیر|بدون|نیست|غير|ليس|ではなく|不要|以外|除外)");

  static List<Constraint> complete(String original, String translated, List<Constraint> model) {
    var result = new ArrayList<>(model);
    String raw = CommerceExtraction.normalize(original).toLowerCase(Locale.ROOT);
    String english = CommerceExtraction.normalize(translated).toLowerCase(Locale.ROOT);
    Evidence whole = new Evidence("original", 0, original.length(), original);
    var categories =
        Map.of(
            "shoes",
            "shoes?|footwear|sneakers?|trainers?|کفش|skor|schuhe|chaussures?|zapatos?|حذاء|ayakkabı|schoenen|靴|鞋|जूते|kasut|sepatu",
            "laptop",
            "laptops?|notebooks?|لپ[‌ ]?تاپ|bärbar|ordinateur"
                + " portable|portátil|حاسوب|dizüstü|ノートパソコン|笔记本|लैपटॉप|komputer riba|dizüstü",
            "mouse",
            "mouse|mice|ماوس|mus|maus|souris|ratón|fare|muis|マウス|鼠标",
            "headphones",
            "headphones?|headsets?|هدفون|hörlurar|kopfhörer|casque|auriculares|سماعات|kulaklık|koptelefoon|ヘッドホン|耳机",
            "smartphone",
            "smartphones?|phones?|گوشی|telefon|téléphone|teléfono|هاتف|スマートフォン|手机");
    var types = categories.entrySet().stream().filter(e -> matches(e.getValue(), raw)).toList();
    if (types.isEmpty())
      types = categories.entrySet().stream().filter(e -> matches(e.getValue(), english)).toList();
    if (types.size() == 1)
      replace(result, "category", Operator.EQ, types.getFirst().getKey(), whole);
    for (var c : model)
      if (Set.of("price", "size").contains(c.field())) {
        var numbers =
            Pattern.compile("\\d+(?:[.,]\\d+)?")
                .matcher(raw)
                .results()
                .map(m -> m.group().replace(',', '.'))
                .toList();
        if (numbers.stream().noneMatch(n -> decimalEqual(n, c.value())))
          throw new IllegalArgumentException("Invented numeric constraint");
      }
    var sourceColors = new ArrayList<Constraint>();
    for (var entry : COLORS.entrySet())
      if (matches(entry.getValue(), raw)) {
        var matcher = matcherFor(entry.getValue(), raw);
        matcher.find();
        String vicinity = raw.substring(Math.max(0, matcher.start() - 10), matcher.end());
        sourceColors.add(
            new Constraint(
                "color",
                NEGATIVE.matcher(vicinity).find() ? Operator.NE : Operator.EQ,
                entry.getKey(),
                whole));
      }
    if (!sourceColors.isEmpty()) {
      result.removeIf(c -> c.field().equals("color"));
      result.addAll(sourceColors);
    }
    var brands = BRANDS.entrySet().stream().filter(e -> matches(e.getValue(), raw)).toList();
    if (!brands.isEmpty()) {
      result.removeIf(c -> c.field().equals("brand"));
      for (var brand : brands) {
        var match = matcherFor(brand.getValue(), raw);
        match.find();
        String vicinity = raw.substring(Math.max(0, match.start() - 10), match.start());
        result.add(
            new Constraint(
                "brand",
                NEGATIVE.matcher(vicinity).find() ? Operator.NE : Operator.EQ,
                brand.getKey(),
                whole));
      }
    }
    var size =
        Pattern.compile(
                "(?iu)(?:size|سایز|مقاس|storlek|größe|taille|talla|maat|サイズ)\\s*([0-9]+(?:\\.[0-9]+)?)")
            .matcher(raw);
    var sizes = new ArrayList<Constraint>();
    while (size.find()) sizes.add(new Constraint("size", Operator.EQ, size.group(1), whole));
    var reversedSize = Pattern.compile("(?iu)([0-9]+)\\s*(?:numara|码)").matcher(raw);
    while (reversedSize.find())
      sizes.add(new Constraint("size", Operator.EQ, reversedSize.group(1), whole));
    if (!sizes.isEmpty()) {
      result.removeIf(c -> c.field().equals("size"));
      result.addAll(sizes);
    }
    var condition = Pattern.compile("(?iu)(" + REFURB + ")").matcher(raw);
    if (condition.find()) {
      String vicinity =
          raw.substring(
              Math.max(0, condition.start() - 12), Math.min(raw.length(), condition.end() + 12));
      replace(
          result,
          "condition",
          NEGATIVE.matcher(vicinity).find() ? Operator.NE : Operator.EQ,
          "refurbished",
          whole);
    }
    if (matches(
        "out of stock|not in stock|inte i lager|nicht auf lager|pas en stock|no en stock|sin"
            + " stock|niet op voorraad|stokta değil|ناموجود|rupture de stock|غير متوفر|在庫なし|无库存",
        raw)) replace(result, "inStock", Operator.EQ, "false", whole);
    else if (matches(
        "in stock|موجود|i lager|auf lager|en stock|متوفر|stokta|op voorraad|在庫あり|有库存", raw))
      replace(result, "inStock", Operator.EQ, "true", whole);
    for (var c : CommerceExtraction.explicitPrices(original)) {
      if (c.field().equals("currency"))
        replace(result, c.field(), c.operator(), c.value(), c.evidence());
      else {
        result.removeIf(old -> old.field().equals("price") && old.value().equals(c.value()));
        result.add(c);
      }
    }
    if (result.stream().anyMatch(c -> c.field().equals("price"))
        && result.stream().noneMatch(c -> c.field().equals("currency"))) {
      String currency = currency(raw);
      if (currency == null) currency = currency(english);
      if (currency != null) result.add(new Constraint("currency", Operator.EQ, currency, whole));
    }
    return List.copyOf(result);
  }

  static String render(String translated, List<Constraint> constraints) {
    var clauses = new ArrayList<String>();
    for (var c : constraints) {
      String phrase =
          switch (c.field()) {
            case "condition" -> (c.operator() == Operator.NE ? "not " : "") + c.value();
            case "inStock" -> c.value().equals("true") ? "in stock" : "out of stock";
            case "price" ->
                (switch (c.operator()) {
                      case LT -> "under ";
                      case LTE -> "at most ";
                      case GT -> "over ";
                      case GTE -> "at least ";
                      default -> "price ";
                    })
                    + c.value();
            case "brand", "color", "currency", "category" ->
                (c.operator() == Operator.NE ? "not " : "") + c.value();
            default -> c.field() + " " + c.value();
          };
      if (!translated.toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT)))
        clauses.add(phrase);
    }
    return clauses.isEmpty() ? translated : translated + ", " + String.join(", ", clauses);
  }

  private static String currency(String raw) {
    if (matches("eur|euro|یورو|ユーロ|欧元|يورو|€", raw)) return "EUR";
    if (matches("sek|kronor|کرون", raw)) return "SEK";
    if (matches("usd|dollar|دلار|دولار|\\$", raw)) return "USD";
    return null;
  }

  private static java.util.regex.Matcher matcherFor(String regex, String text) {
    // Do not split regex alternations: aliases also contain nested groups such as (e|es|en).
    // Latin boundaries prevent 'red' inside 'wired', while permitting Nike next to CJK text.
    return Pattern.compile("(?iu)(?<!\\p{IsLatin})(?:" + regex + ")(?!\\p{IsLatin})").matcher(text);
  }

  private static boolean matches(String regex, String text) {
    return matcherFor(regex, text).find();
  }

  private static void replace(
      List<Constraint> result, String field, Operator op, String value, Evidence span) {
    result.removeIf(c -> c.field().equals(field));
    result.add(new Constraint(field, op, value, span));
  }

  private static boolean decimalEqual(String a, String b) {
    try {
      return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b)) == 0;
    } catch (NumberFormatException ex) {
      return a.equals(b);
    }
  }
}
