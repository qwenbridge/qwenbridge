package io.qwenbridge.core.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.QueryIrJson;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.retrieval.CrossLanguageRetrieval.Strategy;
import io.qwenbridge.core.translation.TranslationCapabilities;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** QB-020: cross-language retrieval in the query contract and in provider capabilities. */
class CrossLanguageRetrievalTest {

  static final LanguageTag FA = new LanguageTag("fa");
  static final LanguageTag EN = new LanguageTag("en");
  static final LanguageTag SV = new LanguageTag("sv");

  static RetrievalHints cross(List<LanguageTag> content) {
    return CrossLanguageRetrieval.withContentLanguages(
        new RetrievalHints(Set.of(RetrievalHints.Mode.SEMANTIC), false, List.of()), FA, content);
  }

  @Test
  void contentInAnotherLanguageMakesTheQueryCrossLanguage() {
    RetrievalHints hints = cross(List.of(EN, SV));
    assertTrue(hints.crossLanguage());
    assertEquals(List.of(EN, SV), hints.contentLanguages());
    assertEquals(Set.of(RetrievalHints.Mode.SEMANTIC), hints.modes());
    assertFalse(cross(List.of(new LanguageTag("fa-IR"))).crossLanguage());
  }

  @Test
  void hintsAreCarriedInTheQueryIrWireFormat() {
    QueryIR ir = QueryIR.of("کفش", FA);
    ir =
        new QueryIR(
            ir.version(),
            ir.queryId(),
            ir.originalText(),
            ir.language(),
            ir.intents(),
            ir.entities(),
            ir.constraints(),
            ir.semanticSignals(),
            cross(List.of(EN)),
            ir.subqueries(),
            ir.extensions());
    QueryIR back = ((QueryIrJson.Result.Valid) QueryIrJson.read(QueryIrJson.write(ir))).ir();
    assertTrue(back.retrievalHints().crossLanguage());
    assertEquals(List.of(EN), back.retrievalHints().contentLanguages());
  }

  @Test
  void multilingualProviderNeedsNoTranslation() {
    var decision =
        CrossLanguageRetrieval.decide(
            FA,
            cross(List.of(EN)),
            new ContentLanguageSupport(Set.of("en"), true),
            Optional.empty());
    assertEquals(Strategy.MULTILINGUAL_SEMANTIC, decision.strategy());
  }

  @Test
  void otherwiseTheQueryIsTranslatedToEachContentLanguage() {
    var decision =
        CrossLanguageRetrieval.decide(
            FA,
            cross(List.of(EN, SV)),
            new ContentLanguageSupport(Set.of("en", "sv"), false),
            Optional.of(TranslationCapabilities.anyToAny(1000)));
    assertEquals(Strategy.TRANSLATE_QUERY, decision.strategy());
    assertEquals(List.of(EN, SV), decision.translateTo());
  }

  @Test
  void missingCapabilityIsExplicitlyUnsupported() {
    var noTranslator =
        CrossLanguageRetrieval.decide(
            FA, cross(List.of(EN)), ContentLanguageSupport.UNKNOWN, Optional.empty());
    assertEquals(Strategy.UNSUPPORTED, noTranslator.strategy());
    var missingPair =
        CrossLanguageRetrieval.decide(
            FA,
            cross(List.of(EN, SV)),
            ContentLanguageSupport.UNKNOWN,
            Optional.of(new TranslationCapabilities(Set.of("fa"), Set.of("en"), false, 100)));
    assertEquals(Strategy.UNSUPPORTED, missingPair.strategy());
    var undetermined =
        CrossLanguageRetrieval.decide(
            LanguageTag.UNDETERMINED,
            new RetrievalHints(Set.of(), true, List.of(EN)),
            ContentLanguageSupport.UNKNOWN,
            Optional.of(TranslationCapabilities.anyToAny(1000)));
    assertEquals(Strategy.UNSUPPORTED, undetermined.strategy());
  }

  @Test
  void notRequestedMeansNotNeeded() {
    assertEquals(
        Strategy.NOT_NEEDED,
        CrossLanguageRetrieval.decide(
                FA, RetrievalHints.NONE, ContentLanguageSupport.UNKNOWN, Optional.empty())
            .strategy());
  }
}
