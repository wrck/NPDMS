"""Plan whole-table project identities for editable migration-task configuration.

Inputs contain only IDs, types, codes and established migration bindings. This
module never reads credentials or writes business data. Callers obtain source
rows through the migration task's configured query and target codes through PROJ.
"""
from __future__ import annotations

import re
from copy import deepcopy


def plan_project_identities(source_rows, occupied_codes, established_codes=None):
    """Select maximum ID per exact type/code; allocate cross-type collisions once."""
    established_codes = established_codes or {}
    canonical = {}
    originals = {}
    for row in source_rows:
        source_id = int(row['projectId'])
        identity = (row['projectType'], row['projectCode'])
        if source_id <= 0 or any(not isinstance(value, str) or not value for value in identity):
            raise ValueError('PROJECT_SOURCE_IDENTITY_INVALID')
        if source_id in originals:
            raise ValueError('PROJECT_SOURCE_ID_DUPLICATE')
        originals[source_id] = identity
        canonical[identity] = max(source_id, canonical.get(identity, 0))
    if not set(established_codes).issubset(set(canonical.values())):
        raise ValueError('PROJECT_BINDING_IS_NOT_CANONICAL')
    reserved = set(occupied_codes) | {identity[1] for identity in canonical}
    reserved.update(established_codes.values())
    assigned = {}
    seen_codes = set()
    used_targets = set()
    changes = []
    allocations = {}
    collision_members = {}
    for (project_type,code),source_id in canonical.items():
        collision_members.setdefault(code,[]).append(dict(projectId=source_id,projectType=project_type))
    for identity, source_id in sorted(canonical.items(), key=lambda item: item[1]):
        project_type, original_code = identity
        collision = original_code in seen_codes
        seen_codes.add(original_code)
        target_code = established_codes.get(source_id, original_code)
        suffix = re.fullmatch(r'(.+-)([0-9]+)', original_code)
        if source_id in established_codes:
            if not collision and target_code != original_code:
                raise ValueError('PROJECT_NON_COLLISION_BINDING_CODE_CHANGED')
            if collision and (not suffix or not re.fullmatch(re.escape(suffix[1]) + r'[0-9]+', target_code)
                              or int(target_code[len(suffix[1]):]) <= int(suffix[2])):
                raise ValueError('PROJECT_COLLISION_BINDING_CODE_INVALID')
        elif collision:
            if not suffix:
                raise ValueError('PROJECT_COLLISION_NUMERIC_SUFFIX_MISSING')
            number = int(suffix[2]) + 1
            while suffix[1] + str(number) in reserved:
                number += 1
            target_code = suffix[1] + str(number)
        if target_code in used_targets:
            raise ValueError('PROJECT_BINDING_TARGET_CODE_DUPLICATE')
        if len(target_code) > 64:
            raise ValueError('PROJECT_TARGET_CODE_TOO_LONG')
        assigned[source_id] = target_code
        used_targets.add(target_code)
        reserved.add(target_code)
        if target_code != original_code:
            changes.append(dict(projectId=source_id, projectType=project_type,
                                originalCode=original_code, targetCode=target_code))
            allocations[str(source_id)] = dict(originalProjectCode=original_code,
                                               orderedProjects=sorted(collision_members[original_code],key=lambda row:row['projectId']),
                                               projectCode=target_code)
    return dict(canonicalByOriginalId={source_id: canonical[identity] for source_id, identity in originals.items()},
                projectCodes=assigned, codeChanges=changes, migrationCodeAllocations=allocations)


def apply_code_plan_to_task(definition, plan):
    """Prepare the existing configurable master task; never execute or mutate it."""
    result = deepcopy(definition)
    if result.get('adapter') != 'DPPMS_PROJECT_MASTER' or result.get('mode') != 'ONCE':
        raise ValueError('PROJECT_MASTER_TASK_REQUIRED')
    sources = [source for source in result.get('sources', []) if source.get('object') == 'pm_project_header']
    if len(sources) != 1:
        raise ValueError('PROJECT_MASTER_SOURCE_REQUIRED')
    source = sources[0]
    mappings = [mapping for mapping in source.get('mappings', []) if mapping.get('target') == 'migrationCodeAllocation']
    if len(mappings) != 1 or mappings[0].get('conversion') != 'LOOKUP' or mappings[0].get('source') != 'projectId':
        raise ValueError('PROJECT_CODE_PLAN_MAPPING_REQUIRED')
    expected = plan['migrationCodeAllocations']
    existing = mappings[0].get('values') or {}
    if existing and existing != expected:
        raise ValueError('PROJECT_APPROVED_CODE_PLAN_CHANGED')
    mappings[0]['values'] = deepcopy(expected)
    return result
