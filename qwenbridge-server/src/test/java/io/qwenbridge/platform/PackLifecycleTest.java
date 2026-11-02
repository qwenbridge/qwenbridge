package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qwenbridge.pack.PackManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QB-254 – QB-260: the local pack registry works offline on a file store with no database, Redis or
 * remote marketplace; installation is separate from activation and refuses invalid, corrupted,
 * untrusted or incompatible packages before activation; lifecycle transitions validate dependencies
 * and capability conflicts first; rollback restores a validated earlier version and reports what it
 * cannot undo.
 */
class PackLifecycleTest {

  static String domain(String domainId, String marker) {
    return "public Definition definition(){return new Definition(\""
        + domainId
        + "\",1,Map.of());} public Map<String,Object> normalize(String id,Map<String,Object>"
        + " raw){return Map.of(\"marker\",\""
        + marker
        + "\");} public Understanding understand(String q){return new Understanding(\""
        + domainId
        + "\",q,List.of(),List.of());}";
  }

  static ManagedPacks.SignedPack resigned(
      PackAcceptanceTest.Fixture f,
      ManagedPacks.SignedPack original,
      String manifestJson,
      byte[] jar)
      throws Exception {
    return new ManagedPacks.SignedPack(
        original.keyId(),
        manifestJson,
        Base64.getEncoder().encodeToString(jar),
        f.sign(SignedDocuments.packBytes(manifestJson, jar)));
  }

  static String marker(PackAcceptanceTest.Fixture f, String id) {
    return f.packs.withPack(
        f.admin.tenant(),
        id,
        p ->
            String.valueOf(
                ((io.qwenbridge.pack.DomainPack) p).normalize("x", Map.of()).get("marker")));
  }

  @Test
  void theRegistryPersistsOfflineAcrossRestartsWithoutAnyDatabase(@TempDir Path dir)
      throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    var store = PlatformStore.file(mapper, dir);
    assertThat(store.adapter()).isEqualTo("file");
    String digest;
    String trust;
    try (var f = new PackAcceptanceTest.Fixture(store)) {
      trust = f.trust;
      f.enterprise();
      var artifact =
          f.artifact(
              "example", "1.0.0", domain("example", "one"), PackManifest.Kind.DOMAIN, List.of());
      var installed = f.packs.install(f.admin, artifact);
      digest = installed.digest();
      assertThat(installed.active()).isFalse();
      f.packs.activate(f.admin, "example", "1.0.0", Map.of());
    }
    assertThat(Files.list(dir).map(p -> p.getFileName().toString()).toList())
        .anyMatch(n -> n.contains("packs~registry"));
    // A new process: new store, new registry, same directory.
    var reopened = PlatformStore.file(mapper, dir);
    var licenses = new Entitlements(reopened, mapper, trust);
    var packs =
        new ManagedPacks(
            reopened, mapper, licenses, new PackSecrets(java.util.Optional.of("PATH")), trust);
    try {
      var example =
          packs.list("tenant-a").stream()
              .filter(v -> v.manifest().id().equals("example"))
              .findFirst()
              .orElseThrow();
      assertThat(example.active()).isTrue();
      assertThat(example.digest()).isEqualTo(digest);
      assertThat(example.manifest().kind()).isEqualTo(PackManifest.Kind.DOMAIN);
      assertThat(example.manifest().version()).isEqualTo("1.0.0");
      assertThat(licenses.status("tenant-a").edition()).isEqualTo(Entitlements.Edition.ENTERPRISE);
      // The stored, signed artifact is verified and loaded again after the restart.
      Object marker =
          packs.withPack(
              "tenant-a",
              "example",
              p -> ((io.qwenbridge.pack.DomainPack) p).normalize("x", Map.of()).get("marker"));
      assertThat(marker).isEqualTo("one");
    } finally {
      packs.close();
    }
  }

  @Test
  void invalidCorruptedUntrustedOrIncompatiblePackagesAreRefusedBeforeActivation()
      throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      var good =
          f.artifact(
              "example", "1.0.0", domain("example", "one"), PackManifest.Kind.DOMAIN, List.of());
      byte[] jar = Base64.getDecoder().decode(good.jar());
      // Corrupted: one byte of the signed JAR changed.
      byte[] flipped = jar.clone();
      flipped[flipped.length / 2] ^= 0x5A;
      var corrupted =
          new ManagedPacks.SignedPack(
              good.keyId(),
              good.manifest(),
              Base64.getEncoder().encodeToString(flipped),
              good.signature());
      assertThatThrownBy(() -> f.packs.install(f.admin, corrupted))
          .hasMessageContaining("signature");
      // Untrusted: an unknown publisher key.
      var untrusted =
          new ManagedPacks.SignedPack("stranger", good.manifest(), good.jar(), good.signature());
      assertThatThrownBy(() -> f.packs.install(f.admin, untrusted))
          .isInstanceOf(RuntimeException.class);
      // Signed, but not a pack JAR.
      var notAJar =
          resigned(
              f,
              good,
              good.manifest(),
              "not a jar".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      assertThatThrownBy(() -> f.packs.install(f.admin, notAJar))
          .hasMessageContaining("Pack cannot be loaded");
      // Signed, but for an unsupported pack API version.
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var future = (ObjectNode) mapper.readTree(good.manifest());
      future.put("apiVersion", 2);
      var api2 = resigned(f, good, mapper.writeValueAsString(future), jar);
      assertThatThrownBy(() -> f.packs.install(f.admin, api2))
          .hasMessageContaining("Invalid pack artifact");
      // Signed, but for a runtime this server is not: refused from metadata, no code runs.
      var later = (ObjectNode) mapper.readTree(good.manifest());
      later.putObject("runtime").put("minimum", "99.0.0");
      var newRuntime = resigned(f, good, mapper.writeValueAsString(later), jar);
      assertThatThrownBy(() -> f.packs.inspect(newRuntime))
          .hasMessageContaining("Pack requires runtime [99.0.0, any)");
      assertThatThrownBy(() -> f.packs.install(f.admin, newRuntime))
          .hasMessageContaining("Pack requires runtime");
      // Metadata inspection never runs pack code; installation verifies the code without starting
      // it.
      var trap =
          f.artifact(
              "trap",
              "1.0.0",
              "static { if (Boolean.parseBoolean(\"true\")) throw new IllegalStateException(\"pack"
                  + " code ran\"); } "
                  + domain("trap", "x"),
              PackManifest.Kind.DOMAIN,
              List.of());
      assertThat(f.packs.inspect(trap).manifest().id()).isEqualTo("trap");
      assertThatThrownBy(() -> f.packs.install(f.admin, trap))
          .hasMessageContaining("Pack cannot be loaded");
      // Nothing of the above was registered.
      assertThat(f.packs.list(f.admin.tenant()))
          .extracting(v -> v.manifest().id())
          .containsExactlyInAnyOrder("commerce", "library");
      // Duplicate id and version with different content: installed versions are immutable.
      f.packs.install(f.admin, good);
      var different =
          f.artifact(
              "example", "1.0.0", domain("example", "two"), PackManifest.Kind.DOMAIN, List.of());
      assertThatThrownBy(() -> f.packs.install(f.admin, different))
          .hasMessageContaining("immutable");
      f.packs.install(f.admin, good); // the same artifact again is accepted (repeatable)
    }
  }

  @Test
  void dependenciesAndCapabilityConflictsAreCheckedBeforeTheStateChanges() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      var dependsOnLibrary = List.of(new PackManifest.Dependency("library", "1.0.0", null));
      f.packs.install(
          f.admin,
          f.artifact(
              "addon", "1.0.0", domain("addon", "a"), PackManifest.Kind.DOMAIN, dependsOnLibrary));
      var missing = List.of(new PackManifest.Dependency("absent", "1.0.0", null));
      f.packs.install(
          f.admin,
          f.artifact("orphan", "1.0.0", domain("orphan", "o"), PackManifest.Kind.DOMAIN, missing));
      f.packs.install(
          f.admin,
          f.artifact(
              "ring-a",
              "1.0.0",
              domain("ring-a", "a"),
              PackManifest.Kind.DOMAIN,
              List.of(new PackManifest.Dependency("ring-b", "1.0.0", null))));
      f.packs.install(
          f.admin,
          f.artifact(
              "ring-b",
              "1.0.0",
              domain("ring-b", "b"),
              PackManifest.Kind.DOMAIN,
              List.of(new PackManifest.Dependency("ring-a", "1.0.0", null))));
      var before = f.packs.state(f.admin.tenant()).revision();

      assertThatThrownBy(() -> f.packs.activate(f.admin, "orphan", "1.0.0", Map.of()))
          .hasMessageContaining("Unsatisfied dependency: orphan requires absent");
      assertThatThrownBy(() -> f.packs.activate(f.admin, "ring-a", "1.0.0", Map.of()))
          .hasMessageContaining("Unsatisfied dependency");
      assertThatThrownBy(() -> f.packs.activate(f.admin, "ring-b", "1.0.0", Map.of()))
          .hasMessageContaining("Unsatisfied dependency");
      assertThat(f.packs.state(f.admin.tenant()).revision()).isEqualTo(before);

      f.packs.activate(f.admin, "addon", "1.0.0", Map.of());
      f.packs.activate(f.admin, "addon", "1.0.0", Map.of()); // repeatable
      // A pack others depend on cannot be disabled from under them.
      assertThatThrownBy(() -> f.packs.disable(f.admin, "library"))
          .hasMessageContaining("Unsatisfied dependency: addon requires library");

      // Two Domain Packs may not provide the same domain.
      f.packs.install(
          f.admin,
          f.artifact(
              "library-fork",
              "1.0.0",
              domain("library", "fork"),
              PackManifest.Kind.DOMAIN,
              List.of()));
      assertThatThrownBy(() -> f.packs.activate(f.admin, "library-fork", "1.0.0", Map.of()))
          .hasMessageContaining(
              "Conflicting capability: domain library is already provided by pack library");
      assertThat(f.packs.state(f.admin.tenant()).enabled()).doesNotContain("library-fork");
      // Unrelated packs were not disturbed by any refusal.
      assertThat(f.packs.list(f.admin.tenant()))
          .filteredOn(v -> List.of("commerce", "library", "addon").contains(v.manifest().id()))
          .allMatch(ManagedPacks.View::active);
      assertThat(marker(f, "addon")).isEqualTo("a");
    }
  }

  @Test
  void upgradeAndRollbackRestoreAValidatedVersionAndSayWhatTheyCannotUndo() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      // Version 1.0.0 needs the Library pack; 1.1.0 does not.
      var needsLibrary = List.of(new PackManifest.Dependency("library", "1.0.0", null));
      f.packs.install(
          f.admin,
          f.artifact(
              "example",
              "1.0.0",
              domain("example", "one"),
              PackManifest.Kind.DOMAIN,
              needsLibrary));
      f.packs.activate(f.admin, "example", "1.0.0", Map.of());
      assertThatThrownBy(() -> f.packs.rollback(f.admin, "example"))
          .hasMessageContaining("Rollback of example is not supported: no previous pack version");
      f.packs.install(
          f.admin,
          f.artifact(
              "example", "1.1.0", domain("example", "two"), PackManifest.Kind.DOMAIN, List.of()));
      f.packs.activate(f.admin, "example", "1.1.0", Map.of());
      assertThat(marker(f, "example")).isEqualTo("two");
      var rollback = f.packs.rollback(f.admin, "example");
      assertThat(rollback.from()).isEqualTo("1.1.0");
      assertThat(rollback.to()).isEqualTo("1.0.0");
      assertThat(rollback.configurationRestored()).isTrue();
      assertThat(rollback.notReverted()).isNotEmpty();
      assertThat(marker(f, "example")).isEqualTo("one");
      assertThat(f.packs.list(f.admin.tenant()))
          .filteredOn(v -> v.manifest().id().equals("example"))
          .extracting(v -> v.manifest().version() + ":" + v.active())
          .containsExactlyInAnyOrder("1.0.0:true", "1.1.0:false");
      // Forward again, then remove what 1.0.0 needs: rolling back is refused with the reason.
      f.packs.activate(f.admin, "example", "1.1.0", Map.of());
      f.packs.disable(f.admin, "library");
      var revision = f.packs.state(f.admin.tenant()).revision();
      assertThatThrownBy(() -> f.packs.rollback(f.admin, "example"))
          .hasMessageContaining("Rollback of example to 1.0.0 is not supported")
          .hasMessageContaining("Unsatisfied dependency: example requires library");
      assertThat(f.packs.state(f.admin.tenant()).revision()).isEqualTo(revision);
      assertThat(marker(f, "example")).isEqualTo("two");
    }
  }

  /** Section 4 Scenario K: a pack activated by one tenant is not usable by another. */
  @Test
  void packsAreTenantScoped() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      f.packs.install(
          f.admin,
          f.artifact(
              "example", "1.0.0", domain("example", "one"), PackManifest.Kind.DOMAIN, List.of()));
      f.packs.activate(f.admin, "example", "1.0.0", Map.of());
      assertThat(marker(f, "example")).isEqualTo("one");
      var other =
          new io.qwenbridge.security.ApiIdentity.Principal(
              "bea", "tenant-b", java.util.Set.of("packs:write", "packs:read", "license:write"));
      assertThat(f.packs.list("tenant-b"))
          .extracting(v -> v.manifest().id())
          .containsExactlyInAnyOrder("commerce", "library");
      assertThatThrownBy(() -> f.packs.withPack("tenant-b", "example", p -> p))
          .hasMessageContaining("Pack is disabled or unavailable: example");
      assertThatThrownBy(() -> f.packs.activate(other, "example", "1.0.0", Map.of()))
          .hasMessageContaining("not installed");
      // Tenant A's view is unchanged by tenant B's attempts.
      assertThat(marker(f, "example")).isEqualTo("one");
    }
  }
}
