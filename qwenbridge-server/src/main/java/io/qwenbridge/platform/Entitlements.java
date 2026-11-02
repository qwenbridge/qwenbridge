package io.qwenbridge.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The central entitlement engine (QB-261 – QB-273). Commercial access is a set of capability names
 * granted by a signed, offline-verifiable license; every gated operation asks {@link #require},
 * never an edition name. Entitlements are not permissions: a gated operation still needs the
 * caller's authorization.
 *
 * <ul>
 *   <li>License payloads are typed and versioned (version 1) and signed with Ed25519 over the exact
 *       payload bytes. Only public keys are configured ({@code qwenbridge.licensing.trusted-keys},
 *       key id to key); several keys may be trusted at once for rotation.
 *   <li>Validation errors are typed ({@link Problem}). An invalid, expired or not-yet-valid license
 *       never grants anything: the tenant falls back to Community and the reason is reported.
 *   <li>{@code qwenbridge.licensing.grace-period} (default none) keeps an expired license's
 *       entitlements for that long, reported as {@code GRACE}. Expiry never deletes state.
 *   <li>Nothing here contacts a network service.
 * </ul>
 */
@ApplicationScoped
public final class Entitlements {
  /** Why a license is not in effect (QB-261, QB-266). */
  public enum Problem {
    MALFORMED,
    UNKNOWN_KEY,
    BAD_SIGNATURE,
    UNSUPPORTED_VERSION,
    WRONG_TENANT,
    ISSUER_MISMATCH,
    INCOMPLETE,
    NOT_YET_VALID,
    EXPIRED,
    FEATURES_EXCEED_EDITION
  }

  /** A license refused for a typed reason; the message never repeats license content. */
  public static final class LicenseException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final Problem problem;

    LicenseException(Problem problem, String message) {
      super("License problem " + problem + ": " + message);
      this.problem = problem;
    }

    public Problem problem() {
      return problem;
    }
  }

  /** NONE: no license (Community). VALID. GRACE: expired, within the grace period. INVALID. */
  public enum State {
    NONE,
    VALID,
    GRACE,
    INVALID
  }

  public static final int LICENSE_VERSION = 1;

  public enum Edition {
    COMMUNITY,
    PROFESSIONAL,
    ENTERPRISE
  }

  public record License(
      int version,
      String id,
      String issuer,
      String tenant,
      Edition edition,
      Instant notBefore,
      Instant expiresAt,
      Set<String> features) {
    public License {
      // A missing list is reported as INCOMPLETE by validation, not as an unreadable document.
      features = features == null ? Set.of() : Set.copyOf(features);
    }
  }

  public record SignedLicense(String keyId, String payload, String signature) {}

  public record Status(
      Edition edition,
      Set<String> features,
      String licenseId,
      Instant expiresAt,
      State state,
      Problem problem) {
    public Status {
      features = Set.copyOf(features);
    }
  }

  /**
   * Matrix features with no server-side gate (QB-268): {@code web} is the browser component, a
   * static asset every edition may use. Every other feature is checked by {@link #require}.
   */
  public static final Set<String> UNGATED = Set.of("web");

  public static final Map<Edition, Set<String>> MATRIX =
      Map.of(
          Edition.COMMUNITY,
              Set.of(
                  "search",
                  "sessions",
                  "relaxation",
                  "web",
                  "playground",
                  "builtin-packs",
                  "marketplace-read"),
          Edition.PROFESSIONAL,
              Set.of(
                  "search",
                  "sessions",
                  "relaxation",
                  "web",
                  "playground",
                  "builtin-packs",
                  "marketplace-read",
                  "external-packs"),
          Edition.ENTERPRISE,
              Set.of(
                  "search",
                  "sessions",
                  "relaxation",
                  "web",
                  "playground",
                  "builtin-packs",
                  "marketplace-read",
                  "external-packs",
                  "marketplace-publish",
                  "tenant-policy"));
  private final PlatformStore store;
  private final ObjectMapper mapper;
  private final Map<String, String> trusted;
  private final java.time.Duration grace;
  private final java.time.Clock clock;

  public Entitlements(PlatformStore store, ObjectMapper mapper, String keys) {
    this(store, mapper, keys, java.time.Duration.ZERO, java.time.Clock.systemUTC());
  }

  @jakarta.inject.Inject
  public Entitlements(
      PlatformStore store,
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.licensing.trusted-keys", defaultValue = "{}") String keys,
      @ConfigProperty(name = "qwenbridge.licensing.grace-period", defaultValue = "PT0S")
          java.time.Duration grace) {
    this(store, mapper, keys, grace, java.time.Clock.systemUTC());
  }

  public Entitlements(
      PlatformStore store,
      ObjectMapper mapper,
      String keys,
      java.time.Duration grace,
      java.time.Clock clock) {
    this.store = store;
    this.mapper = mapper.copy();
    try {
      trusted = Map.copyOf(mapper.readValue(keys, new TypeReference<Map<String, String>>() {}));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid license trust configuration", ex);
    }
    // QB-262: only public verification keys are accepted; a private key here is refused.
    SignedDocuments.requirePublicKeys(trusted, "qwenbridge.licensing.trusted-keys");
    if (grace.isNegative() || grace.compareTo(java.time.Duration.ofDays(90)) > 0)
      throw new IllegalArgumentException("qwenbridge.licensing.grace-period must be 0..90 days");
    this.grace = grace;
    this.clock = clock;
  }

  /** Verifies and checks a license; on success it is valid or within the grace period. */
  private License validate(SignedLicense envelope, String tenant, boolean allowGrace) {
    if (envelope == null
        || envelope.payload() == null
        || envelope.payload().isEmpty()
        || envelope.payload().length() > 16384)
      throw new LicenseException(Problem.MALFORMED, "no license payload");
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(envelope.payload());
    } catch (IllegalArgumentException ex) {
      throw new LicenseException(Problem.MALFORMED, "payload is not Base64");
    }
    if (envelope.keyId() == null || !trusted.containsKey(envelope.keyId()))
      throw new LicenseException(Problem.UNKNOWN_KEY, "signing key is not trusted");
    try {
      SignedDocuments.verify(trusted, envelope.keyId(), bytes, envelope.signature());
    } catch (IllegalArgumentException ex) {
      throw new LicenseException(Problem.BAD_SIGNATURE, "invalid signature");
    }
    License license;
    try {
      license = mapper.readValue(bytes, License.class);
    } catch (java.io.IOException ex) {
      throw new LicenseException(Problem.MALFORMED, "payload is not a license document");
    }
    if (license.version() != LICENSE_VERSION)
      throw new LicenseException(
          Problem.UNSUPPORTED_VERSION,
          "license version " + license.version() + " is not supported");
    if (license.id() == null
        || license.id().isBlank()
        || license.edition() == null
        || license.notBefore() == null
        || license.expiresAt() == null
        || license.tenant() == null
        || license.issuer() == null)
      throw new LicenseException(Problem.INCOMPLETE, "required license fields are missing");
    if (!tenant.equals(license.tenant()))
      throw new LicenseException(Problem.WRONG_TENANT, "the license is for another tenant");
    if (!envelope.keyId().equals(license.issuer()))
      throw new LicenseException(Problem.ISSUER_MISMATCH, "issuer does not match the signing key");
    if (!MATRIX.get(license.edition()).containsAll(license.features()))
      throw new LicenseException(
          Problem.FEATURES_EXCEED_EDITION, "features exceed the license edition");
    Instant now = clock.instant();
    if (now.isBefore(license.notBefore()))
      throw new LicenseException(Problem.NOT_YET_VALID, "the license is not valid yet");
    if (!now.isBefore(license.expiresAt())
        && !(allowGrace && now.isBefore(license.expiresAt().plus(grace))))
      throw new LicenseException(Problem.EXPIRED, "the license has expired");
    return license;
  }

  public Status status(String tenant) {
    var envelope = store.read(tenant, "license", "active", SignedLicense.class);
    if (envelope != null && envelope.payload() != null && !envelope.payload().isEmpty())
      try {
        var license = validate(envelope, tenant, true);
        boolean inGrace = !clock.instant().isBefore(license.expiresAt());
        // Community capabilities are the baseline of every licence.
        var features = new java.util.TreeSet<>(MATRIX.get(Edition.COMMUNITY));
        features.addAll(license.features());
        return new Status(
            license.edition(),
            features,
            license.id(),
            license.expiresAt(),
            inGrace ? State.GRACE : State.VALID,
            inGrace ? Problem.EXPIRED : null);
      } catch (LicenseException ex) {
        // QB-265: expired, revoked trust or tampering falls back to Community; never grants
        // paid capabilities, never deletes the stored license or anything it unlocked.
        return new Status(
            Edition.COMMUNITY,
            MATRIX.get(Edition.COMMUNITY),
            "community",
            null,
            State.INVALID,
            ex.problem());
      }
    return new Status(
        Edition.COMMUNITY, MATRIX.get(Edition.COMMUNITY), "community", null, State.NONE, null);
  }

  public Status install(ApiIdentity.Principal caller, SignedLicense envelope) {
    caller.require("license:write");
    validate(envelope, caller.tenant(), false);
    store.update(caller.tenant(), "license", "active", SignedLicense.class, null, old -> envelope);
    return status(caller.tenant());
  }

  public Status downgrade(ApiIdentity.Principal caller) {
    caller.require("license:write");
    store.update(
        caller.tenant(),
        "license",
        "active",
        SignedLicense.class,
        null,
        old -> new SignedLicense("", "", ""));
    return status(caller.tenant());
  }

  public void require(String tenant, String feature) {
    if (!status(tenant).features().contains(feature))
      throw new jakarta.ws.rs.ForbiddenException(
          "Feature requires an entitled license: " + feature);
  }
}
