package io.qwenbridge.exception;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.core.security.Sanitizer;
import io.qwenbridge.execution.provider.exception.SearchProviderException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.Instant;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/** JAX-RS exception mappers that render {@link ApiError} payloads for the public API. */
public final class ApiExceptionMappers {

  private ApiExceptionMappers() {}

  static Response build(
      Response.StatusType status,
      ErrorCode code,
      String message,
      UriInfo uriInfo,
      HttpHeaders headers) {
    return response(
        status, code, message, uriInfo == null ? "" : uriInfo.getPath(), requestId(headers));
  }

  /** Whether {@code path} belongs to the v2 API, whose errors carry category and retryable. */
  public static boolean v2(String path) {
    return path != null && (path.startsWith("/api/v2/") || path.startsWith("api/v2/"));
  }

  /** The public error response for {@code path} (QB-181); used by mappers and request filters. */
  public static Response response(
      Response.StatusType status, ErrorCode code, String message, String path, String requestId) {
    boolean v2 = v2(path);
    io.qwenbridge.operations.metrics.ApiMetrics.error(v2 ? "v2" : "v1", code);
    ApiError error =
        ApiError.builder()
            .timestamp(Instant.now())
            .status(status.getStatusCode())
            .error(status.getReasonPhrase())
            .code(code.name())
            .message(message)
            .path(path == null ? "" : path)
            .requestId(requestId == null ? "" : requestId)
            .category(v2 ? code.category().name() : null)
            .retryable(v2 ? code.category().retryable() : null)
            .build();

    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .header(ApiHeaders.REQUEST_ID, error.requestId())
        .header(
            ApiHeaders.QWENBRIDGE_VERSION,
            org.eclipse.microprofile.config.ConfigProvider.getConfig()
                .getOptionalValue("qwenbridge.version", String.class)
                .orElse(""))
        .entity(error)
        .build();
  }

  /**
   * The status, code and safe message an exception is reported with (QB-181), for paths that cannot
   * use a response (an SSE stream that has already started).
   */
  public record Described(int status, ErrorCode code, String message) {}

  public static Described describe(Throwable failure) {
    return switch (failure) {
      case io.qwenbridge.core.context.RequestScope.Interrupted i ->
          i.reason() == io.qwenbridge.core.context.RequestScope.Interruption.CANCELLED
              ? new Described(409, ErrorCode.CANCELLED, "Request was cancelled")
              : new Described(504, ErrorCode.TIMEOUT, "Request deadline exceeded");
      case IllegalArgumentException e ->
          new Described(400, ErrorCode.BAD_REQUEST, safeMessage(e, "Bad request"));
      case AIException e -> new Described(502, ErrorCode.AI_PROVIDER_ERROR, "AI provider failure");
      case SearchProviderException e ->
          new Described(502, ErrorCode.SEARCH_PROVIDER_ERROR, "Search provider failure");
      case org.jboss.resteasy.reactive.ClientWebApplicationException e ->
          new Described(502, ErrorCode.DEPENDENCY_UNAVAILABLE, "Upstream service failure");
      case WebApplicationException e -> {
        int status = e.getResponse().getStatus();
        ErrorCode code = WebApplicationExceptionMapper.code(status, true);
        yield new Described(
            status,
            code,
            status >= 500 ? defaultMessage(code) : safeMessage(e, defaultMessage(code)));
      }
      default -> new Described(500, ErrorCode.INTERNAL_ERROR, "Unexpected server error");
    };
  }

  private static String defaultMessage(ErrorCode code) {
    return switch (code) {
      case DEPENDENCY_UNAVAILABLE -> "A dependency is unavailable";
      case TIMEOUT -> "Request deadline exceeded";
      case UNSUPPORTED_CAPABILITY -> "Capability not supported";
      case INTERNAL_ERROR -> "Unexpected server error";
      default -> "Request failed";
    };
  }

  static String requestId(HttpHeaders headers) {
    if (headers == null) {
      return "";
    }
    String value = headers.getHeaderString(ApiHeaders.REQUEST_ID);
    // RequestIdFilter has normally replaced unsafe values; never echo one that slipped through.
    return value == null || !io.qwenbridge.core.context.RequestIds.isValid(value.trim())
        ? ""
        : value.trim();
  }

  /**
   * The message of an exception thrown by request validation, made safe for an error body (QB-062):
   * secrets masked, class names removed, control characters escaped, length bounded.
   */
  static String safeMessage(Throwable throwable, String fallback) {
    if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
      return fallback;
    }
    return Sanitizer.forSink(Sanitizer.Sink.ERROR_MESSAGE, throwable.getMessage());
  }

  @Provider
  public static class ConstraintViolationMapper
      implements ExceptionMapper<ConstraintViolationException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
      String message =
          exception.getConstraintViolations().stream()
              .map(this::format)
              .collect(Collectors.joining(", "));

      return build(
          Response.Status.BAD_REQUEST,
          ErrorCode.VALIDATION_ERROR,
          message.isBlank() ? "Validation failed" : message,
          uriInfo,
          headers);
    }

    private String format(ConstraintViolation<?> violation) {
      return leaf(violation.getPropertyPath()) + " " + violation.getMessage();
    }

    private String leaf(Path path) {
      String name = "";
      for (Path.Node node : path) {
        if (node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.PARAMETER) {
          name = node.getName();
        }
      }
      return name;
    }
  }

  /** QB-182: a cancelled request is 409 CANCELLED; an expired one is 504 TIMEOUT. */
  @Provider
  public static class RequestInterruptedMapper
      implements ExceptionMapper<io.qwenbridge.core.context.RequestScope.Interrupted> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(io.qwenbridge.core.context.RequestScope.Interrupted exception) {
      Described d = describe(exception);
      Response.StatusType status =
          d.status() == 504 ? Response.Status.GATEWAY_TIMEOUT : Response.Status.CONFLICT;
      return build(status, d.code(), d.message(), uriInfo, headers);
    }
  }

  @Provider
  public static class IllegalArgumentMapper implements ExceptionMapper<IllegalArgumentException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(IllegalArgumentException exception) {
      return build(
          Response.Status.BAD_REQUEST,
          ErrorCode.BAD_REQUEST,
          safeMessage(exception, "Bad request"),
          uriInfo,
          headers);
    }
  }

  @Provider
  public static class AIExceptionMapper implements ExceptionMapper<AIException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(AIException exception) {
      return build(
          Response.Status.BAD_GATEWAY,
          ErrorCode.AI_PROVIDER_ERROR,
          // Upstream details (hosts, model errors) are logged, never returned (QB-062).
          "AI provider failure",
          uriInfo,
          headers);
    }
  }

  @Provider
  public static class SearchProviderExceptionMapper
      implements ExceptionMapper<SearchProviderException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(SearchProviderException exception) {
      return build(
          Response.Status.BAD_GATEWAY,
          ErrorCode.SEARCH_PROVIDER_ERROR,
          "Search provider failure",
          uriInfo,
          headers);
    }
  }

  @Provider
  public static class WebApplicationExceptionMapper
      implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(WebApplicationException exception) {
      if (exception instanceof org.jboss.resteasy.reactive.ClientWebApplicationException) {
        // An upstream service answered with an error: that is a dependency failure of this
        // server, never the caller's 404 or 400, and its details stay in the logs (QB-181).
        LOG.warnf("Upstream call failed with HTTP %d", exception.getResponse().getStatus());
        boolean v2 = v2(uriInfo == null ? "" : uriInfo.getPath());
        return build(
            Response.Status.BAD_GATEWAY,
            v2 ? ErrorCode.DEPENDENCY_UNAVAILABLE : ErrorCode.INTERNAL_ERROR,
            "Upstream service failure",
            uriInfo,
            headers);
      }
      int status = exception.getResponse().getStatus();
      Response.StatusType resolved = Response.Status.fromStatusCode(status);
      if (resolved == null) {
        // Codes the JAX-RS enum lacks (such as 422) keep their status when they are client
        // errors; they used to become 500, which blamed the server for a client request.
        resolved =
            status >= 400 && status < 500
                ? clientStatus(status)
                : Response.Status.INTERNAL_SERVER_ERROR;
      }

      boolean v2 = v2(uriInfo == null ? "" : uriInfo.getPath());
      return build(
          resolved, code(status, v2), message(status, exception, resolved), uriInfo, headers);
    }

    /**
     * The code for an HTTP status. v1 keeps its historical codes; v2 tells dependency failures,
     * timeouts and unsupported capabilities apart from internal errors (QB-181).
     */
    static ErrorCode code(int status, boolean v2) {
      ErrorCode code =
          switch (status) {
            case 400, 405, 406, 415 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 413, 422 -> ErrorCode.VALIDATION_ERROR;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.INTERNAL_ERROR;
          };
      if (!v2 || code != ErrorCode.INTERNAL_ERROR) {
        return code;
      }
      return switch (status) {
        case 501 -> ErrorCode.UNSUPPORTED_CAPABILITY;
        case 502, 503 -> ErrorCode.DEPENDENCY_UNAVAILABLE;
        case 504 -> ErrorCode.TIMEOUT;
        default -> status >= 400 && status < 500 ? ErrorCode.BAD_REQUEST : code;
      };
    }

    private static Response.StatusType clientStatus(int status) {
      String reason = status == 422 ? "Unprocessable Content" : "Client Error";
      return new Response.StatusType() {
        @Override
        public int getStatusCode() {
          return status;
        }

        @Override
        public Response.Status.Family getFamily() {
          return Response.Status.Family.CLIENT_ERROR;
        }

        @Override
        public String getReasonPhrase() {
          return reason;
        }
      };
    }

    private String message(
        int status, WebApplicationException exception, Response.StatusType resolved) {
      return switch (status) {
        case 400 -> "Malformed JSON request body";
        case 413 -> "Request body exceeds configured size limit";
        case 415 -> "Unsupported content type";
        default -> safeMessage(exception, resolved.getReasonPhrase());
      };
    }
  }

  @Provider
  public static class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(Exception exception) {
      LOG.errorf(
          exception, "Unexpected API error on path %s", uriInfo == null ? "" : uriInfo.getPath());

      return build(
          Response.Status.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_ERROR,
          "Unexpected server error",
          uriInfo,
          headers);
    }
  }
}
