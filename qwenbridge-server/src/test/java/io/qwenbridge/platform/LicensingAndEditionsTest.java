package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.security.ApiIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-261 – QB-273: signed offline licenses with typed validation, capability-based entitlements
 * checked centrally, pack-level licensing decided by the server, expiry and grace without deleting
 * state, and edition changes that suspend and resume packs predictably.
 */
class LicensingAndEditionsTest {

  final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  static String sign(KeyPair keys, byte[] bytes) throws Exception {
    var s = Signature.getInstance("Ed25519");
    s.initSign(keys.getPrivate());
    s.update(bytes);
    return Base64.getEncoder().encodeToString(s.sign());
  }

  Entitlements.SignedLicense license(KeyPair keys, String keyId, Object license) throws Exception {
    byte[] bytes = mapper.writeValueAsBytes(license);
    return new Entitlements.SignedLicense(
        keyId, Base64.getEncoder().encodeToString(bytes), sign(keys, bytes));
  }

  static Entitlements.License doc(
      int version,
      String issuer,
      String tenant,
      Entitlements.Edition edition,
      Instant notBefore,
      Instant expires) {
    return new Entitlements.License(
        version,
        "lic-" + edition,
        issuer,
        tenant,
        edition,
        notBefore,
        expires,
        Entitlements.MATRIX.get(edition));
  }

  static String trust(Map<String, KeyPair> keys) {
    var map = new java.util.TreeMap<String, String>();
    keys.forEach(
        (id, k) -> map.put(id, Base64.getEncoder().encodeToString(k.getPublic().getEncoded())));
    try {
      return new ObjectMapper().writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static final ApiIdentity.Principal ADMIN =
      new ApiIdentity.Principal(
          "ops", "tenant-a", Set.of("license:write", "search", "packs:write"));

  static Clock at(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  @Test
  void licensesAreTypedVerifiedOfflineAndRefusedForEveryKindOfTampering() throws Exception {
    var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    var stranger = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    var store = new PlatformStore(null, mapper, Optional.of(false));
    var engine = new Entitlements(store, mapper, trust(Map.of("issuer-1", keys)));
    var now = Instant.now();
    var valid =
        license(
            keys,
            "issuer-1",
            doc(
                1,
                "issuer-1",
                "tenant-a",
                Entitlements.Edition.PROFESSIONAL,
                now.minusSeconds(60),
                now.plusSeconds(3600)));
    record Case(String name, Entitlements.SignedLicense envelope, Entitlements.Problem problem) {}
    byte[] payload = Base64.getDecoder().decode(valid.payload());
    String modified =
        new String(payload, java.nio.charset.StandardCharsets.UTF_8)
            .replace("PROFESSIONAL", "ENTERPRISE");
    var cases =
        List.of(
            new Case(
                "modified payload",
                new Entitlements.SignedLicense(
                    "issuer-1",
                    Base64.getEncoder()
                        .encodeToString(modified.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    valid.signature()),
                Entitlements.Problem.BAD_SIGNATURE),
            new Case(
                "invalid signature",
                new Entitlements.SignedLicense(
                    "issuer-1", valid.payload(), Base64.getEncoder().encodeToString(new byte[64])),
                Entitlements.Problem.BAD_SIGNATURE),
            new Case(
                "signed by an untrusted key",
                license(
                    stranger,
                    "issuer-1",
                    doc(
                        1,
                        "issuer-1",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(60),
                        now.plusSeconds(3600))),
                Entitlements.Problem.BAD_SIGNATURE),
            new Case(
                "unknown key id",
                license(
                    stranger,
                    "issuer-9",
                    doc(
                        1,
                        "issuer-9",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(60),
                        now.plusSeconds(3600))),
                Entitlements.Problem.UNKNOWN_KEY),
            new Case(
                "unsupported version",
                license(
                    keys,
                    "issuer-1",
                    doc(
                        2,
                        "issuer-1",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(60),
                        now.plusSeconds(3600))),
                Entitlements.Problem.UNSUPPORTED_VERSION),
            new Case(
                "another tenant",
                license(
                    keys,
                    "issuer-1",
                    doc(
                        1,
                        "issuer-1",
                        "tenant-b",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(60),
                        now.plusSeconds(3600))),
                Entitlements.Problem.WRONG_TENANT),
            new Case(
                "issuer mismatch",
                license(
                    keys,
                    "issuer-1",
                    doc(
                        1,
                        "someone-else",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(60),
                        now.plusSeconds(3600))),
                Entitlements.Problem.ISSUER_MISMATCH),
            new Case(
                "features beyond the edition",
                license(
                    keys,
                    "issuer-1",
                    new Entitlements.License(
                        1,
                        "x",
                        "issuer-1",
                        "tenant-a",
                        Entitlements.Edition.COMMUNITY,
                        now.minusSeconds(60),
                        now.plusSeconds(3600),
                        Set.of("search", "tenant-policy"))),
                Entitlements.Problem.FEATURES_EXCEED_EDITION),
            new Case(
                "not valid yet",
                license(
                    keys,
                    "issuer-1",
                    doc(
                        1,
                        "issuer-1",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.plusSeconds(3600),
                        now.plusSeconds(7200))),
                Entitlements.Problem.NOT_YET_VALID),
            new Case(
                "expired",
                license(
                    keys,
                    "issuer-1",
                    doc(
                        1,
                        "issuer-1",
                        "tenant-a",
                        Entitlements.Edition.ENTERPRISE,
                        now.minusSeconds(7200),
                        now.minusSeconds(1))),
                Entitlements.Problem.EXPIRED),
            new Case(
                "missing fields",
                license(
                    keys,
                    "issuer-1",
                    Map.of("version", 1, "issuer", "issuer-1", "tenant", "tenant-a")),
                Entitlements.Problem.INCOMPLETE),
            new Case(
                "not a license",
                new Entitlements.SignedLicense("issuer-1", "%%%", valid.signature()),
                Entitlements.Problem.MALFORMED));
    for (var c : cases)
      assertThatThrownBy(() -> engine.install(ADMIN, c.envelope()))
          .as(c.name())
          .isInstanceOfSatisfying(
              Entitlements.LicenseException.class,
              e -> assertThat(e.problem()).isEqualTo(c.problem()))
          .hasMessageNotContaining("tenant-b")
          .hasMessageNotContaining("ENTERPRISE");
    assertThat(engine.status("tenant-a").state()).isEqualTo(Entitlements.State.NONE);
    assertThat(engine.status("tenant-a").edition()).isEqualTo(Entitlements.Edition.COMMUNITY);
    var installed = engine.install(ADMIN, valid);
    assertThat(installed.edition()).isEqualTo(Entitlements.Edition.PROFESSIONAL);
    assertThat(installed.state()).isEqualTo(Entitlements.State.VALID);
    // A licence alone is not a permission: installing one needs license:write.
    var reader = new ApiIdentity.Principal("ray", "tenant-a", Set.of("search"));
    assertThatThrownBy(() -> engine.install(reader, valid))
        .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
  }

  @Test
  void onlyPublicKeysAreTrustedAndKeysRotateByOverlap() throws Exception {
    var oldKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    var newKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String privateKey = Base64.getEncoder().encodeToString(oldKey.getPrivate().getEncoded());
    assertThatThrownBy(
            () ->
                new Entitlements(
                    new PlatformStore(null, mapper, Optional.of(false)),
                    mapper,
                    "{\"leaked\":\"" + privateKey + "\"}"))
        .hasMessageContaining("key leaked is not an Ed25519 public key")
        .hasMessageNotContaining(privateKey);
    var now = Instant.now();
    var store = new PlatformStore(null, mapper, Optional.of(false));
    var both = new Entitlements(store, mapper, trust(Map.of("2025", oldKey, "2026", newKey)));
    both.install(
        ADMIN,
        license(
            oldKey,
            "2025",
            doc(
                1,
                "2025",
                "tenant-a",
                Entitlements.Edition.ENTERPRISE,
                now.minusSeconds(60),
                now.plusSeconds(3600))));
    assertThat(both.status("tenant-a").edition()).isEqualTo(Entitlements.Edition.ENTERPRISE);
    // After the old key is retired, its licences stop granting; the new key's still do.
    var rotated = new Entitlements(store, mapper, trust(Map.of("2026", newKey)));
    assertThat(rotated.status("tenant-a").problem()).isEqualTo(Entitlements.Problem.UNKNOWN_KEY);
    assertThat(rotated.status("tenant-a").edition()).isEqualTo(Entitlements.Edition.COMMUNITY);
    rotated.install(
        ADMIN,
        license(
            newKey,
            "2026",
            doc(
                1,
                "2026",
                "tenant-a",
                Entitlements.Edition.ENTERPRISE,
                now.minusSeconds(60),
                now.plusSeconds(3600))));
    assertThat(rotated.status("tenant-a").state()).isEqualTo(Entitlements.State.VALID);
  }

  @Test
  void expiryAndGraceAreExplicitAndNeverDestructive() throws Exception {
    var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    var store = new PlatformStore(null, mapper, Optional.of(false));
    var issued = Instant.parse("2026-01-01T00:00:00Z");
    var expires = Instant.parse("2026-12-31T00:00:00Z");
    var envelope =
        license(
            keys, "k", doc(1, "k", "tenant-a", Entitlements.Edition.ENTERPRISE, issued, expires));
    String trust = trust(Map.of("k", keys));
    new Entitlements(store, mapper, trust, Duration.ofDays(7), at(issued.plusSeconds(10)))
        .install(ADMIN, envelope);
    var beforeExpiry =
        new Entitlements(store, mapper, trust, Duration.ofDays(7), at(expires.minusSeconds(1)));
    assertThat(beforeExpiry.status("tenant-a").state()).isEqualTo(Entitlements.State.VALID);
    var inGrace =
        new Entitlements(
            store, mapper, trust, Duration.ofDays(7), at(expires.plus(Duration.ofDays(3))));
    assertThat(inGrace.status("tenant-a").state()).isEqualTo(Entitlements.State.GRACE);
    assertThat(inGrace.status("tenant-a").problem()).isEqualTo(Entitlements.Problem.EXPIRED);
    inGrace.require("tenant-a", "tenant-policy");
    var afterGrace =
        new Entitlements(
            store, mapper, trust, Duration.ofDays(7), at(expires.plus(Duration.ofDays(8))));
    assertThat(afterGrace.status("tenant-a").state()).isEqualTo(Entitlements.State.INVALID);
    assertThat(afterGrace.status("tenant-a").edition()).isEqualTo(Entitlements.Edition.COMMUNITY);
    assertThatThrownBy(() -> afterGrace.require("tenant-a", "tenant-policy"))
        .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    // Without a grace period, expiry is immediate. Either way the stored licence is kept.
    var strict = new Entitlements(store, mapper, trust, Duration.ZERO, at(expires.plusSeconds(1)));
    assertThat(strict.status("tenant-a").state()).isEqualTo(Entitlements.State.INVALID);
    assertThat(store.read("tenant-a", "license", "active", Entitlements.SignedLicense.class))
        .isEqualTo(envelope);
    // A licence that is already expired cannot be installed, even within a grace period.
    assertThatThrownBy(() -> inGrace.install(ADMIN, envelope)).hasMessageContaining("expired");
    assertThatThrownBy(
            () -> new Entitlements(store, mapper, trust, Duration.ofDays(365), Clock.systemUTC()))
        .hasMessageContaining("grace-period");
  }

  static final String POLICY =
      "public int order(){return 1;} public Set<String> domains(){return Set.of(\"*\");} public"
          + " Decision evaluate(Input input){return new"
          + " Decision(Effect.ABSTAIN,\"r\",\"Nothing\");}";
  static final String DOMAIN =
      "public Definition definition(){return new Definition(\"extra\",1,Map.of());} public"
          + " Map<String,Object> normalize(String id,Map<String,Object> raw){return Map.of();}"
          + " public Understanding understand(String q){return new"
          + " Understanding(\"extra\",q,List.of(),List.of());}";

  @Test
  void editionsGatePacksCentrallyAndDowngradesSuspendWithoutDeleting() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      // Community: bundled packs work; external packs and tenant policy do not.
      assertThat(f.packs.suspended(f.admin.tenant())).isEmpty();
      var domain = f.artifact("extra", "1.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      assertThatThrownBy(() -> f.packs.install(f.admin, domain))
          .hasMessageContaining("Feature requires an entitled license: external-packs");
      // A pack cannot buy its way in through manifest metadata: a manifest naming the bundled
      // Commerce pack, in a new version, is still an external pack.
      var fakeCommerce =
          f.artifact(
              "commerce",
              "9.0.0",
              DOMAIN.replace("extra", "commerce-x"),
              PackManifest.Kind.DOMAIN,
              List.of());
      assertThatThrownBy(() -> f.packs.install(f.admin, fakeCommerce))
          .hasMessageContaining("external-packs");

      // Professional: external packs, but no tenant policy.
      f.licenses.install(
          f.admin,
          f.license(
              f.admin.tenant(),
              Entitlements.Edition.PROFESSIONAL,
              Instant.now().plusSeconds(3600)));
      f.packs.install(f.admin, domain);
      f.packs.activate(f.admin, "extra", "1.0.0", Map.of());
      var policy = f.artifact("guard", "1.0.0", POLICY, PackManifest.Kind.POLICY, List.of());
      f.packs.install(f.admin, policy);
      assertThatThrownBy(() -> f.packs.activate(f.admin, "guard", "1.0.0", Map.of()))
          .hasMessageContaining("tenant-policy");

      // Enterprise: tenant policy, still only with the caller's permissions.
      f.enterprise();
      f.packs.activate(f.admin, "guard", "1.0.0", Map.of());
      var noPermission = new ApiIdentity.Principal("viewer", f.admin.tenant(), Set.of("search"));
      assertThatThrownBy(() -> f.packs.disable(noPermission, "guard"))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
      var market = new Marketplace(f.store, f.packs, f.licenses);
      var noPublish =
          new ApiIdentity.Principal(
              "viewer", f.admin.tenant(), Set.of("search", "publisher:tests"));
      assertThatThrownBy(() -> market.publish(noPublish, domain))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);

      // Downgrade to Community: both packs are suspended, kept and still configured.
      f.licenses.downgrade(f.admin);
      assertThat(f.packs.suspended(f.admin.tenant())).containsExactly("extra", "guard");
      assertThat(f.packs.state(f.admin.tenant()).enabled()).contains("extra", "guard");
      assertThatThrownBy(() -> f.packs.withPack(f.admin.tenant(), "extra", p -> p))
          .hasMessageContaining("external-packs");
      assertThat(f.packs.list(f.admin.tenant()))
          .extracting(v -> v.manifest().id())
          .contains("extra", "guard");
      // Bundled packs keep working in Community.
      String servedLibrary = f.packs.withPack(f.admin.tenant(), "library", p -> p.manifest().id());
      assertThat(servedLibrary).isEqualTo("library");

      // Upgrade again: the same packs resume with no reinstallation.
      f.enterprise();
      assertThat(f.packs.suspended(f.admin.tenant())).isEmpty();
      String servedExtra = f.packs.withPack(f.admin.tenant(), "extra", p -> p.manifest().id());
      assertThat(servedExtra).isEqualTo("extra");
    }
  }

  @Test
  void everyEditionFeatureHasAServerSideRule() throws Exception {
    var matrix = Entitlements.MATRIX;
    assertThat(matrix.get(Entitlements.Edition.PROFESSIONAL))
        .containsAll(matrix.get(Entitlements.Edition.COMMUNITY));
    assertThat(matrix.get(Entitlements.Edition.ENTERPRISE))
        .containsAll(matrix.get(Entitlements.Edition.PROFESSIONAL));
    Path main = Path.of("src", "main", "java");
    String sources;
    try (Stream<Path> files = Files.walk(main)) {
      var all = new StringBuilder();
      for (Path p : files.filter(p -> p.toString().endsWith(".java")).toList())
        if (!p.getFileName().toString().equals("Entitlements.java"))
          all.append(Files.readString(p));
      sources = all.toString();
    }
    for (String feature : matrix.get(Entitlements.Edition.ENTERPRISE)) {
      if (Entitlements.UNGATED.contains(feature)) continue;
      assertThat(sources).as("a gate for " + feature).contains("\"" + feature + "\"");
    }
    // Pack gates are decided by the server, not by manifests.
    assertThat(sources).doesNotContain("manifest().capabilities().contains(\"external-packs\")");
    // QB-267: licence validation is offline; it has no network client.
    for (String file : List.of("Entitlements.java", "SignedDocuments.java")) {
      String text = Files.readString(main.resolve("io/qwenbridge/platform").resolve(file));
      assertThat(text)
          .as(file)
          .doesNotContain("java.net.http", "HttpClient", "URLConnection", "Socket");
    }
  }
}
