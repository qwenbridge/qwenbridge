package io.qwenbridge.core.validation;

import static io.qwenbridge.core.queryir.Constraint.Operator.BETWEEN;
import static io.qwenbridge.core.queryir.Constraint.Operator.EQ;
import static io.qwenbridge.core.queryir.Constraint.Operator.GT;
import static io.qwenbridge.core.queryir.Constraint.Operator.GTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.IN;
import static io.qwenbridge.core.queryir.Constraint.Operator.LT;
import static io.qwenbridge.core.queryir.Constraint.Operator.LTE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NE;
import static io.qwenbridge.core.queryir.Constraint.Operator.NOT_IN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.extraction.ConstraintExtractor;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.extraction.Extraction;
import io.qwenbridge.core.extraction.ExtractionContext;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.schema.DomainSchema;
import io.qwenbridge.core.schema.FieldDefinition;
import io.qwenbridge.core.schema.FieldType;
import io.qwenbridge.core.schema.SchemaVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * QB-056: constraints survive the whole path from text to the compile boundary. Real extraction
 * output is mapped and validated; every input constraint must come out with the same id, operator,
 * negation and evidence, and money keeps its exact amount (its currency, when known, becomes an
 * explicit filter; when unknown, nothing is filled in).
 */
public class ConstraintPreservationTest {

  public static final DomainSchema SHOP =
      new DomainSchema(
          "shop",
          new SchemaVersion(1, 0),
          Map.of(
              "price",
              FieldDefinition.of("price", FieldType.NUMBER, Set.of(EQ, LT, LTE, GT, GTE, BETWEEN)),
              "currency",
              new FieldDefinition(
                  "currency",
                  FieldType.IDENTIFIER,
                  Set.of(EQ),
                  false,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Set.of("SEK", "EUR", "USD")),
              "material",
              FieldDefinition.of("material", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "color",
              FieldDefinition.of("color", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN)),
              "brand",
              FieldDefinition.of("brand", FieldType.TEXT, Set.of(EQ, NE, IN, NOT_IN))),
          Set.of("brand", "material", "color"),
          Set.of("search"));

  public static final DomainMapping SHOP_MAPPING =
      new DomainMapping(
          "shop",
          List.of(
              new MappingRule.FieldAlias("amount-is-price", "amount", "price"),
              new MappingRule.MoneyToNumberAndCurrency("price-money", "price", "currency")));

  public static final EntityDictionary TERMS =
      new EntityDictionary(
          List.of(
              new EntityDictionary.Term("brand", "brand", "Nike", List.of("Nike", "نایکی")),
              new EntityDictionary.Term(
                  "material", "material", "leather", List.of("leather", "läder", "چرم")),
              new EntityDictionary.Term(
                  "color", "color", "black", List.of("black", "svarta", "svart", "مشکی"))));

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "shoes without leather under 1200 SEK | en | SEK",
        "skor utan läder under 1200 SEK | sv | SEK",
        "کفش بدون چرم زیر ۱۲۰۰ کرون | fa | ",
        "Nike shoes برای winter, not leather, under 100 EUR | en | EUR"
      })
  void constraintsSurviveExtractionMappingAndValidation(
      String text, String language, String currency) {
    QueryIR input = extract(text, language);
    assertTrue(
        input.constraints().stream().anyMatch(Constraint::negative), "fixture has an exclusion");

    ValidationReport report = ConstraintValidator.validate(input, SHOP, SHOP_MAPPING);

    ValidatedQuery q = report.validated().orElseThrow(() -> new AssertionError(report));
    assertTrue(report.lostNegations().isEmpty());
    List<Constraint> out = q.filters();
    for (Constraint before : input.constraints()) {
      Constraint after =
          out.stream()
              .filter(c -> c.id().equals(before.id()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("lost " + before.id() + " in " + out));
      assertEquals(before.operator(), after.operator(), before.id());
      assertEquals(before.negative(), after.negative(), before.id());
      assertEquals(before.strength(), after.strength(), before.id());
      assertEquals(before.span(), after.span(), before.id());
      if (before.value().orElseThrow() instanceof IrValue.Money money) {
        IrValue.Number amount = (IrValue.Number) after.value().orElseThrow();
        assertEquals(0, money.amount().compareTo(amount.value()), "exact amount");
      } else {
        assertEquals(before.value(), after.value(), before.id());
      }
    }
    List<Constraint> currencies = out.stream().filter(c -> c.field().equals("currency")).toList();
    if (currency == null) {
      assertTrue(currencies.isEmpty(), "an unknown currency is never filled in");
    } else {
      assertEquals(1, currencies.size());
      assertEquals(Optional.of(new IrValue.Text(currency)), currencies.getFirst().value());
    }
    Constraint exclusion = out.stream().filter(Constraint::negative).findFirst().orElseThrow();
    assertEquals("material", exclusion.field());
    assertEquals(Optional.of(new IrValue.Text("leather")), exclusion.value());
  }

  private static QueryIR extract(String text, String language) {
    Optional<LanguageTag> tag = LanguageTag.parse(language);
    Extraction extraction =
        ConstraintExtractor.extract(
            text,
            new ExtractionContext(
                tag,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(SHOP),
                TERMS));
    return extraction.applyTo(QueryIR.of(text, tag.orElse(LanguageTag.UNDETERMINED)));
  }
}
