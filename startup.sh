#!/usr/bin/env bash
set -euo pipefail
/usr/bin/time -p node --version
/usr/bin/time -p mkdir -p "${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
