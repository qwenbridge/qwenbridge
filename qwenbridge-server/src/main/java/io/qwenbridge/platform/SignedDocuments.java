package io.qwenbridge.platform;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public final class SignedDocuments {
  private SignedDocuments() {}

  public static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** The signed form of package metadata: bound to the package by its JAR digest (QB-275). */
  public static byte[] metadataBytes(String digest, String metadata) {
    return ("qwenbridge-pack-metadata-v1\n" + digest + "\n" + metadata)
        .getBytes(StandardCharsets.UTF_8);
  }

  public static byte[] packBytes(String manifest, byte[] jar) {
    return ("qwenbridge-pack-v1\n" + manifest + "\n" + digest(jar))
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * QB-262: every configured key must decode as an Ed25519 public key; anything else (for example a
   * private key pasted by mistake) is refused at startup without echoing it.
   */
  public static void requirePublicKeys(Map<String, String> trusted, String setting) {
    for (var entry : trusted.entrySet()) {
      try {
        var key =
            KeyFactory.getInstance("Ed25519")
                .generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(entry.getValue())));
        if (!"EdDSA".equals(key.getAlgorithm()) && !"Ed25519".equals(key.getAlgorithm()))
          throw new IllegalArgumentException("not Ed25519");
      } catch (GeneralSecurityException | IllegalArgumentException ex) {
        throw new IllegalArgumentException(
            setting + ": key " + entry.getKey() + " is not an Ed25519 public key");
      }
    }
  }

  public static void verify(
      Map<String, String> trusted, String keyId, byte[] content, String signature) {
    if (content == null || keyId == null || signature == null || signature.length() > 256)
      throw new IllegalArgumentException("Invalid signing envelope");
    String encoded = trusted.get(keyId);
    if (encoded == null) throw new IllegalArgumentException("Untrusted signing key");
    try {
      var key =
          KeyFactory.getInstance("Ed25519")
              .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
      var verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(key);
      verifier.update(content);
      if (!verifier.verify(Base64.getDecoder().decode(signature)))
        throw new IllegalArgumentException("Invalid signature");
    } catch (GeneralSecurityException ex) {
      throw new IllegalArgumentException("Invalid signed document", ex);
    }
  }
}
