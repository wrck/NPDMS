"""Verify the V398 forward repair in a new owned MySQL schema with Compose/Flyway."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.run' / ('v519-verification-' + uuid.uuid4().hex[:10])
DB = 'npdms_v519_' + uuid.uuid4().hex[:12]
CONTAINER = 'npdms-domain-test-mysql-1'
MIGRATION = 'V519__restore_delivery_material_command_version.sql'
OUT.mkdir(parents=True)
(OUT / 'ownership.json').write_text(json.dumps({'schema': DB, 'purpose': 'V519 verification'}), encoding='utf-8')

def run(args, **kwargs):
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', **kwargs)

def query(sql, database=DB, okay=True):
    args = ['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$@"', 'mysql']
    if database:
        args.append(database)
    result = run(args, input=sql)
    assert not okay or result.returncode == 0, result.stderr
    return result

assert query("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + DB + "';", None).stdout.strip() == '0'
query('CREATE DATABASE ' + DB + ' CHARACTER SET utf8mb4;', None)
query("CREATE TABLE plt_delivery_material (id BIGINT PRIMARY KEY, file_version_no INT NOT NULL, business_revision_no INT NOT NULL, title VARCHAR(100), update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);"
      "INSERT INTO plt_delivery_material VALUES (1,3,7,'原材料与证据','2020-01-01 00:00:00');"
      "CREATE TABLE protected_evidence (id BIGINT PRIMARY KEY, evidence JSON NOT NULL);"
      "INSERT INTO protected_evidence VALUES (1,JSON_OBJECT('frozen','unchanged'));")
business_sql = 'SELECT id,file_version_no,business_revision_no,title,update_time FROM plt_delivery_material ORDER BY id; SELECT * FROM protected_evidence ORDER BY id;'
def digest(sql):
    return hashlib.sha256(query(sql).stdout.encode()).hexdigest()
original = digest(business_sql)
metadata = json.loads(run(['docker', 'inspect', CONTAINER]).stdout)[0]
password = dict(x.split('=', 1) for x in metadata['Config']['Env'] if '=' in x)['MYSQL_ROOT_PASSWORD']
migrations = OUT / 'migrations'
migrations.mkdir()
shutil.copyfile(ROOT / 'sql/migrations' / MIGRATION, migrations / MIGRATION)
compose = {'services': {'migrate': {'image': 'flyway/flyway:11.10.5-alpine', 'environment': {
    'FLYWAY_URL': 'jdbc:mysql://host.docker.internal:24306/' + DB + '?useSSL=false&allowPublicKeyRetrieval=true',
    'FLYWAY_USER': 'root', 'FLYWAY_PASSWORD': '${FLYWAY_PASSWORD:?required}',
    'FLYWAY_LOCATIONS': 'filesystem:/flyway/sql', 'FLYWAY_CLEAN_DISABLED': 'true',
    'FLYWAY_BASELINE_ON_MIGRATE': 'true', 'FLYWAY_BASELINE_VERSION': '518',
    'FLYWAY_PLACEHOLDER_REPLACEMENT': 'false'}, 'volumes': ['./migrations:/flyway/sql:ro']}}}
(OUT / 'compose.json').write_text(json.dumps(compose, indent=2), encoding='utf-8')
def flyway(label, command):
    result = run(['docker', 'compose', '-p', 'npdms-v519-verification', '-f', str(OUT / 'compose.json'),
                  'run', '--rm', 'migrate', command], env=dict(os.environ, FLYWAY_PASSWORD=password))
    (OUT / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, (result.stdout + result.stderr)[-1500:]
def direct(label, fail=False):
    result = query((ROOT / 'sql/migrations' / MIGRATION).read_text(encoding='utf-8'), okay=False)
    (OUT / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    assert (result.returncode != 0 and 'incompatible' in result.stderr) if fail else result.returncode == 0, result.stderr

flyway('upgrade', 'migrate')
assert query("SELECT DATA_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='plt_delivery_material' AND column_name='version';").stdout.strip() == 'bigint\tNO\t0'
assert query('SELECT version FROM plt_delivery_material;').stdout.strip() == '0'
assert digest(business_sql) == original
history = query('SELECT * FROM flyway_schema_history ORDER BY installed_rank;').stdout
flyway('validate', 'validate')
flyway('repeat', 'migrate')
assert query('SELECT * FROM flyway_schema_history ORDER BY installed_rank;').stdout == history
assert digest(business_sql) == original
direct('repeat-sql')
assert digest(business_sql) == original
query('UPDATE plt_delivery_material SET version=9;')
versioned = digest('SELECT * FROM plt_delivery_material ORDER BY id;')
direct('existing-version')
assert digest('SELECT * FROM plt_delivery_material ORDER BY id;') == versioned
for label, column in [('wrong-type', 'VARCHAR(10) NOT NULL DEFAULT \'0\''),
                      ('nullable', 'BIGINT NULL DEFAULT 0'), ('wrong-default', 'BIGINT NOT NULL DEFAULT 1')]:
    query('ALTER TABLE plt_delivery_material DROP COLUMN version; ALTER TABLE plt_delivery_material ADD COLUMN version ' + column + ';')
    before = digest('SELECT * FROM plt_delivery_material ORDER BY id; SELECT * FROM protected_evidence;')
    direct(label, True)
    assert digest('SELECT * FROM plt_delivery_material ORDER BY id; SELECT * FROM protected_evidence;') == before
passed = ['baseline518-forward-upgrade', 'column-contract', 'old-row-version-zero', 'business-and-evidence-preserved',
          'validate', 'repeat-migrate-history-preserved', 'repeat-sql', 'nonzero-version-preserved',
          'wrong-type-rejected', 'nullable-rejected', 'wrong-default-rejected']
(OUT / 'results.json').write_text(json.dumps({'schema': DB, 'passed': passed}, indent=2), encoding='utf-8')
print(json.dumps({'schema': DB, 'passed': len(passed), 'evidence': str(OUT)}))
