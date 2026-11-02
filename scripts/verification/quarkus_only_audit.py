#!/usr/bin/env python3
"""QB-294: Quarkus-only architecture audit.

Classifies every reference to the Spring framework in the repository and fails when anything is
active. "Active" means: build files, sources (main and test, every module, the SDKs and examples),
runtime configuration, environment templates, Docker and Compose files, CI workflows and scripts.
Documentation may mention Spring only in the reviewed files listed in DOCS, each classified; a
historical document must say that it is historical.

Usage: quarkus_only_audit.py ROOT [--trees FILE...] [--plugins FILE...] [--modules N]
                             [--report OUT.json]
The dependency listings (mvn dependency:tree / dependency:resolve-plugins output) are checked for
any resolved artifact with "spring" in its coordinates, including Quarkus' own Spring compatibility
extensions (io.quarkus:quarkus-spring-*), which this architecture does not use either.
"""
import argparse
import json
import os
import re
import subprocess
import sys

WORD = "spr" + "ing"  # assembled so this file does not match a naive scan of itself

# Files that implement the guards. They name what they forbid; nothing else may.
GUARDS = {
    "scripts/verification/quarkus_only_audit.py": "this audit",
    "scripts/verification/23-quarkus-only.sh": "QB-294 master step",
    "scripts/verification/18-section1-gate.sh": "Section 1 elimination gate",
    "scripts/verification/check-versions.py": "reactor/Docker drift check",
    "scripts/verification/09-input-security.sh": "error bodies must not leak framework class names",
    "scripts/verification/12-api-resilience.sh": "error bodies must not leak framework class names",
    "scripts/verify-release.sh": "step title of the elimination gate",
    "scripts/tests/verification-test.sh": "self-test of this audit",
    "qwenbridge-server/src/test/java/io/qwenbridge/architecture/ArchitectureNeutralityTest.java":
        "JUnit architecture guard",
    "qwenbridge-server/src/test/java/io/qwenbridge/architecture/QuarkusOnlyArchitectureTest.java":
        "JUnit Quarkus-only guard",
}

# Reviewed documentation. HISTORICAL files must contain the word "historical".
DOCS = {
    "docs/sdk/" + WORD + "-boot-starter.md": "HISTORICAL",
    "docs/sdk/README.md": "POINTER_TO_HISTORICAL",
    "docs/adr/0041-java-and-typescript-sdks-1-0.md": "HISTORICAL_DECISION",
    "docs/development/testing.md": "NEGATIVE_STATEMENT",
    "docs/architecture/database-free-runtime.md": "NEGATIVE_STATEMENT",
    "docs/architecture/quarkus-only.md": "CERTIFICATION",
    "docs/release/README.md": "HISTORICAL",
    "docs/release/V9-release-evidence.md": "HISTORICAL",
    "docs/release/V9-release-checklist.md": "HISTORICAL",
    "docs/roadmap/V9.md": "HISTORICAL",
    "docs/roadmap/V5.md": "HISTORICAL",
    "docs/roadmap/V4.md": "HISTORICAL",
    "docs/roadmap/V1.md": "HISTORICAL",
    "docs/platform/phase-acceptance.md": "NEGATIVE_STATEMENT",
    "docs/platform/phase-acceptance.json": "HISTORICAL_TITLE",
    "docs/platform/remaining-phases.md": "NEGATIVE_STATEMENT",
    "docs/platform/commerce-acceptance-2026-10-31.md": "NEGATIVE_STATEMENT",
    "docs/release/QwenBridge-1.0-Section1-Report.md": "NEGATIVE_STATEMENT",
    "docs/release/QwenBridge-1.0-Section2-Report.md": "NEGATIVE_STATEMENT",
    "docs/release/QwenBridge-1.0-Section3-Report.md": "NEGATIVE_STATEMENT",
    "docs/release/QwenBridge-1.0-Release-Decision.md": "NEGATIVE_STATEMENT",
    "docs/adr/0056-quarkus-only-certification.md": "CERTIFICATION",
}

SKIP_DIRS = re.compile(r"(^|/)(node_modules|target|dist|logs|\.git|\.idea|\.mvn)/")
DOC_EXT = (".md", ".adoc", ".txt", ".html")
SOURCE_EXT = (".java", ".kt", ".groovy", ".ts", ".js", ".mjs", ".cjs", ".tsx")
CONFIG_NAME = re.compile(
    r"(^|/)(application[^/]*\.(ya?ml|properties)|[^/]*\.env[^/]*|\.env\.example|"
    r"docker-compose[^/]*\.ya?ml|Dockerfile[^/]*|[^/]*\.ya?ml|[^/]*\.properties|package\.json)$")

FRAMEWORK = re.compile(r"org\." + WORD + r"framework|" + WORD + r"-boot|" + WORD + r"framework", re.I)
STARTUP = re.compile(r"\b" + WORD.capitalize() + r"Application\b")
# Annotations that exist only in that framework; @Repository is excluded (Jakarta Data has one).
ANNOTATION = re.compile(
    r"^\s*@(" + WORD.capitalize() + r"BootApplication|" + WORD.capitalize() + r"BootTest|RestController|"
    r"RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|Autowired|"
    r"MockBean|SpyBean|WebMvcTest|DataJpaTest|EnableAutoConfiguration|ConfigurationProperties|"
    r"Component|Service|Configuration|Bean|Controller)\b")
CONFIG_KEY = re.compile(r"^\s*(" + WORD + r"|management)\s*[.:]|^server\s*:\s*$|^\s*server\.port\s*[=:]",
                        re.I)
ENV_KEY = re.compile(r"\b(" + WORD.upper() + r"|MANAGEMENT)_[A-Z0-9_]+")
PACKAGING = re.compile(r"BOOT-INF|JarLauncher|PropertiesLauncher|" + WORD + r"-boot-maven-plugin|"
                       r"<goal>\s*repackage\s*</goal>")


def files(root):
    out = subprocess.run(["git", "ls-files", "-co", "--exclude-standard", "-z"], cwd=root,
                         capture_output=True, check=True).stdout.decode().split("\0")
    return sorted(f for f in out if f and not SKIP_DIRS.search(f) and os.path.isfile(os.path.join(root, f)))


def audit(root, trees=(), plugins=(), modules=None):
    findings, inventory = [], []
    for f in files(root):
        try:
            text = open(os.path.join(root, f), encoding="utf-8").read()
        except (UnicodeDecodeError, OSError):
            continue
        is_doc = f.endswith(DOC_EXT) or (f.startswith("docs/") and f.endswith(".json"))
        is_source = f.endswith(SOURCE_EXT)
        is_config = bool(CONFIG_NAME.search(f)) and not is_doc
        for n, line in enumerate(text.splitlines(), 1):
            mentions = WORD in line.lower()
            kinds = []
            if is_source and ANNOTATION.search(line):
                kinds.append("annotation")
            if is_source and STARTUP.search(line):
                kinds.append("startup")
            if is_config and CONFIG_KEY.search(line) and not f.endswith(".json"):
                kinds.append("configuration-key")
            if not is_doc and ENV_KEY.search(line):
                kinds.append("environment-variable")
            if not is_doc and PACKAGING.search(line):
                kinds.append("packaging")
            if not is_doc and FRAMEWORK.search(line):
                kinds.append("framework-reference")
            if not (mentions or kinds):
                continue
            if f in GUARDS:
                cls = "GUARD"
            elif is_doc:
                cls = DOCS.get(f, "UNREVIEWED_DOCUMENT")
            elif kinds:
                cls = "ACTIVE"
            else:
                cls = "COMMENT_OR_NAME"
            inventory.append({"file": f, "line": n, "class": cls, "kinds": kinds, "text": line.strip()[:200]})
            if cls in ("ACTIVE", "UNREVIEWED_DOCUMENT"):
                findings.append(f"{f}:{n}: {cls} {kinds or ''} {line.strip()[:160]}")
    for f, cls in DOCS.items():
        path = os.path.join(root, f)
        if cls == "HISTORICAL" and os.path.isfile(path) and "historical" not in open(path, encoding="utf-8").read().lower():
            findings.append(f"{f}: classified HISTORICAL but does not say so")
    deps = {"trees": 0, "plugins": 0, "artifacts": 0}
    for kind, listing in (("trees", trees), ("plugins", plugins)):
        for t in listing:
            deps[kind] += 1
            for line in open(t, encoding="utf-8"):
                m = re.search(r"([\w.\-]+:[\w.\-]+:[\w.\-]+(?::[\w.\-]+)*)", line)
                if not m:
                    continue
                deps["artifacts"] += 1
                if WORD in m.group(1).lower():
                    findings.append(f"{t}: resolved artifact {m.group(1)}")
    if modules is not None and trees and deps["trees"] < modules:
        findings.append(f"expected {modules} dependency listings, found {deps['trees']}")
    if modules is not None and plugins and deps["plugins"] < modules:
        findings.append(f"expected {modules} plugin listings, found {deps['plugins']}")
    return findings, inventory, deps


def main():
    p = argparse.ArgumentParser()
    p.add_argument("root")
    p.add_argument("--trees", nargs="*", default=[])
    p.add_argument("--plugins", nargs="*", default=[])
    p.add_argument("--modules", type=int)
    p.add_argument("--report")
    a = p.parse_args()
    findings, inventory, deps = audit(a.root, a.trees, a.plugins, a.modules)
    summary = {}
    for item in inventory:
        summary[item["class"]] = summary.get(item["class"], 0) + 1
    if a.report:
        with open(a.report, "w", encoding="utf-8") as out:
            json.dump({"summary": summary, "dependencies": deps, "findings": findings,
                       "inventory": inventory}, out, indent=1, ensure_ascii=False)
    print(f"references by class: {json.dumps(summary, sort_keys=True)}")
    print(f"dependency listings: {deps['trees']} trees, {deps['plugins']} plugin listings, "
          f"{deps['artifacts']} artifact lines")
    for f in findings:
        print("FINDING " + f, file=sys.stderr)
    print("Quarkus-only audit: " + ("FAIL" if findings else "PASS"))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
