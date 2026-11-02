package io.qwenbridge.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-092 – QB-098 over REST: the canonical catalog preview with provenance. */
@QuarkusTest
@TestProfile(CatalogCanonicalApiTest.Keys.class)
class CatalogCanonicalApiTest {
  static final String WRITER = "test-only-catalog-writer-123456";
  static final String READER = "test-only-catalog-reader-123456";

  public static class Keys implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.security.authentication-required",
          "true",
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + WRITER
              + "\",\"subject\":\"cat\",\"tenant\":\"shopA\",\"roles\":[\"catalog:write\"]},"
              + "{\"key\":\""
              + READER
              + "\",\"subject\":\"bob\",\"tenant\":\"shopA\",\"roles\":[\"search\"]}]");
    }
  }

  private static final Map<String, Object> PRODUCT =
      Map.of(
          "id", "rec-7",
          "title", "Svart löparsko",
          "brand", "NIKE",
          "category", "Footwear",
          "currency", "SEK",
          "variants",
              List.of(
                  Map.of("id", "v1", "price", "899.00", "size", "42", "quantity", 0),
                  Map.of("id", "v2", "price", "799", "size", "43", "availability", "PreOrder")));

  @Test
  void previewsTheCanonicalProductWithProvenancePriceRangesAndEmbeddingText() {
    given()
        .header("Authorization", "Bearer " + WRITER)
        .contentType("application/json")
        .body(PRODUCT)
        .post("/api/v2/catalog/canonical")
        .then()
        .statusCode(200)
        .body("product.schemaVersion", equalTo("1.0"))
        .body("product.source.mapping", equalTo("http-json"))
        .body("product.brand.value", equalTo("Nike"))
        .body("product.brand.provenance.origin", equalTo("NORMALIZED"))
        .body("product.brand.provenance.sourceValue", equalTo("NIKE"))
        .body("product.category.path", contains("fashion", "shoes"))
        .body("product.attributes.color.value", equalTo("black"))
        .body("product.attributes.color.certainty", equalTo("UNCERTAIN"))
        .body("product.attributes.color.provenance.origin", equalTo("INFERRED"))
        .body("product.variants[0].inventory.availability", equalTo("OUT_OF_STOCK"))
        .body("product.variants[1].inventory.availability", equalTo("PREORDER"))
        .body("priceRanges.SEK.min.amount", equalTo(799))
        .body("priceRanges.SEK.max.amount", equalTo(899))
        .body("productText.template", equalTo("commerce-product-text/1"))
        .body("productText.text", startsWith("title: Svart löparsko\nbrand: Nike\n"))
        .body("productText.text", not(containsString("899")))
        .body("productText.fingerprint", matchesPattern("[0-9a-f]{64}"));
  }

  @Test
  void legacyNormalizeContractIsUnchanged() {
    given()
        .header("Authorization", "Bearer " + WRITER)
        .contentType("application/json")
        .body(PRODUCT)
        .post("/api/v2/catalog/normalize")
        .then()
        .statusCode(200)
        .body("version", equalTo(1))
        .body("category", equalTo("shoes"))
        .body("attributes", not(hasKey("color")))
        .body("variants[0].stock.availability", equalTo("OUT_OF_STOCK"))
        .body("variants[1].stock.availability", equalTo("UNKNOWN"));
  }

  @Test
  void invalidRecordsAreRejectedAndTheOperationNeedsCatalogWrite() {
    given()
        .header("Authorization", "Bearer " + WRITER)
        .contentType("application/json")
        .body(Map.of("title", "x", "price", "1.5", "currency", "JPY"))
        .post("/api/v2/catalog/canonical")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + WRITER)
        .contentType("application/json")
        .queryParam("connector", "../etc")
        .body(PRODUCT)
        .post("/api/v2/catalog/canonical")
        .then()
        .statusCode(400);
    given()
        .header("Authorization", "Bearer " + READER)
        .contentType("application/json")
        .body(PRODUCT)
        .post("/api/v2/catalog/canonical")
        .then()
        .statusCode(403);
  }
}
