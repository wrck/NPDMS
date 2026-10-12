"""Extract exact production DDL, never infer columns from sync mappings.
Only empty isolated databases may consume this artifact. Data DML from migrations is
intentionally omitted. V160's shadow names are resolved to their canonical rename.
"""
from pathlib import Path
import re,json,hashlib
root=Path(__file__).resolve().parents[3]
out=Path(__file__).parent
names=set('com_contract com_shipment_contract_reference com_shipment_package system_company system_dept ast_device ast_device_shipment ast_device_relationship com_sales_order com_sales_order_line int_sync_connection int_sync_task int_sync_run int_sync_binding plt_migration_batch plt_migration_source_record plt_external_key_mapping plt_migration_issue plt_idempotency_record plt_operation_audit plt_outbox_event'.split())
parts=[]; manifest=[];created=set()
for p in sorted((root/'sql/migrations').glob('V*.sql'),key=lambda p:tuple(map(int,re.match(r'V([\d_]+)__',p.name)[1].split('_')))):
    if int(re.match(r'V(\d+)',p.name)[1])>401:continue
    raw=p.read_text()
    # Anchored statements avoid matching dynamic SQL literals or comments.
    for m in re.finditer(r'(?im)^\s*((?:CREATE TABLE(?: IF NOT EXISTS)?|ALTER TABLE)\s+`?([\w]+)`?\b.*?;)',raw,re.S):
        statement=m[1]; name=m[2];canonical=name.removeprefix('fcom001_shadow_')
        if canonical not in names:continue
        if statement.upper().startswith('CREATE'):
            if canonical in created:raise RuntimeError('duplicate create '+canonical)
            created.add(canonical)
        if name.startswith('fcom001_shadow_'):
            statement=statement.replace('fcom001_shadow_','')
        parts.append('-- '+p.name+'\n'+statement)
        manifest.append(dict(file=str(p.relative_to(root)),table=canonical,sha256=hashlib.sha256(m[1].encode()).hexdigest(),canonicalShadowRename=name!=canonical))
assert created==names,(names-created)
# V367 requires checks disabled only while changing parent auto-increment. Existing
# production V351 drops only four approved device FKs. All remaining FKs/checks stay.
sql='-- Generated exact production DDL; synthetic data is supplied separately.\nSET FOREIGN_KEY_CHECKS=0;\n'+'\n\n'.join(parts)+'\nSET FOREIGN_KEY_CHECKS=1;\n'
(out/'full-chain-schema.sql').write_text(sql)
(out/'full-chain-schema-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(len(parts),'production DDL statements;',len(names),'tables;',len(sql),'characters')
