#!/usr/bin/env bash
# Only initialize an explicitly approved EMPTY native MySQL data directory.
set -euo pipefail
umask 077
[[ $EUID == 0 && ${1:-} == --approved-empty-database ]] || {
  echo 'Usage: init-mysql.sh --approved-empty-database' >&2; exit 1;
}
ip -o -4 addr show | awk '{split($4,a,"/"); print a[1]}' | grep -Fxq 172.17.74.19
exec 9>/run/lock/npdms-production.lock
flock -n 9
if systemctl is-active --quiet sdp-mysql; then echo 'MySQL already running' >&2; exit 1; fi
[[ $(findmnt -no FSTYPE /run) == tmpfs ]]
data=/data/mysql
mountpoint -q /data
[[ ! -L /data && $(realpath "$data") == "$data" ]]
[[ -d $data && ! -L $data ]]
[[ -z $(find "$data" -mindepth 1 -maxdepth 1 -print -quit) ]] || {
  echo 'Nonempty datadir: initialization refused' >&2; exit 1;
}
install -d -o sdp-mysql -g sdp-mysql -m 0700 /run/sdp-mysql-bootstrap
runuser -u sdp-mysql -- /opt/sdp/mysql/bin/mysqld --no-defaults \
  --initialize --basedir=/opt/sdp/mysql --datadir="$data" \
  --auto-generate-certs=OFF --sha256-password-auto-generate-rsa-keys=OFF \
  --caching-sha2-password-auto-generate-rsa-keys=OFF \
  --log-error=/run/sdp-mysql-bootstrap/initialize.log
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
python3 "$here/record-credential.py" mysql root "initial-$(date -u +%Y%m%dT%H%M%SZ)" \
  --state temporary --mysql-init-log /run/sdp-mysql-bootstrap/initialize.log
echo 'Initialized only. Root credential recorded under /opt/sdp/config/credentials/mysql; DBA must rotate and record the verified password before application use.'
