package io.qwenbridge.security;

import io.qwenbridge.core.context.RequestIds;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/** Records a well-formed approval id from the request header for the central policy (QB-075). */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class ApprovalHeaderFilter implements ContainerRequestFilter {

  @Inject ApprovalContext approvals;

  @Override
  public void filter(ContainerRequestContext request) {
    String id = request.getHeaderString(ApprovalContext.HEADER);
    if (id != null && RequestIds.isValid(id.trim())) {
      approvals.set(id.trim());
    }
  }
}
