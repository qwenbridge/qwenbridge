package io.qwenbridge.core.policy;

/**
 * An additional policy, for example from a Domain Pack (QB-071, QB-076). A rule can only restrict:
 * it may deny or ask for approval, but it can never grant what the base check (authentication,
 * tenant, scope) refused. A rule that throws denies.
 */
public interface PolicyRule {

  String id();

  Verdict evaluate(PolicyRequest request);

  enum Verdict {
    NOT_APPLICABLE,
    DENY,
    REQUIRE_APPROVAL
  }
}
