from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "sync_phase3_p3e09_requirement_confirmation.py"
SPEC = importlib.util.spec_from_file_location("sync_phase3_p3e09_requirement_confirmation", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class Phase3P3E09RequirementConfirmationSyncTest(unittest.TestCase):
    def test_sync_publishes_model_baseline_and_keeps_migration_blocks(self) -> None:
        payload = json.loads((ROOT / MODULE.REGISTER).read_text(encoding="utf-8"))
        generated = MODULE.load_generator(ROOT).build_packets()["P3-E09"]
        result = MODULE.sync(payload, generated)
        item = next(row for row in result["items"] if row["id"] == "P3-E09")
        facts = item["confirmedFacts"]
        self.assertEqual(0, facts["deferredItemCount"])
        self.assertEqual("MODEL_BASELINE_READY", facts["modelDecisionStatus"])
        self.assertEqual("ACCEPT_CURRENT", facts["driftDecision"])
        self.assertEqual("VERIFIED", item["status"])
        self.assertEqual("INDEPENDENT_REVIEWER", item["reviewOwner"])
        self.assertNotIn("approvedDdlSha256", facts)
        self.assertNotIn("candidateCommit", facts)
        self.assertNotIn("reviewDate", facts)
        self.assertNotIn("reviewRange", facts)
        self.assertEqual(
            generated["confirmedFacts"]["releaseApplicability"],
            facts["releaseApplicability"],
        )
        self.assertEqual(
            generated["confirmedFacts"]["executionWindowPolicy"],
            facts["executionWindowPolicy"],
        )
        self.assertEqual({"HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"}, set(item["blocks"]))
        self.assertEqual("READY_FOR_SDS_BASELINE", result["overallStatus"])


if __name__ == "__main__":
    unittest.main()
