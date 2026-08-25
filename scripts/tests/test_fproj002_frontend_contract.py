import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "yudao-ui" / "yudao-ui-admin-vue3" / "src"
DETAIL = UI / "views" / "pms" / "project" / "project-master-detail"


class FProj002FrontendContractTest(unittest.TestCase):

    def test_v18_api_contract_retires_legacy_tree_and_weight_routes(self):
        projects = (UI / "api" / "pms" / "project" / "projects" / "index.ts").read_text(encoding="utf-8")
        splits = (UI / "api" / "pms" / "project" / "project-splits" / "index.ts").read_text(encoding="utf-8")
        self.assertIn("/${id}/tree", projects)
        self.assertIn("/progress-policies", projects)
        self.assertIn("/pms/closure-gates/", projects)
        self.assertIn("/pms/project-split-requests", splits)
        self.assertIn("'Idempotency-Key'", projects + splits)
        self.assertIn("'If-Match'", projects + splits)
        for retired in ("/pms/project-tree", "/child-weights", "/${id}/children", "/${id}/descendants"):
            self.assertNotIn(retired, projects + splits)

    def test_detail_is_split_into_four_responsive_components(self):
        page = (DETAIL / "index.vue").read_text(encoding="utf-8")
        component_names = (
            "ProjectSplitWizard", "ProjectTreePanel", "ProjectProgressPanel",
            "ProjectClosureGuardPanel"
        )
        for name in component_names:
            self.assertIn(name, page)
            self.assertTrue((DETAIL / "components" / f"{name}.vue").is_file())
        combined = page + "".join(
            (DETAIL / "components" / f"{name}.vue").read_text(encoding="utf-8")
            for name in component_names
        )
        for breakpoint in ("1199px", "991px", "767px"):
            self.assertIn(breakpoint, combined)
        self.assertIn("el-tree-v2", combined)
        self.assertIn("el-skeleton", combined)
        self.assertIn("var(--el-", combined)
        self.assertNotRegex(combined, r"#[0-9a-fA-F]{6}\b|rgba\(")

    def test_split_draft_can_restore_and_guard_does_not_submit_closure(self):
        split = (DETAIL / "components" / "ProjectSplitWizard.vue").read_text(encoding="utf-8")
        guard = (DETAIL / "components" / "ProjectClosureGuardPanel.vue").read_text(encoding="utf-8")
        self.assertIn("localStorage.setItem", split)
        self.assertIn("getDraft(requestId)", split)
        self.assertIn("previewDraft", split)
        self.assertIn("applyDraft", split)
        self.assertIn("getClosureGuard", guard)
        self.assertNotIn("submitProjectClosure", guard)


if __name__ == "__main__":
    unittest.main()
