#!/usr/bin/env bash
# Sourced by scripts/verify-release.sh (not executed directly).
prepare_secure_opensearch() {
  require_command openssl || return 1
  require_command keytool || return 1
  export QWENBRIDGE_TEST_TLS_DIR="${PROJECT_ROOT}/${VERIFY_LOG_DIR}/tls-${VERIFY_RUN_ID}"
  mkdir -p "${QWENBRIDGE_TEST_TLS_DIR}" || return 1
  local task_tls="${QWENBRIDGE_TEST_TLS_DIR}"
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj '/CN=QwenBridge Test CA' \
    -keyout "${task_tls}/ca-key.pem" -out "${task_tls}/ca.pem" >/dev/null 2>&1 || return 1
  openssl req -newkey rsa:2048 -nodes -subj '/CN=localhost' \
    -keyout "${task_tls}/server-key-input.pem" -out "${task_tls}/server.csr" >/dev/null 2>&1 || return 1
  openssl pkcs8 -topk8 -nocrypt -in "${task_tls}/server-key-input.pem" -out "${task_tls}/server-key.pem" >/dev/null 2>&1 || return 1
  printf '%s\n' 'subjectAltName=DNS:localhost,DNS:qwenbridge-opensearch-secure-test,IP:127.0.0.1' >"${task_tls}/extensions.cnf"
  openssl x509 -req -days 2 -in "${task_tls}/server.csr" -CA "${task_tls}/ca.pem" \
    -CAkey "${task_tls}/ca-key.pem" -CAcreateserial -extfile "${task_tls}/extensions.cnf" \
    -out "${task_tls}/server.pem" >/dev/null 2>&1 || return 1
  chmod 644 "${task_tls}/server-key.pem" "${task_tls}/server.pem" "${task_tls}/ca.pem"
  keytool -importcert -noprompt -alias test-ca -file "${task_tls}/ca.pem" -keystore "${task_tls}/truststore.p12" \
    -storetype PKCS12 -storepass changeit >/dev/null 2>&1 || return 1
  export QWENBRIDGE_TEST_OPENSEARCH_PASSWORD="$(python3 -c 'import secrets; print("QbTest!aA7"+secrets.token_hex(16))')"
  export QWENBRIDGE_TEST_OPENSEARCH_URL="https://localhost:${QWENBRIDGE_TEST_OPENSEARCH_PORT:-19200}"
  export RUN_SECURITY_TEST_CONTAINER_STARTED=true
  docker compose -f docker-compose.security-tests.yml up -d || return 1
  wait_for_container_healthy_or_running qwenbridge-opensearch-secure-test healthy || return 1
  export QWENBRIDGE_RUN_SECURE_OPENSEARCH_IT=true
}

stop_secure_opensearch() {
  if [[ "${RUN_SECURITY_TEST_CONTAINER_STARTED:-false}" == "true" ]]; then
    docker logs qwenbridge-opensearch-secure-test >"${VERIFY_LOG_DIR}/secure-opensearch-${VERIFY_RUN_ID}.log" 2>&1 || true
    docker compose -f docker-compose.security-tests.yml stop || return 1
    RUN_SECURITY_TEST_CONTAINER_STARTED=false
  fi
}
