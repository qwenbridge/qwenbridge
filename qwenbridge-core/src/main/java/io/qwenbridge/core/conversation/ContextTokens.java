package io.qwenbridge.core.conversation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Optional signed context tokens (QB-141): {@code v1.<payload>.<signature>}, both base64url. The
 * payload holds {@code iss}, {@code aud}, {@code kid}, {@code sub}, {@code tenant}, {@code iat},
 * {@code exp} and the context; the Ed25519 signature covers the payload bytes exactly, so any
 * change is detected. Verification checks the signature with the named key, the issuer, the
 * audience and the expiry; ownership is checked afterwards by {@link ContextValidator}.
 */
public final class ContextTokens {

  public enum Failure {
    MALFORMED,
    TOO_LARGE,
    UNKNOWN_KEY,
    BAD_SIGNATURE,
    WRONG_ISSUER,
    WRONG_AUDIENCE,
    EXPIRED,
    CLAIMS_MISMATCH
  }

  /** The signed payload. Times are epoch seconds (Core carries no date-time Jackson module). */
  public record Payload(
      String iss,
      String aud,
      String kid,
      String sub,
      String tenant,
      long iat,
      long exp,
      Wire context) {}

  /** The context on the wire. */
  public record Wire(
      String version,
      String conversationId,
      String tenant,
      String subject,
      int revision,
      long issuedAt,
      long expiresAt,
      java.util.List<ContextConstraint> constraints,
      java.util.List<String> lockedFields,
      java.util.List<String> suppressed,
      java.util.List<ConversationContext.Turn> turns) {

    public Wire {
      constraints = constraints == null ? java.util.List.of() : java.util.List.copyOf(constraints);
      lockedFields =
          lockedFields == null ? java.util.List.of() : java.util.List.copyOf(lockedFields);
      suppressed = suppressed == null ? java.util.List.of() : java.util.List.copyOf(suppressed);
      turns = turns == null ? java.util.List.of() : java.util.List.copyOf(turns);
    }

    public static Wire of(ConversationContext c) {
      return new Wire(
          c.version(),
          c.conversationId(),
          c.tenant(),
          c.subject(),
          c.revision(),
          c.issuedAt().getEpochSecond(),
          c.expiresAt().getEpochSecond(),
          c.constraints(),
          c.lockedFields(),
          c.suppressed(),
          c.turns());
    }

    public ConversationContext context() {
      return new ConversationContext(
          version,
          conversationId,
          tenant,
          subject,
          revision,
          Instant.ofEpochSecond(issuedAt),
          Instant.ofEpochSecond(expiresAt),
          constraints,
          lockedFields,
          suppressed,
          turns);
    }
  }

  /** A verified token: the context and its byte size, or why it was refused. */
  public record Verification(
      Optional<ConversationContext> context, int bytes, Optional<Failure> failure) {}

  static final int MAX_TOKEN_CHARS = 32_768;

  private final String issuer;
  private final String audience;
  private final String keyId;
  private final Optional<PrivateKey> signingKey;
  private final Map<String, PublicKey> verificationKeys;
  private final ObjectMapper json =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public ContextTokens(
      String issuer,
      String audience,
      String keyId,
      Optional<PrivateKey> signingKey,
      Map<String, PublicKey> verificationKeys) {
    this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
    this.audience = Objects.requireNonNull(audience, "audience must not be null");
    this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
    this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
    this.verificationKeys = Map.copyOf(verificationKeys);
  }

  public boolean canIssue() {
    return signingKey.isPresent();
  }

  public String issue(ConversationContext context) {
    PrivateKey key =
        signingKey.orElseThrow(
            () -> new IllegalStateException("context signing is not configured"));
    try {
      byte[] payload =
          json.writeValueAsBytes(
              new Payload(
                  issuer,
                  audience,
                  keyId,
                  context.subject(),
                  context.tenant(),
                  context.issuedAt().getEpochSecond(),
                  context.expiresAt().getEpochSecond(),
                  Wire.of(context)));
      Signature signer = Signature.getInstance("Ed25519");
      signer.initSign(key);
      signer.update(payload);
      var b64 = Base64.getUrlEncoder().withoutPadding();
      return "v1." + b64.encodeToString(payload) + "." + b64.encodeToString(signer.sign());
    } catch (GeneralSecurityException | java.io.IOException e) {
      throw new IllegalStateException("cannot sign context", e);
    }
  }

  public Verification verify(String token, Instant now) {
    if (token == null || token.length() > MAX_TOKEN_CHARS) {
      return failed(token == null ? Failure.MALFORMED : Failure.TOO_LARGE);
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || !parts[0].equals("v1")) {
      return failed(Failure.MALFORMED);
    }
    byte[] payload;
    byte[] signature;
    Payload claims;
    try {
      payload = Base64.getUrlDecoder().decode(parts[1]);
      signature = Base64.getUrlDecoder().decode(parts[2]);
      claims = json.readValue(payload, Payload.class);
    } catch (RuntimeException | java.io.IOException e) {
      return failed(Failure.MALFORMED);
    }
    if (claims == null || claims.context() == null || claims.kid() == null) {
      return failed(Failure.MALFORMED);
    }
    PublicKey key = verificationKeys.get(claims.kid());
    if (key == null) {
      return failed(Failure.UNKNOWN_KEY);
    }
    try {
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(key);
      verifier.update(payload);
      if (!verifier.verify(signature)) {
        return failed(Failure.BAD_SIGNATURE);
      }
    } catch (GeneralSecurityException e) {
      return failed(Failure.BAD_SIGNATURE);
    }
    if (!issuer.equals(claims.iss())) {
      return failed(Failure.WRONG_ISSUER);
    }
    if (!audience.equals(claims.aud())) {
      return failed(Failure.WRONG_AUDIENCE);
    }
    if (now.getEpochSecond() >= claims.exp()) {
      return failed(Failure.EXPIRED);
    }
    ConversationContext ctx;
    try {
      ctx = claims.context().context();
    } catch (RuntimeException e) {
      return failed(Failure.MALFORMED);
    }
    if (!ctx.subject().equals(claims.sub())
        || !ctx.tenant().equals(claims.tenant())
        || ctx.expiresAt().getEpochSecond() != claims.exp()) {
      return failed(Failure.CLAIMS_MISMATCH);
    }
    return new Verification(Optional.of(ctx), payload.length, Optional.empty());
  }

  private static Verification failed(Failure failure) {
    return new Verification(Optional.empty(), 0, Optional.of(failure));
  }
}
