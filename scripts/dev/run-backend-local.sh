#!/usr/bin/env bash
# Run the backend locally WITHOUT Docker, PostgreSQL or Redis.
#
# Uses an H2 file database (PostgreSQL mode) via the test classpath, where H2
# lives. Redis is optional: the app falls back to in-memory rate limiting.
# Payments run against the Paystack stub; SMS/WhatsApp/email are log-only.
#
#   scripts/dev/run-backend-local.sh            # port 8080
#   PORT=8082 scripts/dev/run-backend-local.sh  # another port
#   RESET_DB=1 scripts/dev/run-backend-local.sh # start from an empty database
#   DB_DIR=/path/to/dir scripts/dev/run-backend-local.sh  # use another database folder
#
# Data:  backend/target/dev-db/      Log: backend/target/dev-backend.log
# Then seed it:  python scripts/dev/seed_local.py
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$ROOT/backend"
DB_DIR="${DB_DIR:-$BACKEND/target/dev-db}"
LOG="$BACKEND/target/dev-backend.log"
mkdir -p "$DB_DIR"

if [[ "${RESET_DB:-0}" == "1" ]]; then
  rm -f "$DB_DIR"/rm.*.db "$DB_DIR"/rm.trace.db
fi

# H2 needs an absolute path; convert on Git Bash / Windows.
DB_PATH="$DB_DIR/rm"
if command -v cygpath >/dev/null 2>&1; then DB_PATH="$(cygpath -m "$DB_PATH")"; fi

export PORT="${PORT:-8080}"
export DB_URL="jdbc:h2:file:${DB_PATH};MODE=PostgreSQL;AUTO_SERVER=TRUE"
export DB_USERNAME=sa DB_PASSWORD=
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_FLYWAY_ENABLED=false
export SPRING_JPA_HIBERNATE_DDL_AUTO=update
export MANAGEMENT_TRACING_ENABLED=false
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0
export PUBLIC_WEB_URL="${PUBLIC_WEB_URL:-http://localhost:3000}"
export MEDIA_LOCAL_DIR="${MEDIA_LOCAL_DIR:-$BACKEND/target/dev-uploads}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://localhost:19006}"
export WEBSOCKET_ALLOWED_ORIGIN_PATTERNS="${WEBSOCKET_ALLOWED_ORIGIN_PATTERNS:-http://localhost:3000,http://localhost:19006}"

echo "Backend on http://localhost:${PORT}  (H2: ${DB_PATH}, log: ${LOG})"
cd "$BACKEND"
exec mvn -q -DskipTests spring-boot:run -Dspring-boot.run.useTestClasspath=true 2>&1 | tee "$LOG"
