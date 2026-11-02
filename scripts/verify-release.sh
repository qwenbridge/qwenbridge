#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: bash scripts/verify-release.sh"
  echo "Runs local checks and live Docker/API/SSE verification. Preserves volumes by default."
  echo "RELEASE_LIGHTWEIGHT=true: local verification only. RUN_LOCAL_VERIFICATION=false: runtime checks only."
  echo "FORCE_FRESH=true: explicitly remove the disposable stack volumes before testing."
  exit 0
fi
if [[ "${RELEASE_LIGHTWEIGHT:-false}" == "true" ]]; then
  exec bash "${SCRIPT_DIR}/verify-local.sh"
fi
if [[ "${RUN_V9_PERFORMANCE:-false}" == "true" && "${PERF_MANAGED:-false}" != "true" ]]; then
  export RUN_LOCAL_VERIFICATION="${RUN_LOCAL_VERIFICATION:-true}"
  exec bash "${SCRIPT_DIR}/verify-performance.sh" "$@"
fi
source "${SCRIPT_DIR}/verification/java-env.sh"
configure_java25 || exit 1
for fragment in "${SCRIPT_DIR}"/verification/[0-9][0-9]-*.sh; do
  source "${fragment}"
done

if [[ "${RUN_COMPLETE_VERIFICATION:-false}" == "true" ]]; then
  info "Complete local/live tests will run after Docker readiness and seeding."
elif [[ "${RUN_LOCAL_VERIFICATION:-true}" == "true" ]]; then
  run_step "Local Java, TypeScript and verification tests" bash "${SCRIPT_DIR}/verify-local.sh"
else
  skip "Local verification (RUN_LOCAL_VERIFICATION=false)"
fi
run_step "Project root validation" check_project_root
run_step "Required tools validation" check_required_tools || { print_summary; exit 1; }
run_step "Git state validation" check_git_state
run_step "Release tag validation" check_release_tag
run_step "Release documentation" v8_release_docs_validation
run_step "CI workflow contracts" v8_ci_workflow_validation
run_step "Dependency security files" v8_dependency_security_files_validation
run_step "Abuse protection source contracts" v8_abuse_source_validation
run_step "Quarkus repository structure" v9_repo_structure_validation
run_step "Maven reactor modules" v9_parent_modules_validation
run_step "Java SDK source contracts" v9_java_sdk_source_validation
run_step "TypeScript SDK source contracts" v9_typescript_sdk_source_validation
run_step "TypeScript package metadata" v9_typescript_sdk_package_metadata_validation
run_step "Quality performance scripts" v9_quality_performance_scripts_validation
run_step "Performance script contract" v9_performance_script_validation
run_step "Section 1: Core dependency isolation" section1_core_dependency_isolation_validation
run_step "Section 1: Spring Boot elimination" section1_spring_boot_elimination_validation
run_step "Section 1: startup without Redis, OpenSearch and Ollama" section1_standalone_startup_validation

if [[ "${RUN_DOCKER_RUNTIME}" != "true" ]]; then
  skip "Docker and API runtime verification (RUN_DOCKER_RUNTIME=false)"
  print_summary
  exit "$?"
fi
run_step "Docker readiness" restart_docker_daemon || { print_summary; exit 1; }
if [[ "${RUN_V9_PERFORMANCE}" == "true" ]]; then
  benchmark_budget="$(python3 -c 'import sys; n=int(sys.argv[1]); v=int(sys.argv[2]); assert 1 <= v <= n <= 2000000000; print(n+40000)' "${PERF_TOTAL_REQUESTS}" "${PERF_VUS}")" || exit 1
  export QWENBRIDGE_ABUSE_PER_IP_LIMIT="${PERF_ABUSE_REQUEST_LIMIT:-${benchmark_budget}}"
  export QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA="${PERF_ABUSE_REQUEST_LIMIT:-${benchmark_budget}}"
  info "Benchmark rate-limit budget: ${QWENBRIDGE_ABUSE_PER_IP_LIMIT}; requests=${PERF_TOTAL_REQUESTS}, concurrent VUs=${PERF_VUS}"
else
  export QWENBRIDGE_ABUSE_PER_IP_LIMIT="${QWENBRIDGE_ABUSE_PER_IP_LIMIT:-2000}"
  export QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA="${QWENBRIDGE_ABUSE_AI_REQUEST_QUOTA:-2000}"
fi
export QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX="${QWENBRIDGE_ANALYSIS_CACHE_KEY_PREFIX:-qwenbridge:verification:${VERIFY_RUN_ID}}"
run_step "Fresh Docker environment reset" fresh_environment_reset
run_step "Docker Compose pull/build/up" docker_pull_build_up || { print_summary; exit 1; }
run_step "Docker Compose service readiness" wait_for_compose_services || { print_summary; exit 1; }
run_step "Ollama model validation" verify_ollama_models
run_step "Ollama chat model readiness validation" ollama_chat_readiness_validation
run_step "OpenSearch seed data" seed_opensearch
run_step "Application readiness" wait_for_app_readiness || { print_summary; exit 1; }
if [[ "${RUN_COMPLETE_VERIFICATION:-false}" == "true" ]]; then
  run_step "Authenticated TLS OpenSearch test backend" prepare_secure_opensearch || { print_summary; exit 1; }
  run_step "Complete local suite with live Ollama, OpenSearch and Redis" \
    env QWENBRIDGE_RUN_OLLAMA_IT=true QWENBRIDGE_RUN_OPENSEARCH_IT=true QWENBRIDGE_RUN_REDIS_IT=true \
    QWENBRIDGE_RUN_BENCHMARKS=true QWENBRIDGE_BENCHMARK_PROFILE=quick \
    QWENBRIDGE_AI_OLLAMA_BASE_URL="${OLLAMA_URL}" \
    QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL="${OPENSEARCH_URL}" \
    QWENBRIDGE_SEARCH_OPENSEARCH_INDEX="${OPENSEARCH_INDEX}" \
    VERIFY_LOG_FILE="${VERIFY_LOG_DIR}/verify-local-${VERIFY_RUN_ID}.log" \
    bash "${SCRIPT_DIR}/verify-local.sh" || { print_summary; exit 1; }
  run_step "Stop isolated TLS test backend" stop_secure_opensearch
  run_step "Maven formatting and static analysis" mvn -B -Pcode-quality -DskipTests verify \
    || { print_summary; exit 1; }
fi
run_step "Docker app healthcheck validation" docker_app_healthcheck_validation
run_step "Docker runtime user validation" docker_runtime_user_validation
run_step "V8 Docker runtime hardening validation" v8_docker_runtime_hardening_validation
run_step "OpenSearch vector mapping validation" opensearch_vector_mapping_validation
run_step "Ollama embedding generation validation" ollama_embedding_generation_validation
run_step "OpenSearch vector retrieval validation" opensearch_vector_retrieval_validation
run_step "OpenSearch hybrid retrieval validation" opensearch_hybrid_retrieval_validation

run_step "Quarkus health endpoint" quarkus_health_endpoint
run_step "Public health endpoint" public_health_endpoint
run_step "Version endpoint" version_endpoint
run_step "AI chat endpoint" ai_chat_endpoint
run_step "Analyze API endpoint" analyze_api_endpoint
run_step "Commerce v2 filters, refinement, evidence and SSE" commerce_runtime_validation
run_step "Owned conversations, consent, editions, backends and Playground" sessions_runtime_validation
run_step "Input security corpus validation" input_security_corpus_validation
run_step "Language quality corpus validation" language_quality_corpus_validation
run_step "Retrieval quality corpus validation" retrieval_quality_corpus_validation
run_step "API resilience corpus validation" api_resilience_corpus_validation
run_step "SSE streaming lifecycle validation" sse_streaming_lifecycle_validation
run_step "V8 AI SSE token streaming validation" sse_ai_token_streaming_validation
run_step "Validation error contract" validation_error_contract
run_step "V8 abuse runtime rate-limit validation" v8_abuse_runtime_rate_limit_validation
run_step "Custom request ID propagation" custom_request_id_propagation
run_step "CORS preflight validation" cors_preflight_validation
run_step "Cache miss / cache hit validation" cache_miss_hit_validation
run_step "Concurrent SingleFlight validation" singleflight_validation
run_step "OpenAPI endpoint" openapi_endpoint
run_step "OpenAPI contains V8 endpoints" openapi_contains_v8_endpoints
run_step "Swagger UI endpoint" swagger_endpoint

run_step "TypeScript SDK public exports" v9_typescript_sdk_exports_validation
run_step "TypeScript SDK live analyze" v9_typescript_sdk_runtime_analyze_validation
run_step "TypeScript SDK live SSE" v9_typescript_sdk_runtime_sse_validation
run_step "Java SDK runtime compile" v9_java_sdk_runtime_compile_validation
run_step "Section 1: Java SDK live analyze" section1_java_sdk_live_analyze_validation
run_step "Section 3: Java SDK live v2 client" section3_java_sdk_live_commerce_validation
run_step "Section 3: TypeScript SDK live v2 client" section3_typescript_sdk_live_commerce_validation
run_step "Section 3: TypeScript SDK on Node.js 20" section3_typescript_sdk_node20_validation
run_step "Section 3: Playground strategy comparison (live)" section3_strategy_comparison_validation
run_step "QB-293: database-free runtime (REST, SSE, Java and TypeScript SDKs)" qb293_database_free_runtime_validation
run_step "QB-286: multilingual expansion (real providers, REST, SSE, Java and TypeScript SDKs)" qb286_multilingual_expansion_validation
run_step "QB-294: Quarkus-only certification (dependency graph, plugins, sources, configuration, packaging, container)" qb294_quarkus_only_validation
run_step "QB-295: dual-SDK end-to-end suite (Java and TypeScript consumers, real server, REST, SSE, providers)" qb295_dual_sdk_e2e_validation
run_step "TypeScript dependency audit" v9_typescript_sdk_audit_validation
run_step "V9 dockerized k6 compose validation" v9_dockerized_k6_compose_validation
run_step "V9 optional performance validation" v9_optional_performance_validation
run_step "Section 3: performance and quality regression gate" section3_regression_gate_validation
run_step "Section 3: regression gate detects an injected slowdown" section3_regression_gate_detects_slowdown_validation

echo ""
echo "========== Redis Keys =========="
print_redis_keys
echo "========== ========== =========="

print_relevant_logs

print_summary
SUMMARY_STATUS=$?
echo "Log file: ${VERIFY_LOG_FILE}"
echo "Run ID: ${VERIFY_RUN_ID}"
exit "${SUMMARY_STATUS}"
