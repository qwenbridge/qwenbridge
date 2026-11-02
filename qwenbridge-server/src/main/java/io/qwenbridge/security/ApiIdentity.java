package io.qwenbridge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class ApiIdentity {
  public record Principal(String subject, String tenant, Set<String> roles) {
    public Principal {
      roles = Set.copyOf(roles);
      if (subject == null
          || subject.isBlank()
          || tenant == null
          || !tenant.matches("[A-Za-z0-9_-]{1,64}"))
        throw new IllegalArgumentException("Invalid principal");
    }

    /**
     * Requires permission for an operation in this principal's tenant. Decided by the central
     * policy engine (QB-071–QB-076): unknown operations, missing scopes, unauthenticated access to
     * sensitive operations and missing approvals (when enforced) are refused.
     */
    public void require(String operation) {
      io.qwenbridge.security.Authorization.require(
          this, operation, io.qwenbridge.security.Authorization.resourceFor(operation, tenant));
    }

    /** Requires permission for an operation on a specific resource (connector, plugin, domain). */
    public void require(String operation, io.qwenbridge.core.policy.Resource resource) {
      io.qwenbridge.security.Authorization.require(this, operation, resource);
    }

    /** The principal used for unauthenticated requests where anonymous search is allowed. */
    public boolean anonymous() {
      return ANONYMOUS_SUBJECT.equals(subject) && PUBLIC_TENANT.equals(tenant);
    }
  }

  static final String ANONYMOUS_SUBJECT = "anonymous";
  static final String PUBLIC_TENANT = "public";

  private record Credential(String digest, Principal principal) {}

  private final List<Credential> credentials;
  private final boolean required;
  public static final String PROPERTY = "qwenbridge.verified-principal";

  @Inject
  public ApiIdentity(
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.security.authentication-required", defaultValue = "false")
          boolean required,
      @ConfigProperty(name = "qwenbridge.security.api-principals", defaultValue = "[]")
          String json) {
    this.required = required;
    List<Credential> parsed = new ArrayList<>();
    try {
      var array = mapper.readTree(json);
      if (!array.isArray()) throw new IllegalArgumentException("Principals must be an array");
      for (var item : array) {
        String key = item.path("key").asText();
        if (key.length() < 24)
          throw new IllegalArgumentException("API credentials require at least 24 characters");
        if (ANONYMOUS_SUBJECT.equals(item.path("subject").asText()))
          throw new IllegalArgumentException("The anonymous subject cannot be configured");
        Set<String> roles = new java.util.HashSet<>();
        item.path("roles").forEach(role -> roles.add(role.asText()));
        Credential credential =
            new Credential(
                digest(key),
                new Principal(item.path("subject").asText(), item.path("tenant").asText(), roles));
        if (parsed.stream().anyMatch(c -> c.digest().equals(credential.digest())))
          throw new IllegalArgumentException("Duplicate credential");
        parsed.add(credential);
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid principal configuration");
    }
    if (required && parsed.isEmpty())
      throw new IllegalArgumentException("Authentication requires configured principals");
    credentials = List.copyOf(parsed);
    if (!required) {
      // Audit S3: anonymous search is allowed outside %prod; make that visible at startup.
      org.jboss.logging.Logger.getLogger(ApiIdentity.class)
          .warn(
              "Authentication is not required: requests without a token act as tenant 'public'"
                  + " with search permission only. Set qwenbridge.security.authentication-required"
                  + "=true for production (the %prod profile does).");
    }
  }

  public Principal authenticate(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      if (required) throw new NotAuthorizedException("Bearer");
      return new Principal(ANONYMOUS_SUBJECT, PUBLIC_TENANT, Set.of("search"));
    }
    if (!authorization.startsWith("Bearer ")) throw new NotAuthorizedException("Bearer");
    byte[] candidate = digest(authorization.substring(7)).getBytes(StandardCharsets.US_ASCII);
    for (Credential c : credentials)
      if (MessageDigest.isEqual(candidate, c.digest().getBytes(StandardCharsets.US_ASCII)))
        return c.principal();
    throw new NotAuthorizedException("Bearer");
  }

  public boolean authenticationRequired() {
    return required;
  }

  public static Principal caller(ContainerRequestContext context) {
    Object value = context.getProperty(PROPERTY);
    if (!(value instanceof Principal principal)) throw new NotAuthorizedException("Bearer");
    return principal;
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Provider
  @Priority(Priorities.AUTHENTICATION)
  public static class AuthenticationFilter implements ContainerRequestFilter {
    @Inject ApiIdentity identity;

    public void filter(ContainerRequestContext request) {
      String path = request.getUriInfo().getPath();
      if (path.startsWith("/")) path = path.substring(1);
      if (path.startsWith("api/v2/"))
        request.setProperty(
            PROPERTY, identity.authenticate(request.getHeaderString("Authorization")));
      else if (identity.authenticationRequired()
          && path.startsWith("api/v1/")
          && !path.startsWith("api/v1/health")
          && !path.equals("api/v1/version")) {
        identity.authenticate(request.getHeaderString("Authorization"));
        // The public error model (QB-181), so v1 SDKs parse the refusal like any other error.
        request.abortWith(
            io.qwenbridge.exception.ApiExceptionMappers.response(
                Response.Status.CONFLICT,
                io.qwenbridge.exception.ErrorCode.TENANT_API_REQUIRED,
                "Use tenant-scoped /api/v2 endpoints in authenticated deployments",
                "/" + path,
                request.getProperty(io.qwenbridge.api.header.ApiHeaders.REQUEST_ID)
                        instanceof String id
                    ? id
                    : ""));
      }
    }
  }
}
