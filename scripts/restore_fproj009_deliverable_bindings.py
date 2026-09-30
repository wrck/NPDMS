#!/usr/bin/env python3
"""PM-03/PM-11, F-PROJ-009: restore per-stage/task required+optional deliverable sets.

The requirement owner restated on 2026-09-20 that every stage and task must define
a set of required AND optional deliverables. The 2026-09-09 correction (V209,
user-authorized, master DU 3d6d6686) deliberately slimmed the six FPROJ009 drafts
to 11 optional file slots and deleted 141 bindings without rebuilding them; that
outcome is reversed for the DRAFT revisions only. No database connection.
Run with --check, or without arguments to regenerate SQL.

V305 appends deliverable definition revisions and full binding rows for the six
drafts; it never touches published definition revisions, frozen project snapshots
or the DS_ENG published template revision. Stage/task/milestone/gate/transition
rows stay exactly as V209 left them. Confirmation targets come from the definitions'
own revision-1 refCodes and V209's stage exit criteria, never invented Owners:
stage primaries confirm on STATE <stage>_COMPLETED (S0 has no tasks), surviving
task primaries reuse revision 1, V209-kept slots get a required revision 3, and
orphan task slots become optional confirmations on their V209 consolidation task
(or an optional stage slot for S0).
"""
from __future__ import annotations

import argparse
import json
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Q-MIG-V331-20260929-001 裁决迁出主链：生成物落 sql/oneoff/，不再进入 sql/migrations。
OUTPUT = ROOT / "sql/oneoff/V331__fproj009_deliverable_binding_restore.sql"
SOURCE = "fproj009-deliverable-restore-20260920"
TIME = "2026-09-20 00:00:00"
NEW_ID_MIN = 993009300000
BLOCK_END = 993009399999
AUDIT_ID = 993009299998
CORRELATION = "FLYWAY-V305-fproj009-deliverable-restore"

import generate_fproj009_sample_templates as seed
import revise_fproj009_sample_tasks as v209

DS_ENG = seed.PREFIX + "DS_ENG"
AUDIT = v209.AUDIT
REVISION = seed.REVISION
DEFINITION = seed.DEFINITION
DELIVERABLE = seed.DELIVERABLE

# Ordered consolidation candidates per orphan token, derived from V209's stage
# exit criteria; first candidate present in the draft's own task set wins.
# None marks S0 orphans: S0 has no tasks, so they stay stage-scope optional.
ORPHAN_REMAP = {
    "ASSIGN": None, "TEAM": None,
    "PREP": ["PRE02", "PRE04"], "PRE01": ["PRE02", "PRE04"],
    "PRE05": ["KICKOFF", "PRE04", "PRE02"],
    "PLN04": ["PLN01"], "REVIEW": ["PLAN"], "EXE06": ["EXE04"],
    "ACC04": ["FINAL", "ACC02"], "CLO01": ["CLO02"], "ACC06": ["CLO02", "FINAL"],
}

REQUIRED_STAGE_PAYLOAD = {"scope": "STAGE", "deliverableType": "DOCUMENT", "required": True,
                          "minimumQuantity": 1, "allowedSources": ["UPLOAD"], "outputType": "FILE"}
REQUIRED_TASK_PAYLOAD = {"scope": "TASK", "deliverableType": "DOCUMENT", "required": True,
                         "minimumQuantity": 1, "allowedSources": ["UPLOAD"], "outputType": "FILE"}
OPTIONAL_TASK_PAYLOAD = {"scope": "TASK", "deliverableType": "DOCUMENT", "required": False,
                         "minimumQuantity": 0, "allowedSources": ["UPLOAD"], "outputType": "FILE"}
OPTIONAL_STAGE_PAYLOAD = {"scope": "STAGE", "deliverableType": "DOCUMENT", "required": False,
                          "minimumQuantity": 0, "allowedSources": ["UPLOAD"], "outputType": "FILE"}


def token_of(task_code: str) -> str:
    return task_code.rsplit("_", 1)[1]


def rule(predicate: str, ref_code: str) -> dict:
    return {"predicate": predicate, "parameters": {"refCode": ref_code}}


def deliverable_nodes(bindings, defs_by_id):
    """Exact TemplateDesignerDocument deliverable node shape (designer save format)."""
    nodes = []
    for b in sorted(bindings, key=lambda r: (r["stage_code"], r["task_code"] or "", r["deliverable_code"])):
        nodes.append({
            "code": b["deliverable_code"], "name": b["name"],
            "source": {"transitionId": None, "definitionRevisionId": b["definition_revision_id"],
                       "transitionRevisionNo": None, "workBindingRevisionId": None,
                       "completionRuleRevisionId": None, "permissionPolicyRevisionId": None},
            "nodeKey": "deliverable:" + b["deliverable_code"],
            "required": bool(b["required"]), "taskCode": b["task_code"],
            "stageCode": b["stage_code"],
            "configuration": defs_by_id[b["definition_revision_id"]]["payload"],
        })
    return nodes


def build_restore():
    """Returns (before, after, audit, summary); before is the exact V209 outcome."""
    v208, current, _audit209 = v209.build_correction()
    after = deepcopy(current)
    v208_defs = {d["id"]: d for d in v208[DEFINITION]}
    current_defs = {d["id"]: d for d in after[DEFINITION]}
    v208_bindings: dict[int, list[dict]] = {}
    for b in v208[DELIVERABLE]:
        v208_bindings.setdefault(b["template_revision_id"], []).append(b)
    assert not [r for r in v208[seed.REFERENCE]
                if r["owner_revision_id"] in v208_defs and v208_defs[r["owner_revision_id"]]["definition_kind"] == "DELIVERABLE"]
    draft_of_template = {r["template_id"]: r for r in after[REVISION] if r["revision_no"] == 0}
    assert len(draft_of_template) == len(after[seed.TEMPLATE])
    latest_no = {}
    for d in after[DEFINITION]:
        if d["definition_kind"] == "DELIVERABLE":
            latest_no[d["definition_code"]] = max(latest_no.get(d["definition_code"], 0), d["revision_no"])

    next_id = NEW_ID_MIN
    new_definitions: list[dict] = []
    new_bindings: list[dict] = []
    remap_log: dict[str, str] = {}

    def add_revision(base_row, payload):
        nonlocal next_id
        revision = deepcopy(base_row)
        revision.update(id=next_id, revision_no=latest_no[base_row["definition_code"]] + 1,
                        payload=payload, creator=SOURCE, updater=SOURCE,
                        create_time=TIME, update_time=TIME, published_at=TIME)
        latest_no[base_row["definition_code"]] = revision["revision_no"]
        next_id += 1
        new_definitions.append(revision)
        after[DEFINITION].append(revision)
        return revision

    def add_binding(rid, code, name, stage, task_code, required, def_id):
        nonlocal next_id
        row = {"id": next_id, "template_revision_id": rid, "deliverable_code": code,
               "name": name, "stage_code": stage, "task_code": task_code,
               "required": required, "definition_revision_id": def_id,
               "tenant_id": seed.TENANT, "creator": SOURCE, "create_time": TIME,
               "updater": SOURCE, "update_time": TIME, "deleted": False, "deleted_time": None}
        next_id += 1
        new_bindings.append(row)
        after[DELIVERABLE].append(row)
        return row

    summary = {}
    for template in after[seed.TEMPLATE]:
        scenario = template["code"].removeprefix(seed.PREFIX)
        rid = draft_of_template[template["id"]]["id"]
        tasks = {token_of(t["task_code"]): t for t in after[seed.TASK]
                 if t["template_revision_id"] == rid}
        live = {b["deliverable_code"]: b for b in after[DELIVERABLE]
                if b["template_revision_id"] == rid}
        counts = {"stages": len({b["stage_code"] for b in v208_bindings[rid]}),
                  "tasks": len(tasks), "required": 0, "optional": 0}
        for binding in sorted(v208_bindings[rid], key=lambda b: b["deliverable_code"]):
            code = binding["deliverable_code"]
            stage = binding["stage_code"]
            orig_def = v208_defs[binding["definition_revision_id"]]
            token = token_of(binding["task_code"]) if binding["task_code"] else None
            if token is None:
                # Stage primary: confirmed by stage completion; S0 keeps no task,
                # so the STATE reference is the only real confirmation target.
                revision = add_revision(orig_def, {**REQUIRED_STAGE_PAYLOAD,
                    "confirmationRule": rule("STATE", stage + "_COMPLETED")})
                add_binding(rid, code, binding["name"], stage, None, True, revision["id"])
                counts["required"] += 1
            elif token in tasks:
                task_code = tasks[token]["task_code"]
                assert orig_def["payload"]["confirmationRule"]["parameters"]["refCode"] == task_code
                if code in live:
                    assert current_defs[live[code]["definition_revision_id"]]["payload"][
                        "confirmationRule"]["parameters"]["refCode"] == task_code
                    # V209 kept this slot as optional; a required revision 3
                    # restores it and the surviving binding row is flipped.
                    revision = add_revision(orig_def, {**REQUIRED_TASK_PAYLOAD,
                        "confirmationRule": rule("TASK", task_code)})
                    live[code].update(required=True, definition_revision_id=revision["id"],
                                      updater=SOURCE, update_time=TIME)
                    counts["required"] += 1
                else:
                    # Survivor primary: revision 1 already required with a live
                    # refCode; bind it unchanged instead of stacking revisions.
                    add_binding(rid, code, binding["name"], stage, task_code, True,
                                binding["definition_revision_id"])
                    counts["required"] += 1
            else:
                candidates = ORPHAN_REMAP[token]
                if candidates is None:
                    revision = add_revision(orig_def, {**OPTIONAL_STAGE_PAYLOAD,
                        "confirmationRule": rule("STATE", stage + "_COMPLETED")})
                    add_binding(rid, code, binding["name"], stage, None, False, revision["id"])
                    remap_log[code] = stage + " (stage optional)"
                else:
                    target = next((c for c in candidates if c in tasks), None)
                    assert target, (template["code"], stage, token)
                    task_code = tasks[target]["task_code"]
                    revision = add_revision(orig_def, {**OPTIONAL_TASK_PAYLOAD,
                        "confirmationRule": rule("TASK", task_code)})
                    add_binding(rid, code, binding["name"], stage, task_code, False, revision["id"])
                    remap_log[code] = task_code
                counts["optional"] += 1
        assert counts["required"] == counts["stages"] + counts["tasks"]
        after_codes = {b["deliverable_code"] for b in after[DELIVERABLE] if b["template_revision_id"] == rid}
        assert after_codes == {b["deliverable_code"] for b in v208_bindings[rid]}
        summary[scenario] = {**counts, "total": len(after_codes)}

    defs_by_id = {d["id"]: d for d in after[DEFINITION]}
    before_defs_by_id = current_defs
    changes = v209.changes(current, after)
    assert set(changes) == set(current)
    assert changes[DEFINITION]["delete"] == [] and changes[DEFINITION]["update"] == []
    assert changes[DELIVERABLE]["delete"] == []
    assert all(not changes[t]["insert"] and not changes[t]["delete"] and not changes[t]["update"]
               for t in current if t not in {DEFINITION, DELIVERABLE})
    assert next_id <= BLOCK_END
    audit = dict(id=AUDIT_ID, operation_code="FPROJ009_DELIVERABLE_RESTORE",
                 aggregate_type="ProjectTemplate", aggregate_key=SOURCE, actor_id=0,
                 correlation_id=CORRELATION, idempotency_key_digest=v209.digest(SOURCE),
                 result_code="SUCCESS",
                 detail_snapshot={"source": SOURCE, "executor": "FLYWAY",
                     "authorization": "需求方20260920重申：模板交付件跟阶段和任务绑定，每个阶段、任务"
                         "都要定义一组可选和必选交付件；V209删除141条绑定且未重建，本迁移对DRAFT修订追加重建",
                     "actorNote": "系统actor0执行已授权数据重建，不冒充授权用户身份",
                     "beforeSnapshotDigest": v209.digest(current), "afterSnapshotDigest": v209.digest(after),
                     "changes": changes, "deliverableCounts": summary, "orphanRemap": remap_log},
                 occurred_at=TIME, creator=SOURCE, create_time=TIME, tenant_id=seed.TENANT)
    return current, after, audit, summary


def render_sql():
    before, after, audit, summary = build_restore()
    before = {**before, AUDIT: []}
    after = {**after, AUDIT: [audit]}
    touched = (DEFINITION, DELIVERABLE)
    diff = v209.changes(before, after)
    draft_ids = sorted({r["template_revision_id"] for r in before[DELIVERABLE]}
                       | {r["template_revision_id"] for r in after[DELIVERABLE]}
                       | {r["id"] for r in after[REVISION] if r["revision_no"] == 0})
    template_ids = sorted({r["template_id"] for r in after[REVISION] if r["revision_no"] == 0})
    template_codes = sorted(t["code"] for t in after[seed.TEMPLATE])
    deliverable_codes = sorted({d["definition_code"] for d in before[DEFINITION]
                                if d["definition_kind"] == "DELIVERABLE"}
                               | {d["definition_code"] for d in after[DEFINITION]
                                  if d["definition_kind"] == "DELIVERABLE"})
    new_defs = [r for r in after[DEFINITION] if r["id"] in diff[DEFINITION]["insert"]]
    new_binds = [r for r in after[DELIVERABLE] if r["id"] in diff[DELIVERABLE]["insert"]]
    updated_binds = [r for r in after[DELIVERABLE] if r["id"] in diff[DELIVERABLE]["update"]]

    defs_by_id = {d["id"]: d for d in after[DEFINITION]}
    ds_eng = next(t for t in after[seed.TEMPLATE] if t["code"] == DS_ENG)
    ds_eng_rid = next(r["id"] for r in after[REVISION] if r["template_id"] == ds_eng["id"] and r["revision_no"] == 0)
    doc_before = json.dumps(deliverable_nodes(
        [b for b in before[DELIVERABLE] if b["template_revision_id"] == ds_eng_rid], {**defs_by_id}),
        ensure_ascii=False, separators=(",", ":"))
    doc_after = json.dumps(deliverable_nodes(
        [b for b in after[DELIVERABLE] if b["template_revision_id"] == ds_eng_rid], defs_by_id),
        ensure_ascii=False, separators=(",", ":"))
    assert "\\" not in doc_before + doc_after
    other_codes = [seed.sql_value(c) for c in template_codes if c != DS_ENG]

    def id_list(values):
        return ",".join(map(str, values)) or "NULL"

    scope = {
        seed.TEMPLATE: f"(t.`tenant_id` = 1 AND t.`code` IN ({', '.join(seed.sql_value(c) for c in template_codes)}))",
        REVISION: f"(t.`template_id` IN ({v209.ids(after[REVISION])}) OR t.`id` = {ds_eng_rid})",
        DEFINITION: (f"(t.`id` IN ({v209.ids(new_defs)}) OR (t.`tenant_id` = 1 AND t.`definition_kind` = 'DELIVERABLE'"
                     f" AND t.`definition_code` IN ({', '.join(seed.sql_value(c) for c in deliverable_codes)})))"),
        DELIVERABLE: f"(t.`id` IN ({v209.ids(after[DELIVERABLE])}) OR t.`template_revision_id` IN ({id_list(draft_ids)}))",
        AUDIT: (f"(t.`id` = {AUDIT_ID} OR (t.`tenant_id` = 1 AND (BINARY t.`aggregate_key` = BINARY '{SOURCE}'"
                f" OR BINARY t.`correlation_id` = BINARY '{CORRELATION}')))"),
    }
    temporary = {(state, table): f"tmp_fp305_{state}_{index}" for state in ("before", "after")
                 for index, table in enumerate(touched + (AUDIT,))}
    cleanup = [f"    DROP TEMPORARY TABLE IF EXISTS `{name}`;" for name in temporary.values()]
    routine = "restore_fproj009_deliverable_bindings"
    lines = ["-- GENERATED by scripts/restore_fproj009_deliverable_bindings.py; no database connection in generator.",
        "-- PM-03 / PM-11; F-PROJ-009; Owner PROJ; PRD 3.1/3.2 + requirement owner restatement 20260920.",
        "-- Every stage and task must define a set of required AND optional deliverables; V209 (20260909,",
        "-- authorized DU 3d6d6686) deleted 141 bindings without rebuilding them. This migration rebuilds",
        "-- the six DRAFT revisions only; published definition revisions, frozen project snapshots and the",
        "-- DS_ENG published revision stay immutable. Stage/task/milestone/gate/transition rows untouched.",
        f"-- Source: {SOURCE}; FLYWAY system actor 0, not the authorizing user's identity.",
        "-- Confirmation targets: stage primaries on STATE <stage>_COMPLETED; surviving task primaries",
        "-- reuse revision 1 as-is; V209-kept slots get required revision 3; orphan task slots become",
        "-- optional confirmations on V209's consolidation task; S0 orphans stay stage-scope optional.",
        "-- Exact before/after snapshots include creator/updater/timestamps/version and natural-key scope.",
        "-- Any edit, publication, extra binding/definition row, designer save or ID collision rejects ALL six.",
        "-- SERIALIZABLE + ordered row/gap locks protect guards and writes; execute in the migration window.",
        "-- All checks precede production writes. Second exact execution checks after-state and does no writes."]
    for scenario, counts in summary.items():
        lines.append(f"-- {seed.PREFIX}{scenario}: {counts['total']} deliverables"
                     f" ({counts['required']} required / {counts['optional']} optional)"
                     f" across {counts['stages']} stages and {counts['tasks']} tasks")
    for table in touched:
        lines.append(f"-- {table}: " + "; ".join(
            f"{op}={len(value)} IDs[{','.join(map(str, value))}]" for op, value in diff[table].items()))
    lines += ["", f"DROP PROCEDURE IF EXISTS `{routine}`;", "DELIMITER $$", f"CREATE PROCEDURE `{routine}`()", "BEGIN",
        "    DECLARE v_done BOOLEAN DEFAULT FALSE;",
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
              "    -- START commits only temporary snapshot preparation, then opens the atomic restore transaction.",
              "    SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "    START TRANSACTION;",
              "    -- Lock each full affected identity/natural-key range before reading either snapshot."]
    for table, where in scope.items():
        lines += ["    BEGIN", "        DECLARE v_end BOOLEAN DEFAULT FALSE;", "        DECLARE v_id BIGINT;",
            f"        DECLARE locks CURSOR FOR SELECT t.`id` FROM `{table}` t WHERE {where} ORDER BY t.`id` FOR UPDATE;",
            "        DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_end = TRUE;", "        OPEN locks;", "        lock_rows: LOOP",
            "            FETCH locks INTO v_id;", "            IF v_end THEN LEAVE lock_rows; END IF;", "        END LOOP;",
            "        CLOSE locks;", "    END;"]
    lines += ["    -- Draft identity: exactly one revisionNo=0 DRAFT row per template, none published over it.",
        f"    IF (SELECT COUNT(*) FROM (SELECT t.`id` FROM `{seed.TEMPLATE}` t JOIN `{REVISION}` r",
        f"        ON r.`template_id` = t.`id` AND r.`revision_no` = 0 WHERE t.`tenant_id` = 1",
        f"        AND t.`code` IN ({', '.join(seed.sql_value(c) for c in template_codes)}) GROUP BY t.`id`",
        "        HAVING COUNT(*) <> 1 OR MAX(BINARY r.`status` <> BINARY 'DRAFT') OR MAX(r.`deleted` <> b'0')) x) > 0",
        f"    OR (SELECT COUNT(DISTINCT r.`template_id`) FROM `{REVISION}` r JOIN `{seed.TEMPLATE}` t ON t.`id` = r.`template_id`",
        f"        WHERE t.`tenant_id` = 1 AND t.`code` IN ({', '.join(seed.sql_value(c) for c in template_codes)})",
        "        AND r.`revision_no` = 0) <> 6 THEN",
        "        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 draft revision identity';", "    END IF;",
        f"    IF EXISTS (SELECT 1 FROM `{REVISION}` r JOIN `{seed.TEMPLATE}` t ON t.`id` = r.`template_id`",
        f"        WHERE t.`tenant_id` = 1 AND t.`code` IN ({', '.join(other_codes)}) AND r.`revision_no` = 0",
        "        AND r.`designer_document` IS NOT NULL) THEN",
        "        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 unexpected designer document';", "    END IF;",
        f"    SELECT COUNT(*) > 0 INTO v_done FROM `{AUDIT}` t WHERE {scope[AUDIT]};",
        "    -- Existing audit is accepted only with the exact completed snapshot, not merely by source."]
    for state, snapshot in (("before", before), ("after", after)):
        lines.append("    IF " + ("NOT v_done" if state == "before" else "v_done") + " THEN")
        for table in touched:
            entries = snapshot[table]
            temp = temporary[state, table]
            lines += [f"        IF EXISTS (SELECT 1 FROM `{temp}` s LEFT JOIN `{table}` t ON t.`id` = s.`id`",
                "                   WHERE t.`id` IS NULL OR NOT (" + v209.sql_equal(entries) + ")) THEN",
                f"            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 {state} snapshot: {table}';", "        END IF;",
                f"        IF EXISTS (SELECT 1 FROM `{table}` t WHERE {scope[table]}" +
                (f" AND t.`id` NOT IN ({v209.ids(entries)})" if entries else "") + ") THEN",
                f"            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 {state} extra row: {table}';", "        END IF;"]
        doc_check = (doc_after if state == "after" else doc_before)
        lines += ["        IF NOT COALESCE((SELECT JSON_EXTRACT(r.`designer_document`, '$.deliverables') <=>",
            f"            CAST({seed.sql_value(doc_check)} AS JSON) FROM `{seed.TEMPLATE}` t JOIN `{REVISION}` r",
            f"            ON r.`template_id` = t.`id` AND r.`revision_no` = 0 WHERE t.`tenant_id` = 1",
            f"            AND BINARY t.`code` = BINARY {seed.sql_value(DS_ENG)}), FALSE) THEN",
            f"            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 {state} designer deliverables';",
            "        END IF;", "    END IF;"]
    lines += ["    -- ALL_PREFLIGHT_COMPLETE: no production DML occurs above this point.", "    IF NOT v_done THEN"]
    for table, entries in ((DEFINITION, new_defs), (DELIVERABLE, new_binds)):
        columns = list(entries[0])
        lines += [f"        INSERT INTO `{table}` (" + ", ".join(f"`{k}`" for k in columns) + ")",
            "        SELECT " + ", ".join(f"s.`{k}`" for k in columns) + f" FROM `{temporary['after', table]}` s",
            f"        WHERE s.`id` IN ({v209.ids(entries)});"]
    old_by_id = {r["id"]: r for r in before[DELIVERABLE]}
    columns = [k for k in updated_binds[0]
               if k == "update_time" or any(r[k] != old_by_id[r["id"]][k] for r in updated_binds)]
    lines += ["        -- Flip the surviving V209 slots to required with CAS on the exact pre-image.",
        f"        UPDATE `{DELIVERABLE}` t JOIN `{temporary['before', DELIVERABLE]}` b ON b.`id` = t.`id`",
        f"        JOIN `{temporary['after', DELIVERABLE]}` s ON s.`id` = t.`id`",
        f"        JOIN `{REVISION}` r ON r.`id` = t.`template_revision_id` AND r.`revision_no` = 0",
        "            AND BINARY r.`status` = BINARY 'DRAFT' AND r.`deleted` = b'0'",
        "        SET " + ", ".join(f"t.`{k}` = s.`{k}`" for k in columns),
        f"        WHERE t.`id` IN ({v209.ids(updated_binds)}) AND t.`tenant_id` = 1",
        f"          AND BINARY t.`creator` = BINARY '{seed.SOURCE}' AND " + v209.sql_equal(before[DELIVERABLE], right="b") + ";",
        f"        IF ROW_COUNT() <> {len(updated_binds)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 update CAS failed'; END IF;",
        "        -- Designer deliverables of the edited DS_ENG draft are rewritten from the restored bindings.",
        f"        UPDATE `{REVISION}` r JOIN `{seed.TEMPLATE}` t ON t.`id` = r.`template_id`",
        f"        SET r.`designer_document` = JSON_SET(r.`designer_document`, '$.deliverables',",
        f"                CAST({seed.sql_value(doc_after)} AS JSON)), r.`updater` = {seed.sql_value(SOURCE)},",
        f"            r.`update_time` = {seed.sql_value(TIME)}",
        f"        WHERE t.`tenant_id` = 1 AND BINARY t.`code` = BINARY {seed.sql_value(DS_ENG)}",
        "          AND r.`revision_no` = 0 AND BINARY r.`status` = BINARY 'DRAFT' AND r.`deleted` = b'0'",
        "          AND r.`designer_document` IS NOT NULL",
        "          AND COALESCE(JSON_EXTRACT(r.`designer_document`, '$.deliverables') <=>",
        f"              CAST({seed.sql_value(doc_before)} AS JSON), FALSE);",
        "        IF ROW_COUNT() <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'FP305 designer document CAS failed'; END IF;"]
    columns = list(audit)
    lines += [f"        INSERT INTO `{AUDIT}` (" + ", ".join(f"`{k}`" for k in columns) + ")",
        "        SELECT " + ", ".join(f"s.`{k}`" for k in columns) + f" FROM `{temporary['after', AUDIT]}` s;",
        "    END IF;", "    COMMIT;", *cleanup, "END$$", "DELIMITER ;", f"CALL `{routine}`();",
        f"DROP PROCEDURE `{routine}`;", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    sql = render_sql()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != sql:
            raise SystemExit("V305 differs; regenerate with this script")
        print("V305 deterministic SQL is current (no database executed)")
    else:
        OUTPUT.write_text(sql, encoding="utf-8", newline="\n")
        print(OUTPUT)
    before, after, _, summary = build_restore()
    print(json.dumps({"deliverableCounts": summary,
        "ranges": {table: {op: len(value) for op, value in delta.items()}
                   for table, delta in v209.changes(before, after).items()}}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
