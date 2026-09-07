#!/usr/bin/env python3
"""Execute prospective SDS DDL only inside a labelled network-isolated MySQL container.

Never connects to a host database. Does not test production migration or business
application code. Every case uses synthetic identities in a new empty schema.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

SCHEMA = 'npdms_sds016_validation'
CONTRACT = Path('docs/traceability/sds-revision-016-physical-contract.json')
DDL = Path('specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql')

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def literal(value: object) -> str:
    if value is None: return 'NULL'
    if isinstance(value, int): return str(value)
    # Test data contains no secrets or arbitrary SQL fragments.
    return "'" + str(value).replace('\\','\\\\').replace("'","''") + "'"

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    inspected=json.loads(subprocess.check_output(['docker','inspect',args.container],text=True))[0]
    if inspected.get('Config',{}).get('Labels',{}).get('npdms.sds-validation')!='true' or inspected['HostConfig']['NetworkMode']!='none':
        raise SystemExit('refusing a container without validation label AND network=none')
    def sql(text: str, *, database: bool=True) -> subprocess.CompletedProcess:
        cmd=['docker','exec','-i',args.container,'mysql','-uroot','--batch','--skip-column-names']
        if database:cmd.append(SCHEMA)
        return subprocess.run(cmd,input=text,text=True,capture_output=True,timeout=30,check=False)
    version=sql('SELECT VERSION();',database=False)
    if version.returncode or not version.stdout.strip().startswith('8.4.'):
        raise SystemExit('validation requires MySQL 8.4.x')
    if sql(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{SCHEMA}';",database=False).stdout.strip():
        raise SystemExit('refusing an existing schema; use a fresh isolated container')
    created=sql(f'CREATE DATABASE `{SCHEMA}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;',database=False)
    if created.returncode:raise SystemExit(created.stderr)
    ddl_result=sql((args.root/DDL).read_text())
    if ddl_result.returncode:raise SystemExit(ddl_result.stderr)
    model=json.loads((args.root/CONTRACT).read_text())
    table_count=int(sql('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE();').stdout)
    if table_count!=len(model['tables']):raise SystemExit('physical table count mismatch')
    cases=[]
    next_id=1000
    def insert(table: str, **values: object) -> str:
        nonlocal next_id
        next_id+=1
        fields=[];data=[]
        for name,definition in model['tables'][table]['columns']:
            if 'GENERATED ALWAYS' in definition:continue
            if name in values:value=values[name]
            elif name=='id':value=next_id
            elif name=='tenant_id':value=1
            elif name=='created_by':value=900000001
            elif name=='created_at':value='2026-09-07 00:00:00.000000'
            elif ' NULL' in definition and 'NOT NULL' not in definition:value=None
            elif definition.startswith(('BIGINT','INT','TINYINT')):value=1
            elif definition.startswith('DATETIME'):value='2026-09-07 00:00:00.000000'
            elif definition.startswith('JSON'):value='{}'
            elif definition.startswith('CHAR(64)'):value='a'*64
            else:value='fixture'
            fields.append('`'+name+'`');data.append(literal(value))
        return f'INSERT INTO `{table}` ({",".join(fields)}) VALUES ({",".join(data)});'
    def case(name: str, statement: str, expected_error: int|None=None):
        result=sql(statement)
        matched=re.search(r'ERROR (\d+)',result.stderr)
        error=int(matched.group(1)) if matched else None
        passed=result.returncode==0 if expected_error is None else result.returncode!=0 and error==expected_error
        cases.append({'case':name,'expectedError':expected_error,'actualError':error,'passed':passed,'sqlSha256':hashlib.sha256(statement.encode()).hexdigest()})
        print(f"[{'PASS' if passed else 'FAIL'}] {name}; MySQL error={error}",flush=True)
        if not passed: print(result.stderr[:1200],flush=True)
    dd=dict(definition_kind='STAGE',definition_code='stage',revision_no=1,revision_state='PUBLISHED',schema_version=1,payload='{}',published_at='2026-09-07')
    case('published_definition',insert('proj_delivery_definition_revision',id=101,**dd))
    case('tenant_scoped_same_code',insert('proj_delivery_definition_revision',id=102,tenant_id=2,**dd))
    case('second_definition',insert('proj_delivery_definition_revision',id=103,**(dd|{'definition_code':'task'})))
    case('duplicate_definition_revision',insert('proj_delivery_definition_revision',**dd),1062)
    case('unrecognised_definition_kind',insert('proj_delivery_definition_revision',**(dd|{'definition_kind':'SERVER_SCRIPT'})),3819)
    case('published_requires_timestamp',insert('proj_delivery_definition_revision',**(dd|{'definition_code':'new','published_at':None})),3819)
    case('definition_requires_tenant',insert('proj_delivery_definition_revision',tenant_id=None,**dd),1048)
    case('definition_payload_is_object',insert('proj_delivery_definition_revision',**(dd|{'definition_code':'array','payload':'[]'})),3819)
    case('valid_definition_reference',insert('proj_delivery_definition_reference',owner_revision_id=101,target_revision_id=103))
    case('cross_tenant_definition_reference',insert('proj_delivery_definition_reference',tenant_id=2,owner_revision_id=102,target_revision_id=103),1452)
    case('definition_self_reference',insert('proj_delivery_definition_reference',owner_revision_id=101,target_revision_id=101,reference_key='self'),3819)
    edge=dict(template_revision_id=20,from_stage_code='S0',to_stage_code='S4',transition_code='edge',is_default=1)
    case('non_numeric_adjacent_edge',insert('proj_stage_transition_definition',**edge))
    case('single_default_per_source',insert('proj_stage_transition_definition',**(edge|{'transition_code':'other','to_stage_code':'S1'})),1062)
    case('reject_stage_self_edge',insert('proj_stage_transition_definition',**(edge|{'transition_code':'self','from_stage_code':'S5','to_stage_code':'S5','is_default':0})),3819)
    case('reject_invalid_default_flag',insert('proj_stage_transition_definition',**(edge|{'transition_code':'flag','is_default':2})),3819)
    frozen=dict(project_id=50,from_stage_id=100,to_stage_id=104,graph_version=1,is_default=1,source_transition_id=500)
    case('frozen_edge',insert('proj_project_stage_transition',**frozen))
    case('frozen_graph_single_default',insert('proj_project_stage_transition',**(frozen|{'source_transition_id':501})),1062)
    case('frozen_graph_version_positive',insert('proj_project_stage_transition',**(frozen|{'graph_version':0,'source_transition_id':502})),3819)
    binding=dict(project_id=50,stage_id=104,binding_type='STAGE_NATIVE',binding_version=1,effective_from='2026-09-07')
    case('stage_native_binding',insert('proj_project_stage_execution_contract',**binding))
    case('only_one_current_stage_binding',insert('proj_project_stage_execution_contract',**(binding|{'binding_version':2})),1062)
    case('historical_stage_binding',insert('proj_project_stage_execution_contract',**(binding|{'binding_version':3,'effective_to':'2026-09-08'})))
    case('task_native_not_stage_native',insert('proj_project_stage_execution_contract',**(binding|{'stage_id':105,'binding_type':'TASK_NATIVE'})),3819)
    case('binding_time_order',insert('proj_project_stage_execution_contract',**(binding|{'stage_id':106,'effective_to':'2026-09-06'})),3819)
    case('single_com_watermark',insert('com_delivery_scope_project_version',project_id=50,scope_version=1))
    case('duplicate_com_watermark',insert('com_delivery_scope_project_version',project_id=50,scope_version=2),1062)
    scope=dict(project_id=50,scope_version=2,previous_scope_version=1,origin_context='PROJ',origin_record_id=600,origin_revision=1,scope_snapshot='[]',difference_snapshot='{}')
    case('immutable_scope_version',insert('com_project_scope_revision',**scope))
    case('scope_version_duplicate',insert('com_project_scope_revision',**(scope|{'origin_record_id':601})),1062)
    case('scope_version_nonmonotonic',insert('com_project_scope_revision',**(scope|{'scope_version':3,'previous_scope_version':3,'origin_record_id':602})),3819)
    request=dict(project_id=50,operation_id='append',apply_state='NOT_APPLIED')
    case('unapplied_scope_request',insert('proj_contract_scope_append_request',**request))
    case('request_operation_deduplicated',insert('proj_contract_scope_append_request',**request),1062)
    case('cannot_apply_without_approval',insert('proj_contract_scope_append_request',**(request|{'operation_id':'bad','apply_state':'APPLIED','applied_scope_version':2,'applied_at':'2026-09-07'})),3819)
    case('applied_request_has_approval',insert('proj_contract_scope_append_request',**(request|{'operation_id':'good','apply_state':'APPLIED','applied_scope_version':2,'applied_at':'2026-09-07','approval_fact_ref':'fixture-approval'})))
    case('final_report_root',insert('acc_acceptance_report',id=201,project_id=50,report_type='FINAL'))
    case('report_root_unique_type',insert('acc_acceptance_report',project_id=50,report_type='FINAL'),1062)
    report=dict(report_id=201,revision_no=1,conclusion_code='FAILED',source_evidence_refs='[]')
    case('failed_report_evidence_is_preserved',insert('acc_acceptance_report_revision',**report))
    case('report_revision_unique',insert('acc_acceptance_report_revision',**report),1062)
    case('report_cross_tenant_root_rejected',insert('acc_acceptance_report_revision',tenant_id=2,**report),1452)
    exit=dict(project_id=50,project_version=5,closure_type='NO_TRACKING',closed_from_stage='S4',source_context='ACC',source_record_id=800)
    case('no_tracking_exit_from_s4',insert('proj_project_exit_record',**exit))
    case('closure_event_source_deduplicated',insert('proj_project_exit_record',**(exit|{'project_version':6})),1062)
    case('no_tracking_wrong_writer_rejected',insert('proj_project_exit_record',**(exit|{'project_version':7,'source_record_id':801,'source_context':'PROJ'})),3819)
    case('unknown_stage_rejected',insert('proj_project_exit_record',**(exit|{'project_version':8,'source_record_id':802,'closed_from_stage':'S7'})),3819)
    case('exception_exit_from_s1',insert('proj_project_exit_record',**(exit|{'project_version':9,'source_record_id':803,'closure_type':'EXCEPTION','source_context':'PROJ','closed_from_stage':'S1'})))
    report_json={
        'schemaVersion':1,'status':'PASS' if all(c['passed'] for c in cases) else 'FAIL',
        'evidenceType':'ISOLATED_MYSQL_SCHEMA_AND_CONSTRAINT_TESTS_NOT_BUSINESS_RUNTIME',
        'mysqlVersion':version.stdout.strip(),'containerImage':inspected['Config']['Image'],
        'imageId':inspected['Image'],'networkMode':'none','schemaCreatedEmpty':True,
        'sourceCommit':os.environ.get('INPUT_SHA') or subprocess.check_output(['git','rev-parse','HEAD'],cwd=args.root,text=True).strip(),
        'actionsRunId':os.environ.get('GITHUB_RUN_ID'),
        'prdGitBlob':model['prdGitBlob'],'contractSha256':digest(args.root/CONTRACT),'ddlSha256':digest(args.root/DDL),
        'tableCount':table_count,'caseCount':len(cases),'passedCount':sum(c['passed'] for c in cases),'cases':cases,
        'limitations':['NOT_AN_UPGRADE_TEST','NOT_AN_APPLICATION_TEST','NOT_INDEPENDENT_APPROVAL','NO_PRODUCTION_ACCESS'],
    }
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report_json,ensure_ascii=False,indent=2)+'\n')
    print(f"SUMMARY {report_json['passedCount']}/{len(cases)} cases; {table_count} tables")
    return 0 if report_json['status']=='PASS' else 1
if __name__=='__main__': raise SystemExit(main())
