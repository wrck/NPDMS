"""Reviewed seed boundary for the 178 empty-database release.

A new seed family requires review here; source row counts cannot authorize it.
"""
import re

SEED_TABLES = frozenset({
    'ACT_GE_BYTEARRAY',
    'ACT_GE_PROPERTY',
    'ACT_ID_PROPERTY',
    'ACT_PROCDEF_INFO',
    'ACT_RE_DEPLOYMENT',
    'ACT_RE_MODEL',
    'ACT_RE_PROCDEF',
    'acc_satisfaction_questionnaire_template',
    'acc_satisfaction_questionnaire_template_revision',
    'bpm_category',
    'bpm_form',
    'bpm_process_definition_info',
    'cut_checklist_binding_condition',
    'cut_checklist_external_mapping',
    'cut_checklist_field_definition',
    'cut_checklist_field_option',
    'cut_configuration_dictionary_value',
    'cut_configuration_dimension',
    'cut_configuration_plan_section',
    'cut_configuration_plan_section_scope',
    'cut_cutover_checklist_binding_rule_revision',
    'cut_cutover_checklist_item_definition_revision',
    'cut_cutover_configuration_revision',
    'imp_eng_doc_template',
    'imp_eng_doc_template_version',
    'infra_config',
    'infra_job',
    'plt_business_view_revision',
    'plt_delivery_type',
    'plt_dynamic_form_template',
    'plt_dynamic_form_template_revision',
    'plt_entity_extension_definition',
    'plt_form_template',
    'proj_delivery_definition_reference',
    'proj_delivery_definition_revision',
    'proj_project_template',
    'proj_project_template_deliverable_definition',
    'proj_project_template_gate_definition',
    'proj_project_template_gate_reference',
    'proj_project_template_milestone_definition',
    'proj_project_template_revision',
    'proj_project_template_stage_definition',
    'proj_project_template_task_definition',
    'proj_stage_suggestion_rule',
    'proj_stage_transition_definition',
    'proj_task_state_machine_revision',
    'proj_task_state_transition',
    'system_company',
    'system_dept',
    'system_dict_data',
    'system_dict_type',
    'system_menu',
    'system_notify_template',
    'system_oauth2_client',
    'system_role',
    'system_role_menu',
    'system_tenant',
    'system_user_role',
    'system_users',
})


def validate_configuration(configuration):
    tables = configuration['tables']
    unknown = set(tables) - SEED_TABLES
    if unknown:
        raise ValueError('Unreviewed seed tables: ' + ', '.join(sorted(unknown)))
    menus = tables.get('system_menu', [])
    for row in menus:
        if row['id'] in (5, 1070) or (row.get('component') or '').startswith(('infra/demo/', 'infra/testDemo', 'bpm/oa/')) or (row.get('permission') or '').startswith(('infra:demo', 'bpm:oa-leave:')):
            raise ValueError('Upstream example menu in production seeds')
    if any(r.get('template_code') == 'SAT_MANUAL_EXAMPLE' for r in tables.get('acc_satisfaction_questionnaire_template', [])):
        raise ValueError('Explicitly excluded sample questionnaire')
    if any(r.get(key) == 'bpm_oa_leave_type'
           for table, key in [('system_dict_type', 'type'), ('system_dict_data', 'dict_type')]
           for r in tables.get(table, [])):
        raise ValueError('Upstream OA example dictionary in production seeds')
    users = tables.get('system_users', [])
    if users and (len(users) != 1 or any(users[0].get(k) != v for k, v in
            {'id': 1, 'tenant_id': 1, 'username': 'admin', 'status': 1, 'password': '!BOOTSTRAP_REQUIRED!'}.items())):
        raise ValueError('Only the disabled bootstrap administrator is allowed')
    for row in tables.get('system_dept', []):
        if any(row.get(k) not in (None, '') for k in ('leader_user_id', 'phone', 'email')):
            raise ValueError('Organization seeds cannot include people/contact bindings')
    def inspect(value, key=''):
        if re.fullmatch(r'(?i)(password|passwd|private_?key|access_token|refresh_token|client_secret|secret|token|api_key|api_secret)', key):
            if value not in (None, '', [], {}):
                raise ValueError('Credential-like value in seed field: ' + key)
        if isinstance(value, dict):
            for k, v in value.items():
                inspect(v, k)
        elif isinstance(value, list):
            for v in value:
                inspect(v)
        elif isinstance(value, str):
            if '-----BEGIN PRIVATE KEY-----' in value:
                raise ValueError('Private key in seed')
            if value.strip().startswith(('{', '[')):
                import json
                try:
                    parsed = json.loads(value)
                except ValueError:
                    return
                inspect(parsed)
    for name, rows in tables.items():
        for row in rows:
            inspect({k: v for k, v in row.items() if not (name == 'system_users' and k == 'password')})
