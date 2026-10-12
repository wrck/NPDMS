"""Restore six source fixtures from checked-in historical physical-field evidence.
Column definitions/defaults and key flags are evidenced; original composite index
layout, index names, engine and collation are not recorded and are not invented as facts.
"""
from pathlib import Path
import json, re, hashlib
root=Path(__file__).resolve().parents[3]
evidence=root/'specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-canonical.jsonl'
names=['fb_contract','pm_order_data_from_erp','sms_ofst_contract_head_sap','fb_shipment','fb_shipment_barcode','fb_shipment_barcode_relation']
rows=[json.loads(line) for line in evidence.read_text().splitlines()]
statements=['-- Generated source fixture from historical physical-field evidence, not a live SHOW CREATE TABLE export.', '-- Individual indexes reproduce known PRI/UNI/MUL flags; original composite index definitions are unavailable.']
manifest={'evidence':str(evidence.relative_to(root)),'evidenceSha256':hashlib.sha256(evidence.read_bytes()).hexdigest(),'limitations':['Historical field evidence, not current DPPMS DDL export','Original index names/composite index grouping and table collation unavailable; use declared per-column key flags and MySQL default collation'], 'tables':[]}
for name in names:
    columns=sorted((r for r in rows if r.get('sourceTable')==name),key=lambda r:r['sourceOrdinal'])
    assert columns and len({r['sourceColumn'] for r in columns})==len(columns)
    definitions=[]; indexes=[]; entries=[]
    for r in columns:
        col=r['sourceColumn'];typ=r['sourceType'].lower();flags=str(r.get('constraint','')).upper();default=r.get('defaultValue')
        assert re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',col)
        assert re.fullmatch(r'[a-z]+(?:\([0-9,]+\))?',typ),typ
        definition=f'`{col}` {typ} '+('NOT NULL' if r['nullable']=='NO' else 'NULL')
        if default not in (None,'-'):
            definition+=' DEFAULT '+('CURRENT_TIMESTAMP' if default.upper()=='CURRENT_TIMESTAMP' else "'"+default.replace("'","''")+"'")
        if 'AUTO_INCREMENT' in flags:definition+=' AUTO_INCREMENT'
        definitions.append(definition)
        if 'PRI' in flags:indexes.append(f'PRIMARY KEY (`{col}`)')
        elif 'UNI' in flags:indexes.append(f'UNIQUE KEY `uk_fixture_{col}` (`{col}`)')
        elif 'MUL' in flags:indexes.append(f'KEY `idx_fixture_{col}` (`{col}`)')
        normalized=re.sub(r'^(bigint|int|smallint|tinyint|mediumint)\(\d+\)',r'\1',typ)
        entries.append({'name':col,'type':normalized,'nullable':r['nullable'],'default':default,'keyFlag':flags})
    statements.append(f'CREATE TABLE `{name}` (\n  '+',\n  '.join(definitions+indexes)+'\n) ENGINE=InnoDB;')
    manifest['tables'].append({'name':name,'columns':entries})
out=Path(__file__).parent
(out/'source-schema.sql').write_text('\n\n'.join(statements)+'\n')
(out/'source-schema-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print({t['name']:len(t['columns']) for t in manifest['tables']})
