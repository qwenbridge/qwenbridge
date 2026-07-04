wait_for_docker() {
  local attempt=""

  for attempt in {1..90}; do
    if docker info >/dev/null 2>&1; then
      DOCKER_AVAILABLE=true
      docker version \
        --format 'Client={{.Client.Version}} Server={{.Server.Version}}' \
        || true
      return 0
    fi

    sleep 2
  done

  DOCKER_AVAILABLE=false
  echo "Docker daemon did not become ready."
  return 1
}

restart_docker_daemon() {
  if [[ "${RESTART_DOCKER}" != "true" ]]; then
    info "RESTART_DOCKER=false, skipping Docker restart."
    wait_for_docker
    return $?
  fi

  info "Restarting Docker..."

  if [[ "$(uname -s)" == "Darwin" ]]; then
    osascript -e 'quit app "Docker"' >/dev/null 2>&1 || true
    sleep 5
    open -a Docker >/dev/null 2>&1 || true
    wait_for_docker
    return $?
  fi

  if require_command systemctl; then
    sudo systemctl restart docker || return 1
    wait_for_docker
    return $?
  fi

  warn "Docker restart is not supported automatically on this OS."
  wait_for_docker
}

fresh_environment_reset() {
  rm -f /tmp/qwenbridge-*.json
  rm -f /tmp/qwenbridge-*.headers
  rm -f /tmp/qwenbridge-*.body
  rm -f /tmp/qwenbridge-*.log
  rm -f /tmp/qwenbridge-singleflight-*.json
  rm -f /tmp/qwenbridge-sse-*

  : > "${APP_LOG}"

  if [[ "${FORCE_FRESH}" != "true" ]]; then
    info "FORCE_FRESH=false, keeping current Docker environment."
    return 0
  fi

  info "Stopping and removing QwenBridge Compose stack."
  compose down -v --remove-orphans || true

  info "Force-removing stale QwenBridge containers."
  docker rm -f \
    "${APP_CONTAINER}" \
    "${REDIS_CONTAINER}" \
    "${OLLAMA_CONTAINER}" \
    "${OLLAMA_CONTAINER}-init" \
    "${OPENSEARCH_CONTAINER}" \
    2>/dev/null || true

  docker network rm qwenbridge_default 2>/dev/null || true
}

ensure_env_file() {
  if [[ ! -f ".env" ]]; then
    info "Creating local .env for release verification."
    cat > .env <<'ENVEOF'
QWENBRIDGE_CORS_ALLOWED_ORIGINS=*
ENVEOF
    return 0
  fi

  grep -q '^QWENBRIDGE_CORS_ALLOWED_ORIGINS=' .env || {
    info "Adding missing QWENBRIDGE_CORS_ALLOWED_ORIGINS to .env."
    printf '\nQWENBRIDGE_CORS_ALLOWED_ORIGINS=*\n' >> .env
  }
}

docker_pull_build_up() {
  ensure_env_file

  if [[ "${PULL_DOCKER_IMAGES}" == "true" ]]; then
    compose pull --ignore-pull-failures || true
  else
    info "PULL_DOCKER_IMAGES=false, skipping docker compose pull."
  fi

  if [[ "${NO_CACHE_BUILD}" == "true" ]]; then
    compose build --no-cache || return 1
  else
    compose build || return 1
  fi

  compose up -d --build --force-recreate --remove-orphans

  echo ""
  echo "Compose services after up:"
  compose ps || true
}

wait_for_container_healthy_or_running() {
  local container="$1"
  local mode="${2:-healthy}"
  local attempt=""
  local status=""
  local health=""

  for attempt in {1..120}; do
    status="$(docker inspect -f '{{.State.Status}}' "${container}" 2>/dev/null || true)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container}" 2>/dev/null || true)"

    if [[ "${mode}" == "healthy" && "${health}" == "healthy" ]]; then
      return 0
    fi

    if [[ "${mode}" == "running" && "${status}" == "running" ]]; then
      return 0
    fi

    sleep 2
  done

  echo "Container did not become ${mode}: ${container}"
  docker ps -a
  docker logs --tail 160 "${container}" 2>/dev/null || true
  return 1
}

wait_for_compose_services() {
  wait_for_container_healthy_or_running "${REDIS_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${OLLAMA_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${OPENSEARCH_CONTAINER}" healthy \
    && wait_for_container_healthy_or_running "${APP_CONTAINER}" running
}

docker_runtime_user_validation() {
  local configured_user=""

  configured_user="$(docker inspect "${APP_CONTAINER}" --format '{{.Config.User}}' 2>/dev/null | tr -d '\r')"
  echo "Application container configured user: ${configured_user}"

  [[ "${configured_user}" == "nonroot" ]] || [[ "${configured_user}" == "nonroot:nonroot" ]] || [[ "${configured_user}" == "65532" ]] || [[ "${configured_user}" == "65532:65532" ]]
}

docker_app_healthcheck_validation() {
  local health=""

  health="$(docker inspect "${APP_CONTAINER}" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' 2>/dev/null || true)"
  echo "Application container Docker health: ${health:-not-configured}"

  if [[ "${health}" == "healthy" ]]; then
    return 0
  fi

  app_public_health_up
}

print_redis_keys() {
  docker exec "${REDIS_CONTAINER}" redis-cli keys '*' || true
}

print_sse_logs() {
  echo ""
  echo "========== SSE Primary Headers =========="
  cat /tmp/qwenbridge-sse-primary.headers 2>/dev/null || true

  echo ""
  echo "========== SSE Primary Stream =========="
  cat /tmp/qwenbridge-sse-primary.log 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Headers =========="
  cat /tmp/qwenbridge-sse-unrelated.headers 2>/dev/null || true

  echo ""
  echo "========== SSE Unrelated Stream =========="
  cat /tmp/qwenbridge-sse-unrelated.log 2>/dev/null || true
}

print_relevant_logs() {
  echo ""
  echo "========== Docker Containers =========="
  docker ps -a

  echo ""
  echo "========== App Logs =========="
  docker logs --tail 240 "${APP_CONTAINER}" 2>/dev/null || true

  echo ""
  echo "========== Ollama Models =========="
  docker exec "${OLLAMA_CONTAINER}" ollama list 2>/dev/null || true

  echo ""
  echo "========== OpenSearch Count =========="
  curl -fsS "${OPENSEARCH_URL}/${OPENSEARCH_INDEX}/_count" | jq . || true

  print_sse_logs
}
