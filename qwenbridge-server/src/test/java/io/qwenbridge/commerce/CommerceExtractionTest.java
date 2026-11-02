package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CommerceExtractionTest {
  @Test
  void hindiAndMalayNegationCannotBeReversedBySourceCorroboration() throws Exception {
    for (String original :
        java.util.List.of("refurbished नहीं", "bukan refurbished", "tidak refurbished")) {
      var constraints =
          CommerceExtraction.canonical(mapper.readTree("[]"), original, "not refurbished");
      assertThat(constraints)
          .anySatisfy(
              c -> {
                assertThat(c.field()).isEqualTo("condition");
                assertThat(c.operator())
                    .isEqualTo(io.qwenbridge.analysis.model.Grounded.Operator.NE);
              });
    }
    assertThat(
            new io.qwenbridge.language.LanguageService()
                .detect("काले Nike जूते साइज़ 42, 100 EUR से कम, refurbished नहीं, स्टॉक में"))
        .isEqualTo("hi");
  }

  @Test
  void sourceRulesRecognizeStandaloneEnglishColorAndBrand() throws Exception {
    var filters =
        CommerceExtraction.canonical(mapper.readTree("[]"), "black Nike shoes", "black Nike shoes");
    assertThat(filters).anyMatch(c -> c.field().equals("color") && c.value().equals("black"));
    assertThat(filters).anyMatch(c -> c.field().equals("brand") && c.value().equals("Nike"));
  }

  @Test
  void corroborationDoesNotMistakeWiredForRedOrPineappleForApple() throws Exception {
    var filters =
        CommerceExtraction.canonical(
            mapper.readTree("[]"),
            "lightweight wired mouse with pineapple print",
            "lightweight wired mouse with pineapple print");
    assertThat(filters).noneMatch(c -> c.field().equals("color") || c.field().equals("brand"));
  }

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void relaxationRequiresAWholePhraseAndExplicitAnySizeIsNotANumericConstraint() throws Exception {
    assertThat(CommerceExtraction.clearedFields("many sizes", "many sizes")).isEmpty();
    var constraints =
        CommerceExtraction.canonical(
            mapper.readTree("[[\"size\",\"EQ\",\"any\"]]"),
            "same shoes but any size",
            "same shoes but any size");
    assertThat(constraints).noneMatch(c -> c.field().equals("size"));
  }

  @Test
  void preservesMultipleExclusionsAndDoesNotInventAnEqualityForANegativeBrand() throws Exception {
    var filters =
        CommerceExtraction.canonical(
            mapper.readTree("[]"),
            "shoes not red, not blue, not Nike",
            "shoes not red, not blue, not Nike");
    assertThat(filters)
        .filteredOn(c -> c.field().equals("color"))
        .hasSize(2)
        .allMatch(c -> c.operator() == io.qwenbridge.analysis.model.Grounded.Operator.NE);
    assertThat(filters)
        .anyMatch(
            c ->
                c.field().equals("brand")
                    && c.operator() == io.qwenbridge.analysis.model.Grounded.Operator.NE);
    assertThatThrownBy(() -> CommerceExtraction.explicitPrices("under 1,500 EUR"))
        .hasMessageContaining("Ambiguous");
    assertThatThrownBy(() -> CommerceExtraction.explicitPrices("over 20 USD under 30 EUR"))
        .hasMessageContaining("Mixed currencies");
  }

  @Test
  void modelConstraintMustPointAtExactOriginalSource() throws Exception {
    var constraints =
        CommerceExtraction.parse(
            mapper.readTree("[[\"size\",\"EQ\",\"42\",\"سایز ۴۲\"]]"), "کفش سایز ۴۲");
    assertThat(constraints.getFirst().evidence().start()).isEqualTo(4);
    constraints.getFirst().evidence().verify("کفش سایز ۴۲");
    assertThatThrownBy(
            () ->
                CommerceExtraction.parse(
                    mapper.readTree("[[\"size\",\"EQ\",\"43\",\"سایز ۴۲\"]]"), "کفش سایز ۴۲"))
        .hasMessageContaining("grounded");
    assertThatThrownBy(
            () ->
                CommerceExtraction.parse(
                    mapper.readTree("[[\"brand\",\"EQ\",\"Nike\",\"Nike\"]]"), "Adidas shoes"))
        .hasMessageContaining("invented");
  }

  @Test
  void translationsCannotLoseNumbersOrExplicitBrandNames() {
    assertThat(
            CommerceExtraction.translation(
                    "Lenovo زیر ۱۵۰۰ یورو", "fa", "en", "Lenovo below 1500 EUR")
                .constraintsPreserved())
        .isTrue();
    assertThat(
            CommerceExtraction.translation(
                    "Lenovo زیر ۱۵۰۰ یورو", "fa", "en", "laptop below 1000 EUR")
                .issues())
        .hasSize(2);
  }

  @Test
  void translationCheckUsesCorePreservationForNumbersBrandsCurrenciesAndIdentifiers() {
    // Numbers are compared as values, not strings: a changed group separator is not a loss.
    assertThat(
            CommerceExtraction.translation(
                    "Lenovo under 1,500 EUR", "en", "sv", "Lenovo under 1500 EUR")
                .constraintsPreserved())
        .isTrue();
    var changed =
        CommerceExtraction.translation(
            "skor SKU-4471 under 500 kronor", "sv", "en", "shoes SKU-4417 under 500 EUR");
    assertThat(changed.issues())
        .contains(
            "A source identifier was not preserved: SKU-4471",
            "A source currency was not preserved: SEK");
    // Commerce appends constraint phrases after translation, so additions are not losses.
    assertThat(
            CommerceExtraction.translation(
                    "Nike skor under 100 EUR", "sv", "en", "Nike shoes under 100 EUR, under 100")
                .constraintsPreserved())
        .isTrue();
  }

  @Test
  void failedTranslationIsAnExplicitIssueNotAnException() {
    var translation =
        CommerceExtraction.translation(
            "Nike skor", "sv", "en", "Nike skor", java.util.List.of("Translation unavailable"));
    assertThat(translation.constraintsPreserved()).isFalse();
    assertThat(translation.issues()).containsExactly("Translation unavailable");
  }

  @Test
  void legacyPriceExtractionPreservesCurrencyAndStrictBoundary() {
    var constraints = CommerceExtraction.explicitPrices("mouse زیر ۵۰ یورو");
    assertThat(constraints).hasSize(2);
    assertThat(constraints.getFirst().value()).isEqualTo("50");
    assertThat(constraints.getFirst().operator())
        .isEqualTo(io.qwenbridge.analysis.model.Grounded.Operator.LT);
    assertThat(constraints.getLast().value()).isEqualTo("EUR");
  }
}
