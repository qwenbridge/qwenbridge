#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

source "${SCRIPT_DIR}/verification/00-env.sh"
source "${SCRIPT_DIR}/verification/01-common.sh"
source "${SCRIPT_DIR}/verification/02-git-project.sh"
source "${SCRIPT_DIR}/verification/03-docker.sh"
source "${SCRIPT_DIR}/verification/04-opensearch-ollama.sh"
source "${SCRIPT_DIR}/verification/05-api-runtime.sh"
source "${SCRIPT_DIR}/verification/06-sse.sh"
source "${SCRIPT_DIR}/verification/07-v8-validations.sh"
source "${SCRIPT_DIR}/verification/08-v9-validations.sh"
source "${SCRIPT_DIR}/verification/09-input-security.sh"
source "${SCRIPT_DIR}/verification/10-language-quality.sh"
source "${SCRIPT_DIR}/verification/11-retrieval-quality.sh"
source "${SCRIPT_DIR}/verification/12-api-resilience.sh"
source "${SCRIPT_DIR}/verification/13-performance-quality.sh"
source "${SCRIPT_DIR}/verification/99-summary.sh"

echo ""
echo "======================================================"
echo "       QwenBridge - V9 Developer Platform Verification"
echo "======================================================"

if [[ "${RELEASE_LIGHTWEIGHT}" == "true" ]]; then
  run_step "Project root validation" check_project_root
  run_step "Required tools validation" check_required_tools
  run_step "Git state validation" check_git_state
  run_step "V9 release tag validation" check_release_tag
  run_step "V8 release docs validation" v8_release_docs_validation
  run_step "V9 server quality test suite validation" v8_quality_test_suite_validation
  run_step "V8 CI workflow validation" v8_ci_workflow_validation
  run_step "V8 dependency security files validation" v8_dependency_security_files_validation
  run_step "V8 abuse source validation" v8_abuse_source_validation
  run_step "V9 repository structure validation" v9_repo_structure_validation
  run_step "V9 parent modules validation" v9_parent_modules_validation
  run_step "V9 docs validation" v9_docs_validation
  run_step "V9 Maven reactor validation" v9_maven_reactor_validation
  run_step "V9 Java SDK source validation" v9_java_sdk_source_validation
  run_step "V9 Java SDK tests validation" v9_java_sdk_tests_validation
  run_step "V9 Spring Boot starter source validation" v9_starter_source_validation
  run_step "V9 Spring Boot starter tests validation" v9_starter_tests_validation
  run_step "V9 TypeScript SDK source validation" v9_typescript_sdk_source_validation
  run_step "V9 TypeScript SDK package metadata validation" v9_typescript_sdk_package_metadata_validation
  run_step "V9 TypeScript SDK build/test/pack validation" v9_typescript_sdk_build_test_pack_validation
  run_step "V9 TypeScript SDK exports validation" v9_typescript_sdk_exports_validation
  run_step "V9 release docs stale validation" v9_release_docs_no_stale_v8_only_validation
  run_step "V9 performance script validation" v9_performance_script_validation
  run_step "V9 quality performance scripts validation" v9_quality_performance_scripts_validation

  print_summary
  SUMMARY_STATUS=$?
  echo "Log file: ${VERIFY_LOG_FILE}"
  echo "Run ID: ${VERIFY_RUN_ID}"
  exit "${SUMMARY_STATUS}"
fi

run_step "Project root validation" check_project_root
run_step "Required tools validation" check_required_tools
run_step "Git state validation" check_git_state
run_step "V9 release tag validation" check_release_tag
run_step "V8 release docs validation" v8_release_docs_validation
run_step "V9 server quality test suite validation" v8_quality_test_suite_validation
run_step "V8 CI workflow validation" v8_ci_workflow_validation
run_step "V8 dependency security files validation" v8_dependency_security_files_validation
run_step "V8 abuse source validation" v8_abuse_source_validation
run_step "Docker readiness" restart_docker_daemon
run_step "Fresh Docker environment reset" fresh_environment_reset
run_step "Docker Compose pull/build/up" docker_pull_build_up
run_step "Docker Compose service readiness" wait_for_compose_services
run_step "Ollama model validation" verify_ollama_models
run_step "Ollama chat model readiness validation" ollama_chat_readiness_validation
run_step "OpenSearch seed data" seed_opensearch
run_step "Application readiness" wait_for_app_readiness
run_step "Docker app healthcheck validation" docker_app_healthcheck_validation
run_step "Docker runtime user validation" docker_runtime_user_validation
run_step "V8 Docker runtime hardening validation" v8_docker_runtime_hardening_validation
run_step "OpenSearch vector mapping validation" opensearch_vector_mapping_validation
run_step "Ollama embedding generation validation" ollama_embedding_generation_validation
run_step "OpenSearch vector retrieval validation" opensearch_vector_retrieval_validation
run_step "OpenSearch hybrid retrieval validation" opensearch_hybrid_retrieval_validation

run_step "Actuator health endpoint" actuator_health_endpoint
run_step "Public health endpoint" public_health_endpoint
run_step "Version endpoint" version_endpoint
run_step "AI chat endpoint" ai_chat_endpoint
run_step "Analyze API endpoint" analyze_api_endpoint
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

run_step "V9 repository structure validation" v9_repo_structure_validation
run_step "V9 parent modules validation" v9_parent_modules_validation
run_step "V9 docs validation" v9_docs_validation
run_step "V9 Maven reactor validation" v9_maven_reactor_validation
run_step "V9 Java SDK source validation" v9_java_sdk_source_validation
run_step "V9 Java SDK tests validation" v9_java_sdk_tests_validation
run_step "V9 Java SDK examples validation" v9_java_sdk_examples_validation
run_step "V9 Spring Boot starter source validation" v9_starter_source_validation
run_step "V9 Spring Boot starter tests validation" v9_starter_tests_validation
run_step "V9 Spring Boot starter example validation" v9_starter_example_validation
run_step "V9 TypeScript SDK source validation" v9_typescript_sdk_source_validation
run_step "V9 TypeScript SDK package metadata validation" v9_typescript_sdk_package_metadata_validation
run_step "V9 TypeScript SDK build/test/pack validation" v9_typescript_sdk_build_test_pack_validation
run_step "V9 TypeScript SDK audit validation" v9_typescript_sdk_audit_validation
run_step "V9 TypeScript SDK exports validation" v9_typescript_sdk_exports_validation
run_step "V9 TypeScript SDK runtime analyze validation" v9_typescript_sdk_runtime_analyze_validation
run_step "V9 TypeScript SDK runtime SSE validation" v9_typescript_sdk_runtime_sse_validation
run_step "V9 Java SDK runtime compile validation" v9_java_sdk_runtime_compile_validation
run_step "V9 release docs stale validation" v9_release_docs_no_stale_v8_only_validation
run_step "V9 quality performance scripts validation" v9_quality_performance_scripts_validation
run_step "V9 performance script validation" v9_performance_script_validation
run_step "V9 dockerized k6 compose validation" v9_dockerized_k6_compose_validation
run_step "V9 optional performance validation" v9_optional_performance_validation

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
