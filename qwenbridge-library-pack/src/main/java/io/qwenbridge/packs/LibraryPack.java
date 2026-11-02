package io.qwenbridge.packs;

import io.qwenbridge.pack.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * An independent domain with no commerce/server dependency. Its declared rule grammar is English.
 */
public final class LibraryPack implements DomainPack {
  public PackManifest manifest() {
    return new PackManifest(
        "library",
        "1.0.0",
        1,
        PackManifest.Kind.DOMAIN,
        "qwenbridge",
        List.of(),
        Set.of("normalize", "understand", "search"),
        Map.of());
  }

  public Definition definition() {
    return new Definition(
        "library",
        1,
        Map.of(
            "author",
                new Field(Type.TEXT, Set.of(Operator.EQ, Operator.NE), "author.keyword", false),
            "year", new Field(Type.DECIMAL, Set.of(Operator.values()), "year", false),
            "available", new Field(Type.BOOLEAN, Set.of(Operator.EQ), "available", false),
            "domain", new Field(Type.TEXT, Set.of(Operator.EQ), "domain", false)));
  }

  /**
   * The Library QueryIR payload (QB-234): optional {@code edition} and {@code language} strings.
   * Version 1.0 only.
   */
  @Override
  public Optional<QueryExtension> queryExtension() {
    return Optional.of(new QueryExtension("library", "1.0", Set.of("1.0")));
  }

  @Override
  public List<String> validateExtension(String version, Map<String, Object> data) {
    List<String> problems = new ArrayList<>();
    for (var entry : new TreeMap<>(data).entrySet())
      if (!Set.of("edition", "language").contains(entry.getKey()))
        problems.add("unknown library extension key " + entry.getKey());
      else if (!(entry.getValue() instanceof String text) || text.isBlank() || text.length() > 64)
        problems.add(entry.getKey() + " must be a short string");
    return problems;
  }

  public Map<String, Object> normalize(String sourceId, Map<String, Object> input) {
    if (!(input.get("title") instanceof String title)
        || title.isBlank()
        || !(input.get("author") instanceof String author)
        || author.isBlank()) throw new IllegalArgumentException("Book requires title and author");
    Map<String, Object> book = new HashMap<>();
    book.put("id", sourceId);
    book.put("title", title.strip());
    book.put("author", author.strip());
    book.put("category", "book");
    book.put("domain", "library");
    if (input.containsKey("year")) {
      int year = Integer.parseInt(input.get("year").toString());
      if (year < 0 || year > 9999) throw new IllegalArgumentException("Invalid publication year");
      book.put("year", year);
    }
    if (input.containsKey("available")) {
      if (!(input.get("available") instanceof Boolean))
        throw new IllegalArgumentException("Availability must be boolean");
      book.put("available", input.get("available"));
    }
    return Map.copyOf(book);
  }

  public Understanding understand(String text) {
    List<Clause> clauses = new ArrayList<>();
    var author =
        Pattern.compile("(?i)\\bby\\s+(.+?)(?=\\s+(?:published |after |before |available)|$)")
            .matcher(text);
    if (author.find())
      clauses.add(
          new Clause(
              "author",
              Operator.EQ,
              author.group(1).strip(),
              author.start(),
              author.end(),
              author.group()));
    var year = Pattern.compile("(?i)\\b(after|before)\\s+([0-9]{4})\\b").matcher(text);
    while (year.find())
      clauses.add(
          new Clause(
              "year",
              year.group(1).equalsIgnoreCase("after") ? Operator.GT : Operator.LT,
              year.group(2),
              year.start(),
              year.end(),
              year.group()));
    var remaining = text.toCharArray();
    for (var clause : clauses) Arrays.fill(remaining, clause.start(), clause.end(), ' ');
    String unsupported =
        new String(remaining)
            .replaceAll("(?i)\\b(books?|find|show|me|please|published|and)\\b", " ")
            .replaceAll("[^\\p{L}\\p{N}]", "")
            .strip();
    return new Understanding(
        "library",
        text,
        clauses,
        clauses.isEmpty() || !unsupported.isEmpty()
            ? List.of("Specify an author with 'by' or a year with 'after/before'.")
            : List.of());
  }

  public String searchText(Understanding query) {
    return "book";
  }
}
