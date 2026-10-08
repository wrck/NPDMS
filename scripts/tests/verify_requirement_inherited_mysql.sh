#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
PROJECT=npdms-requirement-inherited-20261007
RUN=.run/requirement-inherited-20261007
mkdir -p "$RUN"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT")" ]]; then
  echo 'Refusing to reuse an existing test environment' >&2; exit 1
fi
export RA_ISOLATED_ENV_ID="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
export RA_ISOLATED_MYSQL_SCHEMA="ra_it_$RA_ISOLATED_ENV_ID"
export RA_ISOLATED_MYSQL_PORT=28473
export RA_ISOLATED_DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
cat > "$RUN/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: ${RA_ISOLATED_MYSQL_SCHEMA}
      MYSQL_USER: ra_it
      MYSQL_PASSWORD: ${RA_ISOLATED_DB_PASSWORD}
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:28473:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
YAML
COMPOSE=(docker compose -p "$PROJECT" -f "$RUN/compose.yaml")
trap '"${COMPOSE[@]}" down' EXIT
"${COMPOSE[@]}" up -d --wait
"${COMPOSE[@]}" exec -T mysql mysql -uroot "$RA_ISOLATED_MYSQL_SCHEMA" -e "CREATE TABLE ra_test_environment (environment_id VARCHAR(32) NOT NULL); INSERT INTO ra_test_environment VALUES ('$RA_ISOLATED_ENV_ID');"
export RA_ISOLATED_SERVER_UUID="$("${COMPOSE[@]}" exec -T mysql mysql -uroot -N -B -e 'SELECT @@server_uuid')"
SETTINGS=(); if [[ -n "${DELIVERY_MAVEN_SETTINGS:-}" ]]; then SETTINGS+=(-s "$DELIVERY_MAVEN_SETTINGS"); fi
mvn -B "${SETTINGS[@]}" -pl pms-module-engineering -am test \
  -Dtest="${REQUIREMENT_TESTS:-RequirementInheritedMySqlTest}" -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnpdms.ra.mysql.optIn=true > "$RUN/mysql.log" 2>&1
tail -n 12 "$RUN/mysql.log"
