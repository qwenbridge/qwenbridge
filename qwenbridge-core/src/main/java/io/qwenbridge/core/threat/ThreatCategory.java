package io.qwenbridge.core.threat;

/** Domain-neutral threat categories (QB-064). */
public enum ThreatCategory {
  PROMPT_INJECTION,
  JAILBREAK,
  SQL_INJECTION,
  NOSQL_INJECTION,
  LDAP_INJECTION,
  COMMAND_INJECTION,
  TEMPLATE_INJECTION,
  XSS,
  PATH_TRAVERSAL,
  SSRF,
  SECRET_LEAKAGE,
  UNICODE_OBFUSCATION,
  OTHER
}
