#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
: "${CAPTURE_URL:?Set CAPTURE_URL to the preview URL}"
: "${CAPTURE_DIR:?Set CAPTURE_DIR to an output directory}"
/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
/usr/bin/time -p rm -f "$CAPTURE_DIR/final-desktop.png" "$CAPTURE_DIR/final-mobile.png"
/usr/bin/time -p node scripts/capture.mjs
/usr/bin/time -p test -s "$CAPTURE_DIR/final-desktop.png"
/usr/bin/time -p test -s "$CAPTURE_DIR/final-mobile.png"
/usr/bin/time -p node -e "const fs=require('fs');for(const n of ['desktop','mobile']){const b=fs.readFileSync(process.argv[1]+'/final-'+n+'.png');if(b.length<15000||b.subarray(1,4).toString()!=='PNG'){console.error('Invalid '+n+' capture');process.exit(1)}console.log(n+' capture '+b.length+' bytes')}" "$CAPTURE_DIR"
echo "Captures ready in $CAPTURE_DIR"
