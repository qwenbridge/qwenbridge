package io.qwenbridge.sdk.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QB-286: the server's expansion shape, from a real server response captured by the server's
 * MultilingualApiTest ({@code contract/v2-search-expansion.json}); absent when disabled.
 */
class ExpansionContractTest {

  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  static String expansion() throws Exception {
    var fixture = Path.of("src/test/resources/contract/v2-search-expansion.json");
    return JSON.readTree(Files.readString(fixture)).get("expansion").toString();
  }

  @Test
  void v2ResponsesCarryOneResultPerConfiguredLanguage() throws Exception {
    var response =
        JSON.readValue(
            "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"OK\",\"expansion\":"
                + expansion()
                + "}",
            CommerceContracts.Response.class);
    var e = response.expansion();
    assertEquals("I need a red winter jacket under 100 euros", e.original());
    assertEquals(List.of("en", "FA", "sv", "nb", "nn", "ja", "DK"), e.requestedLanguages());
    assertEquals(7, e.targets().size());
    assertEquals(
        "jag behöver en röd vinterjacka under 100 euro", e.target("sv").orElseThrow().text());
    assertEquals("UNSUPPORTED_LANGUAGE", e.target("ja").orElseThrow().status());
    assertNull(e.target("ja").orElseThrow().text());
    assertEquals(List.of("ja", "DK"), e.unsupportedLanguages());
    assertEquals(5, e.successful().size());
    assertEquals("VOCABULARY", e.source().basis());
    assertEquals("PRESERVED", e.target("sv").orElseThrow().preservation().status());
    assertTrue(e.target("sv").orElseThrow().identifiersPreserved());
    assertFalse(e.configured().isEmpty());
    assertNull(
        JSON.readValue(
                "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"OK\"}",
                CommerceContracts.Response.class)
            .expansion());
  }

  @Test
  void v1ResponsesCarryTheExpansion() throws Exception {
    var response =
        JSON.readValue(
            "{\"requestId\":\"r\",\"originalQuery\":\"q\",\"expansion\":" + expansion() + "}",
            SearchAnalyzeResponse.class);
    assertEquals("PARTIAL", response.expansion().status());
    assertNull(JSON.readValue("{\"requestId\":\"r\"}", SearchAnalyzeResponse.class).expansion());
  }
}
