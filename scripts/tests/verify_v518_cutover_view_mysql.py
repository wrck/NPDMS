"""Verify V517 + V518 in a newly owned MySQL schema via Compose/Flyway; never writes remote data."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = 'npdms-domain-test-mysql-1'
MIGRATION = 'V518__publish_default_cutover_workbench_view.sql'
TABLE = 'plt_business_view_revision'

def run(args, **kwargs):
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', **kwargs)

bootstrap = run([sys.executable, str(ROOT / 'scripts/tests/verify_v517_default_task_state_mysql.py')])
assert bootstrap.returncode == 0, bootstrap.stderr
receipt = json.loads(bootstrap.stdout)
DB, OUT = receipt['schema'], Path(receipt['evidence'])
assert json.loads((OUT / 'ownership.json').read_text())['schema'] == DB
shutil.copyfile(ROOT / 'sql/migrations' / MIGRATION, OUT / 'migrations' / MIGRATION)
metadata = json.loads(run(['docker', 'inspect', CONTAINER]).stdout)[0]
password = dict(x.split('=', 1) for x in metadata['Config']['Env'] if '=' in x)['MYSQL_ROOT_PASSWORD']

def query(sql, okay=True):
    result = run(['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$@"', 'mysql', DB], input=sql)
    assert not okay or result.returncode == 0, result.stderr
    return result

def digest(sql):
    return hashlib.sha256(query(sql).stdout.encode()).hexdigest()

def direct(label, error=None):
    r = query((ROOT / 'sql/migrations' / MIGRATION).read_text(encoding='utf-8-sig'), False)
    (OUT / ('v518-' + label + '.log')).write_text(r.stdout + r.stderr, encoding='utf-8')
    assert (r.returncode != 0 and error in r.stderr) if error else r.returncode == 0, r.stderr

def flyway(label, *args):
    r = run(['docker', 'compose', '-p', 'npdms-v518-verification', '-f', str(OUT / 'compose.json'), 'run', '--rm', 'migrate', *args], env=dict(os.environ, FLYWAY_PASSWORD=password))
    (OUT / ('v518-' + label + '.log')).write_text(r.stdout + r.stderr, encoding='utf-8')
    assert r.returncode == 0, (r.stdout + r.stderr)[-2000:]

tables = query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()
protected_sql = ''.join("SELECT '" + t + "'; SELECT * FROM `" + t + "`;" for t in tables if t not in [TABLE, 'flyway_schema_history'])
protected = digest(protected_sql)
columns = query("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='" + TABLE + "' ORDER BY ordinal_position;").stdout.splitlines()
business_sql = 'SELECT ' + ','.join('`'+x+'`' for x in columns if x not in ['published_at','updater','update_time','version']) + ' FROM ' + TABLE + ' ORDER BY id;'
business_before = digest(business_sql)
other_sql = 'SELECT * FROM ' + TABLE + ' WHERE id<>996610195020037 ORDER BY id;'
other_before = digest(other_sql)
query('CREATE TABLE backup_v518 LIKE ' + TABLE + '; INSERT INTO backup_v518 SELECT * FROM ' + TABLE + ';')
history = query('SELECT * FROM flyway_schema_history ORDER BY installed_rank;').stdout
flyway('upgrade', 'migrate')
assert query('SELECT COUNT(*) FROM '+TABLE+' WHERE id=996610195020037 AND published_at IS NOT NULL AND disabled_at IS NULL AND version=1;').stdout.strip() == '1'
assert digest(business_sql) == business_before
assert digest(other_sql) == other_before
assert digest(protected_sql) == protected
assert query("SELECT * FROM flyway_schema_history WHERE version<>'518' ORDER BY installed_rank;").stdout == history
published = digest('SELECT * FROM '+TABLE+' ORDER BY id;')
flyway('validate', 'validate'); flyway('repeat', 'migrate'); direct('repeat')
assert digest('SELECT * FROM '+TABLE+' ORDER BY id;') == published
passed=['upgrade-published-exact-id','business-fields-preserved','other-view-history-preserved','other-tables-preserved','flyway-history-preserved','validate','repeat-migrate','repeat-sql']

def reset():
    query('DELETE FROM '+TABLE+'; INSERT INTO '+TABLE+' SELECT * FROM backup_v518;')

for label, change, error in [
 ('custom-provider', "UPDATE "+TABLE+" SET command_provider_key='CUSTOM' WHERE id=996610195020037;", 'view changed'),
 ('custom-actions', "UPDATE "+TABLE+" SET supported_actions=JSON_ARRAY('QUERY') WHERE id=996610195020037;", 'view changed'),
 ('custom-context', "UPDATE "+TABLE+" SET context_schema=JSON_OBJECT('type','object') WHERE id=996610195020037;", 'view changed'),
 ('edited-version', "UPDATE "+TABLE+" SET version=1 WHERE id=996610195020037;", 'view changed'),
 ('another-revision', "INSERT INTO "+TABLE+" SELECT * FROM backup_v518 WHERE 1=0; INSERT INTO "+TABLE+" ("+','.join('`'+x+'`' for x in columns)+") SELECT "+','.join('777' if x=='id' else '2' if x=='revision_no' else '`'+x+'`' for x in columns)+" FROM backup_v518 WHERE id=996610195020037;", 'additional cutover view revisions'),
]:
    reset(); query(change); before=digest('SELECT * FROM '+TABLE+' ORDER BY id;');direct(label,error);assert digest('SELECT * FROM '+TABLE+' ORDER BY id;')==before;passed.append(label)

for label, change in [
 ('published', "UPDATE "+TABLE+" SET published_at=NOW(6) WHERE id=996610195020037;"),
 ('disabled', "UPDATE "+TABLE+" SET published_at=NOW(6),disabled_at=NOW(6) WHERE id=996610195020037;"),
 ('deleted', "UPDATE "+TABLE+" SET deleted=1 WHERE id=996610195020037;"),
]:
    reset();query(change);before=digest('SELECT * FROM '+TABLE+' ORDER BY id;');direct(label);assert digest('SELECT * FROM '+TABLE+' ORDER BY id;')==before;passed.append(label)
reset();query("CREATE TRIGGER v518_test_failure AFTER UPDATE ON "+TABLE+" FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected view failure';")
before=digest('SELECT * FROM '+TABLE+' ORDER BY id;');direct('rollback','injected view failure');assert digest('SELECT * FROM '+TABLE+' ORDER BY id;')==before
query('DROP TRIGGER v518_test_failure;');passed.append('rollback')
direct('recovery')
(OUT / 'v518-results.json').write_text(json.dumps({'schema':DB,'v517Checks':receipt['passed'],'v518Checks':passed},indent=2),encoding='utf-8')
print(json.dumps({'v517Passed':receipt['passed'],'v518Passed':len(passed),'schema':DB,'evidence':str(OUT)}))
