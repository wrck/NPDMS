#!/usr/bin/env bash
set -euo pipefail
OWNER_MATERIAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$OWNER_MATERIAL_ROOT"
OWNER_MATERIAL_PROJECT=npdms-native-owner-material-contract-20261007
OWNER_MATERIAL_RUN_DIR=.run/native-owner-material-contract-20261007
mkdir -p "$OWNER_MATERIAL_RUN_DIR"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$OWNER_MATERIAL_PROJECT")" ]]; then
  echo "Refusing to reuse existing containers for $OWNER_MATERIAL_PROJECT" >&2
  exit 1
fi
cat > "$OWNER_MATERIAL_RUN_DIR/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: npdms_delivery_withdrawal_owner_actions_20261007
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:28501:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
YAML
OWNER_MATERIAL_COMPOSE=(docker compose -p "$OWNER_MATERIAL_PROJECT" -f "$OWNER_MATERIAL_RUN_DIR/compose.yaml")
trap '"${OWNER_MATERIAL_COMPOSE[@]}" down' EXIT
"${OWNER_MATERIAL_COMPOSE[@]}" up -d --wait
OWNER_MATERIAL_MAVEN_ARGS=()
if [[ -n "${OWNER_MATERIAL_MAVEN_SETTINGS:-}" ]]; then
  OWNER_MATERIAL_MAVEN_ARGS+=(-s "$OWNER_MATERIAL_MAVEN_SETTINGS")
fi
mvn -B "${OWNER_MATERIAL_MAVEN_ARGS[@]}" -pl pms-module-platform -am test \
  "-Dtest=${OWNER_MATERIAL_TESTS:-DeliveryMaterialWithdrawalPersistenceTest,NativeOwnerMaterialWithdrawalMySqlIntegrationTest,DeliveryMaterialServiceTest,DeliveryMaterialServiceAuthorizationTest,DeliverySourceReuseTest}" \
  -Dsurefire.failIfNoSpecifiedTests=false -Dnpdms.delivery.withdrawal.mysql.exclusive=true \
  -Dnpdms.delivery.withdrawal.mysql.database=npdms_delivery_withdrawal_owner_actions_20261007 \
  -Dnpdms.delivery.withdrawal.mysql.port=28501 \
  > "$OWNER_MATERIAL_RUN_DIR/mysql.log" 2>&1
tail -n 12 "$OWNER_MATERIAL_RUN_DIR/mysql.log"
