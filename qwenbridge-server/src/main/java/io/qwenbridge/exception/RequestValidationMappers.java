package io.qwenbridge.exception;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import io.qwenbridge.api.request.RequestValidationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps universal request contract and structural-limit failures to {@code 400 VALIDATION_ERROR}.
 */
public final class RequestValidationMappers {

  private RequestValidationMappers() {}

  @Provider
  public static class UniversalRequestViolationMapper
      implements ExceptionMapper<RequestValidationException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(RequestValidationException exception) {
      // Violation messages are built by the parser and never contain submitted values.
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.VALIDATION_ERROR,
          exception.getMessage(),
          uriInfo,
          headers);
    }
  }

  /** JSON nesting, number or string length beyond the configured parser constraints (QB-005). */
  @Provider
  public static class JsonStructureLimitMapper
      implements ExceptionMapper<StreamConstraintsException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(StreamConstraintsException exception) {
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.VALIDATION_ERROR,
          "Request JSON exceeds structural limits",
          uriInfo,
          headers);
    }
  }

  /**
   * Unparseable JSON (QB-062). Replaces the framework's default rendering, which includes parser
   * locations and source excerpts, with the standard error body.
   */
  @Provider
  public static class JsonParseMapper
      implements ExceptionMapper<com.fasterxml.jackson.core.JsonParseException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(com.fasterxml.jackson.core.JsonParseException exception) {
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.BAD_REQUEST,
          "Malformed JSON request body",
          uriInfo,
          headers);
    }
  }

  /**
   * Well-formed JSON of the wrong shape, e.g. an array where an object is expected (QB-062). The
   * framework default lists internal class and attribute names; only the stable message is
   * returned.
   */
  @Provider
  public static class JsonMappingMapper
      implements ExceptionMapper<com.fasterxml.jackson.databind.JsonMappingException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(com.fasterxml.jackson.databind.JsonMappingException exception) {
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.VALIDATION_ERROR,
          "Request JSON does not match the expected structure",
          uriInfo,
          headers);
    }
  }

  /** Any other JSON processing failure while reading a request (QB-062). */
  @Provider
  public static class JsonProcessingMapper
      implements ExceptionMapper<com.fasterxml.jackson.core.JsonProcessingException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(com.fasterxml.jackson.core.JsonProcessingException exception) {
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.BAD_REQUEST,
          "Malformed JSON request body",
          uriInfo,
          headers);
    }
  }

  /**
   * Overrides the framework's built-in mapper for this exact type, which lists internal class and
   * attribute names in dev/test mode and returns an empty body in production (QB-062).
   */
  @Provider
  @jakarta.annotation.Priority(1)
  public static class MismatchedInputMapper
      implements ExceptionMapper<com.fasterxml.jackson.databind.exc.MismatchedInputException> {

    @Context UriInfo uriInfo;
    @Context HttpHeaders headers;

    @Override
    public Response toResponse(
        com.fasterxml.jackson.databind.exc.MismatchedInputException exception) {
      return ApiExceptionMappers.build(
          Response.Status.BAD_REQUEST,
          ErrorCode.VALIDATION_ERROR,
          "Request JSON does not match the expected structure",
          uriInfo,
          headers);
    }
  }
}
