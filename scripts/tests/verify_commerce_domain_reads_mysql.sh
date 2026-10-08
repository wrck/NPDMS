#!/usr/bin/env bash
set -euo pipefail
COM_READ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$COM_READ_ROOT"
COM_READ_PROJECT=npdms-commerce-domain-reads-20261006
COM_READ_RUN_DIR=.run/commerce-domain-reads-20261006
mkdir -p "$COM_READ_RUN_DIR"
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$COM_READ_PROJECT")" ]]; then
  echo "Refusing to reuse existing containers for $COM_READ_PROJECT" >&2
  exit 1
fi
cat > "$COM_READ_RUN_DIR/compose.yaml" <<'YAML'
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ALLOW_EMPTY_PASSWORD: 'yes'
      MYSQL_DATABASE: commerce_domain_reads_verify
    command: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci']
    ports: ['127.0.0.1:27611:3306']
    tmpfs: ['/var/lib/mysql']
    healthcheck:
      test: ['CMD', 'mysqladmin', 'ping', '-h', 'localhost', '-uroot', '--silent']
      interval: 2s
      timeout: 2s
      retries: 40
  declared:
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
COM_READ_COMPOSE=(docker compose -p "$COM_READ_PROJECT" -f "$COM_READ_RUN_DIR/compose.yaml")
trap '"${COM_READ_COMPOSE[@]}" down' EXIT
"${COM_READ_COMPOSE[@]}" up -d --wait
COM_READ_MAVEN_ARGS=()
if [[ -n "${COM_READ_MAVEN_SETTINGS:-}" ]]; then
  COM_READ_MAVEN_ARGS+=(-s "$COM_READ_MAVEN_SETTINGS")
fi
# Production declarations, bindings, permission services and native MySQL/XML;
# controlled OrganizationScope/ProjectScope ports, isolated fixture tables only.
mvn -B "${COM_READ_MAVEN_ARGS[@]}" -pl pms-module-commerce -am test \
  -Dtest=CommerceBusinessReadMySqlTest,ContractAccessServiceTest,ContractControllerTest,ProviderBusinessEntityContentReaderTest,DeclaredOwnerReadCompatibilityRuntimeTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dnpdms.com-read.mysql=true -Dnpdms.declared.exclusive=true \
  '-Dnpdms.com-read.jdbcUrl=jdbc:mysql://127.0.0.1:27611/commerce_domain_reads_verify?useSSL=false&allowPublicKeyRetrieval=true' \
  > "$COM_READ_RUN_DIR/mysql.log" 2>&1
tail -n 12 "$COM_READ_RUN_DIR/mysql.log"
