package io.qwenbridge.sdk.spelling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import org.junit.jupiter.api.Test;

/**
 * QB-291: the server's spelling shape on v1 analyze and v2 search responses; absent when disabled.
 */
class SpellingContractTest {

  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  static final String SPELLING =
      "{\"status\":\"CORRECTED\",\"mode\":\"SAFE_AUTO_CORRECT\",\"original\":\"röd"
          + " jakca\",\"normalized\":\"röd jakca\",\"corrected\":\"röd"
          + " jacka\",\"languages\":[{\"configured\":\"sv\",\"status\":\"SUPPORTED\",\"providers\":[\"dictionary\"]},"
          + "{\"configured\":\"de\",\"status\":\"PROVIDER_UNAVAILABLE\",\"providers\":[]}],\"detectedLanguage\":null,"
          + "\"activeLanguages\":[\"sv\"],\"corrections\":[{\"token\":\"jakca\",\"start\":4,\"end\":9,\"language\":\"sv\","
          + "\"candidates\":[{\"form\":\"jacka\",\"language\":\"sv\",\"score\":0.92}],\"replacement\":\"jacka\",\"confidence\":0.92,\"applied\":true,\"reason\":\"sv:"
          + " edit distance 0.8\"}],\"protectedTokens\":0,\"explanations\":[\"jakca -> jacka\"]}";

  @Test
  void v2ResponsesCarrySpelling() throws Exception {
    var response =
        JSON.readValue(
            "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"OK\",\"spelling\":"
                + SPELLING
                + "}",
            CommerceContracts.Response.class);
    var s = response.spelling();
    assertEquals("röd jakca", s.original());
    assertEquals("röd jacka", s.corrected());
    assertTrue(s.changed());
    assertEquals("PROVIDER_UNAVAILABLE", s.languages().get(1).status());
    assertTrue(s.corrections().getFirst().applied());
    assertEquals("jacka", s.corrections().getFirst().candidates().getFirst().form());
    assertNull(
        JSON.readValue(
                "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"OK\"}",
                CommerceContracts.Response.class)
            .spelling());
  }

  @Test
  void v1ResponsesCarrySpelling() throws Exception {
    var response =
        JSON.readValue(
            "{\"requestId\":\"r\",\"originalQuery\":\"röd jakca\",\"spelling\":" + SPELLING + "}",
            SearchAnalyzeResponse.class);
    assertEquals("CORRECTED", response.spelling().status());
    assertNull(JSON.readValue("{\"requestId\":\"r\"}", SearchAnalyzeResponse.class).spelling());
  }
}
