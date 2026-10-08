#!/usr/bin/env bash
set -euo pipefail
DELIVERY_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$DELIVERY_REPO_ROOT"
DELIVERY_PROJECT=npdms-default-business-delivery-20261007
DELIVERY_RUN=.run/default-business-delivery-20261007
mkdir -p "$DELIVERY_RUN"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$DELIVERY_PROJECT")" ]]; then
  echo "Refusing to reuse an existing test container" >&2
  exit 1
fi
cat > "$DELIVERY_RUN/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: npdms_declared_framework
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:27461:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
YAML
DELIVERY_COMPOSE=(docker compose -p "$DELIVERY_PROJECT" -f "$DELIVERY_RUN/compose.yaml")
trap '"${DELIVERY_COMPOSE[@]}" down' EXIT
"${DELIVERY_COMPOSE[@]}" up -d --wait
DELIVERY_SETTINGS=()
if [[ -n "${DELIVERY_MAVEN_SETTINGS:-}" ]]; then DELIVERY_SETTINGS+=(-s "$DELIVERY_MAVEN_SETTINGS"); fi
mvn -B "${DELIVERY_SETTINGS[@]}" -pl pms-module-platform -am test \
  -Dtest="${DELIVERY_TESTS:-DefaultBusinessDeliveryMySqlTest,DefaultBusinessDeliveryBrowserMySqlTest#browserTwoDefaultBusinessPagesAndCollection,DeclaredViewDeliveryRuntimePersistenceTest}" -Dsurefire.failIfNoSpecifiedTests=false \
  -Dnpdms.declared.exclusive=true -Ddefault.delivery.browser="${DELIVERY_BROWSER:-true}" \
  '-Dnpdms.declared.jdbcUrl=jdbc:mysql://127.0.0.1:27461/npdms_declared_framework?useSSL=false&allowPublicKeyRetrieval=true' \
  > "$DELIVERY_RUN/mysql.log" 2>&1
 tail -n 12 "$DELIVERY_RUN/mysql.log"
