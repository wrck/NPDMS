#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
PROJECT=npdms-direct-version-20261007
RUN=.run/direct-version-20261007
mkdir -p "$RUN"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT")" ]]; then
  echo 'Refusing to reuse an existing test environment' >&2; exit 1
fi
export SURVEY_ISOLATED_ENV_ID="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
export SURVEY_ISOLATED_MYSQL_SCHEMA="survey_it_$SURVEY_ISOLATED_ENV_ID"
export SURVEY_ISOLATED_MYSQL_PORT=28474
export SURVEY_ISOLATED_DB_PASSWORD="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
cat > "$RUN/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: ${SURVEY_ISOLATED_MYSQL_SCHEMA}
      MYSQL_USER: survey_it
      MYSQL_PASSWORD: ${SURVEY_ISOLATED_DB_PASSWORD}
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:28474:3306']
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
"${COMPOSE[@]}" exec -T mysql mysql -uroot "$SURVEY_ISOLATED_MYSQL_SCHEMA" -e "CREATE TABLE survey_test_environment (environment_id VARCHAR(32) NOT NULL); INSERT INTO survey_test_environment VALUES ('$SURVEY_ISOLATED_ENV_ID');"
export SURVEY_ISOLATED_SERVER_UUID="$("${COMPOSE[@]}" exec -T mysql mysql -uroot -N -B -e 'SELECT @@server_uuid')"
SETTINGS=(); if [[ -n "${DELIVERY_MAVEN_SETTINGS:-}" ]]; then SETTINGS+=(-s "$DELIVERY_MAVEN_SETTINGS"); fi
mvn -B "${SETTINGS[@]}" -pl pms-module-engineering -am test \
  -Dtest="${VERSION_TESTS:-DirectVersionedBusinessTest}" -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnpdms.survey.mysql.optIn=true -Dnpdms.version.browser=${VERSION_BROWSER:-false} > "$RUN/mysql.log" 2>&1
tail -n 12 "$RUN/mysql.log"
