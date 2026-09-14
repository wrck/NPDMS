#!/usr/bin/env python3
"""Read-only reconciliation of DPPMS orders, COM copies and integration lineage.

The migration itself must run through run_dppms_order_migration.py and the module.
Source credentials come from the environment; target credentials from the named
local Docker container. No SQL writes or credentials are stored by this checker.
"""
import argparse
from collections import Counter
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess

import mysql.connector


HEAD_FIELDS = {
    'customerCode': 'customer_code', 'customerName': 'customer_name',
    'salesType': 'sales_type', 'projectName': 'source_project_name',
    'orderComment': 'order_comment', 'orderCreateTime': 'order_create_time',
    'customerRequireTime': 'customer_required_time',
}
LINE_FIELDS = {
    'itemCode': 'item_code', 'itemDesc': 'item_desc',
    'orderQuantity': 'order_qty', 'openQuantity': 'open_qty',
    'bundleCode': 'bundle_code', 'warrantyMonth': 'warranty_month',
    'profitCenter': 'profit_center', 'realOrderExecNumber': 'real_execution_no',
}


def normalize(value):
    # COM's text contract uses Java String.trim(); preserve meaningful non-ASCII spaces.
    return (value.strip(''.join(map(chr, range(33)))) or None) if isinstance(value, str) else value


def source_groups(connection, line):
    table = 'pm_order_line_from_erp' if line else 'pm_order_data_from_erp'
    fields = LINE_FIELDS if line else HEAD_FIELDS
    kind = 'lineType' if line else 'orderType'
    columns = ['id', 'source', 'compCode', kind, 'orderNumber', 'syncTime', *fields]
    if line:
        columns += ['lineNum', 'customInfo']
    cursor = connection.cursor(dictionary=True)
    cursor.execute('SELECT ' + ','.join(columns) + ' FROM ' + table)
    groups = {}
    for row in cursor:
        identity = [normalize(row[name]) for name in ['source', 'compCode', kind, 'orderNumber']]
        if line:
            identity.append(normalize(row['lineNum']))
        key = 'DPPMS|' + '|'.join('' if value is None else str(value) for value in identity)
        payload = {target: normalize(row[source]) for source, target in fields.items()}
        # Match source conflict isolation, including raw ERP custom metadata.
        raw = tuple(row[name] for name in fields) + ((row['customInfo'],) if line else ())
        previous = groups.get(key)
        if previous:
            previous['conflict'] |= previous['raw'] != raw
            previous['version'] = max(previous['version'], row['syncTime'])
            previous['ids'].append(str(row['id']))
        else:
            groups[key] = {'payload': payload, 'raw': raw, 'version': row['syncTime'],
                           'ids': [str(row['id'])], 'identity': identity, 'conflict': False}
    cursor.close()
    return groups


def reconcile(source, target, task_id):
    heads = source_groups(source, False)
    lines = source_groups(source, True)
    expected = {'ORDER': {}, 'LINE': {}}
    issues = {}
    for object_name, groups in [('ORDER', heads), ('LINE', lines)]:
        for key, group in groups.items():
            reason = 'DUPLICATE_BUSINESS_KEY_CONFLICT' if group['conflict'] else None
            if not reason and object_name == 'LINE' and key.rsplit('|', 1)[0] not in heads:
                reason = 'PARENT_ORDER_MISSING'
            if reason:
                for source_id in group['ids']:
                    issues[(object_name, source_id)] = reason
                continue
            values = dict(group['payload'])
            identity = group['identity']
            values.update(company_code=str(identity[1]), order_type=str(identity[2]), order_no=str(identity[3]),
                          source_system='ERP', source_updated_at=group['version'],
                          source_lifecycle_status='RETURNED' if str(identity[2]) == '1' else 'ACTIVE')
            if object_name == 'LINE':
                qty, opened = values['order_qty'], values['open_qty']
                values.update(line_no=str(identity[4]), line_type=str(identity[2]), unit_code=None,
                              unit_scale=0, quantity_status='PENDING_AUTHORITY', product_code=None, model_code=None,
                              delivered_qty=None if qty is None or opened is None else qty-opened)
            expected[object_name][key] = (values, group['ids'])
    findings = []
    finding_counts = Counter()

    def discrepancy(kind, example):
        finding_counts[kind] += 1
        if len(findings) < 20:
            findings.append({'kind': kind, 'example': example})

    ids = {'ORDER': {}, 'LINE': {}}
    expected_bindings = {}
    target_counts = {}
    negative_lines = 0
    cursor = target.cursor(dictionary=True)
    for object_name, table in [('ORDER', 'com_sales_order'), ('LINE', 'com_sales_order_line')]:
        cursor.execute('SELECT * FROM ' + table + " WHERE tenant_id=1 AND source_record_key LIKE 'DPPMS|%' AND deleted=0")
        count = 0
        for row in cursor:
            count += 1
            key = row['source_record_key']
            ids[object_name][key] = row['id']
            wanted = expected[object_name].get(key)
            if not wanted:
                discrepancy('unexpected_target', key)
                continue
            fields, raw_ids = wanted
            mismatched = [name for name, value in fields.items() if row[name] != value]
            if datetime.fromisoformat(row['source_version']) != fields['source_updated_at']:
                mismatched.append('source_version')
            if object_name == 'LINE':
                if row['order_id'] != ids['ORDER'].get(key.rsplit('|', 1)[0]):
                    mismatched.append('order_id')
                negative_lines += row['order_qty'] is not None and row['order_qty'] < 0
            if mismatched:
                discrepancy('field_mismatch', {'key': key, 'fields': mismatched})
            for source_id in raw_ids:
                expected_bindings[(object_name, source_id)] = row['id']
        target_counts[object_name] = count
        for key in expected[object_name].keys() - ids[object_name].keys():
            discrepancy('missing_target', key)
    cursor.execute('SELECT object_key,source_key,target_id,target_shared FROM int_sync_binding WHERE tenant_id=1 AND task_id=%s AND deleted=0', (task_id,))
    actual_bindings = {}
    for row in cursor:
        key = (row['object_key'], row['source_key'])
        actual_bindings[key] = row['target_id']
        if expected_bindings.get(key) != row['target_id']:
            discrepancy('binding_mismatch', str(key))
    for key in expected_bindings.keys() - actual_bindings.keys():
        discrepancy('missing_binding', str(key))
    cursor.execute("""SELECT DISTINCT b.source_table,s.source_record_key,i.raw_payload
        FROM plt_migration_issue i JOIN plt_migration_source_record s
          ON s.tenant_id=i.tenant_id AND s.id=i.source_record_id
        JOIN plt_migration_batch b ON b.tenant_id=s.tenant_id AND b.id=s.batch_id
        JOIN int_sync_run r ON b.purpose_code=CONCAT('SYNC_',r.id) AND r.tenant_id=b.tenant_id
        WHERE r.tenant_id=1 AND r.task_id=%s AND r.status='SUCCESS' AND r.preview=0
          AND i.issue_status='OPEN' AND i.deleted=0""", (task_id,))
    actual_issues = {}
    for row in cursor:
        object_name = 'LINE' if row['source_table'] == 'pm_order_line_from_erp' else 'ORDER'
        key = (object_name, row['source_record_key'])
        reason = json.loads(row['raw_payload'] or '{}').get('reason', '')
        actual_issues[key] = reason.split(':', 1)[0]
        if actual_issues[key] != issues.get(key):
            discrepancy('issue_mismatch', str(key))
    for key in issues.keys() - actual_issues.keys():
        discrepancy('missing_issue', str(key))
    cursor.execute('SELECT status,COUNT(*) count,SUM(read_count) read_count FROM int_sync_run WHERE tenant_id=1 AND task_id=%s GROUP BY status', (task_id,))
    runs = cursor.fetchall()
    cursor.close()
    return {'checkedAt': datetime.now().isoformat(), 'taskId': str(task_id),
            'sourceRows': {name: sum(len(group['ids']) for group in groups.values()) for name, groups in [('ORDER', heads), ('LINE', lines)]},
            'expectedTargets': {name: len(rows) for name, rows in expected.items()}, 'actualTargets': target_counts,
            'expectedBindings': len(expected_bindings), 'actualBindings': len(actual_bindings),
            'expectedIssues': dict(Counter(issues.values())), 'actualIssues': dict(Counter(actual_issues.values())),
            'negativeOrderLines': negative_lines, 'runs': runs,
            'findingCounts': dict(finding_counts), 'findingExamples': findings, 'passed': not finding_counts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--task-id', type=int, required=True)
    parser.add_argument('--output', type=Path, default=Path('.run/dppms-order-reconciliation.json'))
    args = parser.parse_args()
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'npdms-50eb-test-mysql-1']))[0]
    environment = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
    if environment['MYSQL_DATABASE'] != 'npdms_test':
        raise RuntimeError('unexpected target database')
    target = mysql.connector.connect(host='127.0.0.1', port=23316, database='npdms_test',
        user=environment['MYSQL_USER'], password=environment['MYSQL_PASSWORD'])
    source = mysql.connector.connect(host=os.environ.get('DPPMS_SOURCE_HOST', '10.210.0.11'), port=3306,
        database='dppms', user=os.environ.get('DPPMS_SOURCE_USER', 'root'), password=os.environ['ORDER_SOURCE_PASSWORD'])
    try:
        source.start_transaction(readonly=True, consistent_snapshot=True)
        target.start_transaction(readonly=True, consistent_snapshot=True)
        report = reconcile(source, target, args.task_id)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, default=str), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=True, default=str))
    finally:
        source.rollback(); source.close()
        target.rollback(); target.close()
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
