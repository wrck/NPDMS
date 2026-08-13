from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPT_DIR))
MODULE_PATH = SCRIPT_DIR / "generate_phase3_evidence_packets.py"
SPEC = importlib.util.spec_from_file_location("generate_phase3_evidence_packets", MODULE_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(GENERATOR)


class Phase3EvidencePacketTest(unittest.TestCase):
    def test_packet_coverage_and_decisions(self) -> None:
        packets = GENERATOR.build_packets()
        self.assertEqual({"P3-E01", "P3-E02", "P3-E03", "P3-E04", "P3-E05", "P3-E06", "P3-E07", "P3-E09"}, set(packets))
        self.assertEqual("B", packets["P3-E07"]["confirmedFacts"]["directionDecision"])
        self.assertEqual(GENERATOR.PERMANENT_AUDIT_POLICY, packets["P3-E05"]["confirmedFacts"]["retentionPolicy"])
        self.assertEqual(GENERATOR.TRACE_SAMPLING_POLICY, packets["P3-E05"]["confirmedFacts"]["samplingPolicy"])
        self.assertEqual(GENERATOR.BACKUP_RETENTION_POLICY, packets["P3-E03"]["confirmedFacts"]["retention"])
        self.assertEqual(GENERATOR.RECOVERY_TOPOLOGY, packets["P3-E03"]["confirmedFacts"]["recoveryTopology"])
        self.assertEqual(GENERATOR.EXPORT_AUTHORIZATION_POLICY, packets["P3-E05"]["confirmedFacts"]["exportAuthorizationPolicy"])
        self.assertIn(GENERATOR.PERMANENT_AUDIT_REF, packets["P3-E05"]["evidenceRefs"])
        self.assertIn(GENERATOR.TRACE_SAMPLING_REF, packets["P3-E05"]["evidenceRefs"])
        self.assertIn(GENERATOR.PROJECT_CODE_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertIn(GENERATOR.MARKET_RELATION_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertIn(GENERATOR.CORE_MIGRATION_SCHEMA_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertEqual("PASS", packets["P3-E09"]["confirmedFacts"]["isolatedMysqlExecution"]["status"])
        self.assertIn("ddl-mysql84-execution-evidence.json", packets["P3-E09"]["evidenceRefs"][-1])
        for identifier, packet in packets.items():
            self.assertEqual("DRAFT", packet["status"])
            self.assertEqual("ACCEPTED", packet["confirmedFacts"]["directionStatus"])
            self.assertEqual(GENERATOR.REQUIRED_FACTS[identifier], set(packet["confirmedFacts"]) & GENERATOR.REQUIRED_FACTS[identifier])

    def test_packets_do_not_contain_secret_value_fields(self) -> None:
        for packet in GENERATOR.build_packets().values():
            self.assertNotIn("password", packet["confirmedFacts"])
            self.assertNotIn("secretValue", packet["confirmedFacts"])
            self.assertNotIn("connectionString", packet["confirmedFacts"])


if __name__ == "__main__":
    unittest.main()
