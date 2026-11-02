package io.qwenbridge.core.conversation;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.conversation.ContextConstraint.Origin;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-139 – QB-145 in Core: contract, limits, untrusted context, signed tokens, merge, isolation.
 */
class ConversationContextTest {
  static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
  static final Set<String> FIELDS = Set.of("brand", "color", "price", "currency", "condition");

  static ContextConstraint cc(String field, String op, String value, Origin origin, int turn) {
    return new ContextConstraint(field, op, value, origin, turn);
  }

  static ConversationContext context(
      String tenant, String subject, List<ContextConstraint> constraints) {
    return new ConversationContext(
        ConversationContext.VERSION,
        "conv-1",
        tenant,
        subject,
        2,
        NOW.minusSeconds(60),
        NOW.plusSeconds(1200),
        constraints,
        List.of("price"),
        List.of("s1"),
        List.of(
            new ConversationContext.Turn("r1", "OK"), new ConversationContext.Turn("r2", "OK")));
  }

  static ContextValidator.Result validate(ConversationContext c, int bytes, boolean signed) {
    return ContextValidator.validate(
        c,
        bytes,
        signed,
        "shopA",
        "alice",
        NOW,
        ContextLimits.DEFAULT,
        (f, op) -> FIELDS.contains(f));
  }

  static final List<ContextConstraint> STATED =
      List.of(
          cc("brand", "EQ", "Nike", Origin.USER_TEXT, 1),
          cc("price", "LT", "100", Origin.USER_TEXT, 1));

  @Test
  void contextBelongsToItsOwnerAndStaysWithinLimits() {
    assertTrue(validate(context("shopA", "alice", STATED), 500, true).valid());
    assertEquals(
        List.of(ContextValidator.Reason.FOREIGN),
        validate(context("shopB", "alice", STATED), 500, true).reasons());
    assertEquals(
        List.of(ContextValidator.Reason.FOREIGN),
        validate(context("shopA", "bob", STATED), 500, true).reasons());
    assertTrue(
        validate(context("shopA", "alice", STATED), 20_000, true)
            .reasons()
            .contains(ContextValidator.Reason.TOO_LARGE));
    var expired =
        new ConversationContext(
            ConversationContext.VERSION,
            "c",
            "shopA",
            "alice",
            0,
            NOW.minusSeconds(4000),
            NOW.minusSeconds(1),
            STATED,
            List.of(),
            List.of(),
            List.of());
    assertTrue(validate(expired, 100, true).reasons().contains(ContextValidator.Reason.EXPIRED));
    var forever =
        new ConversationContext(
            ConversationContext.VERSION,
            "c",
            "shopA",
            "alice",
            0,
            NOW,
            NOW.plus(Duration.ofDays(30)),
            STATED,
            List.of(),
            List.of(),
            List.of());
    assertTrue(
        validate(forever, 100, true).reasons().contains(ContextValidator.Reason.TTL_TOO_LONG));
    var many = new java.util.ArrayList<ContextConstraint>();
    for (int i = 0; i < 33; i++) many.add(cc("brand", "NE", "b" + i, Origin.USER_TEXT, 1));
    assertTrue(
        validate(context("shopA", "alice", many), 500, true)
            .reasons()
            .contains(ContextValidator.Reason.TOO_MANY_CONSTRAINTS));
    var unknown = List.of(cc("internalScore", "GT", "0", Origin.USER_TEXT, 1));
    assertTrue(
        validate(context("shopA", "alice", unknown), 500, true)
            .reasons()
            .contains(ContextValidator.Reason.UNKNOWN_FIELD));
    var wrongVersion =
        new ConversationContext(
            "conversation-context/9",
            "c",
            "shopA",
            "alice",
            0,
            NOW,
            NOW.plusSeconds(60),
            STATED,
            List.of(),
            List.of(),
            List.of());
    assertTrue(
        validate(wrongVersion, 100, true)
            .reasons()
            .contains(ContextValidator.Reason.WRONG_VERSION));
    assertThrows(
        IllegalArgumentException.class,
        () -> cc("brand", "EQ", "x".repeat(300), Origin.USER_TEXT, 1));
  }

  @Test
  void unsignedContextIsUntrustedAndDowngraded() {
    var unsigned = validate(context("shopA", "alice", STATED), 500, false).context().orElseThrow();
    assertTrue(unsigned.constraints().stream().allMatch(c -> c.origin() == Origin.CONTEXT));
    assertEquals(
        List.of("Nike", "100"),
        unsigned.constraints().stream().map(ContextConstraint::value).toList());
    var signed = validate(context("shopA", "alice", STATED), 500, true).context().orElseThrow();
    assertTrue(signed.constraints().stream().allMatch(c -> c.origin() == Origin.USER_TEXT));
  }

  @Test
  void followUpsKeepConstraintsWithProvenance() {
    var previous =
        List.of(
            cc("brand", "EQ", "Nike", Origin.USER_TEXT, 1),
            cc("price", "LT", "100", Origin.USER_TEXT, 1),
            cc("condition", "NE", "refurbished", Origin.FEEDBACK, 2),
            cc("color", "EQ", "black", Origin.SUGGESTION, 2));
    var merged =
        ContextMerge.followUp(
            previous,
            List.of(
                cc("price", "GT", "50", Origin.USER_TEXT, 3),
                cc("condition", "NE", "used", Origin.USER_TEXT, 3)),
            Set.of("color"));
    assertEquals(
        List.of(
            "brand EQ Nike 1 USER_TEXT",
            "price LT 100 1 USER_TEXT",
            "condition NE refurbished 2 FEEDBACK",
            "price GT 50 3 USER_TEXT",
            "condition NE used 3 USER_TEXT"),
        merged.stream()
            .map(
                c ->
                    c.field()
                        + " "
                        + c.operator()
                        + " "
                        + c.value()
                        + " "
                        + c.turn()
                        + " "
                        + c.origin())
            .toList());
    var replaced =
        ContextMerge.followUp(
            previous, List.of(cc("brand", "EQ", "Adidas", Origin.USER_TEXT, 3)), Set.of());
    assertEquals(1, replaced.stream().filter(c -> c.field().equals("brand")).count());
    assertEquals(
        "Adidas",
        replaced.stream().filter(c -> c.field().equals("brand")).findFirst().orElseThrow().value());
  }

  static KeyPair keys() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  @Test
  void signedTokensDetectEveryChange() throws Exception {
    KeyPair pair = keys();
    var tokens =
        new ContextTokens(
            "qwenbridge",
            "qwenbridge-commerce",
            "k1",
            Optional.of(pair.getPrivate()),
            Map.of("k1", pair.getPublic()));
    var ctx = context("shopA", "alice", STATED);
    String token = tokens.issue(ctx);
    var ok = tokens.verify(token, NOW);
    assertEquals(ctx, ok.context().orElseThrow());
    assertTrue(ok.failure().isEmpty());

    String[] parts = token.split("\\.");
    String payload =
        new String(
            Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
    String forged =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                payload
                    .replace("\"shopA\"", "\"shopB\"")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(
        ContextTokens.Failure.BAD_SIGNATURE,
        tokens.verify("v1." + forged + "." + parts[2], NOW).failure().orElseThrow());
    assertEquals(
        ContextTokens.Failure.EXPIRED,
        tokens.verify(token, NOW.plusSeconds(1200)).failure().orElseThrow());
    assertEquals(
        ContextTokens.Failure.MALFORMED, tokens.verify("v1.abc", NOW).failure().orElseThrow());
    assertEquals(
        ContextTokens.Failure.MALFORMED,
        tokens.verify("v2." + parts[1] + "." + parts[2], NOW).failure().orElseThrow());
    assertEquals(
        ContextTokens.Failure.TOO_LARGE,
        tokens.verify("v1." + "a".repeat(40_000) + ".b", NOW).failure().orElseThrow());

    KeyPair other = keys();
    var otherIssuer =
        new ContextTokens(
            "qwenbridge",
            "qwenbridge-commerce",
            "k1",
            Optional.of(other.getPrivate()),
            Map.of("k1", other.getPublic()));
    assertEquals(
        ContextTokens.Failure.BAD_SIGNATURE,
        tokens.verify(otherIssuer.issue(ctx), NOW).failure().orElseThrow());
    var unknownKey =
        new ContextTokens(
            "qwenbridge", "qwenbridge-commerce", "k2", Optional.of(pair.getPrivate()), Map.of());
    assertEquals(
        ContextTokens.Failure.UNKNOWN_KEY,
        tokens.verify(unknownKey.issue(ctx), NOW).failure().orElseThrow());
    var otherAudience =
        new ContextTokens(
            "qwenbridge",
            "other-service",
            "k1",
            Optional.of(pair.getPrivate()),
            Map.of("k1", pair.getPublic()));
    assertEquals(
        ContextTokens.Failure.WRONG_AUDIENCE,
        tokens.verify(otherAudience.issue(ctx), NOW).failure().orElseThrow());
    var otherIss =
        new ContextTokens(
            "someone-else",
            "qwenbridge-commerce",
            "k1",
            Optional.of(pair.getPrivate()),
            Map.of("k1", pair.getPublic()));
    assertEquals(
        ContextTokens.Failure.WRONG_ISSUER,
        tokens.verify(otherIss.issue(ctx), NOW).failure().orElseThrow());
    var verifyOnly =
        new ContextTokens(
            "qwenbridge",
            "qwenbridge-commerce",
            "k1",
            Optional.empty(),
            Map.of("k1", pair.getPublic()));
    assertFalse(verifyOnly.canIssue());
    assertThrows(IllegalStateException.class, () -> verifyOnly.issue(ctx));
    // A valid token for Alice is still refused for Bob: ownership is checked on use.
    var verified = tokens.verify(token, NOW);
    assertEquals(
        List.of(ContextValidator.Reason.FOREIGN),
        ContextValidator.validate(
                verified.context().orElseThrow(),
                verified.bytes(),
                true,
                "shopA",
                "bob",
                NOW,
                ContextLimits.DEFAULT,
                (f, op) -> true)
            .reasons());
  }
}
