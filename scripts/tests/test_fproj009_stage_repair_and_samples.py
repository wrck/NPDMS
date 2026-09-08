"""PM-03 / PM-11 sample structure tests (no MySQL/Flyway or runtime acceptance).

Sample-only section owned by the sample subtask; stage-repair tests are added
serially by the coordinator after this file is handed off.
"""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location(
    "fproj009_sample_generator", ROOT / "scripts/generate_fproj009_sample_templates.py")
samples = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(samples)


class ActiveProjectStageRepairTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (ROOT / 'sql/migrations/V207__fproj009_active_project_stage_backfill.sql').read_text(encoding='utf-8')

    def test_repair_is_limited_to_exact_project_and_template_pairs(self):
        for fragment in ('p.id BETWEEN 920001 AND 920006', 'p.lifecycle_template_id=910001',
                         'p.id=992002000000', 'p.lifecycle_template_id=910008',
                         'p.id BETWEEN 992203060001 AND 992203060003',
                         'p.lifecycle_template_id=992203040001', "p.lifecycle_status='ACTIVE'"):
            self.assertIn(fragment, self.sql)
        self.assertNotIn('parent_id', self.sql)
        self.assertNotIn('root_id', self.sql)

    def test_repair_inserts_only_missing_stages_and_append_audit(self):
        self.assertEqual(set(re.findall(r'INSERT INTO (\w+)', self.sql)),
                         {'proj_project_stage', 'plt_operation_audit'})
        self.assertNotRegex(self.sql, r'(?im)^\s*(UPDATE|DELETE|REPLACE|TRUNCATE)\s')
        self.assertIn('NOT EXISTS (SELECT 1 FROM proj_project_stage s', self.sql)
        self.assertIn("CASE WHEN d.stage_code=v_current THEN 'ACTIVE' ELSE 'PENDING' END", self.sql)
        self.assertNotIn("THEN 'DONE'", self.sql)
        self.assertIn('d.id,', self.sql)

    def test_conflict_checks_precede_inserts_and_errors_roll_back(self):
        insert = self.sql.index('INSERT INTO proj_project_stage')
        self.assertLess(self.sql.index('IF v_current_count<>1'), insert)
        self.assertLess(self.sql.index('IF v_conflicts<>0'), insert)
        self.assertIn('START TRANSACTION;', self.sql)
        self.assertIn('ROLLBACK;', self.sql)
        self.assertIn('RESIGNAL;', self.sql)
        self.assertIn('v_inserted<>v_missing', self.sql)
        self.assertIn("'before',v_before,'after',v_after", self.sql)

    def test_stage_variable_uses_actual_stage_column_collation(self):
        self.assertIn('DECLARE v_current VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;', self.sql)


# BEGIN SAMPLE TESTS -- PM-03 / PM-11
class ScenarioTemplateSampleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = samples.build_rows()
        cls.sql = samples.render_sql(cls.rows)
        cls.definitions = {row["id"]: row for row in cls.rows[samples.DEFINITION]}

    def children(self, table, revision):
        return [row for row in self.rows[table] if row["template_revision_id"] == revision]

    def test_six_named_drafts_with_no_publication_snapshot_or_process(self):
        templates = self.rows[samples.TEMPLATE]
        revisions = self.rows[samples.REVISION]
        self.assertEqual(len(templates), 6)
        self.assertEqual(len(revisions), 6)
        self.assertEqual({t["code"] for t in templates}, {
            samples.PREFIX + key for key in ("DS_ENG", "DS_GEN", "CH_ENG", "CH_DIR", "CH_SUP", "PRE")})
        for template, revision in zip(templates, revisions):
            self.assertEqual(template["id"], revision["template_id"])
            self.assertEqual(template["status"], "DRAFT")
            self.assertEqual(revision["status"], "DRAFT")
            self.assertEqual(revision["revision_no"], 0)
            for key in ("published_by", "published_time", "definition_snapshot", "validation_summary",
                        "process_definition_key", "process_definition_version"):
                self.assertIsNone(revision[key])
            self.assertIn("配置结构示例，生产需绑定Owner", template["description"])
            self.assertIn("未开运行、不进入匹配候选", template["description"])
            self.assertIn("不创建实体或完成事实", template["description"])
            self.assertLessEqual(len(template["description"]), 500)

    def test_tenant_source_identity_range_and_code_namespace(self):
        ids = []
        for table, rows in self.rows.items():
            for row in rows:
                ids.append(row["id"])
                self.assertEqual(row["tenant_id"], 1)
                self.assertEqual(row["creator"], "fproj009-samples-20260909")
                self.assertEqual(row["updater"], row["creator"])
                self.assertFalse(row["deleted"])
                self.assertGreaterEqual(row["id"], 993009000000)
                self.assertLessEqual(row["id"], 993009999999)
                for field in ("code", "definition_code", "task_code", "milestone_code", "deliverable_code", "gate_code", "transition_code"):
                    if row.get(field) is not None:
                        self.assertTrue(row[field].startswith("FPROJ009_SAMPLE_"), (table, field))
                        self.assertLessEqual(len(row[field]), 64)
        self.assertEqual(len(ids), len(set(ids)))

    def test_controlled_dimensions_and_partial_priority_metadata(self):
        for row in self.rows[samples.REVISION]:
            self.assertIn(row["signing_method"], {None, "DIRECT_SIGN", "CHANNEL_SIGN"})
            self.assertIn(row["project_category"], {None, "ENGINEERING", "GENERAL"})
            self.assertIn(row["implementation_method"], {None, "DIRECT_SERVICE", "SUPERVISION", "AGENT_SELF_SERVICE"})
            self.assertIsNone(row["major_project_level"])
        self.assertEqual([t["match_priority"] for t in self.rows[samples.TEMPLATE]], [10, 20, 30, 40, 50, 90])
        self.assertIsNone(self.rows[samples.REVISION][0]["implementation_method"])
        self.assertEqual(self.rows[samples.REVISION][1]["implementation_method"], "DIRECT_SERVICE")
        self.assertIn("潜在重叠不构成匹配候选", self.rows[samples.TEMPLATE][0]["description"])

    def test_every_scenario_has_its_exact_reachable_graph(self):
        for index, revision in enumerate(self.rows[samples.REVISION]):
            rid = revision["id"]
            expected = ["S0", "S4"] if index == 5 else [f"S{i}" for i in range(7)]
            stages = self.children(samples.STAGE, rid)
            self.assertEqual([s["stage_code"] for s in stages], expected)
            self.assertEqual([s["stage_code"] for s in stages if s["start_node"]], [expected[0]])
            self.assertEqual([s["stage_code"] for s in stages if s["terminal_node"]], [expected[-1]])
            transitions = self.children(samples.TRANSITION, rid)
            self.assertEqual([(t["from_stage_code"], t["to_stage_code"]) for t in transitions], list(zip(expected, expected[1:])))
            for edge in transitions:
                self.assertEqual(edge["is_default"], 1)
                self.assertGreater(edge["revision_no"], 0)
                self.assertIsNone(edge["condition_rule_revision_id"])
                self.assertNotIn("default_branch", edge)

    def test_presales_only_exe03_exe04_and_explicit_selection(self):
        revision = self.rows[samples.REVISION][-1]
        tasks = self.children(samples.TASK, revision["id"])
        execution = [t for t in tasks if t["stage_code"] == "S4"]
        self.assertEqual({t["task_code"].rsplit("_", 1)[-1] for t in execution}, {"EXE03", "EXE04"})
        for table in (samples.TASK, samples.MILESTONE, samples.DELIVERABLE, samples.GATE):
            self.assertTrue(all(row["stage_code"] in {"S0", "S4"} for row in self.children(table, revision["id"])))
        for key in ("signing_method", "project_category", "implementation_method", "major_project_level"):
            self.assertIsNone(revision[key])
        self.assertIn("显式选择的场景草稿", self.rows[samples.TEMPLATE][-1]["description"])
        self.assertIn("不补S5/S6", self.rows[samples.TEMPLATE][-1]["description"])
        self.assertFalse(any("初验" in t["name"] or "终验" in t["name"] for t in tasks))

    def test_real_business_obligations_and_scenario_differences(self):
        for index, revision in enumerate(self.rows[samples.REVISION][:5]):
            tasks = self.children(samples.TASK, revision["id"])
            names = "|".join(t["name"] for t in tasks)
            documents = "|".join(d["name"] for d in self.children(samples.DELIVERABLE, revision["id"]))
            for expected in ("工期", "工勘", "需求分析", "施工计划", "方案", "配置Log", "业务联调", "培训", "满意度", "终验", "闭环"):
                self.assertIn(expected, names)
            self.assertEqual("初验报告" in documents, index < 2)
            self.assertEqual("工程启动会纪要" in documents, index in {0, 2})
            self.assertEqual("现场验货单" in documents, index in {2, 3})
            self.assertEqual("现场服务单" in documents, index == 4)
            if index < 2:
                self.assertIn("初验后满意度", names)
            else:
                self.assertIn("验收阶段满意度", names)

    def test_all_elements_at_every_stage_and_task_deliverables(self):
        for revision in self.rows[samples.REVISION]:
            rid = revision["id"]
            for stage in self.children(samples.STAGE, rid):
                code = stage["stage_code"]
                for table in (samples.TASK, samples.MILESTONE, samples.DELIVERABLE, samples.GATE):
                    self.assertTrue(any(row["stage_code"] == code for row in self.children(table, rid)))
                documents = [d for d in self.children(samples.DELIVERABLE, rid) if d["stage_code"] == code]
                self.assertTrue(any(d["task_code"] is None for d in documents))
                for task in [t for t in self.children(samples.TASK, rid) if t["stage_code"] == code]:
                    self.assertTrue(any(d["task_code"] == task["task_code"] for d in documents))
            for document in self.children(samples.DELIVERABLE, rid):
                payload = self.definitions[document["definition_revision_id"]]["payload"]
                self.assertEqual(payload["scope"], "TASK" if document["task_code"] else "STAGE")
                self.assertEqual(payload["minimumQuantity"], 1)
                self.assertTrue(payload["required"])
                self.assertEqual(payload["allowedSources"], ["UPLOAD"])
                self.assertEqual(payload["outputType"], "FILE")
                self.assertEqual(payload["confirmationRule"]["predicate"], "TASK")
                self.assertIn(payload["confirmationRule"]["parameters"]["refCode"],
                              {t["task_code"] for t in self.children(samples.TASK, rid)})

    def test_wbs_parent_is_real_same_stage_and_acyclic(self):
        found = False
        for revision in self.rows[samples.REVISION]:
            tasks = {t["task_code"]: t for t in self.children(samples.TASK, revision["id"])}
            for task in tasks.values():
                visited = {task["task_code"]}
                parent = task["parent_task_code"]
                if parent:
                    found = True
                    self.assertEqual(task["parent_task_definition_key"], parent)
                while parent:
                    self.assertNotIn(parent, visited)
                    visited.add(parent)
                    self.assertIn(parent, tasks)
                    self.assertEqual(tasks[parent]["stage_code"], task["stage_code"])
                    parent = tasks[parent]["parent_task_code"]
        self.assertTrue(found)

    def test_exact_definition_slots_and_supported_native_shapes(self):
        self.assertEqual({d["definition_kind"] for d in self.definitions.values()}, {
            "STAGE", "TASK", "DELIVERABLE", "WORK_BINDING", "COMPLETION_RULE", "PERMISSION_POLICY", "GATE", "MILESTONE"})
        for definition in self.definitions.values():
            self.assertEqual(definition["revision_no"], 1)
            self.assertEqual(definition["revision_state"], "PUBLISHED")
            self.assertIsNotNone(definition["published_at"])
            payload = definition["payload"]
            kind = definition["definition_kind"]
            if kind == "WORK_BINDING":
                self.assertEqual(set(payload), {"bindingType", "instanceResolutionStrategy", "contextMapping"})
                self.assertIn(payload["bindingType"], {"TASK_NATIVE", "STAGE_NATIVE"})
                self.assertEqual(payload["instanceResolutionStrategy"], "CREATE_ON_ENTER")
                self.assertEqual(payload["contextMapping"], {})
            elif kind == "COMPLETION_RULE":
                self.assertIn(payload["predicate"], {"TASK_NATIVE_STATUS", "STAGE_NATIVE_STATUS"})
                self.assertEqual(payload["parameters"], {"requiredStatus": "DONE"})
            elif kind == "PERMISSION_POLICY":
                self.assertEqual(payload, {"requiredActions": ["VIEW"]})
            elif kind in {"STAGE", "TASK"}:
                refs = {r["reference_key"]: r["target_revision_id"] for r in self.rows[samples.REFERENCE]
                        if r["owner_revision_id"] == definition["id"]}
                for slot, target_kind in (("workBinding", "WORK_BINDING"), ("permissionPolicy", "PERMISSION_POLICY"), ("completionRule", "COMPLETION_RULE")):
                    self.assertEqual(payload[slot], slot)
                    self.assertEqual(self.definitions[refs[slot]]["definition_kind"], target_kind)
                self.assertEqual(self.definitions[refs["workBinding"]]["payload"]["bindingType"], kind + "_NATIVE")
        for table, kind in ((samples.STAGE, "STAGE"), (samples.TASK, "TASK")):
            for row in self.rows[table]:
                definition = self.definitions[row["definition_revision_id"]]
                self.assertEqual(definition["definition_kind"], kind)
                refs = {r["reference_key"]: r["target_revision_id"] for r in self.rows[samples.REFERENCE]
                        if r["owner_revision_id"] == definition["id"]}
                for slot, column in (("workBinding", "work_binding_revision_id"), ("permissionPolicy", "permission_policy_revision_id"), ("completionRule", "completion_rule_revision_id")):
                    self.assertEqual(row[column], refs[slot])
                if kind == "TASK":
                    self.assertIn("生产需绑定Owner", row["description"])
                    self.assertIsNone(row["target_context_code"])
                    self.assertIsNone(row["approval_definition_key"])
                    self.assertEqual(row["binding_config"], self.definitions[refs["workBinding"]]["payload"])

    def test_gate_rows_equal_payload_and_resolve_only_inside_template(self):
        for revision in self.rows[samples.REVISION]:
            rid = revision["id"]
            targets = {
                "TASK": {t["task_code"] for t in self.children(samples.TASK, rid)},
                "MILESTONE": {m["milestone_code"] for m in self.children(samples.MILESTONE, rid)},
                "DELIVERABLE": {d["deliverable_code"] for d in self.children(samples.DELIVERABLE, rid)},
                "STATE": {s["stage_code"] + "_COMPLETED" for s in self.children(samples.STAGE, rid)},
            }
            for gate in self.children(samples.GATE, rid):
                refs = [r for r in self.children(samples.GATE_REF, rid) if r["gate_code"] == gate["gate_code"]]
                payload = self.definitions[gate["definition_revision_id"]]["payload"]
                self.assertEqual(payload["gateType"], gate["gate_type"])
                self.assertEqual(payload["references"], [{"refType": r["ref_type"], "refCode": r["ref_code"]} for r in refs])
                self.assertTrue(refs)
                for ref in refs:
                    self.assertIn(ref["ref_type"], targets)
                    self.assertIn(ref["ref_code"], targets[ref["ref_type"]])
                    self.assertIsNone(ref["ref_version"])

    def test_no_business_instance_writes_or_destructive_history_changes(self):
        targets = set(re.findall(r"INSERT INTO `([^`]+)`", self.sql))
        self.assertEqual({table for table in targets if not table.startswith("tmp_")}, set(samples.KEYS))
        self.assertNotRegex(self.sql, r"(?im)^\s*(UPDATE|DELETE|REPLACE|TRUNCATE|ALTER)\s")
        self.assertNotIn("ON DUPLICATE KEY UPDATE", self.sql)
        self.assertNotIn("INSERT IGNORE", self.sql)
        self.assertNotIn("definition_snapshot`=", self.sql)
        self.assertNotIn("system_dict", self.sql)
        self.assertNotIn("proj_project_stage`", self.sql)
        self.assertNotIn("proj_project_task`", self.sql)

    def test_sql_full_preflight_before_any_production_insert_and_rollback(self):
        self.assertIn("DECLARE EXIT HANDLER FOR SQLEXCEPTION", self.sql)
        self.assertIn("ROLLBACK;", self.sql)
        self.assertIn("RESIGNAL;", self.sql)
        first_insert = min(self.sql.index(f"INSERT INTO `{table}`") for table in self.rows)
        self.assertLess(self.sql.rindex("SIGNAL SQLSTATE '45000'"), first_insert)
        self.assertLess(self.sql.index("START TRANSACTION;"), first_insert)
        for table in self.rows:
            self.assertIn(f"WHERE NOT EXISTS (SELECT 1 FROM `{table}` t WHERE t.`id` = s.`id`)", self.sql)
        self.assertEqual(self.sql.count("SIGNAL SQLSTATE '45000'"), len(self.rows))
        self.assertIn("t.`is_default` = 1 AND s.`is_default` = 1", self.sql)

    def _conflict_probe(self, table, existing):
        """Execute generated preflight SELECT in SQLite after syntax translation.

        This tests id/key/content rejection predicates, not MySQL types, collation,
        transactions, Flyway or concurrency. Those remain coordinator validation.
        """
        index = list(self.rows).index(table)
        temp = f"tmp_fproj009_sample_{index}"
        pattern = rf"IF EXISTS \((SELECT 1 FROM `{table}` t JOIN `{temp}` s ON .*?)\) THEN"
        query = re.search(pattern, self.sql, re.S).group(1)
        query = query.replace("<=>", "IS").replace("BINARY ", "")
        seed = self.rows[table][0]
        with sqlite3.connect(":memory:") as db:
            for name, row in ((temp, seed), (table, existing)):
                columns = list(seed)
                db.execute(f"CREATE TABLE `{name}` (" + ",".join(f"`{key}`" for key in columns) + ")")
                values = [json.dumps(row[k], sort_keys=True) if isinstance(row[k], (dict, list)) else row[k] for k in columns]
                db.execute(f"INSERT INTO `{name}` VALUES (" + ",".join("?" for _ in columns) + ")", values)
            return db.execute(query).fetchone() is not None

    def test_generated_preflight_accepts_exact_rerun_and_rejects_foreign_ids_codes_or_content(self):
        for table in self.rows:
            seed = self.rows[table][0]
            self.assertFalse(self._conflict_probe(table, copy.deepcopy(seed)), table)
            for field, value in (("creator", "foreign-seed"), ("tenant_id", 2), ("deleted", True), ("id", seed["id"] + 900000)):
                changed = copy.deepcopy(seed)
                changed[field] = value
                self.assertTrue(self._conflict_probe(table, changed), (table, field))
        for table, field, value in ((samples.DEFINITION, "payload", {"unexpected": True}),
                                    (samples.TEMPLATE, "status", "ACTIVE"),
                                    (samples.REVISION, "status", "PUBLISHED"),
                                    (samples.TRANSITION, "to_stage_code", "S6")):
            changed = copy.deepcopy(self.rows[table][0])
            changed[field] = value
            self.assertTrue(self._conflict_probe(table, changed), (table, field))

    def test_generated_output_is_current_deterministic_and_source_scoped(self):
        self.assertEqual(samples.build_rows(), self.rows)
        self.assertEqual(samples.render_sql(), self.sql)
        self.assertEqual(samples.OUTPUT.read_text(encoding="utf-8"), self.sql)
        self.assertIn("tasks/features/F-PROJ-009.md", self.sql)
        self.assertIn("DU master 01300a6e", self.sql)
        self.assertNotIn("已授权隔离测试阶段补全与场景草稿", self.sql)

    def test_sql_literal_escaping_is_not_mode_dependent(self):
        self.assertEqual(samples.sql_value("Owner's example"), "'Owner''s example'")
        with self.assertRaises(ValueError):
            samples.sql_value("unsafe\\path")

# END SAMPLE TESTS -- coordinator may append stage tests below, serially.


if __name__ == "__main__":
    unittest.main()
