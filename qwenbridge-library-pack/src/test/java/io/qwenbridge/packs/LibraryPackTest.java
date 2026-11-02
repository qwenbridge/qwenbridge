package io.qwenbridge.packs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.pack.DomainPack;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.pack.QwenBridgePack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-287: the Library pack is a genuine, independent domain pack. It is found through the same
 * ServiceLoader contract as every pack, declares its own schema and QueryIR extension, grounds its
 * clauses in the query text, asks instead of guessing, and depends on nothing but the public pack
 * API (no Commerce, no server).
 */
class LibraryPackTest {

  final LibraryPack pack = new LibraryPack();

  @Test
  void isDiscoveredThroughThePackServiceContract() {
    assertTrue(
        ServiceLoader.load(QwenBridgePack.class).stream()
            .anyMatch(p -> p.type().equals(LibraryPack.class)));
    PackManifest m = pack.manifest();
    assertEquals("library", m.id());
    assertEquals(PackManifest.Kind.DOMAIN, m.kind());
    assertTrue(m.dependencies().isEmpty(), "no dependency on another pack");
  }

  @Test
  void declaresItsOwnSchemaAndQueryIrExtension() {
    var definition = pack.definition();
    assertEquals("library", definition.id());
    assertEquals(
        List.of("author", "available", "domain", "year"),
        definition.fields().keySet().stream().sorted().toList());
    assertEquals(DomainPack.Type.DECIMAL, definition.fields().get("year").type());
    var extension = pack.queryExtension().orElseThrow();
    assertEquals("library", extension.namespace());
    assertTrue(pack.validateExtension("1.0", Map.of("edition", "2nd", "language", "en")).isEmpty());
    assertEquals(1, pack.validateExtension("1.0", Map.of("isbn", "x")).size());
    assertEquals(1, pack.validateExtension("1.0", Map.of("edition", "")).size());
  }

  @Test
  void normalizesBooksAndRejectsInvalidRecords() {
    var book =
        pack.normalize(
            "b1", Map.of("title", " A book ", "author", "Ada", "year", "2024", "available", true));
    assertEquals("A book", book.get("title"));
    assertEquals(2024, book.get("year"));
    assertEquals("library", book.get("domain"));
    assertThrows(IllegalArgumentException.class, () -> pack.normalize("b2", Map.of("title", "x")));
    assertThrows(
        IllegalArgumentException.class,
        () -> pack.normalize("b3", Map.of("title", "x", "author", "y", "year", "12000")));
    assertThrows(
        IllegalArgumentException.class,
        () -> pack.normalize("b4", Map.of("title", "x", "author", "y", "available", "yes")));
  }

  @Test
  void understandsGroundedClausesAndAsksInsteadOfGuessing() {
    String query = "books by Ada Lovelace after 2020";
    var understanding = pack.understand(query);
    assertEquals(2, understanding.clauses().size());
    assertTrue(understanding.questions().isEmpty());
    var author = understanding.clauses().get(0);
    assertEquals("author", author.field());
    assertEquals("Ada Lovelace", author.value());
    assertEquals(query.substring(author.start(), author.end()), author.quote());
    var year = understanding.clauses().get(1);
    assertEquals(DomainPack.Operator.GT, year.operator());
    assertEquals("2020", year.value());

    var vague = pack.understand("something nice to read");
    assertTrue(vague.clauses().isEmpty());
    assertFalse(vague.questions().isEmpty(), "an unsupported request is a question, never a guess");
  }

  @Test
  void dependsOnlyOnThePublicPackApi() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));
    assertFalse(pom.contains("qwenbridge-commerce-pack"));
    assertFalse(pom.contains("qwenbridge-server"));
    assertFalse(pom.contains("qwenbridge-core"));
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String text = Files.readString(f);
        assertFalse(text.contains("io.qwenbridge.catalog"), f.toString());
        assertFalse(text.contains("io.qwenbridge.commerce"), f.toString());
        assertFalse(text.contains("io.qwenbridge.core"), f.toString());
      }
    }
  }
}
