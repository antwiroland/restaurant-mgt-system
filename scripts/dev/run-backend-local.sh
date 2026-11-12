#!/usr/bin/env bash
# Run the backend on this machine (not in a container) for local testing.
#
# Two database modes:
#   DB=postgres  PostgreSQL + Redis from docker-compose.yml (containers restaurant_postgres,
#                restaurant_redis). Flyway migrations run as in production.
#   DB=h2        (default) an H2 file database in PostgreSQL mode, via the test classpath
#                where H2 lives. No Docker needed; Redis optional (in-memory rate limiting).
# Payments run against the Paystack stub; SMS/WhatsApp/email are log-only.
#
#   DB=postgres scripts/dev/run-backend-local.sh            # port 8080, Docker Postgres
#   DB=postgres RESET_DB=1 scripts/dev/run-backend-local.sh # drop and recreate the Postgres database first
#   scripts/dev/run-backend-local.sh                        # H2
#   PORT=8082 scripts/dev/run-backend-local.sh              # another port
#   RESET_DB=1 scripts/dev/run-backend-local.sh             # H2: start from an empty database
#   DB_DIR=/path/to/dir scripts/dev/run-backend-local.sh    # H2: use another database folder
#
# Log: backend/target/dev-backend.log   H2 data: backend/target/dev-db/
# Then seed it:  python scripts/dev/seed_local.py [--pg]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$ROOT/backend"
LOG="$BACKEND/target/dev-backend.log"
DB="${DB:-h2}"
mkdir -p "$BACKEND/target"

export PORT="${PORT:-8080}"
export MANAGEMENT_TRACING_ENABLED=false
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0
export PUBLIC_WEB_URL="${PUBLIC_WEB_URL:-http://localhost:3000}"
export MEDIA_LOCAL_DIR="${MEDIA_LOCAL_DIR:-$BACKEND/target/dev-uploads}"
export HR_DOCUMENTS_DIR="${HR_DOCUMENTS_DIR:-$BACKEND/target/dev-hr-documents}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://localhost:19006}"
export WEBSOCKET_ALLOWED_ORIGIN_PATTERNS="${WEBSOCKET_ALLOWED_ORIGIN_PATTERNS:-http://localhost:3000,http://localhost:19006}"

if [[ "$DB" == "postgres" ]]; then
  PG=restaurant_postgres
  DB_NAME="${DB_NAME:-restaurant_manager}"
  (cd "$ROOT" && docker compose up -d postgres redis)
  for _ in $(seq 1 60); do
    docker exec "$PG" pg_isready -U postgres -d "$DB_NAME" >/dev/null 2>&1 && break
    sleep 1
  done
  if [[ "${RESET_DB:-0}" == "1" ]]; then
    docker exec "$PG" psql -q -U postgres -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)" -c "CREATE DATABASE $DB_NAME"
    rm -rf "$HR_DOCUMENTS_DIR"
  fi
  export DB_URL="jdbc:postgresql://localhost:5432/${DB_NAME}" DB_USERNAME=postgres DB_PASSWORD=postgres
  export CACHE_REDIS_ENABLED=true REDIS_HOST=localhost REDIS_PORT=6379
  echo "Backend on http://localhost:${PORT}  (PostgreSQL: container $PG, database $DB_NAME; log: ${LOG})"
  cd "$BACKEND"
  exec mvn -q -DskipTests spring-boot:run 2>&1 | tee "$LOG"
fi

DB_DIR="${DB_DIR:-$BACKEND/target/dev-db}"
mkdir -p "$DB_DIR"
if [[ "${RESET_DB:-0}" == "1" ]]; then
  rm -f "$DB_DIR"/rm.*.db "$DB_DIR"/rm.trace.db
fi

# H2 needs an absolute path; convert on Git Bash / Windows.
DB_PATH="$DB_DIR/rm"
if command -v cygpath >/dev/null 2>&1; then DB_PATH="$(cygpath -m "$DB_PATH")"; fi

export DB_URL="jdbc:h2:file:${DB_PATH};MODE=PostgreSQL;AUTO_SERVER=TRUE"
export DB_USERNAME=sa DB_PASSWORD=
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_FLYWAY_ENABLED=false
export SPRING_JPA_HIBERNATE_DDL_AUTO=update

echo "Backend on http://localhost:${PORT}  (H2: ${DB_PATH}, log: ${LOG})"
cd "$BACKEND"
exec mvn -q -DskipTests spring-boot:run -Dspring-boot.run.useTestClasspath=true 2>&1 | tee "$LOG"
