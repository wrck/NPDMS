#!/usr/bin/env bash
# Run locally on the intended Ubuntu host. Does not SSH or store credentials.
set -euo pipefail
umask 027
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
die() { printf '%s\n' "$*" >&2; exit 1; }
role=${1:-}; action=${2:-}
case "$role" in
  fe1) expected=172.20.19.2;;
  fe2) expected=172.20.20.2;;
  be) expected=172.17.73.104;;
  db) expected=172.17.74.19;;
  *) die 'Usage: operate.sh fe1|fe2|be|db preflight|infra-up|migrate-info|migrate-validate|migrate|publish ...';;
esac
[[ $EUID == 0 ]] || die 'Run as root via the approved operations account'
ip -o -4 addr show | awk '{split($4,a,"/"); print a[1]}' | grep -Fxq "$expected" || die 'Wrong target host IP'
exec 9>/run/lock/npdms-production.lock
flock -n 9 || die 'Another NPDMS operation holds the lock'
case "$action" in
  service)
    kind=${3:-}; operation=${4:-}
    case "$role:$kind" in be:backend|be:dac|be:redis|be:minio|db:mysql) ;; *) die 'Service does not belong to this host';; esac
    case "$operation" in start|stop|restart) ;; *) die 'Service operation must be start, stop or restart';; esac
    [[ ${5:-} == --approved ]] || die 'Usage: ROLE service backend|dac|mysql|redis|minio start|stop|restart --approved'
    unit="sdp-$kind"; [[ $kind != backend ]] || unit=sdp
    systemctl "$operation" "$unit"
    if [[ $operation != stop ]]; then systemctl is-active "$unit"; fi
    ;;
  preflight)
    if [[ $role == be || $role == db ]]; then
      mountpoint -q /data || die '/data must be mounted before operation'
      [[ ! -L /data ]] || die '/data cannot be a symlink'
    fi
    . /etc/os-release
    [[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || die 'Ubuntu 24.04 required'
    df -h /opt /var
    if [[ $role == be || $role == db ]]; then df -h /data; fi
    ss -lnt
    if [[ $role == db ]]; then
      /opt/sdp/mysql/bin/mysqld --version
      /opt/sdp/mysql/bin/mysqld --defaults-file=/etc/sdp/mysql.cnf --validate-config
    elif [[ $role == be ]]; then
      /opt/sdp/redis/bin/redis-server --version
      /opt/sdp/minio/bin/minio --version
      /opt/sdp/flyway/flyway -v
      systemd-analyze verify /etc/systemd/system/sdp-redis.service /etc/systemd/system/sdp-minio.service
    else
      nginx -t
    fi
    if [[ $role == be ]]; then
      /opt/sdp/jdk/bin/java -version 2>&1 | grep -E 'version "25[.\"]' || die 'JDK25 required'
      test -r /etc/credstore.encrypted/sdp-backend || die 'Encrypted backend credentials missing'
      test -r /etc/credstore.encrypted/sdp-backend-tls || die 'Encrypted backend TLS identity missing'
    fi
    ;;
  infra-up)
    [[ ${3:-} == --approved ]] || die 'Requires approved infrastructure change: --approved'
    case "$role" in
      db) systemctl start sdp-mysql; systemctl is-active sdp-mysql;;
      be) systemctl start sdp-redis sdp-minio; systemctl is-active sdp-redis sdp-minio;;
      *) die 'Infrastructure only runs on BE/DB';;
    esac
    ;;
  migrate-info|migrate-validate|migrate|dac-migrate-info|dac-migrate-validate|dac-migrate)
    [[ $role == be ]] || die 'Migration runner is on BE'
    migration_kind=migrations
    migration_config=/run/sdp-secrets/flyway.conf
    migration_action=$action
    if [[ $action == dac-* ]]; then
      migration_kind=dac-migrations
      migration_config=/run/sdp-secrets/dac-flyway.conf
      migration_action=${action#dac-}
    fi
    command=${migration_action#migrate-}
    if [[ $migration_action == migrate ]]; then
      [[ ${3:-} == --approved ]] || die 'Requires approved DDL window and verified backup: --approved'
      command=migrate
    fi
    [[ $(findmnt -no FSTYPE /run) == tmpfs ]] || die '/run must be memory-backed'
    python3 "$here/release.py" verify "$migration_kind" "/opt/sdp/$migration_kind/current" >/dev/null
    [[ -r $migration_config ]] || die 'Native Flyway credential configuration missing'
    /opt/sdp/flyway/flyway "-configFiles=$migration_config" \
      "-locations=filesystem:/opt/sdp/$migration_kind/current" \
      -cleanDisabled=true -baselineOnMigrate=false -validateMigrationNaming=true "$command"
    ;;
  publish)
    kind=${3:-}; source=${4:-}
    [[ ${5:-} == --approved ]] || die 'Usage: ROLE publish backend|frontend|migrations|dac|dac-migrations RELEASE_DIRECTORY --approved'
    case "$role:$kind" in be:backend|be:migrations|be:dac|be:dac-migrations|fe1:frontend|fe2:frontend) ;; *) die 'Role/artifact mismatch';; esac
    release_id=$(python3 "$here/release.py" verify "$kind" "$source")
    base=/opt/sdp/$kind
    target=$base/releases/$release_id
    [[ ! -e $target && ! -L $target ]] || die 'Release already exists; use activate for rollback/reuse'
    install -d -m 755 "$base/releases"
    cp -R -- "$source" "$target"
    chown -R root:root "$target"
    find "$target" -type d -exec chmod 755 {} +
    find "$target" -type f -exec chmod 644 {} +
    python3 "$here/release.py" verify "$kind" "$target" >/dev/null
    printf 'Staged %s; activate separately after configuration/schema checks.\n' "$target"
    ;;
  activate)
    kind=${3:-}; release_id=${4:-}
    [[ ${5:-} == --approved ]] || die 'Usage: ROLE activate backend|frontend|migrations|dac|dac-migrations RELEASE_ID --approved'
    case "$role:$kind" in be:backend|be:migrations|be:dac|be:dac-migrations|fe1:frontend|fe2:frontend) ;; *) die 'Role/artifact mismatch';; esac
    [[ $release_id =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$ ]] || die 'Invalid release ID'
    base=/opt/sdp/$kind
    target=$base/releases/$release_id
    python3 "$here/release.py" verify "$kind" "$target" >/dev/null
    [[ ! -e $base/current || -L $base/current ]] || die 'Current is not a managed symlink'
    [[ ! -e $base/current.next && ! -L $base/current.next ]] || die 'Inspect stale current.next first'
    previous=$(readlink "$base/current" || true)
    if [[ $kind == frontend ]]; then nginx -t; fi
    ln -s "$target" "$base/current.next"
    mv -Tf "$base/current.next" "$base/current"
    printf 'Previous: %s\nCurrent: %s\n' "$previous" "$target"
    if [[ $kind == backend || $kind == dac ]]; then
      unit="sdp-$kind"; [[ $kind != backend ]] || unit=sdp
      systemctl restart "$unit"
      systemctl is-active "$unit"
      printf 'Process check only. Run authenticated dependency and business acceptance before declaring success.\n'
    elif [[ $kind == frontend ]]; then
      nginx -t && systemctl reload nginx
    fi
    ;;
  *) die 'Unknown action';;
esac
