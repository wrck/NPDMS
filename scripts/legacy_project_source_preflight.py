"""Offline source integrity check for a legacy project pilot; never opens a database.

Input is a locally captured bundle of source rows. A passing report proves only
source relationships and quantity snapshots, not customer identity, authorization,
template eligibility, or permission to create a runtime project.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path


def source_checksum(bundle: dict) -> str:
    canonical = {key: sorted(value, key=lambda row: json.dumps(row, sort_keys=True, ensure_ascii=False))
                 if isinstance(value, list) else value for key, value in bundle.items()}
    return hashlib.sha256(json.dumps(canonical, sort_keys=True, ensure_ascii=False,
                                     separators=(',', ':')).encode()).hexdigest()


def inventory_key(line: dict) -> str | None:
    info = line.get('customInfo')
    if isinstance(info, str) and info:
        info = json.loads(info)
    value = info.get('inventTransId') if isinstance(info, dict) else None
    return None if value is None else str(value)


def reconcile_source(bundle: dict) -> dict:
    issues: list[str] = []
    project = bundle['project']
    orders = bundle.get('orders', [])
    executions = bundle.get('executions', [])
    lines = bundle.get('order_lines', [])
    shipments = bundle.get('shipment_lines', [])
    products = bundle.get('products', [])
    links = []
    if not project.get('projectId') or not project.get('projectCode'):
        issues.append('PROJECT_SOURCE_IDENTITY_MISSING')
    groups = [r for r in bundle.get('groups', []) if r['projectCode'] == project['projectCode']]
    if len(groups) != 1:
        issues.append('PROJECT_GROUP_NOT_UNIQUE')
    contracts = [r for r in bundle.get('project_contracts', [])
                 if any(r['projectGroupCode'] == g['projectGroupCode'] for g in groups)]
    contract_numbers = {r['contractNo'] for r in contracts}
    if len(contract_numbers) != 1:
        issues.append('CONTRACT_NOT_UNIQUE')
    if len(orders) != 1:
        issues.append('ORDER_NOT_UNIQUE')
    if len(executions) != 1:
        issues.append('EXECUTION_NOT_UNIQUE')
    if not shipments:
        issues.append('SHIPMENT_MISSING')
    if not lines:
        issues.append('ORDER_LINES_MISSING')
    if not bundle.get('members'):
        issues.append('MEMBER_SNAPSHOT_MISSING')
    elif any(row.get('projectId') != project['projectId'] for row in bundle['members']):
        issues.append('MEMBER_PROJECT_MISMATCH')
    if len({row.get('id') for row in shipments}) != len(shipments) or any(row.get('id') is None for row in shipments):
        issues.append('SHIPMENT_SOURCE_IDENTITY_DUPLICATE_OR_MISSING')
    if not bundle.get('contract_amounts'):
        issues.append('CONTRACT_AMOUNT_SOURCE_MISSING')
    if len(orders) == 1:
        order = orders[0]
        if any(row.get('contract_num') != order['contractNo'] for row in bundle.get('contract_amounts', [])):
            issues.append('CONTRACT_AMOUNT_PARENT_MISMATCH')
        if order['contractNo'] not in contract_numbers or order['orderType'] != 0:
            issues.append('ORDER_CONTRACT_MISMATCH')
        if not order.get('compCode') or not order.get('source'):
            issues.append('ORDER_SOURCE_IDENTITY_MISSING')
        if len(executions) == 1:
            execution = executions[0]
            if (execution['orderExecNumber'] != order['orderExecNumber']
                    or execution['corporationCode'] != order['compCode']
                    or not execution.get('dataSource')):
                issues.append('EXECUTION_ORDER_MISMATCH')
        if any((line['source'], line['compCode'], line['lineType'], line['orderNumber']) !=
               (order['source'], order['compCode'], order['orderType'], order['orderNumber']) for line in lines):
            issues.append('ORDER_LINE_PARENT_MISMATCH')
        if len({line['lineNum'] for line in lines}) != len(lines):
            issues.append('ORDER_LINE_DUPLICATE')
        for line in lines:
            try:
                quantity, opened = Decimal(str(line['orderQuantity'])), Decimal(str(line['openQuantity']))
                if not quantity.is_finite() or not opened.is_finite() or opened < 0 or quantity < opened:
                    raise ValueError()
                snapshots = [r for r in products if r['projectId'] == project['projectId']
                             and r['contractNo'] == order['contractNo']
                             and r['orderNumber'] == line['orderNumber']
                             and str(r['lineNum']) == str(line['lineNum']) and r['itemCode'] == line['itemCode']]
                if len(snapshots) != 1:
                    issues.append('PROJECT_PRODUCT_LINE_NOT_UNIQUE')
                elif any(Decimal(str(snapshots[0][key])) != expected for key, expected in
                         [('orderQuantity', quantity), ('openQuantity', opened),
                          ('deliverQuantity', quantity - opened)]):
                    issues.append('PROJECT_PRODUCT_QUANTITY_DIFF')
            except (InvalidOperation, ValueError, TypeError):
                issues.append('ORDER_QUANTITY_INVALID')
        for shipment in shipments:
            if shipment['orderNumber'] != order['orderNumber']:
                issues.append('SHIPMENT_ORDER_MISMATCH')
                continue
            packages = [r for r in bundle.get('shipment_headers', []) if r['packlist_id'] == shipment['pack_id']]
            refs = [r for r in bundle.get('shipment_contracts', [])
                    if len(packages) == 1 and r['contract_id'] == packages[0]['con_id']]
            if len(packages) != 1 or len(refs) != 1 or refs[0]['contract_code'] != order['contractNo']:
                issues.append('SHIPMENT_CONTRACT_MISMATCH')
                continue
            if shipment.get('rma_no') or shipment.get('isRMA') not in (None, 0):
                issues.append('RMA_REQUIRES_SEPARATE_RULE')
                continue
            if not shipment.get('item'):
                issues.append('SHIPMENT_PRODUCT_MISSING')
                continue
            # fb_shipment_barcode_order_line records a source relationship, not a
            # product equivalence. Preserve both codes; do not infer a BOM or a
            # component-to-order quantity conversion from barcode counts.
            explicit = [r for r in bundle.get('shipment_order_links', [])
                        if shipment.get('barcode') and r.get('barcode') == shipment['barcode']
                        and r.get('pack_id') == shipment['pack_id']]
            if len(explicit) > 1:
                issues.append('SHIPMENT_EXPLICIT_LINK_NOT_UNIQUE')
                continue
            if explicit:
                ref = explicit[0]
                if (ref.get('orderNumber') != order['orderNumber']
                        or ref.get('contractNo') != order['contractNo']
                        or str(ref.get('lineNum')) != str(shipment['lineNum'])
                        or ref.get('orderExecNumber') not in (None, '', order['orderExecNumber'])):
                    issues.append('SHIPMENT_EXPLICIT_LINK_MISMATCH')
                    continue
            try:
                matched = [line for line in lines if (explicit or line['itemCode'] == shipment['item']) and
                           ((order['source'] == 'D365' and inventory_key(line) is not None
                             and inventory_key(line) == str(shipment['lineNum'])) or
                            (order['source'] == 'SAP' and str(line['lineNum']) == str(shipment['lineNum'])))]
            except (ValueError, TypeError):
                matched = []
            if len(matched) != 1:
                issues.append('SHIPMENT_ORDER_LINE_NOT_UNIQUE')
                continue
            links.append({'shipmentSourceId': shipment['id'], 'orderLineSourceId': matched[0]['id'],
                          'packageSourceKey': packages[0]['packlist_id'],
                          'linkBasis': 'EXPLICIT_SHIPMENT_ORDER_LINE' if explicit else 'PRODUCT_AND_SOURCE_LINE',
                          'orderProductCode': matched[0]['itemCode'], 'shipmentProductCode': shipment['item']})
    counts = Counter(issues)
    return {'schemaVersion': 1, 'mode': 'READ_ONLY_SOURCE_PREFLIGHT', 'sourceProjectId': project['projectId'],
            'sourceChecksum': source_checksum(bundle), 'legacyState': project.get('projectState'),
            'sourceComplete': not issues, 'issueCounts': dict(sorted(counts.items())),
            'counts': {'orders': len(orders), 'executions': len(executions), 'orderLines': len(lines),
                       'shipmentLines': len(shipments), 'matchedShipmentLines': len(links)},
            'shipmentLinks': links, 'runtimeAcceptance': 'NOT_RUN', 'databaseWrites': 0}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = reconcile_source(json.loads(args.input.read_text(encoding='utf-8')))
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ['sourceComplete', 'issueCounts', 'databaseWrites']}))
    return 0 if report['sourceComplete'] else 2


if __name__ == '__main__':
    raise SystemExit(main())
