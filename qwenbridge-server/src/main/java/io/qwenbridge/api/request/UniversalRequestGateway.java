package io.qwenbridge.api.request;

import io.qwenbridge.core.request.RawRequest;
import io.qwenbridge.core.request.UniversalRequest;
import io.qwenbridge.core.request.UniversalRequestParser;
import io.qwenbridge.core.request.UniversalRequestParser.Result;
import io.qwenbridge.model.SearchAnalyzeRequest;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The single entry boundary that turns a transport request into the domain-neutral {@link
 * UniversalRequest} (QB-001, QB-005). It runs before any AI, retrieval or external call and rejects
 * invalid requests with a typed {@link RequestValidationException}.
 */
@ApplicationScoped
public class UniversalRequestGateway {

  private final UniversalRequestParser parser;

  public UniversalRequestGateway(RequestLimitsConfig config) {
    this.parser = new UniversalRequestParser(config.toLimits());
  }

  /**
   * @param request the deserialized v1 analyze body
   * @param effectiveRequestId the request ID after header/body resolution, or null
   */
  public UniversalRequest accept(SearchAnalyzeRequest request, String effectiveRequestId) {
    RawRequest raw =
        request == null
            ? null
            : new RawRequest(
                request.contractVersion(),
                effectiveRequestId,
                request.query(),
                request.declaredLanguage(),
                request.responseLanguage(),
                request.locale(),
                request.context(),
                request.domainHints());
    return switch (parser.parse(raw)) {
      case Result.Valid valid -> valid.request();
      case Result.Invalid invalid -> throw new RequestValidationException(invalid.violations());
    };
  }
}
