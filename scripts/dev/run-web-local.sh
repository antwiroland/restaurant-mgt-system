#!/usr/bin/env bash
# Build and serve the web app against a local backend.
#
#   scripts/dev/run-web-local.sh                       # backend http://localhost:8080, web :3000
#   BACKEND_PORT=8083 scripts/dev/run-web-local.sh     # backend on another port
#   SKIP_BUILD=1 scripts/dev/run-web-local.sh          # reuse the last build
#
# NEXT_PUBLIC_* values are baked in at build time, so changing the backend
# port (WebSocket URL) or the restaurant details requires a rebuild.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-8080}"
WEB_PORT="${WEB_PORT:-3000}"

export RESTAURANT_MANAGER_BACKEND_URL="${RESTAURANT_MANAGER_BACKEND_URL:-http://localhost:${BACKEND_PORT}}"
export NEXT_PUBLIC_BACKEND_WS_URL="${NEXT_PUBLIC_BACKEND_WS_URL:-ws://localhost:${BACKEND_PORT}/ws}"
# Sample restaurant details for local testing only — leave unset in real deployments
# you haven't configured, and the pages hide these sections.
export NEXT_PUBLIC_RESTAURANT_HOURS="${NEXT_PUBLIC_RESTAURANT_HOURS:-Mon–Thu 11:30–22:00|Fri–Sat 11:30–23:30|Sun 12:00–21:00}"
export NEXT_PUBLIC_RESTAURANT_ADDRESS="${NEXT_PUBLIC_RESTAURANT_ADDRESS:-12 Oxford Street, Osu, Accra}"

cd "$ROOT/web"
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  npx next build
fi
echo "Web on http://localhost:${WEB_PORT}  ->  API ${RESTAURANT_MANAGER_BACKEND_URL}"
exec node node_modules/next/dist/bin/next start -p "$WEB_PORT"
