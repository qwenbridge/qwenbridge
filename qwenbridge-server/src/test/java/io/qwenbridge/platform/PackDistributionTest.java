package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.pack.tool.PackTool;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QB-274 – QB-278: a pack is packaged and signed with the offline tool, transferred as a file,
 * verified and inspected without executing it, and installed by a server that trusts only the
 * publisher's public key. Corrupted, re-signed, untrusted and revoked packages are refused, and
 * signed release metadata cannot be altered or used to claim another identity.
 */
class PackDistributionTest {

  static final String DOMAIN =
      "public Definition definition(){return new Definition(\"shipped\",1,Map.of());} public"
          + " Map<String,Object> normalize(String id,Map<String,Object> raw){return"
          + " Map.of(\"ok\",true);} public Understanding understand(String q){return new"
          + " Understanding(\"shipped\",q,List.of(),List.of());}";

  record Run(int status, String out, String err) {}

  static Run tool(String... args) {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    int status =
        PackTool.run(
            args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Run(
        status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void aTrustedPackIsPackagedTransferredVerifiedAndInstalledOffline(
      @TempDir Path publisher, @TempDir Path site) throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      // The publisher's side: manifest, JAR and private key (the fixture's "tests" key).
      var built = f.artifact("shipped", "1.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      Files.writeString(publisher.resolve("manifest.json"), built.manifest());
      Files.write(publisher.resolve("pack.jar"), Base64.getDecoder().decode(built.jar()));
      Files.writeString(
          publisher.resolve("tests.key"),
          Base64.getEncoder().encodeToString(f.keys.getPrivate().getEncoded()));
      Files.writeString(
          publisher.resolve("tests.pub"),
          Base64.getEncoder().encodeToString(f.keys.getPublic().getEncoded()));
      Files.writeString(
          publisher.resolve("metadata.json"),
          "{\"description\":\"Shipped"
              + " domain\",\"documentation\":\"https://docs.example.org/shipped\",\"contact\":\"packs@example.org\",\"releaseNotes\":\"First"
              + " release\"}");
      String[] pack = {
        "pack",
        publisher.resolve("manifest.json").toString(),
        publisher.resolve("pack.jar").toString(),
        "tests",
        publisher.resolve("tests.key").toString(),
        publisher.resolve("shipped.qbpack").toString(),
        publisher.resolve("metadata.json").toString()
      };
      assertThat(tool(pack).status()).isZero();
      byte[] first = Files.readAllBytes(publisher.resolve("shipped.qbpack"));
      assertThat(tool(pack).status()).isZero();
      assertThat(Files.readAllBytes(publisher.resolve("shipped.qbpack")))
          .isEqualTo(first); // reproducible

      // Transfer: a file copy to the offline site, which has only the public key.
      Path received =
          Files.copy(publisher.resolve("shipped.qbpack"), site.resolve("shipped.qbpack"));
      Files.copy(publisher.resolve("tests.pub"), site.resolve("tests.pub"));
      var verified =
          tool("verify", received.toString(), "tests", site.resolve("tests.pub").toString());
      assertThat(verified.status()).isZero();
      assertThat(verified.out()).startsWith("OK ");
      var inspected = tool("inspect", received.toString());
      assertThat(inspected.out())
          .contains(
              "id: shipped",
              "version: 1.0.0",
              "kind: DOMAIN",
              "publisher: tests",
              "metadata: signed");

      // Install from the request body, then activate.
      var request = tool("request", received.toString()).out().strip();
      var signed = new ObjectMapper().readValue(request, ManagedPacks.SignedPack.class);
      var view = f.packs.install(f.admin, signed);
      assertThat(view.digest()).isEqualTo(verified.out().strip().substring(3));
      f.packs.activate(f.admin, "shipped", "1.0.0", Map.of());
      var listed =
          f.packs.list(f.admin.tenant()).stream()
              .filter(v -> v.manifest().id().equals("shipped"))
              .findFirst()
              .orElseThrow();
      assertThat(listed.active()).isTrue();
      assertThat(listed.metadata().documentation()).isEqualTo("https://docs.example.org/shipped");
      // The marketplace keeps the verified metadata with the listing.
      var listing = new Marketplace(f.store, f.packs, f.licenses).publish(f.admin, signed);
      assertThat(listing.metadata().contact()).isEqualTo("packs@example.org");
      assertThat(listing.publisher()).isEqualTo("tests");
    }
  }

  @Test
  void corruptedUntrustedAlteredAndRevokedPackagesAreRefused(@TempDir Path dir) throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      var built = f.artifact("shipped", "1.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      Files.writeString(dir.resolve("manifest.json"), built.manifest());
      Files.write(dir.resolve("pack.jar"), Base64.getDecoder().decode(built.jar()));
      Files.writeString(
          dir.resolve("tests.key"),
          Base64.getEncoder().encodeToString(f.keys.getPrivate().getEncoded()));
      Files.writeString(
          dir.resolve("tests.pub"),
          Base64.getEncoder().encodeToString(f.keys.getPublic().getEncoded()));
      Files.writeString(dir.resolve("metadata.json"), "{\"description\":\"Original\"}");
      assertThat(
              tool(
                      "pack",
                      dir.resolve("manifest.json").toString(),
                      dir.resolve("pack.jar").toString(),
                      "tests",
                      dir.resolve("tests.key").toString(),
                      dir.resolve("p.qbpack").toString(),
                      dir.resolve("metadata.json").toString())
                  .status())
          .isZero();
      var mapper = new ObjectMapper();
      var signed =
          mapper.readValue(
              tool("request", dir.resolve("p.qbpack").toString()).out(),
              ManagedPacks.SignedPack.class);

      // A corrupted JAR: the tool and the server both refuse it.
      byte[] jar = Base64.getDecoder().decode(signed.jar());
      jar[jar.length / 3] ^= 0x11;
      String text =
          Files.readString(dir.resolve("p.qbpack"))
              .replace(signed.jar(), Base64.getEncoder().encodeToString(jar));
      Files.writeString(dir.resolve("corrupt.qbpack"), text);
      var corruptRun =
          tool(
              "verify",
              dir.resolve("corrupt.qbpack").toString(),
              "tests",
              dir.resolve("tests.pub").toString());
      assertThat(corruptRun.status()).isEqualTo(1);
      assertThat(corruptRun.err()).contains("digest mismatch");
      var corrupt =
          new ManagedPacks.SignedPack(
              signed.keyId(),
              signed.manifest(),
              Base64.getEncoder().encodeToString(jar),
              signed.signature(),
              signed.metadata(),
              signed.metadataSignature());
      assertThatThrownBy(() -> f.packs.install(f.admin, corrupt)).hasMessageContaining("signature");

      // Altered metadata (claiming another publisher's name does not change the identity, and the
      // change breaks the metadata signature).
      var altered =
          new ManagedPacks.SignedPack(
              signed.keyId(),
              signed.manifest(),
              signed.jar(),
              signed.signature(),
              "{\"description\":\"Official Microsoft pack\"}",
              signed.metadataSignature());
      assertThatThrownBy(() -> f.packs.install(f.admin, altered))
          .hasMessageContaining("Invalid package metadata signature");
      var unsignedMetadata =
          new ManagedPacks.SignedPack(
              signed.keyId(),
              signed.manifest(),
              signed.jar(),
              signed.signature(),
              signed.metadata(),
              null);
      assertThatThrownBy(() -> f.packs.install(f.admin, unsignedMetadata))
          .hasMessageContaining("must be signed");
      // Metadata moved onto another package is refused: it is bound to the digest.
      var other =
          f.artifact(
              "other",
              "1.0.0",
              DOMAIN.replace("shipped", "other"),
              PackManifest.Kind.DOMAIN,
              List.of());
      var moved =
          new ManagedPacks.SignedPack(
              other.keyId(),
              other.manifest(),
              other.jar(),
              other.signature(),
              signed.metadata(),
              signed.metadataSignature());
      assertThatThrownBy(() -> f.packs.install(f.admin, moved))
          .hasMessageContaining("Invalid package metadata signature");

      // An untrusted publisher: a key the site does not trust.
      var stranger = Path.of(dir.toString(), "stranger");
      Files.createDirectories(stranger);
      assertThat(
              tool(
                      "keygen",
                      stranger.resolve("s.key").toString(),
                      stranger.resolve("s.pub").toString())
                  .status())
          .isZero();
      Files.writeString(
          dir.resolve("strange.json"), built.manifest().replace("\"tests\"", "\"stranger\""));
      assertThat(
              tool(
                      "pack",
                      dir.resolve("strange.json").toString(),
                      dir.resolve("pack.jar").toString(),
                      "stranger",
                      stranger.resolve("s.key").toString(),
                      dir.resolve("s.qbpack").toString())
                  .status())
          .isZero();
      var untrusted =
          mapper.readValue(
              tool("request", dir.resolve("s.qbpack").toString()).out(),
              ManagedPacks.SignedPack.class);
      assertThatThrownBy(() -> f.packs.install(f.admin, untrusted))
          .hasMessageContaining("Untrusted signing key");
      // The tool refuses to sign a manifest that names another publisher.
      assertThat(
              tool(
                      "pack",
                      dir.resolve("manifest.json").toString(),
                      dir.resolve("pack.jar").toString(),
                      "stranger",
                      stranger.resolve("s.key").toString(),
                      dir.resolve("x.qbpack").toString())
                  .err())
          .contains("the manifest publisher must equal the key id");

      // Revocation: install the good package, then revoke its digest and its publisher key.
      f.packs.install(f.admin, signed);
      f.packs.activate(f.admin, "shipped", "1.0.0", Map.of());
      String digest =
          f.packs.list(f.admin.tenant()).stream()
              .filter(v -> v.manifest().id().equals("shipped"))
              .findFirst()
              .orElseThrow()
              .digest();
      var secrets = new PackSecrets(Optional.of("PATH"));
      var digestRevoked =
          new ManagedPacks(
              f.store,
              f.mapper,
              f.licenses,
              secrets,
              f.trust,
              "1.0.0",
              Optional.empty(),
              Optional.of(List.of(digest)));
      var keyRevoked =
          new ManagedPacks(
              f.store,
              f.mapper,
              f.licenses,
              secrets,
              f.trust,
              "1.0.0",
              Optional.of(List.of("tests")),
              Optional.empty());
      try {
        for (var revoked : List.of(digestRevoked, keyRevoked)) {
          assertThatThrownBy(() -> revoked.install(f.admin, signed))
              .hasMessageContaining("revoked");
          // Already installed: still listed (nothing is deleted) but it can no longer be used.
          assertThat(revoked.list(f.admin.tenant()))
              .anyMatch(v -> v.manifest().id().equals("shipped"));
          assertThatThrownBy(() -> revoked.withPack(f.admin.tenant(), "shipped", p -> p))
              .hasRootCauseMessage(
                  revoked == digestRevoked
                      ? "Package " + digest + " is revoked"
                      : "Publisher key tests is revoked");
          // Bundled packs are not affected by publisher revocation.
          String bundled = revoked.withPack(f.admin.tenant(), "library", p -> p.manifest().id());
          assertThat(bundled).isEqualTo("library");
        }
      } finally {
        digestRevoked.close();
        keyRevoked.close();
      }
    }
  }
}
