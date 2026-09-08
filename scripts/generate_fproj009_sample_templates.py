#!/usr/bin/env python3
"""PM-03 / PM-11: deterministic, configuration-only PRD 3.2 scenario samples.

Authority: PRD 3.2; tasks/features/F-PROJ-009 '2026-09-09数据补全与样例任务';
master DU DU-20260908-TEMPLATE-BUSINESS-VIEW (01300a6e). V208 is a candidate number.
No database connection. Run with --check, or without arguments to regenerate SQL.
These native bindings demonstrate structure, not implementation of domain work.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "sql/migrations/V208__fproj009_scenario_template_samples.sql"
SOURCE = "fproj009-samples-20260909"
PREFIX = "FPROJ009_SAMPLE_"
ID_MIN, ID_MAX = 993009000000, 993009999999
TENANT = 1
SEED_TIME = "2026-09-09 00:00:00"
NOTICE = (
    "配置结构示例，生产需绑定Owner；TASK_NATIVE/STAGE_NATIVE仅示范原生配置，"
    "不代表领域业务已实现。DRAFT未开运行、不进入匹配候选；仅供浏览、复制。"
    "初验/终验、审批、归档与闭环均为待办理配置，不创建实体或完成事实。"
)

TEMPLATE = "proj_project_template"
REVISION = "proj_project_template_revision"
DEFINITION = "proj_delivery_definition_revision"
REFERENCE = "proj_delivery_definition_reference"
STAGE = "proj_project_template_stage_definition"
TASK = "proj_project_template_task_definition"
MILESTONE = "proj_project_template_milestone_definition"
DELIVERABLE = "proj_project_template_deliverable_definition"
GATE = "proj_project_template_gate_definition"
GATE_REF = "proj_project_template_gate_reference"
TRANSITION = "proj_stage_transition_definition"

# Exact existing unique keys from V52/V206; child keys intentionally have no tenant
# column in those migrations. The id branch must detect cross-tenant collisions.
KEYS = {
    DEFINITION: ("tenant_id", "definition_kind", "definition_code", "revision_no"),
    REFERENCE: ("tenant_id", "owner_revision_id", "reference_key"),
    TEMPLATE: ("tenant_id", "code"),
    REVISION: ("tenant_id", "template_id", "revision_no"),
    STAGE: ("template_revision_id", "stage_code"),
    TASK: ("template_revision_id", "task_code"),
    MILESTONE: ("template_revision_id", "milestone_code"),
    DELIVERABLE: ("template_revision_id", "deliverable_code"),
    GATE: ("template_revision_id", "gate_code"),
    GATE_REF: ("template_revision_id", "gate_code", "ref_type", "ref_code"),
    TRANSITION: ("tenant_id", "template_revision_id", "transition_code"),
}

# NULL is a partial restriction, never a fabricated CRM mapping. Presales cannot
# be identified by the four dimensions: its all-NULL draft needs explicit choice.
SCENARIOS = (
    ("DS_ENG", "直签-工程类", "DIRECT_SIGN", "ENGINEERING", None, 10,
     "含启动会；初验、初验后满意度及终验配置；实施方式未限定（部分限定示例）。"),
    ("DS_GEN", "直签-普通类", "DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", 20,
     "无工程启动会；保留初验、初验后满意度及终验配置；三项受控维度精确限定。"),
    ("CH_ENG", "非直签-工程类", "CHANNEL_SIGN", "ENGINEERING", None, 30,
     "含启动会和现场验货单；不设初验，仅终验及验收阶段满意度；实施方式未限定。"),
    ("CH_DIR", "非直签-普通类（原厂直服）", "CHANNEL_SIGN", "GENERAL", "DIRECT_SERVICE", 40,
     "无启动会和初验；现场验货单、终验及验收阶段满意度配置。"),
    ("CH_SUP", "非直签-普通类（原厂督导）", "CHANNEL_SIGN", "GENERAL", "SUPERVISION", 50,
     "无启动会和初验；督导渠道实施，核心交付件为现场服务单，配置终验。"),
    ("PRE", "售前测试场景（显式选择）", None, None, None, 90,
     "显式选择的场景草稿，四维不能单独识别售前；仅S0→S4，S4仅EXE-03/04。"
     "可从S4按CLO契约申请闭环，不补S5/S6；转销关联待领域Owner办理。"),
)

STAGE_NAMES = {
    "S0": "项目立项与指派", "S1": "工前准备", "S2": "施工计划制定",
    "S3": "实施方案编审", "S4": "实施部署", "S5": "验收交维", "S6": "项目闭环",
}
STAGE_DOCUMENTS = {
    "S0": "项目指派与团队配置记录", "S1": "工前准备基线资料包", "S2": "施工计划表",
    "S3": "实施方案文档", "S4": "实施部署资料包", "S5": "验收交维交付件包",
    "S6": "闭环校验与申请资料包",
}
STAGE_CRITERIA = {
    "S0": "待核对服务经理、项目经理指派及项目团队事实",
    "S1": "待核对有效工期、工勘、需求分析与适用准备项就绪事实",
    "S2": "待核对施工计划及审批事实，不以通知送达替代审批",
    "S3": "待核对实施方案审核事实；重大复审按真实CRM映射及Owner规则办理",
    "S4": "待核对适用到货、安装、配置、联调及割接结果或冻结不适用依据",
    "S5": "待核对显式配置的培训、满意度、验收及归档事实，S5编码不产生验收义务",
    "S6": "待按CLO-01/02核对闭环条件、在途对象处置及审批，不表示项目已闭环",
}


def stage_tasks(scenario: str, stage: str) -> list[tuple[str, str, str, str, str | None]]:
    """token, business obligation, proposed document, requirement, parent token."""
    engineering = scenario in {"DS_ENG", "CH_ENG"}
    direct = scenario.startswith("DS_")
    if stage == "S0":
        return [("ASSIGN", "核对服务经理与项目经理指派", "项目指派核对记录", "PM-01", None),
                ("TEAM", "整理项目团队与四维属性", "团队与项目属性记录", "PM-01/PM-03", None)]
    if stage == "S1":
        parent = "PREP" if scenario == "DS_ENG" else None
        items = []
        if parent:
            items.append((parent, "组织工前准备资料（WBS父任务）", "工前准备资料索引", "PM-03/PM-11", None))
        items.extend([
            ("PRE01", "录入与核对有效工期基线", "有效工期基线记录", "PRE-01", parent),
            ("PRE02", "采集工勘分工与实施就绪信息", "工勘与实施就绪快照", "PRE-02", parent),
            ("PRE04", "填写与确认需求分析", "需求分析文档", "PRE-04", parent),
        ])
        if engineering:
            items.extend([
                ("KICKOFF", "组织工程启动会", "工程启动会纪要", "PM-03/PRD3.2", parent),
                ("PRE05", "整理工程交底书", "工程交底书", "PRE-05", parent),
            ])
        return items
    if stage == "S2":
        return [("PLN01", "编制施工计划", "施工计划编制记录", "PLN-01", None),
                ("PLN04", "提交施工计划并核对审批", "施工计划审批依据", "PLN-04", None)]
    if stage == "S3":
        return [("PLAN", "编写实施方案", "实施方案编制稿", "PM-03/PRD3.1.4", None),
                ("REVIEW", "核对方案审核及适用重大复审", "方案审核与适用复审依据", "PM-03/PRD3.1.4", None)]
    if stage == "S4":
        items = [
            ("EXE03", "采集解析配置Log", "配置Log与解析记录", "EXE-03", None),
            ("EXE04", "收集业务联调配置", "业务联调配置记录", "EXE-04", None),
        ]
        if scenario == "PRE":
            return items
        if scenario == "CH_SUP":
            items.insert(0, ("SERVICE", "记录原厂督导现场服务与渠道实施核对", "现场服务单", "PM-03/PRD3.2", None))
        else:
            receipt = "现场验货单" if scenario.startswith("CH_") else "到货签收单"
            items[0:0] = [
                ("EXE01", "办理到货签收与现场验货", receipt, "EXE-01", None),
                ("EXE02", "记录硬件安装位置与照片", "硬件安装记录", "EXE-02", None),
            ]
        items.append(("EXE06", "核对适用割接结果或冻结不适用依据", "割接范围与适用性核对记录", "EXE-06", None))
        return items
    if stage == "S5":
        items = [("ACC01", "整理现场培训与签字记录", "培训记录", "ACC-01", None)]
        if direct:
            items.append(("INITIAL", "办理初验（待绑定ACC独立实体）", "初验报告", "ACC-03", None))
        items.extend([
            ("ACC02", "收集初验后满意度并核对结果" if direct else "收集验收阶段满意度并核对结果",
             "满意度表与结果依据", "ACC-02", None),
            ("FINAL", "办理终验（待绑定ACC独立实体）", "终验报告", "ACC-03", None),
            ("ACC04", "汇总并归档交付件", "交付件归档清单", "ACC-04", None),
        ])
        return items
    if stage == "S6":
        return [("CLO01", "核对闭环条件与阻断对象", "闭环条件校验依据", "CLO-01", None),
                ("CLO02", "申请项目闭环并核对审批", "闭环申请与审批依据", "CLO-02", None),
                ("ACC06", "整理服务交接快照", "服务交接资料", "ACC-06", None)]
    raise ValueError(stage)


def build_rows() -> dict[str, list[dict]]:
    """Only the eleven existing configuration tables; no project/runtime rows."""
    rows: dict[str, list[dict]] = {table: [] for table in KEYS}
    next_id = ID_MIN

    def add(table: str, **values) -> dict:
        nonlocal next_id
        row = {"id": next_id, **values, "tenant_id": TENANT, "creator": SOURCE,
               "create_time": SEED_TIME, "updater": SOURCE, "update_time": SEED_TIME,
               "deleted": False}
        if table not in {DEFINITION, REFERENCE, TRANSITION}:
            row["deleted_time"] = None
        assert next_id <= ID_MAX
        next_id += 1
        rows[table].append(row)
        return row

    def definition(kind: str, code: str, payload: dict, slots: dict | None = None) -> dict:
        row = add(DEFINITION, definition_kind=kind, definition_code=PREFIX + code,
                  revision_no=1, revision_state="PUBLISHED", schema_version=1,
                  payload=payload, published_at=SEED_TIME, disabled_at=None, version=0)
        for key, target in (slots or {}).items():
            add(REFERENCE, owner_revision_id=row["id"], reference_key=key,
                target_revision_id=target, version=0)
        return row

    bindings, completions = {}, {}
    for kind in ("STAGE", "TASK"):
        bindings[kind] = definition("WORK_BINDING", kind + "_NATIVE_BINDING", {
            "bindingType": kind + "_NATIVE", "instanceResolutionStrategy": "CREATE_ON_ENTER",
            "contextMapping": {},
        })
        completions[kind] = definition("COMPLETION_RULE", kind + "_NATIVE_RULE", {
            "predicate": kind + "_NATIVE_STATUS", "parameters": {"requiredStatus": "DONE"},
        })
    permission = definition("PERMISSION_POLICY", "VIEW_POLICY", {"requiredActions": ["VIEW"]})

    def node(kind: str, code: str, payload: dict) -> dict:
        slots = {"workBinding": bindings[kind]["id"], "permissionPolicy": permission["id"],
                 "completionRule": completions[kind]["id"]}
        item = definition(kind, code, {**payload, **{key: key for key in slots}}, slots)
        return {"definition_revision_id": item["id"],
                "work_binding_revision_id": bindings[kind]["id"],
                "permission_policy_revision_id": permission["id"],
                "completion_rule_revision_id": completions[kind]["id"]}

    for scenario, name, signing, category, implementation, priority, difference in SCENARIOS:
        code = PREFIX + scenario
        description = (NOTICE + difference + f"matchPriority={priority}，小值优先仅为草稿属性说明；"
                       "与售前空维草稿潜在重叠不构成匹配候选或预演通过。CRM重大级别不预置。")
        template = add(TEMPLATE, code=code, name=name + "【结构草稿】", status="DRAFT",
                       match_priority=priority, description=description, system_reserved=False, version=0)
        revision = add(REVISION, template_id=template["id"], revision_no=0, status="DRAFT",
                       signing_method=signing, project_category=category, implementation_method=implementation,
                       major_project_level=None, process_definition_key=None, process_definition_version=None,
                       validation_summary=None, published_by=None, published_time=None, definition_snapshot=None)
        rid = revision["id"]
        stages = ["S0", "S4"] if scenario == "PRE" else list(STAGE_NAMES)
        previous = None
        for index, stage in enumerate(stages):
            local = scenario + "_" + stage
            stage_name = "售前测试配置与联调" if scenario == "PRE" and stage == "S4" else STAGE_NAMES[stage]
            criteria = ("待核对EXE-03配置Log与EXE-04业务联调配置；可按CLO契约从S4申请闭环，不补其他阶段"
                        if scenario == "PRE" and stage == "S4" else STAGE_CRITERIA[stage])
            add(STAGE, template_revision_id=rid, stage_code=stage, name=stage_name,
                sort_order=index * 10, entry_criteria="待按草稿图核对前阶段结果" if previous else "项目立项与指派配置入口",
                exit_criteria=criteria, start_node=index == 0, terminal_node=index == len(stages) - 1,
                **node("STAGE", local, {"name": stage_name, "stageCode": stage,
                                      "start": index == 0, "terminal": index == len(stages) - 1}))
            tasks = []
            for order, (token, task_name, document, requirement, parent) in enumerate(stage_tasks(scenario, stage)):
                task_code = PREFIX + local + "_" + token
                add(TASK, template_revision_id=rid, task_code=task_code, name=task_name,
                    parent_task_code=PREFIX + local + "_" + parent if parent else None,
                    stage_code=stage, priority=2, sort_order=order * 10, estimated_hours=None,
                    satisfaction_timing=None, description=NOTICE + "Requirement: " + requirement,
                    stage_definition_key=stage, task_definition_key=task_code,
                    parent_task_definition_key=PREFIX + local + "_" + parent if parent else None,
                    work_binding_type_code="TASK_NATIVE", target_context_code=None, target_object_type=None,
                    target_object_key=None, component_key=None, dynamic_form_revision_id=None,
                    approval_definition_key=None, binding_config=bindings["TASK"]["payload"],
                    permission_policy_ref=permission["definition_code"], completion_rule_type_code="TASK_NATIVE_STATUS",
                    completion_rule_config={"requiredStatus": "DONE"}, gate_ref=None, definition_version=1,
                    **node("TASK", local + "_" + token, {"name": task_name}))
                tasks.append((task_code, document))

            milestone_code = PREFIX + local + "_M"
            milestone = definition("MILESTONE", local + "_M", {"name": stage_name + "资料核对节点", "criteria": criteria})
            add(MILESTONE, template_revision_id=rid, milestone_code=milestone_code,
                name=milestone["payload"]["name"], stage_code=stage, timing="配置待办理，非达成事实",
                criteria=criteria, definition_revision_id=milestone["id"])

            # One explicit stage-scope requirement and a task-scope requirement per
            # task. Confirmation targets are actual tasks in this exact template.
            stage_document = STAGE_DOCUMENTS[stage]
            if scenario == "PRE" and stage == "S4":
                stage_document = "售前测试配置与联调资料包"
            if scenario == "CH_SUP" and stage == "S4":
                stage_document = "现场服务单与督导资料包"
            documents = [(None, stage_document, tasks[-1][0])] + [(task, title, task) for task, title in tasks]
            deliverable_codes = []
            for number, (task_code, title, confirmation_task) in enumerate(documents):
                document_local = local + "_D" + str(number)
                document_code = PREFIX + document_local
                item = definition("DELIVERABLE", document_local, {
                    "scope": "TASK" if task_code else "STAGE", "deliverableType": "DOCUMENT",
                    "required": True, "minimumQuantity": 1, "allowedSources": ["UPLOAD"], "outputType": "FILE",
                    "confirmationRule": {"predicate": "TASK", "parameters": {"refCode": confirmation_task}},
                })
                add(DELIVERABLE, template_revision_id=rid, deliverable_code=document_code, name=title,
                    stage_code=stage, task_code=task_code, required=True, definition_revision_id=item["id"])
                deliverable_codes.append(document_code)

            gates = [("EXIT", ([{"refType": "TASK", "refCode": task} for task, _ in tasks]
                               + [{"refType": "MILESTONE", "refCode": milestone_code}]
                               + [{"refType": "DELIVERABLE", "refCode": item} for item in deliverable_codes]))]
            if previous:
                gates.insert(0, ("ENTRY", [{"refType": "STATE", "refCode": previous + "_COMPLETED"}]))
                add(TRANSITION, template_revision_id=rid, transition_code=PREFIX + scenario + "_" + previous + "_TO_" + stage,
                    from_stage_code=previous, to_stage_code=stage, condition_rule_revision_id=None,
                    priority=10, is_default=1, revision_no=1, version=0)
            for gate_type, references in gates:
                gate_local = local + "_" + gate_type
                gate_code = PREFIX + gate_local
                item = definition("GATE", gate_local, {"gateType": gate_type, "references": references})
                add(GATE, template_revision_id=rid, gate_code=gate_code,
                    name=stage_name + ("准入配置" if gate_type == "ENTRY" else "准出配置"),
                    gate_type=gate_type, stage_code=stage, description="PM-03/PM-11：" + NOTICE,
                    definition_revision_id=item["id"])
                for reference in references:
                    add(GATE_REF, template_revision_id=rid, gate_code=gate_code,
                        ref_type=reference["refType"], ref_code=reference["refCode"], ref_version=None)
            previous = stage
    return rows


def sql_value(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "b'1'" if value else "b'0'"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    # These controlled seed literals contain no backslashes; do not rely on the
    # session's NO_BACKSLASH_ESCAPES setting for future changes.
    if "\\" in value:
        raise ValueError("seed strings must not contain SQL-mode-dependent backslashes")
    return "'" + value.replace("'", "''") + "'"


def render_sql(rows: dict[str, list[dict]] | None = None) -> str:
    rows = build_rows() if rows is None else rows
    lines = [
        "-- GENERATED by scripts/generate_fproj009_sample_templates.py; do not edit by hand.",
        "-- Requirement: PM-03 / PM-11; PRD 3.1/3.2; F-PROJ-009; Owner: PROJ.",
        "-- Source: PRD3.2; tasks/features/F-PROJ-009.md 2026-09-09数据补全与样例任务; DU master 01300a6e.",
        "-- V208 is provisional: coordinator must approve the integration number after V206/V207.",
        f"-- Tenant {TENANT}; creator='{SOURCE}'; reserved ids {ID_MIN}..{ID_MAX}.",
        "-- Git master/HEAD sql/scripts checked before generation: no prior id/source occupation.",
        "-- Six complete DRAFT revisionNo=0 samples only; no runtime switch or automatic matching.",
        "-- Reusable PUBLISHED definitions assert schema structure only, not business completion.",
        "-- Native requiredStatus DONE is a predicate, never a seeded task/stage instance status.",
        "-- NULL CRM level means unrestricted, not a fabricated CRM value. No dictionary writes.",
        "-- matchPriority/partial restrictions illustrate potential overlap only. DRAFT is never",
        "-- a candidate; no claim of exact-match/multi-match/retired/no-match runtime acceptance.",
        "-- Presales is an explicitly selected scenario draft, not an invented project category.",
        "-- Do not publish these samples before real Owner bindings and runtime integration review.",
        "-- Exact-content id/natural-key conflicts (including deleted/other-tenant ids) SIGNAL.",
        "-- All production inserts follow all preflight checks; failures roll back the entire seed.",
        "-- No update/delete of existing configuration, immutable history or project business data.",
        "",
    ]
    for scenario, name, *_, priority, difference in SCENARIOS:
        lines.append(f"-- {PREFIX}{scenario}: {name}; matchPriority={priority}; {difference}")
    routine = "seed_fproj009_samples_20260909"
    temporary = {table: "tmp_fproj009_sample_" + str(index) for index, table in enumerate(rows)}
    cleanup = [f"    DROP TEMPORARY TABLE IF EXISTS `{temp}`;" for temp in temporary.values()]
    lines.extend(["", f"DROP PROCEDURE IF EXISTS `{routine}`;", "DELIMITER $$",
                  f"CREATE PROCEDURE `{routine}`()", "BEGIN", "    DECLARE EXIT HANDLER FOR SQLEXCEPTION",
                  "    BEGIN", "        ROLLBACK;", *["    " + line for line in cleanup],
                  "        RESIGNAL;", "    END;", *cleanup])
    for table, entries in rows.items():
        temp = temporary[table]
        columns = list(entries[0])
        assert all(list(entry) == columns for entry in entries), table
        lines.extend(["", f"    CREATE TEMPORARY TABLE `{temp}` LIKE `{table}`;",
                      f"    INSERT INTO `{temp}` (" + ", ".join(f"`{column}`" for column in columns) + ") VALUES"])
        lines.append(",\n".join("        (" + ", ".join(sql_value(entry[column]) for column in columns) + ")"
                                   for entry in entries) + ";")
    lines.extend(["", "    START TRANSACTION;", "    -- Preflight every table before the first production insert."])
    for table, entries in rows.items():
        temp = temporary[table]
        natural = " AND ".join(f"t.`{key}` <=> s.`{key}`" for key in KEYS[table])
        collision = f"t.`id` = s.`id` OR ({natural})"
        if table == TRANSITION:
            collision += (" OR (t.`tenant_id` = s.`tenant_id` AND t.`template_revision_id` = s.`template_revision_id`"
                          " AND t.`from_stage_code` = s.`from_stage_code` AND t.`is_default` = 1 AND s.`is_default` = 1)")
        matches = []
        for key in entries[0]:
            # Binary comparison avoids case/accent/trailing-space aliases being
            # accepted as the seed on legacy case-insensitive VARCHAR columns.
            # Inspect all rows: the first row may have a NULL optional string.
            binary = "BINARY " if any(isinstance(entry[key], str) for entry in entries) else ""
            matches.append(f"{binary}t.`{key}` <=> {binary}s.`{key}`")
        lines.extend([f"    IF EXISTS (SELECT 1 FROM `{table}` t JOIN `{temp}` s ON ({collision})",
                      "               WHERE NOT (" + " AND ".join(matches) + ")) THEN",
                      f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FPROJ009 seed conflict: {table}';",
                      "    END IF;"])
    for table, entries in rows.items():
        temp = temporary[table]
        columns = list(entries[0])
        lines.extend(["", f"    INSERT INTO `{table}` (" + ", ".join(f"`{key}`" for key in columns) + ")",
                      "    SELECT " + ", ".join(f"s.`{key}`" for key in columns) + f" FROM `{temp}` s",
                      f"    WHERE NOT EXISTS (SELECT 1 FROM `{table}` t WHERE t.`id` = s.`id`);"])
    lines.extend(["    COMMIT;", *cleanup, "END$$", "DELIMITER ;", f"CALL `{routine}`();",
                  f"DROP PROCEDURE `{routine}`;", ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="read-only generated SQL consistency check")
    args = parser.parse_args()
    sql = render_sql()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != sql:
            raise SystemExit("generated sample SQL differs; regenerate with this script")
        print("F-PROJ-009 sample SQL is deterministic and current")
    else:
        OUTPUT.write_text(sql, encoding="utf-8", newline="\n")
        print(OUTPUT)


if __name__ == "__main__":
    main()
