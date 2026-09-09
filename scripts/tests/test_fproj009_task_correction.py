"""PM-03 / PM-11: V209 structural/transaction-model tests; never connects to MySQL."""
from copy import deepcopy
import hashlib
from pathlib import Path
import re
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import revise_fproj009_sample_tasks as correction

seed = correction.seed


class BusinessTaskCorrectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before, cls.after, cls.audit = correction.build_correction()
        cls.sql = correction.render_sql()
        cls.definitions = {r["id"]: r for r in cls.after[seed.DEFINITION]}

    def test_actual_v208_rows_are_the_precise_before_snapshot(self):
        self.assertEqual(seed.OUTPUT.read_text(encoding="utf-8"), seed.render_sql(self.before))
        with patch.object(seed.OUTPUT.__class__, "read_text", return_value="changed V208"):
            with self.assertRaisesRegex(ValueError, "historical generator"):
                correction.original_rows()
        self.assertIn(hashlib.sha256(seed.OUTPUT.read_bytes()).hexdigest(), self.sql)

    def test_task_counts_follow_scenario_obligations_not_target_quantity(self):
        self.assertEqual({"DS_ENG": 14, "DS_GEN": 13, "CH_ENG": 13,
                          "CH_DIR": 12, "CH_SUP": 11, "PRE": 2}, correction.task_counts(self.after))
        self.assertEqual(115, len(self.before[seed.TASK]))
        self.assertEqual(65, len(self.after[seed.TASK]))

    def test_s0_only_stage_no_tasks_documents_milestones_or_gates(self):
        for table in (seed.TASK, seed.DELIVERABLE, seed.MILESTONE, seed.GATE):
            self.assertFalse(any(row["stage_code"] == "S0" for row in self.after[table]), table)
        self.assertEqual(6, sum(row["stage_code"] == "S0" for row in self.after[seed.STAGE]))
        self.assertFalse(any("_S0_" in row["gate_code"] for row in self.after[seed.GATE_REF]))

    def test_no_auxiliary_v2_or_cutover_result_check_tasks(self):
        forbidden = {"ASSIGN", "TEAM", "PREP", "PRE01", "PRE05", "PLN04", "REVIEW", "EXE06", "ACC04", "CLO01", "ACC06"}
        for row in self.after[seed.TASK]:
            self.assertNotIn(row["task_code"].rsplit("_", 1)[1], forbidden)
            self.assertIsNone(row["parent_task_code"])
            self.assertIsNone(row["parent_task_definition_key"])
            self.assertFalse(any(term in row["name"] for term in ("上传", "采集", "核对", "归档", "审批", "录入", "服务交接")))

    def test_plan_scheme_and_closure_functions_belong_to_single_tasks(self):
        for stage, expected in (("S2", "施工计划制定"), ("S3", "实施方案编审"), ("S6", "项目闭环办理")):
            rows = [r for r in self.after[seed.TASK] if r["stage_code"] == stage]
            self.assertEqual(5, len(rows))
            self.assertEqual({expected}, {r["name"] for r in rows})
            self.assertEqual(5, len({r["template_revision_id"] for r in rows}))
            terms = {"S2": ["推算", "调整", "提交", "审批"], "S3": ["编辑", "导入", "审核", "版本"],
                     "S6": ["检查", "申请", "审批"]}[stage]
            for row in rows:
                for term in terms:
                    self.assertIn(term, row["description"])

    def test_real_implementation_training_satisfaction_and_acceptance(self):
        for template in self.after[seed.TEMPLATE]:
            scenario = template["code"].removeprefix(seed.PREFIX)
            rows = [r for r in self.after[seed.TASK] if r["task_code"].startswith(template["code"] + "_")]
            names = {r["name"] for r in rows if r["stage_code"] == "S4"}
            expected = {"配置调试", "业务联调"}
            if scenario == "CH_SUP":
                expected.add("现场督导")
            elif scenario != "PRE":
                expected.update(["到货验收", "硬件安装"])
            self.assertEqual(expected, names)
            s5 = {r["name"] for r in rows if r["stage_code"] == "S5"}
            expected_s5 = set() if scenario == "PRE" else {"现场培训", "满意度调查", "项目终验"}
            if scenario.startswith("DS_"):
                expected_s5.add("项目初验")
            self.assertEqual(expected_s5, s5)
        kickoff = [r for r in self.after[seed.TASK] if r["name"] == "工程启动会"]
        self.assertEqual(2, len(kickoff))
        self.assertTrue(all("可选" in r["description"] for r in kickoff))
        self.assertFalse(any(r["ref_code"].endswith("_KICKOFF") for r in self.after[seed.GATE_REF]))

    def test_no_duplicate_or_mandatory_document_bundles(self):
        self.assertEqual(11, len(self.after[seed.DELIVERABLE]))
        self.assertEqual({"施工计划", "批准方案", "现场服务单"}, {r["name"] for r in self.after[seed.DELIVERABLE]})
        self.assertEqual(11, len({r["task_code"] for r in self.after[seed.DELIVERABLE]}))
        for row in self.after[seed.DELIVERABLE]:
            payload = self.definitions[row["definition_revision_id"]]["payload"]
            self.assertFalse(row["required"])
            self.assertFalse(payload["required"])
            self.assertEqual(0, payload["minimumQuantity"])
            self.assertIsNotNone(row["task_code"])
            self.assertEqual(row["task_code"], payload["confirmationRule"]["parameters"]["refCode"])
        for row in self.after[seed.TASK]:
            self.assertIn("业务目标", row["description"])
            self.assertIn("办理功能", row["description"])
            self.assertIn("优先业务结果，无重复上传", row["description"])
            self.assertIn("办理界面待绑定", row["description"])
            self.assertLessEqual(len(row["description"]), 500)
            self.assertIsNone(row["target_context_code"])
            self.assertIsNone(row["target_object_key"])

    def test_gate_roots_match_actual_business_results_and_entry_state(self):
        task_codes = {r["task_code"] for r in self.after[seed.TASK]}
        self.assertFalse(self.after[seed.MILESTONE])
        for gate in self.after[seed.GATE]:
            definition = self.definitions[gate["definition_revision_id"]]
            actual = [{"refType": r["ref_type"], "refCode": r["ref_code"]} for r in self.after[seed.GATE_REF]
                      if r["gate_code"] == gate["gate_code"]]
            order = lambda r: (r["refType"], r["refCode"])
            self.assertEqual(sorted(actual, key=order), sorted(definition["payload"]["references"], key=order))
            self.assertTrue(actual)
            if gate["gate_type"] == "EXIT":
                self.assertEqual(2, definition["revision_no"])
                for ref in actual:
                    self.assertEqual("TASK", ref["refType"])
                    self.assertIn(ref["refCode"], task_codes)
            else:
                self.assertEqual(1, definition["revision_no"])
                self.assertEqual({"STATE"}, {ref["refType"] for ref in actual})

    def test_published_revision_one_and_its_references_are_immutable(self):
        for table in (seed.DEFINITION, seed.REFERENCE):
            current = {r["id"]: r for r in self.after[table]}
            for old in self.before[table]:
                self.assertEqual(old, current[old["id"]])
            self.assertNotRegex(self.sql, rf"UPDATE `{table}`|DELETE t FROM `{table}`")
        original_ids = {r["id"] for r in self.before[seed.DEFINITION]}
        new_definitions = [r for r in self.after[seed.DEFINITION] if r["id"] not in original_ids]
        self.assertEqual(107, len(new_definitions))
        for row in new_definitions:
            self.assertEqual(2, row["revision_no"])
            self.assertEqual("PUBLISHED", row["revision_state"])
            self.assertEqual(correction.SOURCE, row["creator"])
            self.assertIn(row["definition_kind"], {"TASK", "DELIVERABLE", "GATE"})
        for task in self.after[seed.TASK]:
            self.assertEqual(task["name"], self.definitions[task["definition_revision_id"]]["payload"]["name"])
            self.assertEqual(2, task["definition_version"])

    def test_template_draft_stage_and_relation_identities_preserved(self):
        for table in (seed.TEMPLATE, seed.REVISION, seed.STAGE):
            self.assertEqual([r["id"] for r in self.before[table]], [r["id"] for r in self.after[table]])
        self.assertEqual(self.before[seed.TRANSITION], self.after[seed.TRANSITION])
        for row in self.after[seed.REVISION]:
            self.assertEqual("DRAFT", row["status"])
            self.assertEqual(0, row["revision_no"])
            self.assertIsNone(row["definition_snapshot"])
        for old, new in zip(self.before[seed.STAGE], self.after[seed.STAGE]):
            for key in ("template_revision_id", "stage_code", "definition_revision_id", "start_node", "terminal_node"):
                self.assertEqual(old[key], new[key])

    def test_entire_transaction_rejects_saved_edited_deleted_extra_or_published_rows(self):
        mutations = [
            (seed.TEMPLATE, "updater", "user123"), (seed.TEMPLATE, "version", 1),
            (seed.REVISION, "validation_summary", "user saved"), (seed.REVISION, "status", "PUBLISHED"),
            (seed.TASK, "name", "user custom work"), (seed.TASK, "deleted", True),
            (seed.STAGE, "update_time", "2026-09-09 01:00:00"),
            (seed.DELIVERABLE, "required", False), (seed.GATE_REF, "ref_code", "S0_COMPLETED"),
            (seed.DEFINITION, "payload", {"changed": True}), (seed.REFERENCE, "target_revision_id", 999),
        ]
        for table, key, value in mutations:
            with self.subTest(table=table, key=key):
                current = deepcopy(self.before)
                current[table][0][key] = value
                snapshot = deepcopy(current)
                with self.assertRaisesRegex(ValueError, "snapshot"):
                    correction.simulate(current)
                self.assertEqual(snapshot, current)
        for table in self.before:
            with self.subTest(extra_table=table):
                current = deepcopy(self.before)
                current[table].append({**current[table][0], "id": 888888})
                with self.assertRaises(ValueError):
                    correction.simulate(current)
                current = deepcopy(self.before)
                current[table].pop()
                with self.assertRaises(ValueError):
                    correction.simulate(current)

    def test_runtime_references_and_mid_transaction_failure_do_not_mutate_inputs(self):
        current = deepcopy(self.before)
        for kwargs in ({"runtime_references": True}, {"fail_after_write": True}):
            with self.assertRaises(ValueError):
                correction.simulate(current, **kwargs)
            self.assertEqual(self.before, current)
        for table, where in correction.runtime_guards(self.before).items():
            self.assertIn(f"FROM `{table}` t WHERE {where}", self.sql)
            self.assertIn(f"FP209 runtime reference: {table}", self.sql)
        self.assertIn("proj_project_task_execution_contract", self.sql)
        self.assertIn("proj_project_template_match_history", self.sql)
        self.assertIn("acc_project_deliverable", self.sql)

    def test_second_execution_is_exactly_unchanged_and_drift_rejected(self):
        after, audit = correction.simulate(self.before)
        replay, replay_audit = correction.simulate(after, audit)
        self.assertEqual(after, replay)
        self.assertEqual(audit, replay_audit)
        self.assertEqual(1, len(audit))
        after[seed.TASK][0]["name"] = "user edit after correction"
        with self.assertRaises(ValueError):
            correction.simulate(after, audit)
        with self.assertRaises(ValueError):
            correction.simulate(self.after, [{**self.audit, "actor_id": 123}])
        with self.assertRaises(ValueError):
            correction.simulate(self.after, [])

    def test_preflight_is_strict_locked_authorized_and_before_all_dml(self):
        preflight, writes = self.sql.split("-- ALL_PREFLIGHT_COMPLETE:")
        self.assertNotRegex(preflight, r"(?:INSERT INTO|UPDATE|DELETE t FROM) `(?:proj_|plt_operation_audit)")
        self.assertIn("用户明确授权20260909", preflight)
        self.assertIn("BINARY t.`updater` <=> BINARY s.`updater`", preflight)
        self.assertIn("t.`version` <=> s.`version`", preflight)
        self.assertIn("t.`update_time`", preflight)
        self.assertIn("SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE", preflight)
        self.assertIn("ORDER BY t.`id` FOR UPDATE", preflight)
        self.assertIn("ROLLBACK;", preflight)
        self.assertIn("RESIGNAL;", preflight)
        self.assertIn("IF NOT v_done THEN", writes)
        self.assertIn("r.`revision_no` = 0", writes)
        self.assertIn("BINARY r.`status` = BINARY 'DRAFT'", writes)
        self.assertIn("BINARY t.`creator` = BINARY 'fproj009-samples-20260909'", writes)
        self.assertIn("ROW_COUNT()", writes)
        self.assertNotRegex(self.sql, r"CREATE TABLE|ALTER TABLE|TRUNCATE|INSERT IGNORE|ON DUPLICATE KEY")
        self.assertEqual(0, self.audit["actor_id"])
        self.assertEqual("FLYWAY", self.audit["detail_snapshot"]["executor"])
        self.assertEqual(correction.SOURCE, self.audit["creator"])

    def test_session_isolation_handles_snapshot_transaction_before_atomic_correction(self):
        # Regression for MySQL 1568 / SQLSTATE 25001 with Flyway autocommit off.
        # Structural evidence only; this test does not execute a MySQL transaction.
        self.assertNotRegex(self.sql, r"\bSET TRANSACTION ISOLATION LEVEL\b")
        self.assertEqual(1, self.sql.count("SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;"))
        setup, correction_sql = self.sql.split("    START TRANSACTION;", 1)
        self.assertRegex(setup, r"SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;\s*$")
        self.assertLess(setup.rfind("INSERT INTO `tmp_fp209_"),
                        setup.index("SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;"))
        self.assertNotRegex(setup, r"(?:INSERT INTO|UPDATE|DELETE t FROM) `(?:proj_|plt_operation_audit)")
        self.assertLess(correction_sql.index("FOR UPDATE"), correction_sql.index("-- ALL_PREFLIGHT_COMPLETE:"))
        self.assertLess(correction_sql.index("-- ALL_PREFLIGHT_COMPLETE:"), correction_sql.index("INSERT INTO `proj_"))
        self.assertEqual(1, correction_sql.count("    COMMIT;"))
        self.assertIn("ROLLBACK;", setup)
        self.assertIn("RESIGNAL;", setup)

    def test_explicit_update_time_prevents_mysql_auto_timestamp_replay_drift(self):
        updates = re.findall(r"UPDATE `proj_[^;]+;", self.sql)
        self.assertEqual(6, len(updates))
        for statement in updates:
            set_clause = statement.split("SET ", 1)[1].split("WHERE ", 1)[0]
            self.assertIn("t.`update_time` = s.`update_time`", set_clause)

    def test_generated_sql_is_current_and_has_exact_delta_manifest(self):
        self.assertEqual(self.sql, correction.OUTPUT.read_text(encoding="utf-8"))
        delta = correction.changes(self.before, self.after)
        self.assertEqual(50, len(delta[seed.TASK]["delete"]))
        self.assertEqual(65, len(delta[seed.TASK]["update"]))
        self.assertEqual(141, len(delta[seed.DELIVERABLE]["delete"]))
        self.assertEqual(37, len(delta[seed.MILESTONE]["delete"]))
        self.assertEqual(6, len(delta[seed.GATE]["delete"]))
        self.assertEqual(delta, self.audit["detail_snapshot"]["changes"])
        self.assertEqual(correction.render_sql(), self.sql)


if __name__ == "__main__":
    unittest.main()
