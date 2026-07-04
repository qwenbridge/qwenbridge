v8_release_docs_validation() {
  [[ -f "docs/roadmap/V8.md" ]] \
    && [[ -f "docs/release/V8-release-checklist.md" ]] \
    && [[ -f "docs/security/abuse-protection.md" ]] \
    && [[ -f "docs/security/secrets-handling-policy.md" ]] \
    && [[ -f "docs/architecture/architecture-rules.md" ]] \
    && grep -qi "production" docs/roadmap/V8.md \
    && grep -qi "security" docs/roadmap/V8.md \
    && grep -qi "abuse" docs/security/abuse-protection.md \
    && grep -qi "secret" docs/security/secrets-handling-policy.md \
    && grep -qi "archunit" docs/architecture/architecture-rules.md
}

v8_quality_test_suite_validation() {
  mvn -q \
    -pl "${SERVER_DIR}" \
    -Dtest='ArchitectureRulesTest,InMemoryFixedWindowRateLimiterTest,DefaultBenchmarkEvaluationRunnerTest,DefaultEvaluationThresholdPolicyTest,DefaultRankingPolicyTest,SearchResultRankerTest,DefaultRerankingServiceTest,NoOpRerankerTest' \
    test
}

v8_ci_workflow_validation() {
  [[ -f ".github/workflows/ci.yml" ]] \
    && [[ -f ".github/workflows/codeql.yml" ]] \
    && [[ -f ".github/dependabot.yml" ]] \
    && grep -q "Dependency Scan" .github/workflows/ci.yml \
    && grep -q "Container Scan" .github/workflows/ci.yml \
    && grep -q "Architecture Tests" .github/workflows/ci.yml \
    && grep -q "workflow_dispatch" .github/workflows/ci.yml \
    && grep -q "CodeQL" .github/workflows/codeql.yml
}

v8_dependency_security_files_validation() {
  [[ -f "dependency-check-suppressions.xml" ]] \
    && [[ -f ".trivyignore" ]] \
    && grep -q "dependency-check" .github/workflows/ci.yml \
    && grep -q "trivy" .github/workflows/ci.yml
}

v8_abuse_source_validation() {
  [[ -f "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse/AbuseProtectionFilter.java" ]] \
    && [[ -f "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse/RateLimiter.java" ]] \
    && [[ -f "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse/InMemoryFixedWindowRateLimiter.java" ]] \
    && [[ -f "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse/RedisBackedRateLimiter.java" ]] \
    && [[ -f "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse/RateLimitDecision.java" ]] \
    && grep -R "X-RateLimit" -n "${SERVER_DIR}/src/main/java/io/qwenbridge/abuse" >/dev/null
}

v8_abuse_runtime_rate_limit_validation() {
  local status=""
  local headers="/tmp/qwenbridge-v8-rate-limit.headers"
  local body="/tmp/qwenbridge-v8-rate-limit.json"
  local request_id="verify-release-v8-rate-limit-$(date +%s)"
  local payload=""

  payload="$(json_payload "${request_id}" "${TEST_QUERY}")"

  status="$(
    curl -sS \
      -D "${headers}" \
      -X POST "${BASE_URL}${ANALYZE_ENDPOINT}" \
      -H "Content-Type: application/json" \
      -H "X-Request-ID: ${request_id}" \
      -H "X-API-Key: verify-release-v8-abuse-test" \
      --data "${payload}" \
      -o "${body}" \
      -w "%{http_code}"
  )"

  echo "HTTP status: ${status}"
  cat "${headers}" || true
  jq . "${body}" || true

  [[ "${status}" == "200" || "${status}" == "429" ]] \
    && grep -qi '^x-ratelimit-limit:' "${headers}" \
    && grep -qi '^x-ratelimit-remaining:' "${headers}" \
    && grep -qi '^x-ratelimit-reset:' "${headers}"
}

v8_docker_runtime_hardening_validation() {
  local dockerfile="Dockerfile"

  if [[ -f "${SERVER_DIR}/Dockerfile" ]]; then
    dockerfile="${SERVER_DIR}/Dockerfile"
  fi

  grep -q "gcr.io/distroless/java21-debian12:nonroot" "${dockerfile}" \
    && grep -q "MaxRAMPercentage" "${dockerfile}" \
    && grep -q "ExitOnOutOfMemoryError" "${dockerfile}" \
    && docker_runtime_user_validation \
    && app_public_health_up
}
