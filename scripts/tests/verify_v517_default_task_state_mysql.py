"""Real MySQL/Flyway V517 regression. Creates only a new owned schema; never resets an existing DB.
Run: python scripts/tests/verify_v517_default_task_state_mysql.py
Uses the existing Compose MySQL container and cached Flyway image. Evidence stays in .run.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.run' / ('v517-verification-' + uuid.uuid4().hex[:10])
DB = 'npdms_v517_' + uuid.uuid4().hex[:12]
CONTAINER = 'npdms-domain-test-mysql-1'
MIGRATION = 'V517__restore_default_task_state_machine.sql'
OUT.mkdir(parents=True)


def run(args, **kwargs):
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', **kwargs)


def query(sql, database=DB, okay=True):
    args = ['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$@"', 'mysql']
    if database:
        args.append(database)
    result = run(args, input=sql)
    if okay and result.returncode:
        raise RuntimeError(result.stderr)
    return result


def digest(sql):
    return hashlib.sha256(query(sql).stdout.encode()).hexdigest()


def state():
    return [digest('SELECT * FROM ' + table + ' ORDER BY id;') for table in TABLES]


def direct(label, expect_error=None):
    result = query((ROOT / 'sql/migrations' / MIGRATION).read_text(encoding='utf-8'), okay=False)
    (OUT / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    if expect_error:
        assert result.returncode and expect_error in result.stderr, result.stderr
    else:
        assert result.returncode == 0, result.stderr


metadata = json.loads(run(['docker', 'inspect', CONTAINER]).stdout)[0]
env = dict(x.split('=', 1) for x in metadata['Config']['Env'] if '=' in x)
assert query("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + DB + "';", None).stdout.strip() == '0'
query('CREATE DATABASE `' + DB + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;', None)
(OUT / 'ownership.json').write_text(json.dumps({'schema': DB, 'container': CONTAINER, 'createdByThisRun': True}))
migrations = OUT / 'migrations'
migrations.mkdir()
for file in (ROOT / 'sql/releases/test-v498/migrations').glob('*.sql'):
    shutil.copyfile(file, migrations / file.name)
for file in (ROOT / 'sql/migrations').glob('V*.sql'):
    version = file.name.split('__')[0][1:]
    if version.isdigit() and 501 <= int(version) <= 514 or file.name == MIGRATION:
        shutil.copyfile(file, migrations / file.name)
compose = {'services': {'migrate': {
    'image': 'flyway/flyway:11.10.5-alpine', 'network_mode': 'container:' + CONTAINER,
    'environment': {'FLYWAY_PASSWORD': '${FLYWAY_PASSWORD:?required}', 'FLYWAY_USER': 'root',
        'FLYWAY_URL': 'jdbc:mysql://127.0.0.1:3306/' + DB + '?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai',
        'FLYWAY_LOCATIONS': 'filesystem:/flyway/sql', 'FLYWAY_CLEAN_DISABLED': 'true',
        'FLYWAY_PLACEHOLDER_REPLACEMENT': 'false'},
    'volumes': ['./migrations:/flyway/sql:ro']}}}
(OUT / 'compose.json').write_text(json.dumps(compose), encoding='utf-8')


def flyway(label, *args):
    result = run(['docker', 'compose', '-p', 'npdms-v517-verification', '-f', str(OUT / 'compose.json'),
                  'run', '--rm', 'migrate', *args], env=dict(os.environ, FLYWAY_PASSWORD=env['MYSQL_ROOT_PASSWORD']))
    (OUT / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    assert result.returncode == 0, (result.stdout + result.stderr)[-2500:]


flyway('baseline', '-target=514', 'migrate')
TABLES = ['proj_task_state_machine_revision', 'proj_task_state_transition']
# Snapshot all business tables; only the two configuration tables may change.
all_tables = query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()
protected_sql = ''.join("SELECT '" + t + "'; SELECT * FROM `" + t + "`;" for t in all_tables if t not in TABLES + ['flyway_schema_history'])
protected = digest(protected_sql)
history = query('SELECT * FROM flyway_schema_history ORDER BY installed_rank;').stdout
original = state()
assert query("SELECT COUNT(*) FROM proj_task_state_machine_revision WHERE tenant_id=1 AND status='PUBLISHED' AND deleted=0;").stdout.strip() == '0'
for table in TABLES:
    query('CREATE TABLE backup_' + table + ' LIKE ' + table + '; INSERT INTO backup_' + table + ' SELECT * FROM ' + table + ';')
query('SELECT id,revision_no,status,creator FROM proj_task_state_machine_revision;')
flyway('upgrade', 'migrate')
assert query("SELECT COUNT(*) FROM proj_task_state_machine_revision WHERE tenant_id=1 AND status='PUBLISHED' AND effective_from<=NOW(3) AND deleted=0;").stdout.strip() == '1'
assert query('SELECT COUNT(*) FROM proj_task_state_transition WHERE revision_id=996610195170001;').stdout.strip() == '8'
fields = 'from_status_code,action_code,to_status_code,standard_status_mapping,allowed_role_code,entry_condition,exit_condition'
assert digest('SELECT ' + fields + ' FROM proj_task_state_transition WHERE revision_id=996610195020063 ORDER BY from_status_code,action_code;') == digest('SELECT ' + fields + ' FROM proj_task_state_transition WHERE revision_id=996610195170001 ORDER BY from_status_code,action_code;')
assert query('SELECT revision_no FROM proj_task_state_machine_revision WHERE id=996610195170001;').stdout.strip() == '2'
for table, before in zip(TABLES, original):
    assert digest('SELECT * FROM ' + table + ' WHERE id IN (SELECT id FROM backup_' + table + ') ORDER BY id;') == before
assert query('SELECT * FROM flyway_schema_history WHERE version<>\'517\' ORDER BY installed_rank;').stdout == history
assert protected == digest(protected_sql)
after = state()
flyway('validate', 'validate')
flyway('repeat', 'migrate')
direct('direct-repeat')
assert state() == after
results = ['B498-to-V514-to-V517', 'current-published-query', 'eight-transitions', 'original-config-and-history-preserved', 'all-other-tables-preserved', 'flyway-validate', 'flyway-repeat', 'direct-repeat']


def reset():
    # Only this run's newly created schema; never touches the existing application's schema.
    query('DELETE FROM proj_task_state_transition; DELETE FROM proj_task_state_machine_revision;'
          'INSERT INTO proj_task_state_machine_revision SELECT * FROM backup_proj_task_state_machine_revision;'
          'INSERT INTO proj_task_state_transition SELECT * FROM backup_proj_task_state_transition;')


for label, sql, error in [
    ('custom-condition', "UPDATE proj_task_state_transition SET exit_condition=JSON_OBJECT('schemaVersion',1,'progress',50) WHERE action_code='SUBMIT';", 'transitions changed'),
    ('case-drift', "UPDATE proj_task_state_transition SET allowed_role_code=LOWER(allowed_role_code) WHERE action_code='START';", 'transitions changed'),
    ('missing-transition', "DELETE FROM proj_task_state_transition WHERE action_code='START';", 'transitions changed'),
    ('deleted-transition', "UPDATE proj_task_state_transition SET deleted=1 WHERE action_code='START';", 'transitions changed'),
    ('edited-draft', "UPDATE proj_task_state_machine_revision SET version=1;", 'draft changed'),
    ('extra-draft', "INSERT INTO proj_task_state_machine_revision(id,revision_no,status,effective_from,tenant_id) VALUES(777,2,'DRAFT',NOW(),1);", 'draft changed'),
    ('reserved-id', "INSERT INTO proj_task_state_machine_revision(id,revision_no,status,effective_from,tenant_id) VALUES(996610195170001,1,'DRAFT',NOW(),99);", 'ID collision'),
]:
    reset(); query(sql); before = state(); direct(label, error); assert state() == before; results.append(label)

for label, sql in [
    ('existing-published', "UPDATE proj_task_state_machine_revision SET status='PUBLISHED',published_at=NOW(3);"),
    ('scheduled-published', "UPDATE proj_task_state_machine_revision SET status='PUBLISHED',published_at=NOW(3),effective_from=DATE_ADD(NOW(3),INTERVAL 10 DAY);"),
    ('unrelated-custom-draft', "UPDATE proj_task_state_machine_revision SET creator='custom-owner',updater='custom-owner';"),
    ('deleted-v502-draft', "UPDATE proj_task_state_machine_revision SET deleted=1;"),
]:
    reset(); query(sql); before = state(); direct(label); assert state() == before; results.append(label)

reset()
# Fail after the new revision insert to prove the transaction rolls it back.
query("CREATE TRIGGER v517_test_failure BEFORE INSERT ON proj_task_state_transition FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected transition failure';")
before = state(); direct('rollback-after-insert', 'injected transition failure'); assert state() == before
query('DROP TRIGGER v517_test_failure;'); results.append('rollback-after-insert')
direct('recovery-after-failure')
assert query("SELECT COUNT(*) FROM proj_task_state_machine_revision WHERE status='PUBLISHED' AND tenant_id=1;").stdout.strip() == '1'
(OUT / 'results.json').write_text(json.dumps({'schema': DB, 'passed': results, 'remoteDatabaseWritten': False}, indent=2), encoding='utf-8')
print(json.dumps({'passed': len(results), 'evidence': str(OUT), 'schema': DB}))
