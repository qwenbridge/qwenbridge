#!/usr/bin/env bash
# QB-294: Quarkus-only architecture certification. Independent of a successful build:
#   - Java 25 for the JVM and for Maven;
#   - every module's resolved dependency tree (all scopes, transitive) and every build plugin with
#     its own dependencies: no artifact of the other framework, including Quarkus' compatibility
#     extensions for it;
#   - the repository audit (scripts/verification/quarkus_only_audit.py): sources, build files,
#     configuration, environment templates, Docker, Compose, CI and scripts; documentation only in
#     reviewed, classified files;
#   - the packaged application and the running Compose container: the Quarkus fast-jar, no such
#     libraries, the Quarkus health endpoint answering.
# Read-only: nothing here starts, stops or changes Docker services or data.

qb294_quarkus_only_validation() {
  local work status=0
  work="$(section1_work_dir)/qb294" || return 1
  mkdir -p "${work}" || return 1

  local java_major maven_java
  java_major="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)"
  maven_java="$(mvn -version 2>/dev/null | sed -nE 's/^Java version: ([0-9]+).*/\1/p' | head -1)"
  echo "java ${java_major}, Maven on Java ${maven_java}"
  if [[ "${java_major}" != "25" || "${maven_java}" != "25" ]]; then
    echo "Java 25 is required for the JVM and Maven." >&2
    return 1
  fi

  find . -path ./node_modules -prune -o -name 'qb294-*.txt' -path '*/target/*' -print0 \
    | xargs -0 rm -f
  mvn -B -q dependency:tree -DoutputFile=target/qb294-tree.txt >"${work}/tree.log" 2>&1 || {
    tail -20 "${work}/tree.log" >&2
    return 1
  }
  mvn -B -q dependency:resolve-plugins -DoutputFile=target/qb294-plugins.txt \
    >"${work}/plugins.log" 2>&1 || {
    tail -20 "${work}/plugins.log" >&2
    return 1
  }
  local trees=() plugins=() modules
  while IFS= read -r f; do trees+=("${f}"); done \
    < <(find . -path '*/node_modules' -prune -o -path '*/target/qb294-tree.txt' -print | sort)
  while IFS= read -r f; do plugins+=("${f}"); done \
    < <(find . -path '*/node_modules' -prune -o -path '*/target/qb294-plugins.txt' -print | sort)
  modules=$(( $(grep -c '<module>' pom.xml) + 1 ))
  cat "${trees[@]}" >"${work}/dependency-trees.txt"
  cat "${plugins[@]}" >"${work}/plugin-dependencies.txt"
  python3 -I scripts/verification/quarkus_only_audit.py . \
    --trees "${trees[@]}" --plugins "${plugins[@]}" --modules "${modules}" \
    --report "${work}/quarkus-only-audit.json" || status=1

  local lib=qwenbridge-server/target/quarkus-app
  if [[ ! -f "${lib}/quarkus-run.jar" ]]; then
    echo "The packaged Quarkus fast-jar is missing: ${lib}/quarkus-run.jar" >&2
    status=1
  elif find "${lib}" -iname '*spr''ing*' | grep -q .; then
    echo "The packaged application contains framework libraries:" >&2
    find "${lib}" -iname '*spr''ing*' >&2
    status=1
  else
    echo "packaged application: quarkus-run.jar, $(find "${lib}/lib" -name '*.jar' | wc -l | tr -d ' ') libraries, none of the other framework"
  fi

  if ! docker inspect qwenbridge-app >/dev/null 2>&1; then
    echo "The Compose application container qwenbridge-app is not running." >&2
    status=1
  else
    local command libs health
    command="$(docker inspect qwenbridge-app --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}')"
    libs="$(docker exec qwenbridge-app sh -c 'find /app -iname "*.jar" | wc -l; find /app -iname "*spr""ing*" | wc -l')"
    health="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_PORT:-8080}/q/health/ready" || true)"
    echo "container command: ${command}; jars and framework files: $(echo ${libs}); /q/health/ready ${health}"
    if [[ "${command}" != *quarkus-run.jar* || "$(echo "${libs}" | tail -1)" != "0" || "${health}" != "200" ]]; then
      echo "The default Docker deployment is not the Quarkus fast-jar or is not healthy." >&2
      status=1
    fi
  fi
  return "${status}"
}
