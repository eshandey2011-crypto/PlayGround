#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
PORT="${PORT:-3000}"
export PORT
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
DIST="$PROJECT_DIR/dist"

/usr/bin/time -p mkdir -p "$WEB_DIR"
if [[ -f package.json && ! -d node_modules ]]; then
  if [[ -f package-lock.json ]]; then
    /usr/bin/time -p npm ci --no-audit --no-fund
  else
    /usr/bin/time -p npm install --no-audit --no-fund
  fi
fi
/usr/bin/time -p node build.mjs
/usr/bin/time -p test -f "$DIST/index.html"
/usr/bin/time -p /usr/bin/printf '{"project":"%s","directory":"%s"}\n' "$PROJECT_DIR" "$DIST" > "$WEB_DIR/deployment-output.json"
/usr/bin/time -p node -e "JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); console.log('deployment-output.json valid')" "$WEB_DIR/deployment-output.json"
echo "Serving $DIST on port $PORT"
exec node server.mjs
