#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/../.."

# Test the verifier itself: command failures and skips must reach its final exit status.
PASSED=0 FAILED=0 WARNINGS=0 SKIPPED=0
SSE_PRIMARY_PID="" SSE_UNRELATED_PID=""
GREEN="" RED="" YELLOW="" BLUE="" NC=""
source scripts/verification/01-common.sh
source scripts/verification/99-summary.sh

run_step success true >/dev/null
[[ "${PASSED}" -eq 1 && "${FAILED}" -eq 0 ]]
failure_status=0
run_step failure bash -c 'exit 23' >/dev/null || failure_status=$?
[[ "${failure_status}" -eq 23 && "${FAILED}" -eq 1 ]]
if print_summary >/dev/null; then
  echo "Verifier incorrectly passed a failed command" >&2
  exit 1
fi
skip_status=0
run_step skipped bash -c 'exit 77' >/dev/null || skip_status=$?
[[ "${skip_status}" -eq 77 && "${SKIPPED}" -eq 1 && "${PASSED}" -eq 1 ]]

FAILED=0
print_summary >/dev/null

while IFS= read -r script; do
  bash -n "${script}"
  /bin/bash -n "${script}"
done < <(find scripts -name '*.sh' -type f)
[[ -f scripts/qwenbridge-final-verification.sh && ! -e qwenbridge-final-verification.sh ]]

for script in scripts/performance/*.js; do
  node --check "${script}"
done

source scripts/verification/java-env.sh
configure_java25 >/dev/null
[[ "$(java_major "$(command -v java)")" == "25" ]]
if (QWENBRIDGE_JAVA_HOME=/does/not/exist; configure_java25) >/dev/null 2>&1; then
  echo "Verifier accepted a missing JDK" >&2
  exit 1
fi

python3 - <<'PY'
import importlib.util
from pathlib import Path
import tempfile
import shutil
spec = importlib.util.spec_from_file_location("versions", "scripts/verification/check-versions.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
root = Path.cwd()
assert mod.check(root)[1] == [], mod.check(root)
with tempfile.TemporaryDirectory() as tmp:
    fixture = Path(tmp)
    for path in ["pom.xml", "qwenbridge-core/pom.xml", "qwenbridge-plugin-sdk/pom.xml", "qwenbridge-commerce-pack/pom.xml", "qwenbridge-library-pack/pom.xml", "qwenbridge-server/pom.xml", "qwenbridge-java-sdk/pom.xml",
                 "qwenbridge-typescript-sdk/package.json",
                 "qwenbridge-typescript-sdk/package-lock.json", "docker-compose.yml",
                 "qwenbridge-server/Dockerfile", "qwenbridge-server/src/main/resources/application.yaml",
                 ".github/workflows/ci.yml"]:
        dest = fixture / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / path, dest)
    pom = fixture / "pom.xml"
    pom.write_text(pom.read_text().replace("<maven.compiler.release>25", "<maven.compiler.release>21"))
    assert any("maven.compiler.release" in e for e in mod.check(fixture)[1])
    sdk = fixture / "qwenbridge-typescript-sdk/package.json"
    sdk.write_text(sdk.read_text().replace('"version": "0.9.1"', '"version": "0.1.0"'))
    assert any("package/lock" in e for e in mod.check(fixture)[1])
    workflow = fixture / ".github/workflows/ci.yml"
    workflow.write_text(workflow.read_text().replace("java-version: 25", "java-version: 21"))
    assert any("CI Java" in e for e in mod.check(fixture)[1])
    workflow.write_text(workflow.read_text().replace(
        "actions/setup-node@820762786026740c76f36085b0efc47a31fe5020", "actions/setup-node@v7"))
    assert any("full commit SHA" in e for e in mod.check(fixture)[1])
spec = importlib.util.spec_from_file_location("performance", "scripts/verification/run-performance.py")
performance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(performance)
summary = {"metrics": {
    "iterations": {"count": 1000}, "http_reqs": {"count": 1000},
    "checks": {"passes": 6000, "fails": 0}, "http_req_failed": {"value": 0},
}}
assert performance.inspect_result(summary, 1000, 0)["passed"]
assert not performance.inspect_result(summary, 1000000, 0)["passed"]
assert not performance.inspect_result(summary, 1000, 99)["passed"]
summary["metrics"]["checks"]["fails"] = 1
assert not performance.inspect_result(summary, 1000, 0)["passed"]
summary["metrics"]["checks"]["fails"] = 0
summary["metrics"]["http_reqs"]["count"] = 999
assert not performance.inspect_result(summary, 1000, 0)["passed"]
assert not performance.inspect_result({}, 1000, 0)["passed"]
summary["metrics"]["http_reqs"]["count"] = 8000
assert performance.inspect_result(summary, 1000, 0, 8)["passed"]
assert not performance.inspect_result(summary, 1000, 0)["passed"]
print("PASS: exit status, skipped checks, syntax, JDK, versions and complete performance request counts")
PY

# QB-294: the Quarkus-only audit must reject each kind of violation and accept a clean tree.
python3 -I - <<'PY'
import importlib.util, os, subprocess, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location("audit", "scripts/verification/quarkus_only_audit.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
W = "spr" + "ing"
def run(files, trees=None):
    with tempfile.TemporaryDirectory() as d:
        subprocess.run(["git", "init", "-q", d], check=True)
        for name, text in files.items():
            p = Path(d, name); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
        tree_files = []
        for i, text in enumerate(trees or []):
            t = Path(d, f"tree{i}.txt"); t.write_text(text); tree_files.append(str(t))
        return audit.audit(d, tree_files, [], None)[0]
clean = {"m/src/main/java/A.java": "@ApplicationScoped\nclass A {}\n",
         "m/src/main/resources/application.yaml": "quarkus:\n  http:\n    port: 8080\n",
         "Dockerfile": "CMD [\"java\", \"-jar\", \"/app/quarkus-run.jar\"]\n"}
assert run(clean) == [], run(clean)
violations = {
    "annotation": {"m/src/main/java/B.java": "@Autowired\nX x;\n"},
    "startup": {"m/src/main/java/C.java": "class C { void m() { " + W.capitalize() + "Application.run(C.class); } }\n"},
    "test support": {"m/src/test/java/D.java": "@" + W.capitalize() + "BootTest\nclass D {}\n"},
    "configuration key": {"m/src/main/resources/application.yaml": W + ":\n  datasource:\n    url: x\n"},
    "management key": {"m/src/main/resources/application.properties": "management.endpoints.web.exposure.include=*\n"},
    "environment": {"docker-compose.yml": "services:\n  a:\n    environment:\n      " + W.upper() + "_PROFILES_ACTIVE: prod\n"},
    "packaging": {"Dockerfile": "ENTRYPOINT [\"java\", \"org.springframework.boot.loader.launch.JarLauncher\"]\n"},
    "build plugin": {"pom.xml": "<plugin><artifactId>" + W + "-boot-maven-plugin</artifactId></plugin>\n"},
    "unreviewed document": {"docs/guide.md": "Run it with " + W.capitalize() + " Boot.\n"},
}
for kind, files in violations.items():
    assert run({**clean, **files}), kind
assert run(clean, ["io.x:a:jar:1.0:compile\n+- org." + W + "framework:" + W + "-core:jar:6.2.0:compile\n"]), "tree"
assert run(clean, ["\\- io.quarkus:quarkus-" + W + "-di:jar:3.40.1:compile\n"]), "compatibility layer"
assert run(clean, ["io.x:a:jar:1.0:compile\n"]) == []
print("PASS: Quarkus-only audit rejects annotations, startup, test support, configuration, environment, packaging, plugins, unreviewed documents and resolved artifacts")
PY
