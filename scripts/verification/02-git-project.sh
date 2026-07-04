check_project_root() {
  echo "Project root: ${PROJECT_ROOT}"

  [[ -f "pom.xml" ]] \
    && [[ -f "${COMPOSE_FILE}" ]] \
    && { [[ -f "Dockerfile" ]] || [[ -f "${SERVER_DIR}/Dockerfile" ]]; }
}

check_required_tools() {
  local ok=0
  local cmd=""

  for cmd in git docker curl jq lsof awk grep sed; do
    if require_command "${cmd}"; then
      pass "Command available: ${cmd}"
    else
      fail "Command missing: ${cmd}"
      ok=1
    fi
  done

  if docker compose version >/dev/null 2>&1; then
    pass "Command available: docker compose"
  else
    fail "Command missing: docker compose"
    ok=1
  fi

  return "${ok}"
}

check_release_tag() {
  local tag_commit=""
  local head_commit=""

  tag_commit="$(git rev-list -n 1 "${EXPECTED_TAG}" 2>/dev/null || true)"
  head_commit="$(git rev-parse HEAD)"

  echo "Expected tag: ${EXPECTED_TAG}"
  echo "Tag commit: ${tag_commit}"
  echo "HEAD commit: ${head_commit}"

  if [[ -z "${tag_commit}" ]]; then
    if [[ "${EXPECTED_TAG_REQUIRED}" == "true" ]]; then
      return 1
    fi

    warn "Expected tag ${EXPECTED_TAG} does not exist yet. Skipping strict tag validation."
    return 0
  fi

  if [[ "${tag_commit}" != "${head_commit}" ]]; then
    if [[ "${EXPECTED_TAG_REQUIRED}" == "true" ]]; then
      return 1
    fi

    warn "Expected tag ${EXPECTED_TAG} is not on HEAD yet. Skipping strict tag validation."
    return 0
  fi

  return 0
}

check_git_state() {
  local branch=""

  branch="$(git branch --show-current)"

  echo "Current branch: ${branch}"
  git status --short

  if [[ "${branch}" != "${EXPECTED_BRANCH}" ]]; then
    warn "Expected branch ${EXPECTED_BRANCH}, current branch is ${branch}"
  fi

  if ! git diff --quiet || ! git diff --cached --quiet; then
    warn "Working tree is not clean. This is allowed during local verification."
  fi

  return 0
}
