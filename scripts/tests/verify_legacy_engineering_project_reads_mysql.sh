#!/usr/bin/env bash
set -euo pipefail
IMP_READ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$IMP_READ_ROOT"
IMP_READ_PROJECT=npdms-imp-project-reads-20261006
IMP_READ_RUN_DIR=.run/imp-project-reads-20261006
mkdir -p "$IMP_READ_RUN_DIR"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$IMP_READ_PROJECT")" ]]; then
  echo "Refusing to reuse an existing container for $IMP_READ_PROJECT" >&2
  exit 1
fi
cat > "$IMP_READ_RUN_DIR/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: imp_project_reads_verify
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:27601:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
YAML
IMP_READ_COMPOSE=(docker compose -p "$IMP_READ_PROJECT" -f "$IMP_READ_RUN_DIR/compose.yaml")
trap '"${IMP_READ_COMPOSE[@]}" down' EXIT
"${IMP_READ_COMPOSE[@]}" up -d --wait
IMP_READ_MAVEN_ARGS=()
if [[ -n "${IMP_READ_MAVEN_SETTINGS:-}" ]]; then
  IMP_READ_MAVEN_ARGS+=(-s "$IMP_READ_MAVEN_SETTINGS")
fi
# Dedicated model tables, production Spring bindings/permission guard/mappers;
# actual system roles/permissions, controlled project-scope port. No original Flyway runs here.
mvn -B "${IMP_READ_MAVEN_ARGS[@]}" -pl yudao-server -am test \
  -Dtest=LegacyProjectBusinessReadMySqlTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnpdms.imp-read.mysql=true \
  '-Dnpdms.imp-read.jdbcUrl=jdbc:mysql://127.0.0.1:27601/imp_project_reads_verify?useSSL=false&allowPublicKeyRetrieval=true' \
  > "$IMP_READ_RUN_DIR/mysql.log" 2>&1
tail -n 12 "$IMP_READ_RUN_DIR/mysql.log"
