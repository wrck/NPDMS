#!/usr/bin/env bash
# Encrypted logical backup; DBA must separately provide continuous binlog shipping.
set -euo pipefail
umask 077
[[ $EUID == 0 && ${1:-} == --approved ]] || { echo 'Usage: backup-db.sh --approved AGE_RECIPIENT OUTPUT_DIRECTORY DATABASE' >&2; exit 1; }
recipient=${2:?}; output=${3:?}; database=${4:?}
[[ $recipient == age1* && $database =~ ^[a-z][a-z0-9_]*$ ]] || exit 1
ip -o -4 addr show | awk '{split($4,a,"/"); print a[1]}' | grep -Fxq 172.17.74.19
command -v age >/dev/null
[[ -d $output ]] || { echo 'Backup destination must already exist' >&2; exit 1; }
mountpoint -q /data
output=$(realpath "$output")
[[ $output == /data/backups/* ]] || { echo 'Use an approved backup mount beneath /data/backups/' >&2; exit 1; }
exec 9>/run/lock/npdms-production.lock
flock -n 9
target=$output/$database-$(date -u +%Y%m%dT%H%M%SZ).sql.age
[[ ! -e $target && ! -e $target.partial ]]
/opt/sdp/mysql/bin/mysqldump --defaults-extra-file=/run/sdp-secrets/mysql-backup.cnf \
  --single-transaction --routines --events --triggers --hex-blob \
  --no-tablespaces --set-gtid-purged=OFF --databases "$database" \
  | age -r "$recipient" -o "$target.partial"
mv "$target.partial" "$target"
sha256sum "$target" > "$target.sha256"
printf 'Encrypted dump: %s; restore verification is still required.\n' "$target"
