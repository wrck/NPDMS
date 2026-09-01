import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md"
TASK = ROOT / "tasks/features/F-INS-001.md"
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

    def test_locked_input_is_current_ancestor(self):
        subprocess.run(
            ["git", "merge-base", "--is-ancestor", LOCKED_INPUT, "HEAD"],
            cwd=ROOT,
            check=True)
        committed = subprocess.run(
            ["git", "diff", "--name-only", f"{LOCKED_INPUT}..HEAD", "--", *LOCKED_FILES],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True).stdout.strip()
        working_tree = subprocess.run(
            ["git", "status", "--porcelain", "--", *LOCKED_FILES],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True).stdout.strip()
        self.assertEqual("", committed)
        self.assertEqual("", working_tree)

    def test_scope_excludes_runtime_and_external_connectors(self):
        plan = PLAN.read_text(encoding="utf-8-sig")
        self.assertRegex(plan, re.compile(r"不实现INS-01.*INS-02.*INT-12", re.DOTALL))
        self.assertIn("不新增产品类型表或从`ast_*`表直读", plan)
        self.assertIn("旧`pms_srv_rule`", plan)


if __name__ == "__main__":
    unittest.main()
