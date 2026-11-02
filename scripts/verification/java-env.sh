#!/usr/bin/env bash
# Sourced by the entry points; compatible with macOS Bash 3.2.
java_major() {
  "$1" -XshowSettings:properties -version 2>&1 |
    awk '/java.specification.version =/ {print $3}'
}

configure_java25() {
  local candidate=""
  if [[ -n "${QWENBRIDGE_JAVA_HOME:-}" ]]; then
    candidate="${QWENBRIDGE_JAVA_HOME}"
    if [[ "$(java_major "${candidate}/bin/java")" != "25" ]]; then
      echo "QWENBRIDGE_JAVA_HOME must point to JDK 25: ${candidate}" >&2
      return 1
    fi
  elif [[ "$(java_major "$(command -v java)")" == "25" ]]; then
    return 0
  else
    if [[ -x /usr/libexec/java_home ]]; then
      candidate="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    fi
    if [[ -z "${candidate}" || "$(java_major "${candidate}/bin/java")" != "25" ]]; then
      candidate=""
      local installed=""
      for installed in "${SDKMAN_CANDIDATES_DIR:-${HOME}/.sdkman/candidates}"/java/25*; do
        if [[ -x "${installed}/bin/javac" && "$(java_major "${installed}/bin/java")" == "25" ]]; then
          candidate="${installed}"
          break
        fi
      done
    fi
  fi
  if [[ -z "${candidate}" || ! -x "${candidate}/bin/javac" ]]; then
    echo "JDK 25 is required. Set QWENBRIDGE_JAVA_HOME to its installation directory." >&2
    return 1
  fi
  export JAVA_HOME="${candidate}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "Using JDK 25: ${JAVA_HOME}"
}
