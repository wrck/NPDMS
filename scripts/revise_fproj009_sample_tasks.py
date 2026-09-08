#!/usr/bin/env python3
"""PM-03 / PM-11: authorized 20260909 correction of the six exact V208 drafts.

Owner PROJ; master DU 3d6d6686; F-PROJ-009 current correction task.
No database connection. V207/V208 and the V208 generator remain immutable inputs.
Generate V209, or --check its deterministic content. Runtime/Flyway validation is
performed by the coordinator, not by this script or the Python model tests.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path

import generate_fproj009_sample_templates as seed

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "sql/migrations/V209__fproj009_business_task_samples.sql"
SOURCE = "fproj009-task-correction"
TIME = "2026-09-09 00:00:00"
NEW_ID_MIN = 993009200000
AUDIT_ID = 993009299999
AUDIT = "plt_operation_audit"
NOTICE = ("办理界面待绑定；DRAFT仅为业务配置，不创建业务实体或完成事实。"
          "TASK_NATIVE/STAGE_NATIVE不代表Owner业务已接入，绑定真实页面/表单及完成依据前不得投用。")
RESULT = "优先业务结果，无重复上传；Owner已有结果时复用其有效版本，不另造上传任务。"

# Existing task identities are retained only for the surviving business work.
# name, goal, in-task functions, actual output, Requirement; no invented Owner API.
WORK = {
    "PRE02": ("现场工勘", "明确现场条件和实施就绪基线", "采集工勘分工、现场条件和就绪信息，引用项目有效工期", "工勘报告与就绪快照", "PRE-02"),
    "PRE04": ("需求分析", "形成可用于方案编制的需求基线", "填写、确认需求分析并查看有效版本", "需求分析", "PRE-04"),
    "KICKOFF": ("工程启动会", "按工程适用范围明确交付分工与目标", "按需组织启动会并记录共识；本项可选，不作为统一准出条件", "会议共识（按需记录，不要求独立文件）", "PM-03/PRD3.2"),
    "PLN01": ("施工计划制定", "形成审批通过的可执行施工计划", "读取有效工期，推算建议时间、调整计划、超期提示、提交与审批，并查看审批结果", "施工计划及审批事实", "PLN-01/PLN-02/PLN-03/PLN-04"),
    "PLAN": ("实施方案编审", "形成审核通过的实施方案", "编辑方案、导入客户方案、引用配置脚本和拓扑、提交审核、查看版本；重大复审按真实适用条件办理", "批准方案及审核版本", "SCH-01/SCH-02/SCH-05/PRD3.1.4"),
    "EXE01": ("到货验收", "确认实际到货与交付范围一致", "现场核验到货并登记签收及差异", "到货签收单或现场验货单", "EXE-01"),
    "EXE02": ("硬件安装", "完成设备现场安装并形成位置依据", "实施硬件安装，记录安装位置及现场照片", "安装记录", "EXE-02"),
    "EXE03": ("配置调试", "完成设备配置并验证有效配置", "执行配置调试，上传或通过手工命令/脚本采集配置Log并查看解析结果", "配置Log及调试结果", "EXE-03"),
    "EXE04": ("业务联调", "验证业务链路与预期功能", "执行联调、记录业务配置和验证结果", "业务联调记录", "EXE-04"),
    "SERVICE": ("现场督导", "督导渠道完成现场实施", "现场指导并记录渠道实施情况与服务确认", "现场服务单", "PM-03/PRD3.2"),
    "ACC01": ("现场培训", "完成适用用户培训", "开展培训并记录内容、参与人员与签字", "培训记录", "ACC-01"),
    "INITIAL": ("项目初验", "形成直签场景初验结论", "按配置办理初验并引用ACC独立验收实体及报告版本；实体接入前不伪造验收事实", "初验报告", "ACC-03"),
    "ACC02": ("满意度调查", "形成适用时点的有效满意度结果", "组织问卷填写并引用Owner判定、结果与整改事实", "满意度结果", "ACC-02"),
    "FINAL": ("项目终验", "形成配置要求的终验结论", "按配置办理终验并引用ACC独立验收实体及报告版本；实体接入前不伪造验收事实", "终验报告", "ACC-03"),
    "CLO02": ("项目闭环办理", "按真实闭环条件完成项目收口", "在同一办理过程进行条件检查、申请、审批和结果查看；交付件汇总/归档为辅助功能，不拆任务", "Owner闭环记录及审批事实（非另传文件）", "CLO-01/CLO-02/ACC-04"),
}


def tokens(scenario: str, stage: str) -> list[str]:
    if stage == "S0":
        return []
    if stage == "S1":
        return ["PRE02", "PRE04"] + (["KICKOFF"] if scenario in {"DS_ENG", "CH_ENG"} else [])
    if stage == "S2":
        return ["PLN01"]
    if stage == "S3":
        return ["PLAN"]
    if stage == "S4":
        return ([] if scenario == "PRE" else ["SERVICE"] if scenario == "CH_SUP" else ["EXE01", "EXE02"]) + ["EXE03", "EXE04"]
    if stage == "S5":
        return ["ACC01"] + (["INITIAL"] if scenario.startswith("DS_") else []) + ["ACC02", "FINAL"]
    if stage == "S6":
        return ["CLO02"]
    raise ValueError(stage)


def original_rows() -> dict[str, list[dict]]:
    # Check the actual SQL rows, not a hand-reconstructed approximation of V208.
    original = seed.build_rows()
    if seed.OUTPUT.read_text(encoding="utf-8") != seed.render_sql(original):
        raise ValueError("V208 actual SQL differs from its historical generator; refuse correction")
    return original


def digest(value) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":")).encode()).hexdigest()


def changes(before, after):
    result = {}
    for table in before:
        old = {r["id"]: r for r in before[table]}
        new = {r["id"]: r for r in after[table]}
        result[table] = {
            "insert": sorted(new.keys() - old.keys()),
            "delete": sorted(old.keys() - new.keys()),
            "update": sorted(i for i in old.keys() & new.keys() if old[i] != new[i]),
        }
    return result


def task_counts(rows):
    return {t["code"].removeprefix(seed.PREFIX): sum(
        task["template_revision_id"] == revision["id"]
        for revision in rows[seed.REVISION] if revision["template_id"] == t["id"]
        for task in rows[seed.TASK]) for t in rows[seed.TEMPLATE]}


def build_correction():
    before = original_rows()
    after = deepcopy(before)
    definitions = {r["id"]: r for r in before[seed.DEFINITION]}
    next_id = NEW_ID_MIN

    def stamp(row):
        row.update(updater=SOURCE, update_time=TIME)

    def revise(row, payload):
        nonlocal next_id
        old_id = row["definition_revision_id"]
        definition = deepcopy(definitions[old_id])
        definition.update(id=next_id, revision_no=2, payload=payload, creator=SOURCE,
                          updater=SOURCE, create_time=TIME, update_time=TIME, published_at=TIME)
        next_id += 1
        after[seed.DEFINITION].append(definition)
        row["definition_revision_id"] = definition["id"]
        for original in before[seed.REFERENCE]:
            if original["owner_revision_id"] != old_id:
                continue
            reference = deepcopy(original)
            reference.update(id=next_id, owner_revision_id=definition["id"], creator=SOURCE,
                             updater=SOURCE, create_time=TIME, update_time=TIME)
            next_id += 1
            after[seed.REFERENCE].append(reference)
        stamp(row)

    for template in after[seed.TEMPLATE]:
        template["name"] = template["name"].replace("【结构草稿】", "【业务草稿】")
        template["description"] = (NOTICE + "PM-03/PM-11；用户授权20260909纠偏。"
            "按实际责任配置任务；提交/审批/上传/查看版本在所属业务中办理。"
            "保留原四维、匹配优先级与阶段关系；售前仍仅S0→S4，不补其他阶段。")
        template["version"] += 1
        stamp(template)
    for revision in after[seed.REVISION]:
        stamp(revision)

    kept = []
    for task in after[seed.TASK]:
        scenario = task["task_code"].removeprefix(seed.PREFIX).split("_" + task["stage_code"] + "_", 1)[0]
        token = task["task_code"].rsplit("_", 1)[1]
        if token not in tokens(scenario, task["stage_code"]):
            continue
        name, goal, functions, output, requirement = WORK[token]
        if token == "ACC02":
            functions += "；直签在初验后收集" if scenario.startswith("DS_") else "；非直签在验收阶段收集"
        if token == "FINAL":
            functions += "；本直签样例配置初验后终验" if scenario.startswith("DS_") else "；本非直签样例仅终验，不要求初验"
        task.update(name=name, description=f"业务目标：{goal}。办理功能：{functions}。业务产出：{output}。{RESULT}{NOTICE}Requirement: {requirement}",
                    parent_task_code=None, parent_task_definition_key=None,
                    sort_order=tokens(scenario, task["stage_code"]).index(token) * 10,
                    definition_version=2)
        revise(task, {**definitions[task["definition_revision_id"]]["payload"], "name": name})
        assert len(task["description"]) <= 500
        kept.append(task)
    after[seed.TASK] = kept
    task_by_code = {t["task_code"]: t for t in kept}

    # Only a few explicit document outputs remain as optional existing-file slots.
    # Required business outcomes stay in their task; no invented result-source
    # enum or pretend Owner binding, no per-task/stage mandatory upload policy.
    documents = []
    for document in after[seed.DELIVERABLE]:
        task = task_by_code.get(document["task_code"])
        if task is None:
            continue
        token = task["task_code"].rsplit("_", 1)[1]
        title = {"PLN01": "施工计划", "PLAN": "批准方案", "SERVICE": "现场服务单"}.get(token)
        if title is None:
            continue
        document.update(name=title, required=False)
        payload = deepcopy(definitions[document["definition_revision_id"]]["payload"])
        payload.update(required=False, minimumQuantity=0)
        revise(document, payload)
        documents.append(document)
    after[seed.DELIVERABLE] = documents
    after[seed.MILESTONE] = []

    for stage in after[seed.STAGE]:
        stage["exit_criteria"] = {
            "S0": "由项目Owner判定真实指派与团队事实；不创建任务、交付件或人工Gate。办理界面待绑定。",
            "S1": "工勘与需求分析结果就绪；有效工期在项目/工勘办理中维护；工程启动会按需配置。",
            "S2": "施工计划制定任务内完成计划与审批，以真实审批结果为准，不以通知替代。",
            "S3": "实施方案编审任务内完成编辑/导入与审核，引用批准版本及适用复审结果。",
            "S4": "仅判定本场景实际配置的到货验收、硬件安装、现场督导、配置调试或业务联调结果。",
            "S5": "判定配置的培训、适用时点满意度及初验/终验结果；汇总归档是辅助功能。",
            "S6": "同一项目闭环办理过程完成条件检查、申请与审批；不生成V2服务交接。",
        }[stage["stage_code"]]
        stamp(stage)
    gates = []
    for gate in after[seed.GATE]:
        if gate["stage_code"] == "S0":
            continue
        if gate["gate_type"] == "EXIT":
            refs = [{"refType": "TASK", "refCode": t["task_code"]} for t in kept
                    if t["template_revision_id"] == gate["template_revision_id"]
                    and t["stage_code"] == gate["stage_code"] and not t["task_code"].endswith("_KICKOFF")]
            gate["description"] = "PM-03/PM-11：仅引用实际业务结果任务，不重复要求人工里程碑或阶段资料包。" + NOTICE
            revise(gate, {"gateType": "EXIT", "references": refs})
        gates.append(gate)
    after[seed.GATE] = gates
    gate_by_code = {g["gate_code"]: g for g in gates}
    all_definitions = {d["id"]: d for d in after[seed.DEFINITION]}
    after[seed.GATE_REF] = [r for r in before[seed.GATE_REF] if r["gate_code"] in gate_by_code
        and {"refType": r["ref_type"], "refCode": r["ref_code"]} in
        all_definitions[gate_by_code[r["gate_code"]]["definition_revision_id"]]["payload"]["references"]]
    assert next_id < AUDIT_ID
    audit = dict(id=AUDIT_ID, operation_code="FPROJ009_TASK_CORRECTION", aggregate_type="ProjectTemplate",
        aggregate_key=SOURCE, actor_id=0, correlation_id="FLYWAY-V209-fproj009-task-correction",
        idempotency_key_digest=digest(SOURCE), result_code="SUCCESS",
        detail_snapshot={"source": SOURCE, "executor": "FLYWAY", "authorization": "用户明确授权20260909；master DU 3d6d6686",
                         "actorNote": "系统actor0执行已授权数据纠偏，不冒充授权用户身份",
                         "beforeSnapshotDigest": digest(before), "afterSnapshotDigest": digest(after),
                         "changes": changes(before, after), "taskCounts": task_counts(after)},
        occurred_at=TIME, creator=SOURCE, create_time=TIME, tenant_id=seed.TENANT)
    return before, after, audit


def ids(rows):
    return ",".join(str(r["id"]) for r in rows) or "NULL"


def scopes(before, after):
    templates, revisions = ids(before[seed.TEMPLATE]), ids(before[seed.REVISION])
    definitions = ids(after[seed.DEFINITION])
    result = {}
    for table in before:
        identity = f"t.`id` IN ({ids(before[table] + after[table])})"
        if table == seed.TEMPLATE:
            extra = "t.`tenant_id` = 1 AND t.`code` IN (" + ",".join(seed.sql_value(r["code"]) for r in before[table]) + ")"
        elif table == seed.REVISION:
            extra = f"t.`template_id` IN ({templates})"
        elif table == seed.DEFINITION:
            extra = "t.`tenant_id` = 1 AND t.`definition_code` IN (" + ",".join(seed.sql_value(r["definition_code"]) for r in before[table]) + ")"
        elif table == seed.REFERENCE:
            extra = f"t.`owner_revision_id` IN ({definitions})"
        else:
            extra = f"t.`template_revision_id` IN ({revisions})"
        result[table] = f"({identity} OR ({extra}))"
    result[AUDIT] = f"(t.`id` = {AUDIT_ID} OR (t.`tenant_id` = 1 AND (BINARY t.`aggregate_key` = BINARY '{SOURCE}' OR BINARY t.`correlation_id` = BINARY 'FLYWAY-V209-fproj009-task-correction')))"
    return result


def runtime_guards(before):
    """Actual V57/V63/V71/V80 carriers, including deleted historical rows."""
    return {
        "proj_project": f"t.`lifecycle_template_id` IN ({ids(before[seed.TEMPLATE])})",
        "proj_project_stage": f"t.`source_definition_id` IN ({ids(before[seed.STAGE])})",
        "proj_project_task": f"t.`source_definition_id` IN ({ids(before[seed.TASK])})",
        "proj_project_milestone": f"t.`source_definition_id` IN ({ids(before[seed.MILESTONE])})",
        "proj_project_gate": f"t.`source_definition_id` IN ({ids(before[seed.GATE])})",
        "proj_project_deliverable": f"t.`source_definition_id` IN ({ids(before[seed.DELIVERABLE])})",
        "acc_project_deliverable": f"t.`source_definition_id` IN ({ids(before[seed.DELIVERABLE])})",
        "proj_project_task_execution_contract": f"t.`template_task_definition_id` IN ({ids(before[seed.TASK])})",
        "proj_project_split_request": f"t.`template_revision_id` IN ({ids(before[seed.REVISION])})",
        "proj_project_template_match_history": f"(t.`matched_template_id` IN ({ids(before[seed.TEMPLATE])}) OR t.`matched_template_revision_id` IN ({ids(before[seed.REVISION])}) OR t.`frozen_template_revision_id` IN ({ids(before[seed.REVISION])}))",
    }


def sql_equal(entries, left="t", right="s"):
    return " AND ".join(
        f"{'BINARY ' if any(isinstance(r[k], str) for r in entries) else ''}{left}.`{k}` <=> "
        f"{'BINARY ' if any(isinstance(r[k], str) for r in entries) else ''}{right}.`{k}`"
        for k in entries[0])


def render_sql():
    before, after, audit = build_correction()
    scope = scopes(before, after)
    diff = changes(before, after)
    before = {**before, AUDIT: []}
    after = {**after, AUDIT: [audit]}
    temporary = {(state, table): f"tmp_fp209_{state}_{i}" for state in ("before", "after")
                 for i, table in enumerate(before)}
    cleanup = [f"    DROP TEMPORARY TABLE IF EXISTS `{name}`;" for name in temporary.values()]
    routine = "correct_fproj009_business_tasks"
    lines = ["-- GENERATED by scripts/revise_fproj009_sample_tasks.py; no database connection in generator.",
        "-- PM-03 / PM-11; F-PROJ-009; Owner PROJ; PRD 3.1/3.2, current Feature Task correction.",
        "-- User expressly authorized 20260909; master DU 3d6d6686. V207/V208 remain historical.",
        f"-- V208 SQL SHA256: {hashlib.sha256(seed.OUTPUT.read_bytes()).hexdigest()}",
        f"-- Source: {SOURCE}; FLYWAY system actor 0, not the authorizing user's identity.",
        "-- Preserves six template IDs, six draft revisionNo=0 IDs, stage identities and transition relations.",
        "-- Appends definition revision2; NEVER updates/deletes published definition revision1 or its references.",
        "-- No new templates, runtime writes, fake Owner sources or business completion facts.",
        "-- Exact before/after snapshots include creator/updater/timestamps/version and natural-key scope.",
        "-- Any edit, extra row/version, missing seed, publication, project/runtime reference or ID collision rejects ALL six.",
        "-- SERIALIZABLE + ordered row/gap locks protect guards and writes; execute in the migration maintenance window.",
        "-- Optional file slots only: real business outputs remain in tasks; prefer Owner results, no duplicate upload.",
        "-- All checks precede production writes. Second exact execution checks after-state and does no writes."]
    for scenario, count in task_counts(after).items():
        lines.append(f"-- {scenario}: {count} business tasks")
    for table, delta in diff.items():
        lines.append(f"-- {table}: " + "; ".join(f"{op}={len(value)} IDs[{','.join(map(str, value))}]" for op, value in delta.items()))
    lines += ["", f"DROP PROCEDURE IF EXISTS `{routine}`;", "DELIMITER $$", f"CREATE PROCEDURE `{routine}`()", "BEGIN",
        "    DECLARE v_done BOOLEAN DEFAULT FALSE;", "    DECLARE v_count BIGINT DEFAULT 0;",
        "    DECLARE EXIT HANDLER FOR SQLEXCEPTION", "    BEGIN", "        ROLLBACK;",
        *["    " + line for line in cleanup], "        RESIGNAL;", "    END;", *cleanup]
    for (state, table), temp in temporary.items():
        entries = (before if state == "before" else after)[table]
        lines += [f"    CREATE TEMPORARY TABLE `{temp}` LIKE `{table}`;"]
        if entries:
            columns = list(entries[0])
            lines += [f"    INSERT INTO `{temp}` (" + ", ".join(f"`{k}`" for k in columns) + ") VALUES",
                ",\n".join("        (" + ", ".join(seed.sql_value(r[k]) for k in columns) + ")" for r in entries) + ";"]
    lines += ["    -- Temporary snapshot INSERTs may already have opened a transaction (Flyway autocommit off).",
              "    -- SESSION sets isolation for subsequent transactions on this one-shot Flyway connection.",
              "    -- START commits only temporary snapshot preparation, then opens the atomic correction transaction.",
              "    SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "    START TRANSACTION;",
              "    -- Lock each full affected identity/natural-key range before reading either snapshot."]
    for table, where in {**scope, **runtime_guards(before)}.items():
        # A cursor avoids aggregate-lock ambiguity and materializes row/gap locks
        # without returning potentially large result sets to the migration runner.
        lines += ["    BEGIN", "        DECLARE v_end BOOLEAN DEFAULT FALSE;", "        DECLARE v_id BIGINT;",
            f"        DECLARE locks CURSOR FOR SELECT t.`id` FROM `{table}` t WHERE {where} ORDER BY t.`id` FOR UPDATE;",
            "        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_end = TRUE;", "        OPEN locks;", "        lock_rows: LOOP",
            "            FETCH locks INTO v_id;", "            IF v_end THEN LEAVE lock_rows; END IF;", "        END LOOP;",
            "        CLOSE locks;", "    END;"]
    for table, where in runtime_guards(before).items():
        lines += [f"    IF EXISTS (SELECT 1 FROM `{table}` t WHERE {where}) THEN",
                  f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP209 runtime reference: {table}';", "    END IF;"]
    lines += [f"    SELECT COUNT(*) > 0 INTO v_done FROM `{AUDIT}` t WHERE {scope[AUDIT]};",
              "    -- Existing audit is accepted only with the exact completed snapshot, not merely by source."]
    for state, snapshot in (("before", before), ("after", after)):
        lines.append("    IF " + ("NOT v_done" if state == "before" else "v_done") + " THEN")
        for table, entries in snapshot.items():
            temp = temporary[state, table]
            if entries:
                lines += [f"        IF EXISTS (SELECT 1 FROM `{temp}` s LEFT JOIN `{table}` t ON t.`id` = s.`id`",
                    "                   WHERE t.`id` IS NULL OR NOT (" + sql_equal(entries) + ")) THEN",
                    f"            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP209 {state} snapshot: {table}';", "        END IF;"]
            lines += [f"        IF EXISTS (SELECT 1 FROM `{table}` t WHERE {scope[table]}" +
                      (f" AND t.`id` NOT IN ({ids(entries)})" if entries else "") + ") THEN",
                      f"            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP209 {state} extra row: {table}';", "        END IF;"]
        lines.append("    END IF;")
    lines += ["    -- ALL_PREFLIGHT_COMPLETE: no production DML occurs above this point.", "    IF NOT v_done THEN"]
    # New published definitions/references are insert-only and precede draft pointers.
    for table in (seed.DEFINITION, seed.REFERENCE):
        entries = [r for r in after[table] if r["id"] in diff[table]["insert"]]
        columns = list(entries[0])
        lines += [f"        INSERT INTO `{table}` (" + ", ".join(f"`{k}`" for k in columns) + ")",
                  "        SELECT " + ", ".join(f"s.`{k}`" for k in columns) + f" FROM `{temporary['after', table]}` s WHERE s.`id` IN ({ids(entries)});"]
    for table in (seed.GATE_REF, seed.GATE, seed.DELIVERABLE, seed.MILESTONE, seed.TASK):
        deleted = diff[table]["delete"]
        if not deleted:
            continue
        temp = temporary["before", table]
        lines += [f"        DELETE t FROM `{table}` t JOIN `{temp}` s ON t.`id` = s.`id`",
            f"        JOIN `{seed.REVISION}` r ON r.`id` = t.`template_revision_id`",
            f"        WHERE t.`id` IN ({','.join(map(str, deleted))}) AND t.`tenant_id` = 1 AND BINARY t.`creator` = BINARY '{seed.SOURCE}'",
            "          AND r.`revision_no` = 0 AND BINARY r.`status` = BINARY 'DRAFT' AND r.`deleted` = b'0'",
            "          AND " + sql_equal(before[table]) + ";",
            f"        IF ROW_COUNT() <> {len(deleted)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP209 delete CAS failed'; END IF;"]
    for table, delta in diff.items():
        if not delta["update"]:
            continue
        old_by_id = {r["id"]: r for r in before[table]}
        updated = [r for r in after[table] if r["id"] in delta["update"]]
        # Explicitly assign update_time even when the authorized timestamp equals
        # V208's seed time; otherwise MySQL ON UPDATE silently uses wall-clock
        # time and breaks the exact after-snapshot / idempotent replay guard.
        columns = [k for k in updated[0] if k == "update_time"
                   or any(r[k] != old_by_id[r["id"]][k] for r in updated)]
        lines += [f"        UPDATE `{table}` t JOIN `{temporary['before', table]}` b ON b.`id` = t.`id`",
            f"        JOIN `{temporary['after', table]}` s ON s.`id` = t.`id`",
            "        SET " + ", ".join(f"t.`{k}` = s.`{k}`" for k in columns),
            f"        WHERE t.`id` IN ({ids(updated)}) AND t.`tenant_id` = 1 AND BINARY t.`creator` = BINARY '{seed.SOURCE}'",
            "          AND " + sql_equal(before[table], right="b") + ";",
            f"        IF ROW_COUNT() <> {len(updated)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP209 update CAS failed'; END IF;"]
    columns = list(audit)
    lines += [f"        INSERT INTO `{AUDIT}` (" + ", ".join(f"`{k}`" for k in columns) + ")",
        "        SELECT " + ", ".join(f"s.`{k}`" for k in columns) + f" FROM `{temporary['after', AUDIT]}` s;",
        "    END IF;", "    COMMIT;", *cleanup, "END$$", "DELIMITER ;", f"CALL `{routine}`();", f"DROP PROCEDURE `{routine}`;", ""]
    return "\n".join(lines)


def simulate(current, audit_rows=None, *, runtime_references=False, fail_after_write=False):
    """Pure Python transaction model, NOT a MySQL execution/locking test.

    Tests retain all input state on rejection; irrelevant rows remain untouched.
    SQL independently uses typed snapshots, binary/null-safe equality and locks.
    """
    before, after, audit = build_correction()
    audits = [] if audit_rows is None else audit_rows
    if runtime_references:
        raise ValueError("runtime reference")
    expected = after if audits else before
    if audits and audits != [audit]:
        raise ValueError("audit conflict")
    # Fixtures represent the complete affected ranges, including any unexpected
    # child/revision. Production SQL determines these ranges with scopes().
    if current != expected:
        raise ValueError("exact snapshot mismatch (edit, publication, collision or extra row)")
    if fail_after_write:
        raise ValueError("injected transaction failure")
    return deepcopy(after), deepcopy([audit])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    sql = render_sql()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != sql:
            raise SystemExit("V209 differs; regenerate with this script")
        print("V209 deterministic SQL is current (no database executed)")
    else:
        OUTPUT.write_text(sql, encoding="utf-8", newline="\n")
        print(OUTPUT)
    before, after, _ = build_correction()
    print(json.dumps({"taskCounts": task_counts(after), "totalTasks": len(after[seed.TASK]),
        "ranges": {table: {op: len(value) for op, value in delta.items()}
                   for table, delta in changes(before, after).items()}}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
