#!/usr/bin/env bash
set -euo pipefail
NATIVE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$NATIVE_REPO_ROOT"
NATIVE_PROJECT=npdms-native-delivery-stage2-20261006
NATIVE_RUN_DIR=.run/native-delivery-stage2-20261006
mkdir -p "$NATIVE_RUN_DIR"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$NATIVE_PROJECT")" ]]; then
  echo "Refusing to reuse an existing container for $NATIVE_PROJECT" >&2
  exit 1
fi
cat > "$NATIVE_RUN_DIR/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: native_delivery_verify
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:28471:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
YAML
NATIVE_COMPOSE=(docker compose -p "$NATIVE_PROJECT" -f "$NATIVE_RUN_DIR/compose.yaml")
trap '"${NATIVE_COMPOSE[@]}" down' EXIT
"${NATIVE_COMPOSE[@]}" up -d --wait
NATIVE_MAVEN_ARGS=()
if [[ -n "${NATIVE_DELIVERY_MAVEN_SETTINGS:-}" ]]; then
  NATIVE_MAVEN_ARGS+=(-s "$NATIVE_DELIVERY_MAVEN_SETTINGS")
fi
# Only model-derived tables in this exclusive tmpfs database; no Flyway/real-data migration.
mvn -B "${NATIVE_MAVEN_ARGS[@]}" -pl pms-module-engineering -am test \
  -Dtest="${NATIVE_DELIVERY_TESTS:-NativeAttachmentDeliveryMySqlTest}" -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnative.delivery.mysql=true -Dnative.delivery.browser=true \
  '-Dnative.delivery.jdbcUrl=jdbc:mysql://127.0.0.1:28471/native_delivery_verify?useSSL=false&allowPublicKeyRetrieval=true' \
  > "$NATIVE_RUN_DIR/mysql-browser.log" 2>&1
tail -n 12 "$NATIVE_RUN_DIR/mysql-browser.log"
