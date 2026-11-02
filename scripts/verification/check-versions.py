#!/usr/bin/env python3
"""Validate the current reactor; historical release documents are intentionally excluded."""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def check(root):
    errors = []
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    parent = ET.parse(root / "pom.xml").getroot()
    version = parent.findtext("m:version", namespaces=ns)
    for property_name in ("java.version", "maven.compiler.release"):
        if parent.findtext(f"m:properties/m:{property_name}", namespaces=ns) != "25":
            errors.append(f"Parent {property_name} must be 25")
    for module in parent.findall("m:modules/m:module", ns):
        path = root / module.text / "pom.xml"
        pom = ET.parse(path).getroot()
        if "spring" in module.text.lower():
            errors.append(f"Unexpected Spring module: {module.text}")
        if pom.findtext("m:parent/m:version", namespaces=ns) != version:
            errors.append(f"{path}: parent version differs from {version}")
        for property_name in ("java.version", "maven.compiler.release"):
            override = pom.findtext(f"m:properties/m:{property_name}", namespaces=ns)
            if override is not None and override != "25":
                errors.append(f"{path}: {property_name} must inherit Java 25")
    for module in ("qwenbridge-typescript-sdk",):
        package = json.loads((root / module / "package.json").read_text())
        lock = json.loads((root / module / "package-lock.json").read_text())
        if package["version"] != version or lock["version"] != version or lock["packages"][""]["version"] != version:
            errors.append(f"{module}: package/lock versions must equal {version}")
    dockerfile = (root / "qwenbridge-server/Dockerfile").read_text()
    if "eclipse-temurin-25" not in dockerfile or "eclipse-temurin:25-jre" not in dockerfile:
        errors.append("Docker build and runtime must use Java 25")
    if "target/quarkus-app/" not in dockerfile or "/app/quarkus-run.jar" not in dockerfile:
        errors.append("Docker must copy and launch the complete Quarkus fast-jar distribution")
    if "spring" in dockerfile.lower():
        errors.append("Docker must not copy deleted Spring modules")
    config = (root / "qwenbridge-server/src/main/resources/application.yaml").read_text()
    if config.count("version: ${quarkus.application.version}") != 2:
        errors.append("Application and OpenAPI versions must follow the Quarkus build version")
    compose = (root / "docker-compose.yml").read_text()
    if f'org.opencontainers.image.version: "{version}"' not in compose:
        errors.append("Compose image version must match the reactor")
    for workflow in (root / ".github/workflows").glob("*.yml"):
        for java in re.findall(r"java-version:\s*[\"']?([0-9]+)", workflow.read_text()):
            if java != "25":
                errors.append(f"{workflow.name}: CI Java must be 25")
    return version, errors


if __name__ == "__main__":
    project = Path(__file__).resolve().parents[2]
    try:
        release, problems = check(project)
    except (OSError, KeyError, ValueError, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        sys.exit(1)
    print(f"PASS: {release}; Quarkus; Java 25 throughout the reactor, Docker and CI")
