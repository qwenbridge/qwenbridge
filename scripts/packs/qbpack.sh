#!/usr/bin/env bash
# Local pack distribution workflow (QB-278): keygen, pack, verify, inspect, request.
# Uses the plugin SDK's PackTool with the JDK only. See docs/architecture/pack-distribution.md.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLASSES="$ROOT/qwenbridge-plugin-sdk/target/classes"
if [[ ! -f "$CLASSES/io/qwenbridge/pack/tool/PackTool.class" ]]; then
  echo "Build the plugin SDK first: mvn -q -pl qwenbridge-plugin-sdk -DskipTests package" >&2
  exit 2
fi
exec java -cp "$CLASSES" io.qwenbridge.pack.tool.PackTool "$@"
