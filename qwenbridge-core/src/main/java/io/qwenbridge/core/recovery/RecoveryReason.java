package io.qwenbridge.core.recovery;

/** Stable reason codes for recovery decisions (QB-130). */
public enum RecoveryReason {
  /** The query returned enough results; nothing to recover. */
  NOT_NEEDED,
  /** Retrieval did not run; recovery does not apply. */
  NOT_EXECUTED,
  /** The provider failed; relaxing would hide the failure and cannot help. */
  PROVIDER_FAILED,
  ZERO_RESULTS,
  LOW_RESULTS,
  /** An alternative was offered. */
  RELAXATION_OFFERED,
  /** Relaxing a HARD constraint changes what the user asked for: it needs the user's consent. */
  HARD_REQUIRES_CONSENT,
  /** A protected (security or isolation) field is never relaxed. */
  PROTECTED_FIELD,
  /** A constraint added by a domain or policy rule is never relaxed. */
  DOMAIN_RULE_NOT_RELAXED,
  /** Exclusions are never relaxed: the user ruled something out. */
  EXCLUSION_NOT_RELAXED,
  /** The user locked this field. */
  LOCKED_BY_USER,
  /** No rule of the policy permits relaxing this constraint. */
  NO_PERMITTED_RELAXATION,
  /** The policy's proposal limit was reached. */
  PROPOSAL_LIMIT,
  // Validation of alternative queries (QB-129).
  ALTERNATIVE_ADDS_CONSTRAINT,
  ALTERNATIVE_REMOVES_EXCLUSION,
  ALTERNATIVE_CHANGES_PROTECTED,
  ALTERNATIVE_CHANGES_LOCKED,
  ALTERNATIVE_NOT_PERMITTED,
  ALTERNATIVE_NO_CHANGE
}
