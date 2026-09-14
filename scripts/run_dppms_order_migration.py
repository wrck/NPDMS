#!/usr/bin/env python3
"""Run bounded DPPMS pages through the migration module's authenticated HTTP API.

No source or target SQL is executed by this client. The module owns source reads,
business writes, transactions, original records, issues and source/target mappings.
Credentials are read from environment variables and never written to the receipt.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import time
import urllib.request
import uuid


class ModuleClient:
    def __init__(self, base: str, tenant: int, username: str):
        self.base = base.rstrip('/')
        self.headers = {'Content-Type': 'application/json', 'tenant-id': str(tenant)}
        result = self.request('/admin-api/system/auth/login', {
            'username': username, 'password': os.environ['NPDMS_ADMIN_PASSWORD'],
            'captchaVerification': '',
        })
        self.headers['Authorization'] = 'Bearer ' + result['accessToken']

    def request(self, path: str, data=None):
        request = urllib.request.Request(self.base + path,
            data=None if data is None else json.dumps(data).encode(), headers=self.headers)
        with urllib.request.urlopen(request, timeout=120) as response:
            result = json.load(response)
        if result['code'] != 0:
            raise RuntimeError(f"{path}: {result.get('msg', 'request failed')}")
        return result['data']

    def api(self, path: str, data=None):
        return self.request('/admin-api/api/v1/pms/integration' + path, data)

    def run(self, task: dict, preview: bool):
        run_id = self.api('/runs', {'taskId': task['id'], 'expectedVersion': task['version'],
            'requestKey': 'dppms-orders-' + str(uuid.uuid4()), 'preview': preview,
            'full': True, 'adoptExisting': False, 'confirmPreparation': False})
        print(f"RUN {run_id} {'PREVIEW' if preview else 'APPLY'}", flush=True)
        while True:
            result = self.api(f'/runs/{run_id}')
            if result['status'] in ('SUCCESS', 'PREVIEW_READY'):
                return result
            if result['status'] == 'FAILED':
                raise RuntimeError(f"run {run_id}: {result.get('errorMessage')}")
            time.sleep(5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:59280')
    parser.add_argument('--tenant', type=int, default=1)
    parser.add_argument('--username', default='admin')
    parser.add_argument('--connection-id', required=True, type=int)
    parser.add_argument('--page-size', type=int, default=2000)
    parser.add_argument('--apply', action='store_true', help='default creates and previews the first page only')
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--max-pages', type=int, default=0)
    parser.add_argument('--receipt', type=Path, default=Path('.run/dppms-order-migration.json'))
    args = parser.parse_args()
    if not 1 <= args.page_size <= 10000:
        parser.error('page-size must be between 1 and 10000')
    client = ModuleClient(args.base, args.tenant, args.username)
    template = client.api(f'/templates/dppms-orders?connectionId={args.connection_id}')
    if template.get('autoPaging'):
        raise RuntimeError('模板已由迁移模块自动分页；请在工作台启动或关联重试，不再使用旧的手工游标脚本。')
    candidates = [task for task in client.api('/tasks?pageNo=1&pageSize=100')['list']
                  if task['definition']['adapter'] == 'DPPMS_ERP_ORDER'
                  and task['definition']['sourceSystem'] == 'DPPMS']
    if len(candidates) > 1:
        raise RuntimeError('multiple matching tasks')
    task = candidates[0] if candidates else None
    if task and str(task['definition']['connectionId']) != str(args.connection_id):
        raise RuntimeError('existing task uses a different source connection')
    receipt = {'base': args.base, 'tenant': args.tenant, 'connectionId': args.connection_id,
               'nextAfter': {'ORDER': 0, 'LINE': 0}, 'completed': [], 'runs': []}
    if args.resume:
        receipt = json.loads(args.receipt.read_text(encoding='utf-8'))
        if any(receipt[k] != v for k, v in [('base', args.base), ('tenant', args.tenant), ('connectionId', args.connection_id)]):
            raise RuntimeError('receipt belongs to a different endpoint, tenant or source')
        if task is None or str(task['id']) != str(receipt['taskId']):
            raise RuntimeError('receipt task does not match the current task')
    applied_pages = 0
    for object_name in ('ORDER', 'LINE'):
        if object_name in receipt['completed']:
            continue
        while True:
            definition = copy.deepcopy(template)
            for source in definition['sources']:
                source['parameters'] = {'afterId': receipt['nextAfter'][source['object']],
                                        'pageSize': args.page_size if source['object'] == object_name else 0}
            if task:
                task = client.api(f"/tasks/{task['id']}")
                if task.get('activeRunId'):
                    raise RuntimeError('task already has an active run; wait for it before resuming')
            task_id = client.api('/tasks', {'id': task['id'] if task else None,
                'expectedVersion': task['version'] if task else None,
                'name': 'DPPMS 销售订单与订单行迁移（分批）', 'definition': definition})
            task = client.api(f'/tasks/{task_id}')
            receipt['taskId'] = task_id
            result = client.run(task, preview=not args.apply)
            run_id = result['id']
            changes = []
            page = 1
            while True:
                data = client.api(f'/runs/{run_id}/changes?pageNo={page}&pageSize=100')
                changes.extend(data['list'])
                if len(changes) >= data['total']:
                    break
                page += 1
            record = {'runId': run_id, 'object': object_name, 'preview': not args.apply,
                'readCount': result['readCount'], 'summary': json.loads(result.get('summaryJson') or '{}'),
                'afterId': receipt['nextAfter'][object_name], 'finishedAt': result.get('finishedAt')}
            receipt['runs'].append(record)
            if args.apply:
                if result['readCount'] != len(changes):
                    raise RuntimeError('module did not classify every source row; cursor not advanced')
                if changes:
                    receipt['nextAfter'][object_name] = max(int(c['sourceKey']) for c in changes)
                if result['readCount'] < args.page_size:
                    receipt['completed'].append(object_name)
            args.receipt.parent.mkdir(parents=True, exist_ok=True)
            args.receipt.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps(record, ensure_ascii=True), flush=True)
            if not args.apply:
                return
            applied_pages += 1
            if args.max_pages and applied_pages >= args.max_pages:
                return
            if object_name in receipt['completed']:
                break
    print('COMPLETE: both source tables classified; check module issue records and target reconciliation.', flush=True)


if __name__ == '__main__':
    main()
