#!/usr/bin/env python3
"""Exclusive normal-JAR HTTP acceptance; production role mutations, real scope Providers.

Requires the task-owned Compose database/Redis and the normal application on port 59294.
Fixtures are synthetic and confined to npdms_commerce_boot. No role SQL is used after login.
"""
import datetime
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--final-gaps', action='store_true', help='Only the combined-scope and authorization-sparse gaps')
FINAL_GAPS = parser.parse_args().final_gaps
RUN = ROOT / ('.run/commerce-final-gaps-20261006' if FINAL_GAPS else '.run/commerce-real-login-20261006')
OUT = RUN / 'acceptance.json'
BASE = 'http://127.0.0.1:59294'
DB = 'npdms_commerce_boot'
COMPOSE = ['docker', 'compose', '-p', 'npdms-commerce-real-login-20261006', '-f', str(RUN / 'compose.yaml')]
MARK = 'commerce-read-fixture'
IDS = {'reader': 894901, 'operator': 894902, 'no_role': 894903, 'route_only': 894904}
ROLE = 895901
AMOUNT_ROLE = 895902
ROUTE_ROLE = 895903
DATA = {'company': 896010, 'department': 896011, 'outside': 896020, 'project': 896040, 'foreign': 896030}
result = {'status': 'RUNNING', 'passed': False, 'cases': [], 'metrics': [], 'cacheKeys': [],
          'mode': 'FINAL_TWO_GAPS' if FINAL_GAPS else 'ORIGINAL_REAL_CHAIN',
          'securityMockEnable': False, 'normalJar': True, 'providersMocked': False,
          'schema': 'original migrations failed at V374; V373 plus partial V374',
          'limitations': ['V374 original collation failure blocks complete migration acceptance',
              'OrganizationScope has no maintenance HTTP entrypoint; synthetic scope facts are changed in the exclusive database',
              'Commerce company and project relationship facts are changed only in the exclusive fixture database',
              'Quartz, MQ consumers and external device operations disabled; permission cache proxies remain active']}


def persist():
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


def sql(statement):
    process = subprocess.run(COMPOSE + ['exec', '-T', 'mysql', 'mysql', '-uroot', '-N', '-B', '--raw', DB],
                             input=statement, text=True, capture_output=True)
    if process.returncode:
        raise RuntimeError('exclusive fixture SQL failed: ' + process.stderr[:500])
    return process.stdout.strip()


def request(name, method, path, token=None, body=None, tenant=1, extra=None):
    headers = {'tenant-id': str(tenant), 'Content-Type': 'application/json', **(extra or {})}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status, value = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, value = error.code, json.load(error)
    redacted = {'code': value.get('code'), 'authenticated': value.get('code') == 0} if '/auth/login' in path else value
    result['cases'].append({'name': name, 'method': method, 'path': path, 'tenant': tenant,
                            'httpStatus': status, 'elapsedMs': round((time.monotonic() - started) * 1000, 1), 'body': redacted})
    persist()
    return value


def ok(value):
    assert value['code'] == 0, (value.get('code'), value.get('msg'))
    return value['data']


def denied(value, code=None, message=None):
    assert value['code'] != 0, 'unexpected authorization success'
    if code is not None:
        assert value['code'] == code, value
    if message:
        assert message in value['msg'], value


def root(model):
    return '/admin-api/api/v1/pms/business-models/COM/' + model


def detail(model, entity_id, token, label, tenant=1):
    return request(label + ' ' + model, 'GET', root(model) + '/data?id=' + str(entity_id), token, tenant=tenant)


def page(model, token, label, cursor=None, size=200, filters=None):
    return ok(request(label + ' ' + model, 'POST', root(model) + '/page', token,
                      {'sceneCode': 'list', 'pageSize': size, 'cursor': cursor, 'filters': filters or []}))


def ids(data):
    return [row['ref']['entityId'] for row in data['members']]


def caches(label):
    process = subprocess.run(COMPOSE + ['exec', '-T', 'redis', 'redis-cli', '--scan'], text=True, capture_output=True, check=True)
    keys = sorted(key for key in process.stdout.splitlines() if any(part in key for part in ['user_role_ids', 'menu_role_ids', 'permission_menu_ids']))
    result['cacheKeys'].append({'label': label, 'keys': keys})
    persist()
    return keys


def order_selects():
    return int(sql("SELECT COALESCE(SUM(COUNT_STAR),0) FROM performance_schema.events_statements_summary_by_digest "
                   "WHERE DIGEST_TEXT LIKE 'SELECT%com_sales_order%';"))


def assign_menus(menu_ids, label):
    ok(request(label, 'POST', '/admin-api/system/permission/assign-role-menu', tokens['operator'], {'roleId': ROLE, 'menuIds': menu_ids}))


def assign_roles(role_ids, label):
    ok(request(label, 'POST', '/admin-api/system/permission/assign-user-role', tokens['operator'], {'userId': IDS['reader'], 'roleIds': role_ids}))


def verify_final_gaps(token):
    # Same two existing roots, one company-only row, one project-only row, and an overlap.
    sql(f"INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,source_system,status,creator,updater) "
        f"VALUES(899050,1,1002,{DATA['company']},'FIXTURE','ACTIVE','{MARK}','{MARK}');")
    sql(f"INSERT INTO com_order_contract_relation(id,tenant_id,order_id,contract_id,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,effective_from,creator,updater) "
        f"VALUES(899051,1,{DATA['company']},{DATA['company']},'FIXTURE','FIXTURE','order-overlap','contract-overlap','1',JSON_OBJECT('fixture',true),'2026-01-01','{MARK}','{MARK}');")
    sql(f"UPDATE com_contract SET customer_code='Fixture code' WHERE id IN ({DATA['company']},{DATA['department']},{DATA['project']});")
    grant = ok(request('combined scope production HTTP grant project view', 'POST', '/admin-api/pms/projects/1002/authorization-grants', tokens['operator'],
                       {'subjectUserId': IDS['reader'], 'actionCode': 'PROJECT_VIEW', 'scopeCode': 'CURRENT_PROJECT', 'reason': 'exclusive combined commerce scope acceptance'},
                       extra={'Idempotency-Key': 'com-combined-grant-' + secrets.token_hex(6)}))
    stages = []

    def check_stage(label, expected, sensitive):
        for model in ['contract', 'salesOrder']:
            data = page(model, token, label)
            assert ids(data) == expected and len(set(ids(data))) == len(expected), (label, model, ids(data))
            assert data['nextCursor'] is None and data['completeness'] == 'COMPLETE'
            for entity_id in expected:
                value = ok(detail(model, entity_id, token, label + ' detail'))
                assert value['available']
                projected = next(row for row in data['members'] if row['ref']['entityId'] == entity_id)
                assert projected['fieldValues'] == value['fieldValues'], (label, model, 'list/detail projection')
                if model == 'contract':
                    for field, native_value in {'contractAmount': 123.45, 'contractType': 'PRIVATE', 'customerCode': 'Fixture code',
                                                'customerName': 'Fixture customer', 'currencyCode': 'CNY'}.items():
                        assert value['fieldValues'][field] == (native_value if sensitive else None), (label, field, value['fieldValues'][field])
                else:
                    assert 'orderAmount' not in value['fieldValues'] and 'currencyCode' not in value['fieldValues']
            closed = [value for value in [DATA['company'], DATA['department'], DATA['project']] if value not in expected]
            for entity_id in closed + [DATA['outside'], DATA['foreign']]:
                denied(detail(model, entity_id, token, label + ' closed route'), code=1010006000, message='ENTITY_SCOPE_DENIED')
        stages.append({'label': label, 'authorizedIds': expected, 'sensitiveRead': sensitive, 'bothModelsChecked': True})
        result['combinedScope'] = {'companyOnlyId': DATA['department'], 'projectOnlyId': DATA['project'],
                                   'overlapId': DATA['company'], 'sameReaderBearer': True, 'stages': stages}
        persist()

    both = [DATA['company'], DATA['department'], DATA['project']]
    check_stage('both company/project paths; overlap once; amounts masked', both, False)
    caches('combined paths warm role/menu caches')
    assign_roles([ROLE, AMOUNT_ROLE], 'combined paths production sensitive role grant')
    check_stage('both company/project paths; overlap once; amounts allowed', both, True)
    sql('UPDATE system_user_company_department_scope SET status=1,version=version+1 WHERE id=898901;')
    check_stage('company path revoked; project and overlap remain', [DATA['company'], DATA['project']], True)
    sql('UPDATE system_user_company_department_scope SET status=0,version=version+1 WHERE id=898901;')
    check_stage('company path restored; both paths active', both, True)
    ok(request('combined scope production HTTP revoke project grant', 'POST', '/admin-api/pms/project-authorization-grants/' + str(grant['id']) + '/actions/revoke', tokens['operator'],
               {'reason': 'combined scope project path revoke'}, extra={'Idempotency-Key': 'com-combined-revoke-' + secrets.token_hex(6), 'If-Match': str(grant['version'])}))
    check_stage('project path revoked; company and overlap remain', [DATA['company'], DATA['department']], True)
    assign_roles([ROLE], 'combined paths production sensitive role revoke')
    check_stage('company path remains; sensitive role revoked', [DATA['company'], DATA['department']], False)
    sql('UPDATE system_user_company_department_scope SET status=1,version=version+1 WHERE id=898901;')
    check_stage('both company/project paths revoked', [], False)

    # Same exclusive environment; first 9950 rows are outside scope, last 50 authorized.
    # Record physical work; LIMIT and SELECT count deliberately impose no RowsExamined claim.
    sql(f"INSERT INTO system_company(id,tenant_id,code,name,status,creator) VALUES(898003,1,'CLOUD_SPARSE','Sparse fixture',0,'{MARK}');")
    sql("UPDATE system_user_company_department_scope SET company_id=898003,company_code='CLOUD_SPARSE',company_name='Sparse fixture',status=0,version=version+1 WHERE id=898901;")
    assert int(sql('SELECT COUNT(*) FROM com_sales_order WHERE id BETWEEN 930000 AND 939999;')) == 0
    for start in range(0, 10000, 500):
        rows = ','.join(f"({930000+i},1,'{'CLOUD_SPARSE' if i>=9950 else 'CLOUD_B'}','AUTH-SPARSE-{i:05d}','FIXTURE','auth-sparse-{i}','0','Authorization sparse','ENABLED','{MARK}','{MARK}')" for i in range(start, start+500))
        sql('INSERT INTO com_sales_order(id,tenant_id,company_code,order_no,source_system,source_record_key,order_type,customer_name,status,creator,updater) VALUES ' + rows + ';')
    sql("UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_statements_history_long';")
    sparse = {'sampleRows': 10000, 'authorizedSampleRows': 50, 'unauthorizedSampleRows': 9950,
              'distribution': '9950 company B rows precede 50 authorized CLOUD_SPARSE rows in native order',
              'schema': result['schema'], 'originalIndexes': sql('SHOW CREATE TABLE com_sales_order;'),
              'nativeTenantRows': int(sql('SELECT COUNT(*) FROM com_sales_order WHERE tenant_id=1 AND deleted=0;')),
              'pages': [], 'plans': [], 'expectedIds': list(range(939950, 940000)),
              'claim': 'Returned native rows <=201 and cursor coverage tested; physical scan cost is measured, not bounded by LIMIT.'}
    result['authorizationSparse'] = sparse

    def events():
        values = sql("SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('threadId',THREAD_ID,'eventId',EVENT_ID,'sql',SQL_TEXT,"
                     "'rowsExamined',ROWS_EXAMINED,'rowsSent',ROWS_SENT,'durationMs',TIMER_WAIT/1000000000,'mysqlError',MYSQL_ERRNO)),JSON_ARRAY()) "
                     "FROM performance_schema.events_statements_history_long WHERE SQL_TEXT LIKE 'SELECT o.% FROM com_sales_order%' AND SQL_TEXT LIKE '%CLOUD_SPARSE%';")
        return json.loads(values)

    cursor, seen_cursors, covered = None, set(), []
    for number in range(1, 10):
        previous = {(value['threadId'], value['eventId']) for value in events()}
        started = time.monotonic()
        data = page('salesOrder', token, '10k authorization-sparse native resume', cursor=cursor, size=17)
        elapsed = round((time.monotonic() - started) * 1000, 1)
        statements = [value for value in events() if (value['threadId'], value['eventId']) not in previous]
        assert len(statements) == 1, ('actual native SELECTs', statements)
        assert statements[0]['mysqlError'] == 0 and statements[0]['rowsSent'] <= 201
        page_ids = ids(data)
        for entity_id in page_ids:
            assert entity_id not in covered, ('duplicate cursor row', entity_id)
        covered.extend(page_ids)
        assert len(page_ids) <= 17
        sparse['pages'].append({'number': number, 'requestElapsedMs': elapsed, 'returnedIds': page_ids,
                                'nextCursor': data['nextCursor'], 'completeness': data['completeness'], 'nativeStatement': statements[0]})
        statement = statements[0]['sql'].strip().rstrip(';')
        sparse['plans'].append({'page': number, 'explainJson': json.loads(sql('EXPLAIN FORMAT=JSON ' + statement + ';')),
                                'explainAnalyze': sql('EXPLAIN ANALYZE ' + statement + ';')})
        persist()
        cursor = data['nextCursor']
        if cursor is None:
            assert data['completeness'] == 'COMPLETE'
            break
        assert cursor not in seen_cursors
        seen_cursors.add(cursor)
    assert cursor is None and covered == sparse['expectedIds'], ('cursor coverage', covered)
    sparse.update(coveredIds=covered, coveragePassed=True, totalRowsExamined=sum(value['nativeStatement']['rowsExamined'] for value in sparse['pages']),
                  totalNativeSqlMs=sum(value['nativeStatement']['durationMs'] for value in sparse['pages']),
                  totalRequestMs=sum(value['requestElapsedMs'] for value in sparse['pages']))
    # Preserve a replayable synthetic dataset; no credentials/tokens, no production schema/index changes.
    dump = subprocess.run(COMPOSE + ['exec', '-T', 'mysql', 'mysqldump', '-uroot', '--no-tablespaces', '--no-create-info', '--skip-add-locks',
                                     '--skip-comments', '--set-gtid-purged=OFF', "--where=source_record_key LIKE 'auth-sparse-%'", DB, 'com_sales_order'],
                          text=True, capture_output=True, check=True).stdout
    (RUN / 'authorization-sparse-fixture.sql').write_text(dump)
    sparse['syntheticFixture'] = {'path': str((RUN / 'authorization-sparse-fixture.sql').relative_to(ROOT)),
                                 'sha256': hashlib.sha256(dump.encode()).hexdigest(), 'rows': 10000}
    caches('final gaps permission caches; no manual clear')
    persist()


persist()
try:
    assert sql('SELECT DATABASE();') == DB, 'wrong fixture database'
    result['jarSha256'] = hashlib.sha256((ROOT / 'yudao-server/target/yudao-server.jar').read_bytes()).hexdigest()
    password = secrets.token_hex(6)
    digest_source = RUN / 'FixturePasswordDigest.java'
    digest_source.write_text('import java.io.*; import org.springframework.security.crypto.bcrypt.BCrypt;\n'
                            'class FixturePasswordDigest { public static void main(String[] args) throws Exception {'
                            'String value=new BufferedReader(new InputStreamReader(System.in)).readLine();'
                            'System.out.print(BCrypt.hashpw(value,BCrypt.gensalt()));}}\n')
    digest = subprocess.check_output(['java', '-cp', '/workspace/toolchains/m2/org/springframework/security/spring-security-crypto/7.1.0/spring-security-crypto-7.1.0.jar',
                                     str(digest_source)], input=(password + '\n').encode()).decode()
    assert not sql(f"SELECT id FROM system_users WHERE id IN ({','.join(map(str, IDS.values()))});"), 'exclusive fixtures already exist; use a fresh environment'
    for name, uid in IDS.items():
        sql(f"INSERT INTO system_users(id,username,password,nickname,status,tenant_id,creator) VALUES({uid},'comread{name.replace('_', '')}','{digest}','Commerce test',0,1,'{MARK}');")
    for rid, name in [(ROLE, 'reader'), (AMOUNT_ROLE, 'amount'), (ROUTE_ROLE, 'route')]:
        sql(f"INSERT INTO system_role(id,name,code,sort,status,type,tenant_id,creator) VALUES({rid},'Commerce {name}','commerce_fixture_{name}',1,0,2,1,'{MARK}');")
    permissions = ['pms:business-model:query', 'pms:commerce:contract:query', 'pms:commerce:contract:sensitive-read']
    menus = {}
    for index, permission in enumerate(permissions):
        mid = sql(f"SELECT id FROM system_menu WHERE permission='{permission}' AND deleted=0 AND status=0 ORDER BY id LIMIT 1;")
        if not mid:
            mid = str(897901 + index)
            sql(f"INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,status,visible,keep_alive,always_show,creator) VALUES({mid},'Commerce permission','{permission}',3,9999,0,'',0,0,1,1,'{MARK}');")
        menus[permission] = int(mid)
    base_menus = [menus[permissions[0]], menus[permissions[1]]]
    for rid, mids in [(ROLE, base_menus), (AMOUNT_ROLE, [menus[permissions[2]]]), (ROUTE_ROLE, [menus[permissions[0]]])]:
        for mid in mids:
            sql(f"INSERT INTO system_role_menu(role_id,menu_id,tenant_id,creator) VALUES({rid},{mid},1,'{MARK}');")
    admin_role = int(sql("SELECT id FROM system_role WHERE tenant_id=1 AND code='super_admin' AND deleted=0 ORDER BY id LIMIT 1;"))
    for uid, rid in [(IDS['reader'], ROLE), (IDS['operator'], admin_role), (IDS['route_only'], ROUTE_ROLE)]:
        sql(f"INSERT INTO system_user_role(user_id,role_id,tenant_id,creator) VALUES({uid},{rid},1,'{MARK}');")
    sql(f"INSERT INTO system_company(id,tenant_id,code,name,status,creator) VALUES(898001,1,'CLOUD_A','Fixture A',0,'{MARK}'),(898002,1,'CLOUD_B','Fixture B',0,'{MARK}');")
    sql(f"INSERT INTO system_dept(id,tenant_id,code,name,status,creator) VALUES(898011,1,'D1','Fixture D1',0,'{MARK}'),(898022,1,'D2','Fixture D2',0,'{MARK}');")
    sql(f"INSERT INTO system_user_company_department_scope(id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,scope_role,effective_from,status,version,creator) "
        f"VALUES(898901,1,{IDS['reader']},898001,'CLOUD_A','Fixture A',898011,'D1','FCOM001_ACCEPTANCE','2026-01-01',0,1,'{MARK}');")
    for name, entity_id in DATA.items():
        tenant = 121 if name == 'foreign' else 1
        company = 'CLOUD_A' if name in ['company', 'department', 'foreign'] else 'CLOUD_B'
        department = 'D2' if name == 'department' else 'D1'
        sql(f"INSERT INTO com_contract(id,tenant_id,company_code,contract_no,master_source_system,master_source_record_key,contract_name,contract_type,customer_name,contract_amount,currency_code,department_code,status,creator,updater) "
            f"VALUES({entity_id},{tenant},'{company}','CLOUD-{entity_id}','FIXTURE','contract-{entity_id}','Fixture contract','PRIVATE','Fixture customer',123.45,'CNY','{department}','ENABLED','{MARK}','{MARK}');")
        sql(f"INSERT INTO com_sales_order(id,tenant_id,company_code,order_no,source_system,source_record_key,order_type,customer_name,order_amount,currency_code,status,creator,updater) "
            f"VALUES({entity_id},{tenant},'{company}','CLOUD-{entity_id}','FIXTURE','order-{entity_id}','0','Fixture customer',123.45,'CNY','ENABLED','{MARK}','{MARK}');")
    sql(f"INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,source_system,status,creator,updater) VALUES(899040,1,1002,{DATA['project']},'FIXTURE','ACTIVE','{MARK}','{MARK}');")
    sql(f"INSERT INTO com_order_contract_relation(id,tenant_id,order_id,contract_id,relation_source,source_system,sales_order_source_key,contract_source_key,source_version,source_evidence,effective_from,creator,updater) "
        f"VALUES(899041,1,{DATA['project']},{DATA['project']},'FIXTURE','FIXTURE','order-project','contract-project','1',JSON_OBJECT('fixture',true),'2026-01-01','{MARK}','{MARK}');")
    tokens = {}
    for name in IDS:
        tokens[name] = ok(request('real password login ' + name, 'POST', '/admin-api/system/auth/login', body={'username': 'comread' + name.replace('_', ''), 'password': password}))['accessToken']
    password = None
    token = tokens['reader']
    if FINAL_GAPS:
        verify_final_gaps(token)
        result.update(status='PASS', passed=True, completedAtUtc=datetime.datetime.now(datetime.timezone.utc).isoformat())
        raise SystemExit(0)
    for model in ['contract', 'salesOrder']:
        denied(detail(model, DATA['company'], None, 'anonymous'), 401)
        denied(detail(model, DATA['company'], tokens['no_role'], 'no role'), 403)
        denied(detail(model, DATA['company'], tokens['route_only'], 'route permission cannot replace native permission'), code=1010006005)
        assert ids(page(model, token, 'real OrganizationScope current company')) == [DATA['company'], DATA['department']]
        assert ok(detail(model, DATA['department'], token, 'native company scope includes another department'))['available']
        values = [detail(model, entity_id, token, 'uniform denied ID') for entity_id in [DATA['outside'], DATA['foreign'], 899999]]
        for value in values:
            denied(value, message='ENTITY_SCOPE_DENIED')
        assert [(value['code'], value['msg']) for value in values].count((values[0]['code'], values[0]['msg'])) == 3
        denied(detail(model, DATA['company'], token, 'authenticated tenant substitution', tenant=121))
    assert ok(detail('contract', DATA['company'], token, 'masked contract'))['fieldValues']['contractAmount'] is None
    keys = caches('warm native permission and role caches')
    assert any('user_role_ids' in key and str(IDS['reader']) in key for key in keys), 'role cache was not warmed'
    assert any('menu_role_ids' in key for key in keys), 'menu-role cache was not warmed'
    assign_menus([base_menus[0]], 'production HTTP revoke native query menu')
    for model in ['contract', 'salesOrder']:
        denied(detail(model, DATA['company'], token, 'same warm bearer after menu revocation'), code=1010006005)
        denied(request('list after menu revocation ' + model, 'POST', root(model) + '/page', token, {'pageSize': 1, 'sceneCode': 'list'}), code=1010006005)
    assign_menus(base_menus, 'production HTTP restore native query menu')
    assign_roles([], 'production HTTP revoke user roles')
    for model in ['contract', 'salesOrder']:
        denied(detail(model, DATA['company'], token, 'same warm bearer after user-role revocation'), 403)
    assign_roles([ROLE, AMOUNT_ROLE], 'production HTTP assign native sensitive role')
    assert ok(detail('contract', DATA['company'], token, 'sensitive amount allowed'))['fieldValues']['contractAmount'] == 123.45
    amount_filter = [{'fieldCode': 'contractAmount', 'operator': 'EQ', 'values': [123.45]}]
    assert ids(page('contract', token, 'sensitive amount filter allowed', filters=amount_filter)) == [DATA['company'], DATA['department']]
    assert 'orderAmount' not in ok(detail('salesOrder', DATA['company'], token, 'order REST projection stays without amount'))['fieldValues']
    caches('warm sensitive and assigned-role caches')
    assign_roles([ROLE], 'production HTTP revoke sensitive role')
    assert ok(detail('contract', DATA['company'], token, 'same bearer sensitive amount masked immediately'))['fieldValues']['contractAmount'] is None
    denied(request('sensitive filter after revoke', 'POST', root('contract') + '/page', token, {'pageSize': 20, 'sceneCode': 'list', 'filters': amount_filter}), message='FIELD_NOT_OPEN')
    before = {table: int(sql('SELECT COUNT(*) FROM ' + table + ';')) for table in ['com_contract', 'com_sales_order', 'plt_idempotency_record', 'plt_operation_audit', 'plt_outbox_event']}
    for model in ['contract', 'salesOrder']:
        for operation in ['create', 'save', 'delete', 'complete']:
            denied(request('native writer cannot invoke undeclared common operation ' + model + '/' + operation, 'POST',
                           root(model) + '/operations/' + operation, tokens['operator'],
                           {'idempotencyKey': 'com-read-denied-' + secrets.token_hex(6), 'input': {}}), message='OPERATION_NOT_DECLARED')
    after = {table: int(sql('SELECT COUNT(*) FROM ' + table + ';')) for table in before}
    assert before == after, ('denied write side effects', before, after)
    result['deniedWriteCounts'] = {'before': before, 'after': after}
    sql('UPDATE system_user_company_department_scope SET department_id=898022,department_code=\'D2\',version=version+1 WHERE id=898901;')
    for model in ['contract', 'salesOrder']:
        assert ids(page(model, token, 'real Provider department changed; native company scope remains')) == [DATA['company'], DATA['department']]
    sql("UPDATE system_user_company_department_scope SET company_id=898002,company_code='CLOUD_B',company_name='Fixture B',version=version+1 WHERE id=898901;")
    for model in ['contract', 'salesOrder']:
        assert ids(page(model, token, 'real Provider current company changed')) == [DATA['outside'], DATA['project']]
        denied(detail(model, DATA['company'], token, 'former company after grant moved'), message='ENTITY_SCOPE_DENIED')
    for change, label in [("company_code='cloud_a'", 'binary company code mismatch'),
                          ("company_code=''", 'malformed company scope'),
                          ("company_id=898001,company_code='CLOUD_A',company_name='Fixture A',effective_from='2099-01-01'", 'future organization scope'),
                          ("effective_from='2026-01-01',effective_to='2026-01-02'", 'expired organization scope')]:
        sql('UPDATE system_user_company_department_scope SET ' + change + ',version=version+1 WHERE id=898901;')
        for model in ['contract', 'salesOrder']:
            assert not ids(page(model, token, 'real Provider ' + label))
            denied(detail(model, DATA['company'], token, label + ' detail'), message='ENTITY_SCOPE_DENIED')
    sql("UPDATE system_user_company_department_scope SET tenant_id=121,effective_to=NULL,version=version+1 WHERE id=898901;")
    for model in ['contract', 'salesOrder']:
        assert not ids(page(model, token, 'real Provider foreign tenant organization grant'))
    sql("UPDATE system_user_company_department_scope SET tenant_id=1,version=version+1 WHERE id=898901;")
    sql("UPDATE system_user_company_department_scope SET status=1,version=version+1 WHERE id=898901;")
    for model in ['contract', 'salesOrder']:
        assert not ids(page(model, token, 'real Provider revoked organization scope'))
        denied(detail(model, DATA['company'], token, 'detail after organization revoke'), message='ENTITY_SCOPE_DENIED')
    grant = ok(request('production HTTP grant project view', 'POST', '/admin-api/pms/projects/1002/authorization-grants', tokens['operator'],
                       {'subjectUserId': IDS['reader'], 'actionCode': 'PROJECT_VIEW', 'scopeCode': 'CURRENT_PROJECT', 'reason': 'exclusive commerce read acceptance'},
                       extra={'Idempotency-Key': 'com-read-grant-' + secrets.token_hex(6)}))
    for model in ['contract', 'salesOrder']:
        assert ids(page(model, token, 'real ProjectScope and commerce relationship')) == [DATA['project']]
        assert ok(detail(model, DATA['project'], token, 'project-authorized detail'))['available']
    sql(f"UPDATE com_order_contract_relation SET contract_id={DATA['outside']} WHERE id=899041;")
    denied(detail('salesOrder', DATA['project'], token, 'current order-contract relationship moved'), message='ENTITY_SCOPE_DENIED')
    assert ok(detail('contract', DATA['project'], token, 'contract project scope remains'))['available']
    sql(f"UPDATE com_order_contract_relation SET contract_id={DATA['project']} WHERE id=899041;")
    sql("UPDATE com_project_contract_relation SET effective_to=CURRENT_TIMESTAMP WHERE id=899040;")
    for model in ['contract', 'salesOrder']:
        assert not ids(page(model, token, 'project relationship ended'))
        denied(detail(model, DATA['project'], token, 'ended project relationship detail'), message='ENTITY_SCOPE_DENIED')
    sql("UPDATE com_project_contract_relation SET effective_to=NULL WHERE id=899040;")
    ok(request('production HTTP revoke project grant', 'POST', '/admin-api/pms/project-authorization-grants/' + str(grant['id']) + '/actions/revoke', tokens['operator'],
               {'reason': 'exclusive read acceptance revoke'}, extra={'Idempotency-Key': 'com-read-revoke-' + secrets.token_hex(6), 'If-Match': str(grant['version'])}))
    for model in ['contract', 'salesOrder']:
        assert not ids(page(model, token, 'real ProjectScope revoked grant'))
        denied(detail(model, DATA['project'], token, 'detail after real project grant revoke'), message='ENTITY_SCOPE_DENIED')
    sql("UPDATE system_user_company_department_scope SET status=0,version=version+1 WHERE id=898901;")
    for model, table in [('contract', 'com_contract'), ('salesOrder', 'com_sales_order')]:
        first = page(model, token, 'cursor before preceding row deletion', size=1)
        assert ids(first) == [DATA['company']]
        sql(f"UPDATE {table} SET deleted=1 WHERE id={DATA['company']};")
        assert ids(page(model, token, 'key cursor after preceding row deletion', cursor=first['nextCursor'], size=1)) == [DATA['department']]
        sql(f"UPDATE {table} SET deleted=0 WHERE id={DATA['company']};")
        sql(f"UPDATE {table} SET company_code='CLOUD_B' WHERE id={DATA['company']};")
        denied(detail(model, DATA['company'], token, 'current company moved; no positive access cache'), message='ENTITY_SCOPE_DENIED')
        sql(f"UPDATE {table} SET company_code='CLOUD_A' WHERE id={DATA['company']};")
    # Original migration schema/indexes, 10k distinct native business/source keys, sparse last match.
    for start in range(0, 10000, 500):
        rows = ','.join(f"({900000+i},1,'CLOUD_A','ZZ-BULK-{i:05d}','FIXTURE','bulk-{i}','0','{'RARE' if i==9999 else 'COMMON'}','ENABLED','{MARK}','{MARK}')" for i in range(start, start+500))
        sql('INSERT INTO com_sales_order(id,tenant_id,company_code,order_no,source_system,source_record_key,order_type,customer_name,status,creator,updater) VALUES ' + rows + ';')
    before, started = order_selects(), time.monotonic()
    assert ok(detail('salesOrder', 909999, token, '10k indexed scoped detail'))['available']
    count = order_selects() - before
    assert count == 1, ('detail SELECT count', count)
    result['metrics'].append({'name': '10k original-schema detail', 'selects': count, 'elapsedMs': round((time.monotonic()-started)*1000,1)})
    cursor, seen, found, requests, max_selects = None, set(), [], 0, 0
    started = time.monotonic()
    while True:
        before = order_selects()
        data = page('salesOrder', token, '10k sparse bounded resume', cursor=cursor, size=1,
                    filters=[{'fieldCode': 'customerName', 'operator': 'EQ', 'values': ['RARE']}])
        count = order_selects() - before
        assert count == 1, ('sparse SELECT count', count)
        requests += 1
        max_selects = max(max_selects, count)
        found.extend(ids(data))
        cursor = data['nextCursor']
        if cursor is None:
            break
        assert cursor not in seen and requests <= 100, 'cursor did not advance'
        seen.add(cursor)
    assert found == [909999], found
    result['metrics'].append({'name': '10k original-schema sparse filter', 'requests': requests, 'maxSelectsPerRequest': max_selects,
                              'foundIds': found, 'elapsedMs': round((time.monotonic()-started)*1000,1)})
    caches('after production permission mutations; no manual Redis clears')
    result.update(status='PASS', passed=True, completedAtUtc=datetime.datetime.now(datetime.timezone.utc).isoformat())
except Exception as error:
    result.update(status='FAIL', passed=False, error=str(error), completedAtUtc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    raise
finally:
    persist()
    print(json.dumps({key: result[key] for key in ['status', 'passed', 'schema']}))
