package io.qwenbridge.core.policy;

/** Stable reason codes for policy decisions (QB-071). */
public enum ReasonCode {
  ALLOWED_BY_SCOPE,
  APPROVED,
  /** High-risk, no valid approval, enforcement is switched off: allowed and reported. */
  APPROVAL_NOT_ENFORCED,
  UNKNOWN_OPERATION,
  UNAUTHENTICATED,
  TENANT_MISMATCH,
  NO_SCOPE,
  APPROVAL_REQUIRED,
  APPROVAL_MISMATCH,
  APPROVAL_EXPIRED,
  APPROVAL_SELF,
  APPROVAL_TOO_LONG,
  POLICY_DENY,
  POLICY_REVIEW,
  POLICY_ERROR
}
