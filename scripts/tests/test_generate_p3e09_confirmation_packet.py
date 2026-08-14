from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "generate_p3e09_confirmation_packet.py"
SPEC = importlib.util.spec_from_file_location("generate_p3e09_confirmation_packet", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class P3E09ConfirmationPacketTest(unittest.TestCase):
    def test_packet_exactly_covers_current_deferred_items(self) -> None:
        packet = MODULE.build(ROOT)
        item_ids = {
            item["itemId"]
            for group in packet["groups"]
            for item in group["items"]
        }
        self.assertEqual(692, packet["deferredItemCount"])
        self.assertEqual(692, packet["coveredDeferredItemCount"])
        self.assertEqual(695, len(item_ids))
        self.assertEqual(3, packet["reconfirmedExistingDecisionItemCount"])

    def test_packet_has_expected_review_groups(self) -> None:
        packet = MODULE.build(ROOT)
        counts = {group["code"]: group["itemCount"] for group in packet["groups"]}
        self.assertEqual({
            "Q07": 257, "Q08": 122, "V1.7": 257,
            "Q09": 50, "Q10": 14, "Q11": 13,
            "Q12": 16, "Q13": 2, "Q14": 13,
        }, counts)
        self.assertTrue(all(group["recommendedDecision"] == "A" for group in packet["groups"]))
        self.assertEqual("REQUIREMENT_OWNER_ACCEPTED", packet["status"])
        self.assertEqual("ADR-0028", packet["confirmation"]["decisionRef"])
        self.assertEqual("REVIEW_PENDING", packet["confirmation"]["reviewStatus"])
        self.assertIsNone(packet["confirmation"]["approvedDdlSha256"])


if __name__ == "__main__":
    unittest.main()
