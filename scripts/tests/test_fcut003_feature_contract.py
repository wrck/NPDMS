import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md"
CONTRACT = ROOT / "specs/features/F-CUT-003-physical-contract.json"
AUDIT = ROOT / "specs/features/F-CUT-003-legacy-reuse-audit.md"
INDEX = ROOT / "specs/features/README.md"


class Fcut003FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        cls.audit = AUDIT.read_text(encoding="utf-8")
        cls.index = INDEX.read_text(encoding="utf-8")

    def test_candidate_scope_is_full_v1_without_premature_gate(self) -> None:
        self.assertIn("CUT-03@V1=FULL", self.spec)
        self.assertIn("CANDIDATE / NOT_READY", self.spec)
        self.assertEqual("CANDIDATE_NOT_READY", self.contract["status"])
        self.assertEqual(["CUT-03@V1=FULL"], self.contract["requirementSlices"])

    def test_only_three_sds_checklist_tables_are_business_truth(self) -> None:
        self.assertEqual(
            {
                "cut_cutover_checklist",
                "cut_cutover_checklist_item",
                "cut_cutover_checklist_item_result",
            },
            {table["name"] for table in self.contract["tables"]},
        )

    def test_manual_positive_path_does_not_fake_external_success(self) -> None:
        self.assertIn("人工证据降级", self.spec)
        self.assertIn("原自动失败事实不可覆盖或改写为成功", self.spec)
        self.assertEqual(
            "technical result reference only; never means item passed",
            self.contract["resultSelection"]["collectionMeaning"],
        )

    def test_v2_and_cross_context_providers_stay_out_of_scope(self) -> None:
        self.assertIn("V2清单导出", self.spec)
        self.assertIn("不实现Provider", self.spec)
        self.assertIn("V2_EXPORT", self.contract["outOfScope"])
        self.assertIn("DAC_PROVIDER", self.contract["outOfScope"])

    def test_legacy_risk_stack_cannot_become_new_truth(self) -> None:
        self.assertIn("DO_NOT_REUSE / PRESERVE_EXISTING", self.audit)
        self.assertEqual(
            "DO_NOT_REUSE_AS_CHECKLIST_OR_RESULT_PRESERVE_EXISTING",
            self.contract["legacyDisposition"]["pms_cut_risk"],
        )

    def test_feature_index_projects_candidate_state(self) -> None:
        self.assertIn("[F-CUT-003]", self.index)
        self.assertIn("CUT-03（V1，FULL） | CANDIDATE | NOT_READY | NOT_STARTED", self.index)


if __name__ == "__main__":
    unittest.main()
