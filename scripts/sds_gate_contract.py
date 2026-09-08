#!/usr/bin/env python3
"""Substantive design/schema checks plus metadata for remaining risk gates.

Phase 1/2 no longer impose admission or whole-PRD provenance requirements.
Historical provenance can be audited explicitly; actual schema/DDL bindings,
negative cases and remaining risk-gate approval checks are preserved.
"""
from __future__ import annotations
import hashlib
import json
import re
from pathlib import Path

PRD = 'docs/baseline/prd-v1.8.md'
MODEL = 'docs/traceability/sds-revision-016-physical-contract.json'
DDL = 'specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql'
MYSQL_EVIDENCE = 'docs/engineering/gates/phase-2/revision-017-standard-fields-mysql-schema.json'
OBSOLETE_OBJECTS = {'MultiPhaseProjectGroup', 'MultiPhaseProjectMember', 'CrossPhaseContentReference'}
NEW_OBJECT_OWNERS = {
    'ProjectTemplateVersion':'PROJ','DeliveryConfigurationRevision':'PROJ',
    'StageTransitionDefinition':'PROJ','ProjectStageTransition':'PROJ',
    'StageWorkBinding':'PROJ','ContractScopeAppendRequest':'PROJ',
    'ProjectScopeVersion':'COM','AcceptanceReportRevision':'ACC',
    'BusinessViewRegistration':'PLT','ProjectExitRecord':'PROJ',
}
CRITICAL_COLUMNS = {
    'proj_stage_transition_definition': {'template_revision_id','from_stage_code','to_stage_code','priority','is_default','revision_no'},
    'proj_project_stage_transition': {'project_id','graph_version','from_stage_id','to_stage_id','condition_snapshot'},
    'proj_project_stage_execution_contract': {'stage_id','binding_version','binding_type','binding_snapshot','permission_policy_revision_id','completion_rule_revision_id','current_marker'},
    'com_delivery_scope_project_version': {'project_id','scope_version'},
    'com_project_scope_revision': {'project_id','scope_version','previous_scope_version','scope_snapshot','scope_digest','difference_snapshot'},
    'proj_contract_scope_append_request': {'project_id','expected_scope_version','expected_project_version','operation_id','request_digest','approval_fact_ref','applied_scope_version'},
    'acc_acceptance_report_revision': {'report_id','revision_no','project_scope_version','scope_revision_id','conclusion_code','accepted_at','acceptor_reference','file_artifact_id','file_version','initial_report_revision_id'},
    'proj_project_exit_record': {'project_id','project_version','closure_type','closed_from_stage','source_context','source_record_id'},
}

def read(path: Path) -> str:
    return path.read_text(encoding='utf-8-sig')

def revision(root: Path) -> str:
    path = root / PRD
    if not path.is_file():
        return '007'  # Legacy fixture/input compatibility, not a current approval default.
    values = re.findall(r'(?m)^\| V1\.8修订(\d{3}) \|', read(path))
    return max(values) if values else '007'

def current(root: Path) -> bool:
    return revision(root) >= '016'

def blob(path: Path) -> str:
    # Git stores these text inputs as LF; keep the user's checkout line endings intact.
    raw = path.read_bytes().replace(b'\r\n', b'\n')
    return hashlib.sha1(f'blob {len(raw)}\0'.encode() + raw).hexdigest()

def metadata(text: str, label: str) -> list[str]:
    return re.findall(r'^> '+re.escape(label)+r'：`([^`]+)`',text,re.M)

def validate_gate(root: Path, phase: int, *, technical: bool) -> list[str]:
    # Phase 1/2 are design categories, not admission gates. No approval is implied.
    if phase in (1, 2):
        return []
    path = root / f'docs/engineering/gates/phase-{phase}/gate-status.md'
    if not path.is_file():
        return [f'Phase {phase}: missing gate-status']
    text=read(path)
    errors=[]
    expected={'PRD Blob':blob(root/PRD),'适用修订':f'PRD_V1.8_REVISION_{revision(root)}'}
    for key,value in expected.items():
        if metadata(text,key) != [value]:
            errors.append(f'Phase {phase}: missing, duplicate or stale {key}')
    states=metadata(text,'审查状态')
    if len(states)!=1 or states[0] not in {'REVALIDATION_REQUIRED','IN_REVIEW','APPROVED'}:
        errors.append(f'Phase {phase}: invalid/duplicate review state')
    approved=states==['APPROVED']
    if metadata(text,'技术结论') == ['TECHNICAL_GO']:
        errors.extend(validate_mysql_evidence(root))
    if approved or not technical:
        for key,value in [('审查状态','APPROVED'),('机器门禁','PASS'),('需求方批准','GO'),('独立复审','GO')]:
            if metadata(text,key)!=[value]:
                errors.append(f'Phase {phase}: APPROVAL_REQUIRED {key}={value}')
        refs=metadata(text,'当前复审证据')
        if len(refs)!=1:
            errors.append(f'Phase {phase}: current independent review evidence is required')
        else:
            ref=Path(refs[0])
            if ref.is_absolute() or '..' in ref.parts or not (root/ref).is_file():
                errors.append(f'Phase {phase}: invalid review evidence path')
            else:
                review=read(root/ref)
                # A text GO alone cannot clear the gate. Require scoped revision,
                # immutable PRD identity and explicit decision/reviewer provenance.
                for marker in [expected['PRD Blob'],expected['适用修订'],'Reviewer:','Decision: GO','Review scope:']:
                    if marker not in review:
                        errors.append(f'Phase {phase}: review evidence missing {marker}')
                if 'SELF_REVIEW_ONLY' in review or 'PENDING' in review:
                    errors.append(f'Phase {phase}: self-review/pending evidence cannot approve')
    elif any(metadata(text,key)==['GO'] for key in ('需求方批准','独立复审')):
        errors.append(f'Phase {phase}: pending gate must not contain approved-only GO metadata')
    return errors

def validate_design(root: Path, *, check_prd_identity: bool = False) -> list[str]:
    errors=[]
    try:
        carrier=json.loads(read(root/MODEL))
        directory=json.loads(read(root/'docs/traceability/domain-object-table-map.json'))['objects']
    except (OSError,ValueError,KeyError) as exc:
        return [f'current carrier contract unavailable: {exc}']
    if check_prd_identity and carrier.get('prdGitBlob')!=blob(root/PRD):
        errors.append('carrier contract PRD identity is stale')
    if carrier.get('scope')!='PROSPECTIVE_FEATURE_FORWARD_SCHEMA_NOT_RUNTIME_MIGRATION':
        errors.append('prospective design must not claim deployed migration')
    if OBSOLETE_OBJECTS & set(directory):
        errors.append('PM-06 obsolete multi-phase objects in active map')
    for name,owner in NEW_OBJECT_OWNERS.items():
        if directory.get(name,{}).get('owner')!=owner:
            errors.append(f'{name}: current Owner must be {owner}')
    tables=carrier.get('tables',{})
    from generate_sds_revision_016_schema import render
    try:
        if read(root/DDL)!=render(carrier):
            errors.append('prospective schema differs from canonical field/constraint contract')
    except (OSError,KeyError,ValueError) as exc:
        errors.append(f'invalid prospective schema: {exc}')
    for table,columns in CRITICAL_COLUMNS.items():
        shape=tables.get(table,{})
        actual={pair[0] for pair in shape.get('columns',[])}
        if not columns <= actual:
            errors.append(f'{table}: missing critical columns {sorted(columns-actual)}')
    for table,shape in tables.items():
        if not re.fullmatch(r'[a-z][a-z0-9_]*',table): errors.append(f'invalid table identifier: {table}')
        required_owner=table.split('_',1)[0].upper()
        if shape.get('owner')!=required_owner: errors.append(f'{table}: wrong physical Owner')
        columns=shape.get('columns',[])
        if ['tenant_id','BIGINT NOT NULL'] not in columns:
            errors.append(f'{table}: tenant key missing or nullable')
        types = dict(columns)
        for field, prefix in {'version':'INT NOT NULL', 'creator':'VARCHAR(64) NOT NULL',
                              'updater':'VARCHAR(64) NOT NULL', 'create_time':'DATETIME(3) NOT NULL',
                              'update_time':'DATETIME(3) NOT NULL', 'deleted':'BIT(1) NOT NULL'}.items():
            if not types.get(field, '').startswith(prefix):
                errors.append(f'{table}.{field}: incompatible database common field')
        constraints=shape.get('constraints',[])
        if not any(x.startswith('PRIMARY KEY') for x in constraints):errors.append(f'{table}: no primary key')
        for key in constraints:
            if key.startswith('UNIQUE KEY') and '`tenant_id`' not in key:
                errors.append(f'{table}: tenant-less business uniqueness')
            if 'FOREIGN KEY' in key:
                m=re.search(r'REFERENCES `([^`]+)`',key)
                if not m or m.group(1).split('_',1)[0]!=table.split('_',1)[0]:
                    errors.append(f'{table}: cross-Context physical foreign key')
    required_constraints={
       'proj_stage_transition_definition':['uk_std_default','ck_std_self'],
       'proj_project_stage_transition':['uk_pst_default','ck_pst_versions'],
       'proj_project_stage_execution_contract':['uk_psec_current','ck_psec_type'],
       'com_project_scope_revision':['uk_cpsr_version','ck_cpsr_previous'],
       'acc_acceptance_report':['uk_aar_type'],
       'proj_project_exit_record':['uk_per_source','ck_per_type'],
    }
    required_predicates = {
        'proj_stage_transition_definition': ['UNIQUE KEY `uk_std_default` (`tenant_id`, `template_revision_id`, `from_stage_code`, `default_marker`)', 'CHECK (from_stage_code <> to_stage_code)'],
        'proj_project_stage_transition': ['UNIQUE KEY `uk_pst_default` (`tenant_id`, `project_id`, `graph_version`, `from_stage_id`, `default_marker`)', 'CHECK (transition_revision > 0 AND graph_version > 0)'],
        'proj_project_stage_execution_contract': ['UNIQUE KEY `uk_psec_current` (`tenant_id`, `stage_id`, `current_marker`)', "binding_type IN ('STAGE_NATIVE','BUSINESS_OBJECT','BUSINESS_COMPONENT','DYNAMIC_FORM','APPROVAL','COMPOSITE')"],
        'com_project_scope_revision': ['CHECK (previous_scope_version IS NULL OR scope_version > previous_scope_version)'],
        'proj_contract_scope_append_request': ["apply_state='APPLIED' AND applied_scope_version IS NOT NULL AND applied_scope_version > expected_scope_version AND applied_at IS NOT NULL AND approval_fact_ref IS NOT NULL"],
        'acc_acceptance_report': ['UNIQUE KEY `uk_aar_type` (`tenant_id`, `project_id`, `report_type`)'],
        'proj_project_exit_record': ["closure_type IN ('NORMAL','NO_TRACKING') AND source_context='ACC'", "closure_type='EXCEPTION' AND source_context='PROJ'", "closed_from_stage IN ('S0','S1','S2','S3','S4','S5','S6')"],
    }
    normalized = lambda value: re.sub(r"\s+", "", value)
    for table, predicates in required_predicates.items():
        content = normalized('\n'.join(tables.get(table, {}).get('constraints', [])))
        for predicate in predicates:
            if normalized(predicate) not in content:
                errors.append(f'{table}: weakened invariant predicate: {predicate}')
    for table, column, expression in [
        ('proj_stage_transition_definition','default_marker','CASE WHEN is_default=1 THEN 1 ELSE NULL END'),
        ('proj_project_stage_transition','default_marker','CASE WHEN is_default=1 THEN 1 ELSE NULL END'),
        ('proj_project_stage_execution_contract','current_marker','CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END'),
    ]:
        definition = dict(tables.get(table, {}).get('columns', [])).get(column, '')
        if normalized(expression).lower() not in normalized(definition).lower() or 'GENERATED ALWAYS' not in definition:
            errors.append(f'{table}.{column}: derived uniqueness marker cannot be client writable or weakened')
    for table,names in required_constraints.items():
        content='\n'.join(tables.get(table,{}).get('constraints',[]))
        for name in names:
            if f'`{name}`' not in content: errors.append(f'{table}: missing invariant {name}')
    phase2=read(root/'docs/traceability/phase2-contract-map.md')
    for value in re.findall(r'^- 数据(?:对象|表)：(.+)$',phase2,re.M):
        for item in value.split('、'):
            if item.startswith('FEATURE_FORWARD_MIGRATION('):continue
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*',item):errors.append(f'noncanonical object/table identifier: {item}')
    forbidden={
        'docs/design/09-database-design.md':['正常闭环后保持S6','| PM-06 | `proj_multi_phase'],
        'docs/design/10-api-design.md':['POST /pms/v1/project-phase-groups'],
        'docs/design/11-event-design.md':['| `ProjectPhaseGroupChanged`','再执行本地关闭命令并发布'],
        'docs/traceability/phase2-contract-map.md':['多期群组无环','唯一期次','CrossPhaseContentReference'],
    }
    for path,values in forbidden.items():
        text=read(root/path)
        for value in values:
            if value in text: errors.append(f'{path}: obsolete current contract {value}')
    return errors


def validate_mysql_evidence(root: Path) -> list[str]:
    """Check input binding of preserved CI evidence, not reviewer approval."""
    try:
        evidence=json.loads(read(root/MYSQL_EVIDENCE))
    except (OSError, ValueError) as exc:
        return [f'MySQL execution evidence unavailable: {exc}']
    errors=[]
    for key, path in [('contractSha256', MODEL), ('ddlSha256', DDL)]:
        actual=hashlib.sha256((root/path).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
        if evidence.get(key)!=actual: errors.append(f'MySQL execution evidence stale: {key}')
    cases=evidence.get('cases',[])
    names={case.get('case') for case in cases}
    essential={'cross_tenant_definition_reference','only_one_current_stage_binding',
        'scope_version_nonmonotonic','cannot_apply_without_approval',
        'report_cross_tenant_root_rejected','no_tracking_wrong_writer_rejected',
        'failed_report_evidence_is_preserved','no_tracking_exit_from_s4'}
    if not essential <= names: errors.append('MySQL evidence missing business-constraint negative cases')
    if evidence.get('status')!='PASS' or not cases or any(case.get('passed') is not True for case in cases):
        errors.append('MySQL execution evidence contains failed/unexecuted cases')
    if len(cases)!=evidence.get('caseCount') or len(cases)!=evidence.get('passedCount') or len(names)!=len(cases):
        errors.append('MySQL evidence counts/unique case identities inconsistent')
    if not str(evidence.get('mysqlVersion','')).startswith('8.4.') or evidence.get('networkMode')!='none' or evidence.get('schemaCreatedEmpty') is not True:
        errors.append('MySQL evidence not from isolated empty 8.4 schema')
    if evidence.get('tableCount')!=len(json.loads(read(root/MODEL))['tables']):
        errors.append('MySQL executed table count differs from current contract')
    local_run = evidence.get('executionEnvironment') == 'LOCAL_WORKTREE' and isinstance(evidence.get('sourceTreeDirty'), bool)
    if not evidence.get('sourceCommit') or not (evidence.get('actionsRunId') or local_run):
        errors.append('MySQL evidence lacks execution provenance')
    return errors
