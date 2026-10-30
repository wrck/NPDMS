#!/usr/bin/env bash
# Create empty directories only, on the approved mounted data partition.
set -euo pipefail
[[ $EUID == 0 && ${2:-} == --approved ]] || { echo 'Usage: prepare-data.sh be|db --approved' >&2; exit 1; }
case "${1:-}" in be) expected=172.17.73.104;; db) expected=172.17.74.19;; *) exit 1;; esac
ip -o -4 addr show | awk '{split($4,a,"/"); print a[1]}' | grep -Fxq "$expected"
mountpoint -q /data
[[ ! -L /data ]]
exec 9>/run/lock/npdms-production.lock
flock -n 9
create() {
  local path=$1 owner=$2
  getent passwd "$owner" >/dev/null
  [[ $(realpath -m "$path") == "$path" ]] || { echo "Symlinked path refused: $path" >&2; exit 1; }
  if [[ -e $path ]]; then
    [[ -d $path && $(stat -c %U "$path") == "$owner" ]] || { echo "Review existing directory: $path" >&2; exit 1; }
  else
    install -d -o "$owner" -g "$owner" -m 0750 "$path"
  fi
}
if [[ $1 == db ]]; then
  create /data/mysql sdp-mysql
  create /data/mysql-tmp sdp-mysql
else
  create /data/backend sdp
  create /data/dac sdp-dac
  create /data/redis sdp-redis
  create /data/minio sdp-minio
  create /data/minio-runtime sdp-minio
fi
echo 'Data directories prepared; no existing data moved or services started.'
