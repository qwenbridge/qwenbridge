package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.pack.*;
import io.qwenbridge.security.ApiIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;

public class PackAcceptanceTest {
  /** Signed-pack fixture; also used by the central-policy test in the security package. */
  public static class Fixture implements AutoCloseable {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final KeyPair keys;
    final PlatformStore store;
    final Entitlements licenses;
    public final ManagedPacks packs;
    public final ApiIdentity.Principal admin =
        new ApiIdentity.Principal(
            "operator",
            "tenant-a",
            Set.of(
                "search",
                "license:write",
                "packs:write",
                "publisher:tests",
                "marketplace:publish"));

    final String trust;

    public Fixture() throws Exception {
      this(null);
    }

    /** A fixture on the given store (QB-259: e.g. a file store); memory when null. */
    public Fixture(PlatformStore given) throws Exception {
      keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      trust =
          mapper.writeValueAsString(
              Map.of("tests", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())));
      store = given == null ? new PlatformStore(null, mapper, Optional.of(false)) : given;
      licenses = new Entitlements(store, mapper, trust);
      packs =
          new ManagedPacks(store, mapper, licenses, new PackSecrets(Optional.of("PATH")), trust);
    }

    String sign(byte[] bytes) throws Exception {
      var s = Signature.getInstance("Ed25519");
      s.initSign(keys.getPrivate());
      s.update(bytes);
      return Base64.getEncoder().encodeToString(s.sign());
    }

    Entitlements.SignedLicense license(String tenant, Entitlements.Edition edition, Instant expires)
        throws Exception {
      var license =
          new Entitlements.License(
              1,
              "license-1",
              "tests",
              tenant,
              edition,
              Instant.now().minusSeconds(60),
              expires,
              Entitlements.MATRIX.get(edition));
      byte[] bytes = mapper.writeValueAsBytes(license);
      return new Entitlements.SignedLicense(
          "tests", Base64.getEncoder().encodeToString(bytes), sign(bytes));
    }

    public void enterprise() throws Exception {
      licenses.install(
          admin,
          license(
              admin.tenant(), Entitlements.Edition.ENTERPRISE, Instant.now().plusSeconds(3600)));
    }

    public ManagedPacks.SignedPack artifact(
        String id,
        String version,
        String body,
        PackManifest.Kind kind,
        List<PackManifest.Dependency> dependencies)
        throws Exception {
      return artifact(id, version, body, kind, dependencies, Map.of());
    }

    ManagedPacks.SignedPack artifact(
        String id,
        String version,
        String body,
        PackManifest.Kind kind,
        List<PackManifest.Dependency> dependencies,
        Map<String, PackManifest.ConfigField> configuration)
        throws Exception {
      PackManifest manifest =
          new PackManifest(
              id, version, 1, kind, "tests", dependencies, Set.of("test"), configuration);
      Path root = Files.createTempDirectory("pack-acceptance-");
      Path source = root.resolve("ExternalPack.java"), classes = root.resolve("classes");
      Files.createDirectories(classes);
      String type =
          switch (kind) {
            case POLICY -> "PolicyPack";
            case DOMAIN -> "DomainPack";
            case CONNECTOR -> "ConnectorPack";
          };
      String fields =
          "new PackManifest(\""
              + id
              + "\",\""
              + version
              + "\",1,PackManifest.Kind."
              + kind
              + ",\"tests\",List.of(),Set.of(\"test\"),Map.of())";
      if (!dependencies.isEmpty())
        fields =
            "new PackManifest(\""
                + id
                + "\",\""
                + version
                + "\",1,PackManifest.Kind."
                + kind
                + ",\"tests\",List.of(new PackManifest.Dependency(\""
                + dependencies.getFirst().id()
                + "\",\""
                + dependencies.getFirst().minimum()
                + "\",null)),Set.of(\"test\"),Map.of())";
      if (!configuration.isEmpty()) {
        var field = configuration.entrySet().iterator().next();
        fields =
            fields.replace(
                "Map.of())",
                "Map.of(\""
                    + field.getKey()
                    + "\",new PackManifest.ConfigField(PackManifest.ConfigType."
                    + field.getValue().type()
                    + ","
                    + field.getValue().required()
                    + ",\""
                    + field.getValue().description()
                    + "\")))");
      }
      String code =
          "import java.util.*;import io.qwenbridge.pack.*;public class ExternalPack implements "
              + type
              + " { public PackManifest manifest(){return "
              + fields
              + ";} "
              + body
              + "}";
      Files.writeString(source, code);
      String sdk =
          Path.of(QwenBridgePack.class.getProtectionDomain().getCodeSource().getLocation().toURI())
              .toString();
      assertThat(
              javax.tools.ToolProvider.getSystemJavaCompiler()
                  .run(
                      null,
                      null,
                      null,
                      "--release",
                      "25",
                      "-cp",
                      sdk,
                      "-d",
                      classes.toString(),
                      source.toString()))
          .isZero();
      Path jar = root.resolve("external.jar");
      try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
        out.putNextEntry(new JarEntry("ExternalPack.class"));
        out.write(Files.readAllBytes(classes.resolve("ExternalPack.class")));
        out.closeEntry();
        out.putNextEntry(new JarEntry("META-INF/services/io.qwenbridge.pack.QwenBridgePack"));
        out.write("ExternalPack\n".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
      byte[] bytes = Files.readAllBytes(jar);
      String json = mapper.writeValueAsString(manifest);
      try (var files = Files.walk(root)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
      }
      return new ManagedPacks.SignedPack(
          "tests",
          json,
          Base64.getEncoder().encodeToString(bytes),
          sign(SignedDocuments.packBytes(json, bytes)));
    }

    public void close() {
      packs.close();
    }
  }

  private static final String DOMAIN =
      "public Definition definition(){return new Definition(\"example\",1,Map.of());} public"
          + " Map<String,Object> normalize(String id,Map<String,Object> raw){return"
          + " Map.of(\"version\",manifest().version());} public Understanding understand(String"
          + " q){return new Understanding(\"example\",q,List.of(),List.of());}";

  @Test
  void connectorSecretReferencesAndLiveResourcesFollowLeasesAndVersionedRollback()
      throws Exception {
    try (var f = new Fixture()) {
      f.enterprise();
      String body =
          "private java.net.ServerSocket socket;public void start(Context c){char[]"
              + " secret=c.secrets().resolve(c.configuration().get(\"token\"));java.util.Arrays.fill(secret,'x');try{socket=new"
              + " java.net.ServerSocket(0);}catch(java.io.IOException e){throw new"
              + " IllegalStateException(e);}} public Set<String> modes(){return"
              + " Set.of(\"KEYWORD\");} public Page search(String d,String"
              + " t,List<DomainPack.Clause> cs,String m,String cursor,int limit){return new"
              + " Page(List.of(Map.of(\"port\",socket.getLocalPort(),\"open\",!socket.isClosed())),null);}public"
              + " void close(){try{if(socket!=null)socket.close();}catch(java.io.IOException"
              + " e){throw new IllegalStateException(e);}}";
      var config =
          Map.of(
              "token",
              new PackManifest.ConfigField(PackManifest.ConfigType.SECRET_REF, true, "Reference"));
      var first =
          f.artifact(
              "external-connector", "1.0.0", body, PackManifest.Kind.CONNECTOR, List.of(), config);
      f.packs.install(f.admin, first);
      assertThatThrownBy(
              () ->
                  f.packs.activate(
                      f.admin,
                      "external-connector",
                      "1.0.0",
                      Map.of("token", "plain-text-is-forbidden")))
          .hasMessageContaining("Secret reference");
      f.packs.activate(f.admin, "external-connector", "1.0.0", Map.of("token", "env:PATH"));
      var second =
          f.artifact(
              "external-connector", "2.0.0", body, PackManifest.Kind.CONNECTOR, List.of(), config);
      f.packs.install(f.admin, second);
      f.packs.activate(f.admin, "external-connector", "2.0.0", Map.of("token", "env:PATH"));
      f.packs.rollback(f.admin, "external-connector");
      assertThat(f.packs.state("tenant-a").configuration())
          .containsEntry("external-connector@1.0.0", Map.of("token", "env:PATH"));
      int port =
          f.packs.withPack(
              "tenant-a",
              "external-connector",
              p -> {
                var connector = (ConnectorPack) p;
                int number =
                    ((Number)
                            connector
                                .search("commerce", "x", List.of(), "KEYWORD", null, 1)
                                .documents()
                                .getFirst()
                                .get("port"))
                        .intValue();
                f.packs.disable(f.admin, "external-connector");
                assertThat(
                        connector
                            .search("commerce", "x", List.of(), "KEYWORD", null, 1)
                            .documents()
                            .getFirst())
                    .containsEntry("open", true);
                try (var socket = new java.net.Socket("127.0.0.1", number)) {
                  assertThat(socket.isConnected()).isTrue();
                } catch (java.io.IOException e) {
                  throw new AssertionError(e);
                }
                return number;
              });
      assertThatThrownBy(
              () -> {
                try (var socket = new java.net.Socket("127.0.0.1", port)) {}
              })
          .isInstanceOf(java.io.IOException.class);
    }
  }

  @Test
  void signedLicensesEnforceTenantExpirySignatureAndDowngrades() throws Exception {
    try (var f = new Fixture()) {
      assertThatThrownBy(() -> f.licenses.require("tenant-a", "external-packs"))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
      f.enterprise();
      assertThat(f.licenses.status("tenant-a").edition())
          .isEqualTo(Entitlements.Edition.ENTERPRISE);
      f.licenses.require("tenant-a", "external-packs");
      assertThatThrownBy(
              () ->
                  f.licenses.install(
                      f.admin,
                      f.license(
                          "tenant-b",
                          Entitlements.Edition.ENTERPRISE,
                          Instant.now().plusSeconds(30))))
          .hasMessageContaining("tenant");
      assertThatThrownBy(
              () ->
                  f.licenses.install(
                      f.admin,
                      f.license(
                          "tenant-a",
                          Entitlements.Edition.ENTERPRISE,
                          Instant.now().minusSeconds(1))))
          .hasMessageContaining("expired");
      var valid =
          f.license("tenant-a", Entitlements.Edition.ENTERPRISE, Instant.now().plusSeconds(30));
      assertThatThrownBy(
              () ->
                  f.licenses.install(
                      f.admin,
                      new Entitlements.SignedLicense(
                          "tests",
                          valid.payload(),
                          Base64.getEncoder().encodeToString(new byte[64]))))
          .hasMessageContaining("signature");
      f.licenses.downgrade(f.admin);
      assertThatThrownBy(() -> f.licenses.require("tenant-a", "external-packs"))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    }
  }

  @Test
  void independentlyCompiledSignedJarSupportsUpgradeRollbackDisableAndDependencyChecks()
      throws Exception {
    try (var f = new Fixture()) {
      f.enterprise();
      var first = f.artifact("example", "1.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      f.packs.install(f.admin, first);
      assertThatThrownBy(() -> f.packs.withPack("tenant-a", "example", p -> p.manifest()))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class);
      f.packs.activate(f.admin, "example", "1.0.0", Map.of());
      assertThat(f.packs.<String>withPack("tenant-a", "example", p -> p.manifest().version()))
          .isEqualTo("1.0.0");
      var second = f.artifact("example", "2.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      f.packs.install(f.admin, second);
      f.packs.activate(f.admin, "example", "2.0.0", Map.of());
      f.packs.rollback(f.admin, "example");
      assertThat(f.packs.<String>withPack("tenant-a", "example", p -> p.manifest().version()))
          .isEqualTo("1.0.0");
      f.packs.disable(f.admin, "example");
      assertThatThrownBy(() -> f.packs.withPack("tenant-a", "example", p -> p.manifest()))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class);
      var dependent =
          f.artifact(
              "dependent",
              "1.0.0",
              DOMAIN,
              PackManifest.Kind.DOMAIN,
              List.of(new PackManifest.Dependency("example", "1.0.0", null)));
      f.packs.install(f.admin, dependent);
      assertThatThrownBy(() -> f.packs.activate(f.admin, "dependent", "1.0.0", Map.of()))
          .hasMessageContaining("dependency");
      assertThatThrownBy(() -> f.packs.withPack("tenant-b", "example", p -> p.manifest()))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class);
    }
  }

  @Test
  void policyDenialWinsConflictsAndMarketplaceChecksPublisherAndImmutableArtifacts()
      throws Exception {
    try (var f = new Fixture()) {
      f.enterprise();
      for (String effect : List.of("ALLOW", "DENY")) {
        String body =
            "public int order(){return 10;} public Set<String> domains(){return"
                + " Set.of(\"commerce\");} public Decision evaluate(Input input){return new"
                + " Decision(Effect."
                + effect
                + ",\"rule\",\"Reference policy "
                + effect
                + "\");}";
        var artifact =
            f.artifact(
                "policy-" + effect.toLowerCase(),
                "1.0.0",
                body,
                PackManifest.Kind.POLICY,
                List.of());
        f.packs.install(f.admin, artifact);
        f.packs.activate(f.admin, "policy-" + effect.toLowerCase(), "1.0.0", Map.of());
      }
      var result = new PackPolicies(f.packs).evaluate(f.admin, "commerce", "search", Map.of());
      assertThat(result.effect()).isEqualTo("DENY");
      assertThat(result.conflict()).isTrue();
      assertThat(result.decisions()).hasSize(2);
      var market = new Marketplace(f.store, f.packs, f.licenses);
      var artifact = f.artifact("listed", "1.0.0", DOMAIN, PackManifest.Kind.DOMAIN, List.of());
      assertThat(market.publish(f.admin, artifact).publisher()).isEqualTo("tests");
      assertThat(market.list()).hasSize(1);
      assertThat(market.download("listed", "1.0.0")).isEqualTo(artifact);
      var tampered =
          new ManagedPacks.SignedPack(
              artifact.keyId(),
              artifact.manifest(),
              artifact.jar(),
              Base64.getEncoder().encodeToString(new byte[64]));
      assertThatThrownBy(() -> market.publish(f.admin, tampered)).hasMessageContaining("signature");
      f.licenses.downgrade(f.admin);
      assertThatThrownBy(() -> market.publish(f.admin, artifact))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    }
  }
}
