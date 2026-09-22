#!/usr/bin/env python3
"""PM-03/PM-11, F-PROJ-009: accepted configuration plus PRD 3.2 authoring drafts.

No database writes. Historical migrations and accepted snapshots are never rewritten.
Derived drafts require their own publication and business acceptance.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import json
from pathlib import Path

from generate_fproj009_sample_templates import SCENARIOS
from revise_fproj009_sample_tasks import tokens

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "sql/template-seeds/accepted-fproj009-20260921.json"
OUTPUT = ROOT / "sql/migrations/V336__project_template_accepted_seed_pack.sql"
SCENARIO_OUTPUT = ROOT / "sql/template-seeds/prd-project-scenarios.json"
ACTOR = "fproj009-seeds-20260921"
OLD = [(910000 + i, code, "seed") for i, code in enumerate([
    "TPL-DIRECT-ENG-STD", "TPL-DIRECT-ENG-SUP", "TPL-DIRECT-ENG-AGENT",
    "TPL-CHANNEL-GEN-STD", "TPL-GEN-FALLBACK", "TPL-DIRECT-GEN-LEGACY",
    "TPL-NEW-DRAFT", "TPL-CHANNEL-GEN-DIRECT"], 1)] + [
    (992203040001, "TPL-PRE04-NO-EXT", "seed"),
] + [(i, "FPROJ009_SAMPLE_" + code, "fproj009-samples-20260909") for i, code in [
    (993009000005, "DS_ENG"), (993009000347, "DS_GEN"),
    (993009000662, "CH_ENG"), (993009000986, "CH_DIR"),
    (993009001292, "CH_SUP"), (993009001589, "PRE")]]


def condition(key, name, expression):
    return dict(key=key, name=name, kind="CONDITION", shared=False,
                expression=expression, decision=None)


def derive(asset):
    result = []
    for index, (code, name, signing, category, implementation, priority, note) in enumerate(SCENARIOS):
        if code == "DS_GEN":
            implementation = None
            note = "无工程启动会；保留初验、初验后满意度及终验；实施方式不限。"
        d = deepcopy(asset["revision"]["document"])
        stages = ["S0", "S4"] if code == "PRE" else [f"S{i}" for i in range(7)]
        d["match"] = dict(signingMethod=signing, projectCategory=category,
                          implementationMethod=implementation, majorProjectLevel=None)
        d["stages"] = [s for s in d["stages"] if s["code"] in stages]
        s0 = deepcopy(d["stages"][0])
        s0.update(code="S0", name="项目立项与指派", nodeKey="stage:S0", lifecycleStage="S0",
                  source=None, entryCriteria="按PM-01办理项目指派", exitCriteria="服务经理、项目经理与团队事实待绑定复核",
                  completionRuleKey="prd_s0_completion", admissionRuleKey=None, exitRuleKey=None)
        d["stages"].insert(0, s0)
        d["rules"].append(condition("prd_s0_completion", "项目立项与指派·完成",
                                    {"predicate": "STAGE_NATIVE_STATUS", "parameters": {"requiredStatus": "DONE"}}))
        d["tasks"] = [t for t in d["tasks"] if t["stageCode"] in stages
                       and t["code"].rsplit("_", 1)[1] in tokens(code, t["stageCode"])]
        if code == "CH_SUP":
            service = deepcopy(next(t for t in asset["revision"]["document"]["tasks"]
                                    if t["code"].endswith("S4_EXE02")))
            service.update(code="FPROJ009_SAMPLE_DS_ENG_S4_SERVICE", nodeKey="task:FPROJ009_SAMPLE_DS_ENG_S4_SERVICE",
                           name="现场督导", description="PRD3.2：记录原厂督导现场服务；办理绑定待Owner确认。",
                           completionRuleKey="prd_service_completion", source=None)
            service["workBinding"] = {"type": "TASK_NATIVE", "parameters": {}}
            d["rules"].append(condition("prd_service_completion", "现场督导·完成",
                                        {"predicate": "TASK_NATIVE_STATUS", "parameters": {"requiredStatus": "DONE"}}))
            d["tasks"].append(service)
        task_codes = {t["code"] for t in d["tasks"]}
        for t in d["tasks"]:
            if t["code"].endswith("_ACC02"):
                t["description"] = ("PRD3.2：初验后开展满意度调查。" if code.startswith("DS_")
                                    else "PRD3.2：验收阶段开展满意度调查，不依赖初验。")
                if code.startswith("DS_"):
                    t["satisfactionTiming"] = "AFTER_INITIAL_ACCEPTANCE"
        d["milestones"] = [dict(code=t["code"] + "_M", nodeKey="milestone:" + t["code"],
                                 name=t["name"] + "通过", stageCode="S5", timing="验收领域事实通过后",
                                 criteria="核对对应ACC验收通过事实；不生成完成记录。",
                                 configuration={"name": t["name"] + "通过", "criteria": "核对对应ACC验收通过事实"},
                                 source=None)
                            for t in d["tasks"] if t["code"].endswith(("_INITIAL", "_FINAL"))]
        d["deliverables"] = [f for f in d["deliverables"] if f["stageCode"] in stages
                              and (not f["taskCode"] or f["taskCode"] in task_codes)]
        if code == "CH_SUP":
            f = deepcopy(asset["revision"]["document"]["deliverables"][0])
            f.update(code="FPROJ009_SAMPLE_DS_ENG_S4_SERVICE_DOC", nodeKey="deliverable:FPROJ009_SAMPLE_DS_ENG_S4_SERVICE_DOC",
                     name="现场服务单", taskCode=service["code"], stageCode="S4", source=None)
            f["configuration"]["scope"] = "TASK"
            d["deliverables"].append(f)
        if code.startswith("CH_"):
            for f in d["deliverables"]:
                if f["taskCode"] and f["taskCode"].endswith("_EXE01"):
                    f["name"] = "现场验货单"
        # Rebuild stage-local gates/rules instead of retaining dangling references
        # to initial acceptance, kickoff or stages removed by the scenario.
        d["gates"] = []
        for i, s in enumerate(d["stages"]):
            stage = s["code"]
            s.update(start=i == 0, terminal=i == len(stages) - 1, sortOrder=i * 10)
            s["admissionRuleKey"] = None
            if i:
                key = "prd_admission_" + stage
                s["admissionRuleKey"] = key
                d["rules"].append(condition(key, s["name"] + "·准入", {
                    "predicate": "STATE", "parameters": {"refCode": stages[i - 1] + "_COMPLETED"}}))
            refs = [{"refType": "TASK", "refCode": t["code"], "refVersion": None}
                    for t in d["tasks"] if t["stageCode"] == stage]
            refs += [{"refType": "DELIVERABLE", "refCode": f["code"], "refVersion": None}
                     for f in d["deliverables"] if f["stageCode"] == stage and f["required"]]
            s["exitRuleKey"] = None
            if refs:
                key = "prd_exit_" + stage
                s["exitRuleKey"] = key
                d["rules"].append(condition(key, s["name"] + "·退出", {"operator": "ALL", "rules": [
                    {"predicate": ref["refType"], "parameters": {"refCode": ref["refCode"]}} for ref in refs]}))
                d["gates"].append(dict(code="PRD_" + code + "_" + stage + "_EXIT", nodeKey="gate:" + stage,
                                       name=s["name"] + "准出", stageCode=stage, gateType="EXIT", references=refs,
                                       description="交付件按实际存在性检测；不以关联任务完成替代材料。", source=None))
        used = {n.get(k) for group in ("stages", "tasks") for n in d[group]
                for k in ("completionRuleKey", "admissionRuleKey", "exitRuleKey")}
        used |= {d.get("matchRuleKey"), d.get("closureRuleKey")}
        d["rules"] = [r for r in d["rules"] if r["key"] in used]
        d["transitions"] = []
        d["layout"] = {"nodes": {}, "views": {}}
        # Source pins are provenance, never new runtime/business facts.
        prefix = "PRD_20260921_" + code
        d = json.loads(json.dumps(d, ensure_ascii=False).replace("FPROJ009_SAMPLE_DS_ENG", prefix))
        result.append(dict(id=993009900000 + index * 2, revisionId=993009900001 + index * 2,
                           code=prefix, name=name, priority=priority, status="DRAFT", document=d,
                           description=note + "派生草稿，未独立验收；S0指派、施工计划审批、满意度时点及闭环流程须核对后发布。",
                           explicitSelectionOnly=code == "PRE"))
    return result


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (bool, int)):
        return str(int(value))
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    # UTF-8 hex literals are independent of NO_BACKSLASH_ESCAPES and quotes.
    return "CONVERT(X'" + value.encode("utf8").hex() + "' USING utf8mb4)"


def rows(asset, scenarios):
    base = dict(tenant_id=1, creator=ACTOR, updater=ACTOR, deleted=0)
    templates, revisions = [], []
    t, r = asset["template"], asset["revision"]
    templates.append(dict(base, id=int(t["id"]), code=t["code"], name=t["name"], status="ACTIVE",
                          match_priority=t["matchPriority"], description="2026-09-21已验收S1-S6配置；来源项目PJT2026000009，保留原发布快照。",
                          version=0, system_reserved=0))
    def revision(identity, template_id, number, status, document, snapshot=None):
        m = document["match"]
        return dict(base, id=identity, template_id=template_id, revision_no=number, status=status,
                    signing_method=m.get("signingMethod"), project_category=m.get("projectCategory"),
                    implementation_method=m.get("implementationMethod"), major_project_level=m.get("majorProjectLevel"),
                    process_definition_key=document.get("processDefinitionKey"), designer_schema_version=document["schemaVersion"],
                    designer_document=document, execution_schema_version=3 if snapshot else None,
                    execution_snapshot=snapshot, compiler_version="template-version-3" if snapshot else None,
                    snapshot_hash=None, closure_policy=document.get("closurePolicy"),
                    published_by=ACTOR if snapshot else None,
                    published_time="2026-09-21 00:00:00" if snapshot else None)
    revisions.append(revision(int(r["id"]), int(t["id"]), r["revisionNo"], "PUBLISHED", r["document"], r["snapshot"]))
    for s in scenarios:
        templates.append(dict(base, id=s["id"], code=s["code"], name=s["name"], status="DRAFT",
                              match_priority=s["priority"], description=s["description"], version=0, system_reserved=0))
        revisions.append(revision(s["revisionId"], s["id"], 0, "DRAFT", s["document"]))
    return {"plt_business_view_revision": asset["businessViews"], "proj_project_template": templates,
            "proj_project_template_revision": revisions}


def render(asset, scenarios):
    data = rows(asset, scenarios)
    natural = {"plt_business_view_revision": ("tenant_id", "entity_type", "view_key", "revision_no"),
               "proj_project_template": ("tenant_id", "code"),
               "proj_project_template_revision": ("tenant_id", "template_id", "revision_no")}
    lines = ["-- GENERATED: scripts/generate_project_template_seed_pack.py; PM-03/PM-11, F-PROJ-009.",
             "-- Exact accepted snapshot; six unaccepted DRAFT scenarios. No project/history writes.",
             "-- Preserve historical Flyway files. Retire only exact old seed identities in tenant 1.",
             "DROP PROCEDURE IF EXISTS seed_project_templates_20260921;", "DELIMITER $$",
             "CREATE PROCEDURE seed_project_templates_20260921()", "BEGIN",
             "DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;"]
    for i, (table, entries) in enumerate(data.items()):
        temp = "tmp_project_seed_" + str(i)
        keys = list(entries[0])
        assert all(set(e) == set(keys) for e in entries)
        cols = ",".join("`" + k + "`" for k in keys)
        lines += [f"DROP TEMPORARY TABLE IF EXISTS {temp};", f"CREATE TEMPORARY TABLE {temp} LIKE `{table}`;",
                  f"INSERT INTO {temp} ({cols}) VALUES " + ",\n".join(
                      "(" + ",".join(literal(e[k]) for k in keys) + ")" for e in entries) + ";"]
    lines.append("START TRANSACTION;")
    for i, (table, entries) in enumerate(data.items()):
        temp = "tmp_project_seed_" + str(i)
        identity = " OR (" + " AND ".join(f"t.`{k}` <=> s.`{k}`" for k in natural[table]) + ")"
        equal = " AND ".join(f"BINARY t.`{k}` <=> BINARY s.`{k}`" for k in entries[0])
        lines += [f"IF EXISTS (SELECT 1 FROM `{table}` t JOIN {temp} s ON (t.id=s.id{identity}) WHERE NOT ({equal})) THEN",
                  f"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Project template seed collision: {table}';", "END IF;"]
    for i, (table, entries) in enumerate(data.items()):
        cols = ",".join("`" + k + "`" for k in entries[0])
        selected = ",".join("s.`" + k + "`" for k in entries[0])
        lines += [f"INSERT INTO `{table}` ({cols}) SELECT {selected} FROM tmp_project_seed_{i} s",
                  f"WHERE NOT EXISTS (SELECT 1 FROM `{table}` t WHERE t.id=s.id);"]
    allow = " OR\n".join(f"(id={i} AND BINARY code={literal(c)} AND BINARY creator={literal(a)})" for i, c, a in OLD)
    lines += [f"UPDATE proj_project_template SET status='RETIRED', version=version+1, updater='{ACTOR}'",
              "WHERE tenant_id=1 AND deleted=b'0' AND system_reserved=b'0' AND status IN ('ACTIVE','DRAFT') AND (" + allow + ");",
              "COMMIT;"]
    lines += [f"DROP TEMPORARY TABLE tmp_project_seed_{i};" for i in range(len(data))]
    lines += ["END$$", "DELIMITER ;", "CALL seed_project_templates_20260921();", "DROP PROCEDURE seed_project_templates_20260921;", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    asset = json.loads(ASSET.read_text(encoding="utf8"))
    scenarios = derive(asset)
    outputs = {OUTPUT: render(asset, scenarios),
               SCENARIO_OUTPUT: json.dumps(scenarios, ensure_ascii=False, indent=2) + "\n"}
    for path, value in outputs.items():
        if args.check:
            if not path.exists() or path.read_text(encoding="utf8") != value:
                raise SystemExit(f"stale generated asset: {path}")
        else:
            path.write_text(value, encoding="utf8", newline="\n")
    print("Project template seed pack: consistent" if args.check else "Project template seed pack: generated")


if __name__ == "__main__":
    main()
