package io.qwenbridge.catalog.canonical;

/**
 * The Commerce pack's built-in vocabulary. It carries the aliases the previous normalizer
 * hard-coded, plus attribute-name aliases and a two-level category taxonomy. Operators extend it by
 * building their own {@link CatalogVocabulary}.
 */
public final class CommerceVocabulary {
  public static final String VERSION = "commerce-vocabulary/1";

  private CommerceVocabulary() {}

  public static CatalogVocabulary builtIn() {
    return CatalogVocabulary.builder(VERSION)
        .category("fashion", null)
        .category("electronics", null)
        .category(
            "shoes",
            "fashion",
            "shoe",
            "footwear",
            "sneakers",
            "schuhe",
            "chaussures",
            "کفش",
            "鞋",
            "kasut")
        .category("laptop", "electronics", "laptops", "notebook", "notebooks")
        .category("smartphone", "electronics", "phone", "phones", "smartphones")
        .category("mouse", "electronics", "mice")
        .category("headphones", "electronics", "headphone")
        .brand("Nike")
        .brand("Adidas")
        .brand("Lenovo")
        .brand("Apple")
        .brand("Samsung")
        .brand("Sony")
        .brand("Bose")
        .brand("Razer")
        .brand("Logitech")
        .attributeName("color", "colour", "farbe", "couleur", "färg", "رنگ")
        .attributeName("size", "größe", "taille", "storlek")
        .attributeName("material", "materials")
        .attributeName("condition")
        .attributeName("location")
        .attributeValue(
            "color", "black", "noir", "noire", "schwarz", "svart", "مشکی", "黑色", "黒", "hitam",
            "काला")
        .attributeValue("color", "red", "rouge", "rot", "röd", "قرمز", "红色", "merah")
        .attributeValue("color", "blue", "blau", "bleu", "blå", "آبی", "蓝色", "biru")
        .attributeValue("color", "white", "blanc", "weiß", "vit", "سفید", "白色", "putih")
        .lowercase("color", "condition")
        .inferable("color")
        .build();
  }
}
