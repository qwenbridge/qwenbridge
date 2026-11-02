package io.qwenbridge.core.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.translation.GlossaryTranslationProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Downstream retrieval over expansion variants (QB-286, QueryIR and retrieval integration). */
class ExpansionRetrievalTest {

  @Test
  void variantsReachContentInEachLanguageAndDocumentsAreCountedOnce() throws Exception {
    var index = new InMemoryKeywordIndex("kw", 100, 50);
    index.put(new IndexedDocument("t1", "sv-1", "röd vinterjacka i ull", Map.of()));
    index.put(new IndexedDocument("t1", "en-1", "red winter jacket in wool", Map.of()));
    index.put(new IndexedDocument("t1", "nb-1", "rød vinterjakke", Map.of()));
    index.put(new IndexedDocument("t2", "sv-2", "röd vinterjacka", Map.of()));

    var expansion =
        QueryExpander.expand(
            new QueryExpander.Request(
                "red winter jacket",
                "red winter jacket",
                SourceLanguage.of(new LanguageTag("en")),
                new ExpansionSettings(
                    true, List.of("en", "sv", "nb"), ExpansionPolicy.ALL_TARGETS, Map.of(), 0.5),
                null),
            List.of(GlossaryTranslationProvider.load("g", GlossaryExpansionTest.GLOSSARY)),
            ExpansionLimits.DEFAULT,
            Optional.empty(),
            () -> false);
    assertEquals(
        List.of("red winter jacket", "röd vinterjacka", "rød vinterjakke"),
        expansion.retrievalTexts());

    var result =
        ExpansionRetrieval.keyword(
            index,
            "t1",
            Filter.ALL,
            expansion,
            10,
            Duration.ofSeconds(1),
            CancellationToken.create());
    Set<String> ids = result.documents().stream().map(d -> d.id()).collect(Collectors.toSet());
    assertEquals(Set.of("sv-1", "en-1", "nb-1"), ids, "tenant isolation kept: sv-2 is t2's");
    assertEquals(result.documents().size(), ids.size(), "each document once");
    assertEquals(3, result.searched().size());
    assertTrue(result.failed().isEmpty());
  }
}
