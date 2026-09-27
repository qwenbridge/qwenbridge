package io.qwenbridge.exception;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.api.header.ApiHeaders;
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
      Response.Status status, ErrorCode code, String message, UriInfo uriInfo, HttpHeaders headers) {
    ApiError error =
        ApiError.builder()
            .timestamp(Instant.now())
            .status(status.getStatusCode())
            .error(status.getReasonPhrase())
            .code(code.name())
            .message(message)
            .path(uriInfo == null ? "" : uriInfo.getPath())
            .requestId(requestId(headers))
            .build();

    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .header(ApiHeaders.REQUEST_ID, error.requestId())
        .header(ApiHeaders.QWENBRIDGE_VERSION, "0.1.0-SNAPSHOT")
        .entity(error)
        .build();
  }

  static String requestId(HttpHeaders headers) {
    if (headers == null) {
      return "";
    }
    String value = headers.getHeaderString(ApiHeaders.REQUEST_ID);
    return value == null || value.isBlank() ? "" : value.trim();
  }

  static String safeMessage(Throwable throwable, String fallback) {
    if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
      return fallback;
    }
    return throwable.getMessage();
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
        if (node.getKind() == ElementKind.PROPERTY
            || node.getKind() == ElementKind.PARAMETER) {
          name = node.getName();
        }
      }
      return name;
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
          safeMessage(exception, "AI provider failure"),
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
          safeMessage(exception, "Search provider failure"),
          uriInfo,
          headers);
    }
  }

  @Provider
  public static class WebApplicationExceptionMapper
      implements ExceptionMapper<WebApplicationException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(WebApplicationException exception) {
      int status = exception.getResponse().getStatus();
      Response.Status resolved = Response.Status.fromStatusCode(status);
      if (resolved == null) {
        resolved = Response.Status.INTERNAL_SERVER_ERROR;
      }

      return build(resolved, mapCode(status), message(status, exception, resolved), uriInfo, headers);
    }

    private ErrorCode mapCode(int status) {
      return switch (status) {
        case 400, 405, 406, 415 -> ErrorCode.BAD_REQUEST;
        case 401 -> ErrorCode.UNAUTHORIZED;
        case 403 -> ErrorCode.FORBIDDEN;
        case 404 -> ErrorCode.NOT_FOUND;
        case 409 -> ErrorCode.CONFLICT;
        case 429 -> ErrorCode.RATE_LIMITED;
        default -> ErrorCode.INTERNAL_ERROR;
      };
    }

    private String message(
        int status, WebApplicationException exception, Response.Status resolved) {
      return switch (status) {
        case 400 -> "Malformed JSON request body";
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
      LOG.errorf(exception, "Unexpected API error on path %s", uriInfo == null ? "" : uriInfo.getPath());

      return build(
          Response.Status.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_ERROR,
          "Unexpected server error",
          uriInfo,
          headers);
    }
  }
}
