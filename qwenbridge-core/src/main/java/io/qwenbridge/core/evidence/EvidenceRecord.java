package io.qwenbridge.core.evidence;

import io.qwenbridge.core.queryir.Span;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One piece of evidence and its provenance, in a versioned, domain-neutral contract (QB-080).
 *
 * <p>Evidence identifies content; it does not copy it. User text is referenced by a {@link Span}
 * into the original query; other content (a retrieved document, a model answer) is referenced by
 * its source and a SHA-256 digest, so stored evidence never becomes a second copy of customer data
 * or secrets.
 *
 * @param version contract version, {@value #VERSION}
 * @param sourceType e.g. {@code query}, {@code document}, {@code model}, {@code rule}, {@code
 *     policy}
 * @param sourceId identifier within the source type (document id, model id, rule id)
 * @param parentId evidence this one was derived from, forming a provenance chain
 * @param attributes non-sensitive metadata (connector, schema version, detector)
 */
public record EvidenceRecord(
    String version,
    String id,
    Kind kind,
    String sourceType,
    String sourceId,
    Optional<Span> span,
    Optional<String> contentSha256,
    Instant observedAt,
    Optional<String> parentId,
    Map<String, String> attributes) {

  public static final String VERSION = "1.0";

  private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
  private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

  public enum Kind {
    /** A span of the user's own text. */
    USER_TEXT,
    /** Validated conversation or request context. */
    CONTEXT,
    /** Content returned by a retrieval connector. */
    RETRIEVED_CONTENT,
    /** An approved domain or schema rule. */
    DOMAIN_RULE,
    /** A model's output. */
    MODEL_OUTPUT,
    /** A recorded policy or validation decision. */
    POLICY_DECISION
  }

  public EvidenceRecord {
    if (!VERSION.equals(version)) {
      throw new IllegalArgumentException("unsupported evidence version");
    }
    if (id == null || !ID.matcher(id).matches()) {
      throw new IllegalArgumentException("invalid evidence id");
    }
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    Objects.requireNonNull(sourceId, "sourceId must not be null");
    span = span == null ? Optional.empty() : span;
    contentSha256 = contentSha256 == null ? Optional.empty() : contentSha256;
    Objects.requireNonNull(observedAt, "observedAt must not be null");
    parentId = parentId == null ? Optional.empty() : parentId;
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    if (kind == Kind.USER_TEXT && span.isEmpty()) {
      throw new IllegalArgumentException("user-text evidence needs a span");
    }
    if (contentSha256.isPresent() && !SHA256.matcher(contentSha256.get()).matches()) {
      throw new IllegalArgumentException("content digest must be lowercase SHA-256 hex");
    }
  }

  /** Evidence for a span of the original query. */
  public static EvidenceRecord userText(String id, Span span, Instant observedAt) {
    return new EvidenceRecord(
        VERSION,
        id,
        Kind.USER_TEXT,
        "query",
        "original",
        Optional.of(span),
        Optional.empty(),
        observedAt,
        Optional.empty(),
        Map.of());
  }

  /** Evidence for other content, identified by source and digest only. */
  public static EvidenceRecord of(
      String id,
      Kind kind,
      String sourceType,
      String sourceId,
      String content,
      Instant observedAt) {
    return new EvidenceRecord(
        VERSION,
        id,
        kind,
        sourceType,
        sourceId,
        Optional.empty(),
        Optional.ofNullable(content).map(EvidenceRecord::sha256),
        observedAt,
        Optional.empty(),
        Map.of());
  }

  public static String sha256(String content) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }
}
