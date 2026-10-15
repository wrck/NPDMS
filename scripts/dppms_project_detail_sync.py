"""Read audited DPPMS details and import immutable project evidence through the authorized API.

All SQL is confined to this source-only adapter. The target is written exclusively by
ProjectLegacyDetailService. Credentials are provided in environment variables, never in
the manifest, command line, output or payload. Dry-run is the default.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re

CATALOG = Path(__file__).resolve().parents[1] / 'pms-module-project/src/main/resources/projectlegacy/dppms-detail-domains.json'
SOURCE_SCHEMA = CATALOG.with_name('dppms-original-source-schema.json')
PROJECT_SCOPE_SQL = Path(__file__).resolve().parents[1] / 'pms-module-integration/src/main/resources/migration/dppms/valid-projects.sql'
SECRET = re.compile(r'password|passwd|pwd\s*[:=]|secret|token|authorization\s*[:=]|bearer\s+|private[ _-]?key|密码|口令|私钥|密钥|BEGIN[^\r\n]*PRIVATE KEY|[a-z][a-z0-9+.-]*://[^\s/]+:[^\s/]+@', re.I)
REDACTED = '[敏感内容已隐藏]'


def valid_project_sql():
    sql = PROJECT_SCOPE_SQL.read_text('utf8')
    values = {'projectTypes': '10', 'projectIds': ''}
    params = []
    def parameter(match):
        params.append(values[match[1]])
        return '%s'
    return re.sub(r':([A-Za-z][A-Za-z0-9_]*)', parameter, sql), params


def shipment_contracts(numbers):
    # Old project shipment/warranty queries use both the displayed contract and
    # sourceContractNo, whose historical derivation removes -L and -C.
    return set(numbers) | {re.sub(r'-L|-C', '', number) for number in numbers}


def text(value):
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.isoformat(sep=' ', timespec='milliseconds')
    if isinstance(value, bytes):
        if value in (b'\x00', b'\x01'):
            return str(int.from_bytes(value, 'big'))
        value = value.decode('utf8', errors='strict')
    value = str(value)
    return REDACTED if SECRET.search(value) else value


class DppmsDetailReader:
    """Fixed column and predicate allowlists; does not accept arbitrary source SQL."""

    def __init__(self, connection, catalog=None, schema_contract=None):
        self.connection = connection
        self.catalog = catalog or json.loads(CATALOG.read_text('utf8'))
        self.domains = {d['code']: d for d in self.catalog}
        self.rows = {}
        self.schema_contract = schema_contract or json.loads(SOURCE_SCHEMA.read_text('utf8'))
        self.schema_validated = False
        self.validate_catalog_schema()

    def validate_catalog_schema(self):
        excluded = {(x['sourceTable'], x['column']) for x in self.schema_contract['excludedColumns']}
        if len(self.domains) != len(self.catalog):
            raise ValueError('SOURCE_CATALOG_DOMAIN_DUPLICATE')
        for domain in self.catalog:
            table = self.schema_contract['tables'].get(domain['sourceTable'])
            if table is None:
                raise ValueError('SOURCE_CATALOG_TABLE_UNAUDITED:' + domain['code'])
            actual = [field['key'] for field in domain['fields']]
            required = {column['name'] for column in table['columns']
                        if (domain['sourceTable'], column['name']) not in excluded}
            if len(set(actual)) != len(actual) or set(actual) != required:
                raise ValueError('SOURCE_CATALOG_COLUMNS_INCOMPLETE:' + domain['code'])

    def validate_source_schema(self):
        if self.schema_validated:
            return
        expected = self.schema_contract['tables']
        table_names = sorted(expected)
        if len(table_names) != self.schema_contract['tableCount'] or sum(
                len(table['columns']) for table in expected.values()) != self.schema_contract['columnCount']:
            raise ValueError('SOURCE_SCHEMA_CONTRACT_INVALID')
        sql = ('SELECT c.TABLE_NAME,c.ORDINAL_POSITION,c.COLUMN_NAME,c.COLUMN_TYPE,c.IS_NULLABLE,c.COLUMN_KEY,t.TABLE_TYPE '
               'FROM information_schema.COLUMNS c JOIN information_schema.TABLES t '
               'ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME '
               'WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME IN ('
               + ','.join(['%s'] * len(table_names)) + ') ORDER BY c.TABLE_NAME,c.ORDINAL_POSITION')
        with self.connection.cursor() as cursor:
            cursor.execute(sql, table_names)
            actual_rows = list(cursor.fetchall())
        actual = {}
        for row in actual_rows:
            actual.setdefault(row['TABLE_NAME'], []).append(row)
        if set(actual) != set(expected):
            raise ValueError('SOURCE_SCHEMA_TABLE_DRIFT')
        for table_name, table in expected.items():
            observed = sorted(actual[table_name], key=lambda row: row['ORDINAL_POSITION'])
            if [row['COLUMN_NAME'] for row in observed] != [column['name'] for column in table['columns']]:
                raise ValueError('SOURCE_SCHEMA_COLUMN_DRIFT:' + table_name)
            for row, column in zip(observed, table['columns']):
                if (row['COLUMN_TYPE'] != column['type'] or row['IS_NULLABLE'] != column['nullable']
                        or row['COLUMN_KEY'] != column['key'] or row['ORDINAL_POSITION'] != column['position']
                        or row['TABLE_TYPE'] != table['tableType']):
                    raise ValueError('SOURCE_SCHEMA_TYPE_OR_CONSTRAINT_DRIFT:' + table_name + '.' + column['name'])
        self.schema_validated = True

    def select(self, code, predicate, params):
        self.validate_source_schema()
        domain = self.domains[code]
        columns = ','.join('`' + f['key'] + '`' for f in domain['fields'])
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT ' + columns + ' FROM `' + domain['sourceTable'] + '` WHERE ' + predicate, params)
            rows = list(cursor.fetchall())
        self.rows[code] = rows
        return rows

    def select_in(self, code, field, values):
        if field not in {column['key'] for column in self.domains[code]['fields']}:
            raise ValueError('SOURCE_SCOPE_FIELD_UNAUDITED:' + code)
        values = sorted({value for value in values if value is not None and str(value).strip()}, key=str)
        if not values:
            self.rows[code] = []
            return []
        return self.select(code, '`' + field + '` IN (' + ','.join(['%s'] * len(values)) + ')', values)

    def collect(self, selection, batch_key, expected_checksum=None):
        self.rows = {code: [] for code in self.domains}
        pid = str(selection['sourceProjectKey'])
        if not re.fullmatch(r'[1-9][0-9]*', pid):
            raise ValueError('INVALID_SOURCE_PROJECT_KEY')
        self.validate_source_schema()
        self.validate_project_scope(pid)
        root = self.select('project', '`projectId`=%s', (pid,))
        if len(root) != 1:
            raise ValueError('SOURCE_PROJECT_NOT_UNIQUE')
        project_code = root[0]['projectCode']
        groups = self.select('groups', '`projectCode`=%s', (project_code,))
        contracts = self.select_in('contracts', 'projectGroupCode', [r['projectGroupCode'] for r in groups])
        contract_numbers = {r['contractNo'] for r in contracts}
        if selection['sourceContractNo'] not in contract_numbers:
            raise ValueError('SOURCE_CONTRACT_NOT_ASSOCIATED')
        orders = self.select_in('orders', 'id', selection['sourceOrderIds'])
        executions = self.select_in('executions', 'id', selection['sourceExecutionIds'])
        if len(orders) != len(set(selection['sourceOrderIds'])) or len(executions) != len(set(selection['sourceExecutionIds'])):
            raise ValueError('SOURCE_CHAIN_ID_NOT_FOUND')
        execution_numbers = {r['orderExecNumber'] for r in executions}
        if any(r['contractNo'] not in contract_numbers or r['orderExecNumber'] not in execution_numbers for r in orders):
            raise ValueError('SOURCE_ORDER_CHAIN_MISMATCH')
        crm_projects = {r['smsProjectCode'] for r in groups} | {project_code}
        if any(r['projectCode'] not in crm_projects for r in executions):
            raise ValueError('SOURCE_EXECUTION_PROJECT_MISMATCH')
        lines = []
        for order in orders:
            # ERP line identity includes company and source; an order number alone is not unique.
            lines.extend(self.select('order-lines', '`orderNumber`=%s AND `compCode`=%s AND `source`=%s AND `lineType`=%s',
                                     (order['orderNumber'], order['compCode'], order['source'], order['orderType'])))
        self.rows['order-lines'] = lines
        direct = ['state', 'parties', 'members', 'products', 'installations', 'software-changes',
                  'software-versions', 'tasks', 'maintenance', 'callbacks', 'notifications',
                  'weekly', 'instructions', 'real-products', 'supervision']
        for code in direct:
            self.select(code, '`projectId`=%s', (pid,))
        self.collect_real_product_upstream()
        self.select_in('plans', 'contractNo', contract_numbers)
        self.collect_project_code_children(pid, project_code)
        self.select('incidents', '`projectCode`=%s OR `contractNo` IN (' + ','.join(['%s'] * len(contract_numbers)) + ')',
                    [project_code, *sorted(contract_numbers)])
        self.select_in('licenses', 'contract', contract_numbers)
        subcontracts = self.select('subcontracts', 'FIND_IN_SET(%s,`projectIds`)>0', (pid,))
        for code in ['subcontract-payments', 'subcontract-prices']:
            self.select_in(code, 'subcontractId', [r['id'] for r in subcontracts])
        for code in ['weekly-items', 'weekly-feedback']:
            self.select_in(code, 'weeklyId', [r['weeklyId'] for r in self.rows['weekly']])
        links = self.select_in('callback-questionnaires', 'callBackId', [r['id'] for r in self.rows['callbacks']])
        questionnaire_ids = [r['quesnaireId'] for r in links] + [r['quesnaireId'] for r in self.rows['maintenance']] + [r['quesnaireId'] for r in self.rows['supervision']]
        questionnaires = self.select_in('questionnaires', 'id', [x for x in questionnaire_ids if x and str(x) != '0'])
        answers = self.select_in('questionnaire-answers', 'quesnaireResultHeaderId', [r['id'] for r in questionnaires])
        template_ids = {r['quesnaireTemplateHeaderId'] for r in questionnaires + answers if r.get('quesnaireTemplateHeaderId')}
        self.select_in('questionnaire-templates', 'id', template_ids)
        questions = self.select_in('questionnaire-questions', 'quesnaireTemplateHeaderId', template_ids)
        self.select_in('questionnaire-options', 'questionId', [r['id'] for r in questions])
        approvals = []
        # Exact process keys come from the old callback/subcontract query contracts; objId alone can collide.
        for process, parents in [('CallBack', self.rows['callbacks']), ('Subcontract', subcontracts)]:
            ids = [r['id'] for r in parents]
            if ids:
                approvals.extend(self.select('approvals', '`procdefKey`=%s AND `objId` IN (' + ','.join(['%s'] * len(ids)) + ')', [process, *ids]))
        self.rows['approvals'] = approvals
        # Shipment lineage was reconciled during the approved pilot; preserve explicit source row IDs.
        shipments = self.select_in('shipment-facts', 'id', selection['sourceShipmentIds'])
        if len(shipments) != len(set(selection['sourceShipmentIds'])):
            raise ValueError('SOURCE_SHIPMENT_ID_NOT_FOUND')
        if 'shipment-packages' in self.domains:
            self.select_in('shipment-packages', 'packlist_id', [r['pack_id'] for r in shipments])
        for code in ['shipment-logistics', 'shipment-products', 'warranties']:
            self.select_in(code, 'contract_code', shipment_contracts(contract_numbers))
        normalized_contracts = {number.replace('-C', '') for number in contract_numbers}
        self.select_in('device-relations', 'contract', normalized_contracts)
        serials = {r[field] for r in shipments for field in ['barcode', 'barcode2'] if r.get(field)}
        serials |= {r['barcode'] for r in self.rows['installations'] if r.get('barcode')}
        self.select_in('factory-versions', 'serial_number', serials)
        # Scope remains the exact approved raw shipment rows and this source project's installation rows.
        # The current target device owner must not filter out older source business facts.
        for code, field in [('warranty-services', 'barcode'), ('warranty-registrations', 'barCode'),
                            ('warranty-changes', 'barcode')]:
            self.select_in(code, field, serials)
        material_codes = {r[field] for r in shipments for field in ['item', 'item2'] if r.get(field)}
        self.select_in('shipment-materials', 'item', material_codes)
        return self.bundle(selection, batch_key, expected_checksum)

    def validate_project_scope(self, source_project_key):
        """Use the same canonical-first SQL as P16 and the downstream task definitions."""
        sql, params = valid_project_sql()
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT canonicalProjectId FROM (' + sql + ') valid_project WHERE projectId=%s',
                           [*params, source_project_key])
            scoped = list(cursor.fetchall())
        if len(scoped) != 1:
            raise ValueError('SOURCE_PROJECT_NOT_EFFECTIVE')
        self.canonical_project_id = str(scoped[0]['canonicalProjectId'])

    def collect_project_code_children(self, source_project_key, project_code):
        # The original DAO uses the first project-code segment for these two lists.
        # A shared segment cannot prove which split project owns a row.
        if not isinstance(project_code, str) or not project_code.strip():
            raise ValueError('SOURCE_ASSOCIATED_PROJECT_CODE_INVALID')
        query_code = project_code.split('-', 1)[0]
        if not query_code:
            raise ValueError('SOURCE_ASSOCIATED_PROJECT_CODE_INVALID')
        for code in ['lease-products', 'configured-products']:
            self.select(code, '`projectCode`=%s AND BINARY `projectCode`=BINARY %s', (query_code, query_code))
        if not any(self.rows[code] for code in ['lease-products', 'configured-products']):
            return
        self.validate_source_schema()
        scope, scope_params = valid_project_sql()
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT projectId,projectCode FROM (' + scope + ') valid_candidate '
                           "WHERE projectId=canonicalProjectId AND SUBSTRING_INDEX(projectCode,'-',1)=%s "
                           "AND BINARY SUBSTRING_INDEX(projectCode,'-',1)=BINARY %s", [*scope_params, query_code, query_code])
            candidates = list(cursor.fetchall())
        if len(candidates) != 1 or str(candidates[0]['projectId']) != getattr(self, 'canonical_project_id', str(source_project_key)):
            raise ValueError('SOURCE_ASSOCIATED_PROJECT_LINK_NOT_UNIQUE')

    def collect_real_product_upstream(self):
        # Every original field is read; equality preserves case, empty strings and NULL.
        fields = ['projectCode', 'orderExecNumber', 'productFirstName', 'productName',
                  'productSubCode', 'productSubModel', 'productSubName', 'num', 'memo']
        captured = {}
        for row in self.rows['real-products']:
            identity = tuple(row.get(field) for field in fields)
            if identity in captured:
                continue
            predicate = ' AND '.join(('`' + field + '` <=> %s') if field == 'num'
                                     else ('BINARY `' + field + '` <=> BINARY %s') for field in fields)
            matches = self.select('real-product-upstream', predicate, identity)
            if len(matches) != 1:
                raise ValueError('SOURCE_REAL_PRODUCT_UPSTREAM_NOT_UNIQUE')
            captured[identity] = matches[0]
        self.rows['real-product-upstream'] = list(captured.values())

    def bundle(self, selection, batch_key, expected_checksum):
        records = []
        keys = {}
        occurrences = Counter()
        for code, rows in self.rows.items():
            domain = self.domains[code]
            for row in rows:
                values = {field['key']: text(row.get(field['key'])) for field in domain['fields']}
                if domain['keyField'] == 'composite':
                    digest = hashlib.sha256(json.dumps(values, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
                    occurrences[(code, digest)] += 1
                    key = digest + ':' + str(occurrences[(code, digest)])
                else:
                    key = values[domain['keyField']]
                    if not key:
                        raise ValueError('SOURCE_RECORD_KEY_MISSING:' + code)
                if (code, key) in keys:
                    raise ValueError('SOURCE_RECORD_KEY_DUPLICATE:' + code)
                keys[(code, key)] = row
                updated = next((values[k] for k in ['updateTime', 'updatedTime', 'updatetime', 'syncTime', 'createTime', 'createdTime'] if values.get(k)), None)
                records.append(dict(domain=code, sourceKey=key, sourceUpdatedAt=updated, values=values,
                                    parentDomain='project' if code != 'project' else None,
                                    parentSourceKey=str(selection['sourceProjectKey']) if code != 'project' else None))
        parent_fields = {'contracts': ('groups', 'projectGroupCode', 'projectGroupCode'),
                         'software-versions': ('software-changes', 'logId', 'id'),
                         'callback-questionnaires': ('callbacks', 'callBackId', 'id'),
                         'questionnaire-answers': ('questionnaires', 'quesnaireResultHeaderId', 'id'),
                         'questionnaire-questions': ('questionnaire-templates', 'quesnaireTemplateHeaderId', 'id'),
                         'questionnaire-options': ('questionnaire-questions', 'questionId', 'id'),
                         'subcontract-payments': ('subcontracts', 'subcontractId', 'id'),
                         'subcontract-prices': ('subcontracts', 'subcontractId', 'id'),
                         'weekly-items': ('weekly', 'weeklyId', 'weeklyId'),
                         'weekly-feedback': ('weekly', 'weeklyId', 'weeklyId'),
                         'tasks': ('tasks', 'parentId', 'taskId'),
                         'instructions': ('instructions', 'instructionsId', 'id')}
        for record in records:
            code, values = record['domain'], record['values']
            relation = parent_fields.get(code)
            if code == 'approvals':
                relation = ('callbacks' if values['procdefKey'] == 'CallBack' else 'subcontracts', 'objId', 'id')
            if code == 'order-lines':
                parents = [r for r in records if r['domain'] == 'orders' and all(r['values'][field] == values[field] for field in ['orderNumber', 'compCode', 'source'])
                           and r['values'].get('orderType') == values.get('lineType')]
            elif relation:
                parent_code, child_field, parent_field = relation
                parents = [r for r in records if r['domain'] == parent_code and not (parent_code == code and r['sourceKey'] == record['sourceKey']) and r['values'].get(parent_field) == values.get(child_field)]
            else:
                continue
            if len(parents) == 1:
                record.update(parentDomain=parents[0]['domain'], parentSourceKey=parents[0]['sourceKey'])
            elif len(parents) > 1:
                raise ValueError('SOURCE_PARENT_AMBIGUOUS:' + code)
        return dict(batchKey=batch_key, sourceProjectKey=str(selection['sourceProjectKey']),
                    sourceContractNo=selection['sourceContractNo'], sourceReadAt=datetime.now().isoformat(timespec='milliseconds'),
                    expectedSnapshotChecksum=expected_checksum,
                    domainCounts={code: len(rows) for code, rows in self.rows.items()}, records=records)


def synchronize(session, api_base, project_id, bundle):
    """Exact project scope and idempotency are enforced again by the server."""
    response = session.post(api_base.rstrip('/') + '/api/v1/pms/projects/' + str(project_id) + '/legacy-detail/imports', json=bundle, timeout=120)
    response.raise_for_status()
    result = response.json()
    if result.get('code') != 0:
        raise ValueError('DETAIL_IMPORT_REJECTED:' + str(result.get('code')))
    return result['data']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True, help='Private, approved source-to-target selection manifest')
    parser.add_argument('--batch-key', required=True)
    parser.add_argument('--api-base', default='http://127.0.0.1:59281/admin-api')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    import pymysql
    import requests
    manifest = json.loads(args.manifest.read_text('utf8'))
    with pymysql.connect(host=os.environ['DPPMS_HOST'], port=int(os.environ.get('DPPMS_PORT', '3306')),
                         user=os.environ['DPPMS_USER'], password=os.environ['DPPMS_PASSWORD'], database=os.environ['DPPMS_DATABASE'],
                         charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor, autocommit=False) as source:
        with source.cursor() as cursor:
            cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        session = requests.Session()
        if args.apply:
            session.headers.update({'Authorization': 'Bearer ' + os.environ['NPDMS_ACCESS_TOKEN'],
                                    'tenant-id': os.environ['NPDMS_TENANT_ID']})
        for selection in manifest:
            bundle = DppmsDetailReader(source).collect(selection, args.batch_key, selection.get('expectedSnapshotChecksum'))
            result = synchronize(session, args.api_base, selection['projectId'], bundle) if args.apply else None
            print(json.dumps({'selection': selection['maskedKey'], 'mode': 'APPLY' if args.apply else 'DRY_RUN',
                              'domainCounts': bundle['domainCounts'], 'decision': result['decision'] if result else None}))
        source.rollback()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Database driver errors can include connection secrets or source values; keep console output bounded.
        print(json.dumps({'result': 'FAILED', 'errorType': type(error).__name__}))
        raise SystemExit(1)
