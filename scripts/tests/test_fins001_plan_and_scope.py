import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md"
TASK = ROOT / "tasks/features/F-INS-001.md"
FEATURE = ROOT / "specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md"
Q005_AMENDMENT = ROOT / "docs/baseline/prd-v1.8-amendment-012-inspection-security-review-last-fact.md"
Q006_AMENDMENT = ROOT / "docs/baseline/prd-v1.8-amendment-013-inspection-security-review-boolean-permission.md"
OPEN_QUESTIONS = ROOT / "docs/decisions/open-questions.md"
LOCKED_INPUT = "27b5b4b3"
REQUIREMENT_IDS = {"INS-03", "INS-09", "NFR-02"}
LOCKED_FILES = (
    "docs/baseline/prd-v1.8.md",
    "docs/design/04-module-design.md",
    "docs/design/07-authorization-design.md",
    "docs/design/08-data-model.md",
    "docs/design/09-database-design.md",
    "docs/design/10-api-design.md",
    "docs/design/14-security-design.md",
    "docs/design/20-test-design.md",
    "specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md",
    "specs/features/F-INS-001-legacy-reuse-audit.md",
)


class FIns001PlanAndScopeTest(unittest.TestCase):

    def test_has_one_current_plan_and_locked_inputs(self):
        self.assertTrue(PLAN.is_file())
        current_plans = []
        temporary_copies = []
        for path in ROOT.rglob("*.md"):
            if any(part in {".git", "target", "node_modules", "archive"} for part in path.parts):
                continue
            text = path.read_text(encoding="utf-8-sig")
            if re.search(r"^#\s+F-INS-001\b.*Implementation Plan\s*$", text, re.MULTILINE):
                current_plans.append(path)
            normalized_name = path.name.lower()
            if "f-ins-001" in normalized_name and re.search(
                    r"(?:draft|review|final\d+|copy|tmp)", normalized_name):
                temporary_copies.append(path)
        self.assertEqual([PLAN], sorted(current_plans))
        self.assertEqual([], temporary_copies)
        plan = PLAN.read_text(encoding="utf-8-sig")
        task = TASK.read_text(encoding="utf-8-sig")
        self.assertIn(f"锁定实施输入提交：`{LOCKED_INPUT}`", plan)
        self.assertIn(f"锁定实施输入提交：`{LOCKED_INPUT}`", task)
        self.assertIn("NPDMS-FINS001-TECHPLAN-20260830-01", task)
        self.assertTrue(REQUIREMENT_IDS.issubset(set(re.findall(r"(?:INS|NFR)-\d+", plan))))

    def test_locked_input_is_preserved_as_selective_integration_source(self):
        subprocess.run(
            ["git", "cat-file", "-e", f"{LOCKED_INPUT}^{{commit}}"],
            cwd=ROOT,
            check=True)
        self.assertTrue(all((ROOT / path).is_file() for path in LOCKED_FILES))
        task = TASK.read_text(encoding="utf-8-sig")
        self.assertIn("来源分支PRD修订009～012统一收口为master修订011", task)
        self.assertIn("来源Flyway V148～V150重编号为master V173～V175", task)

    def test_scope_excludes_runtime_and_external_connectors(self):
        plan = PLAN.read_text(encoding="utf-8-sig")
        self.assertRegex(plan, re.compile(r"不实现INS-01.*INS-02.*INT-12", re.DOTALL))
        self.assertIn("不新增产品类型表或从`ast_*`表直读", plan)
        self.assertIn("旧`pms_srv_rule`", plan)

    def test_q005_and_q006_are_closed_with_distinct_decisions(self):
        q005_amendment = Q005_AMENDMENT.read_text(encoding="utf-8-sig")
        q006_amendment = Q006_AMENDMENT.read_text(encoding="utf-8-sig")
        feature = FEATURE.read_text(encoding="utf-8-sig")
        task = TASK.read_text(encoding="utf-8-sig")
        plan = PLAN.read_text(encoding="utf-8-sig")
        questions = OPEN_QUESTIONS.read_text(encoding="utf-8-sig")

        for text in (q005_amendment, feature, task):
            self.assertIn("reviewed_at DESC, id DESC", text)
        self.assertIn("Q-FINS001-005`方案A", q005_amendment)
        self.assertIn("不关闭`Q-FINS001-006`", q005_amendment)
        for text in (q006_amendment, feature, task, plan):
            self.assertIn("PermissionApi.hasAnyPermissions", text)
            self.assertIn("超级管理员", text)
        self.assertIn("不新增System API", q006_amendment)
        self.assertIn("authorizationSourceId`保持为空", q006_amendment)
        self.assertIn("当前无F-INS-001规格阻断", feature)
        self.assertIn("无规格阻断", task)
        self.assertIn("InspectionRuleExplicitAuthorizationApi", plan)
        self.assertIn("删除或收口", plan)
        q005 = questions.split("### Q-FINS001-005", 1)[1].split("### Q-FINS001-006", 1)[0]
        q006 = questions.split("### Q-FINS001-006", 1)[1].split("### ", 1)[0]
        self.assertIn("Status: RESOLVED", q005)
        self.assertIn("Status: RESOLVED", q006)
        self.assertIn("PermissionApi.hasAnyPermissions", q006)
        self.assertIn("authorizationSourceId`为空", q006)


if __name__ == "__main__":
    unittest.main()
