from __future__ import annotations

import importlib.util
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
REPOSITORY_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(SCRIPT_DIR))
MODULE_PATH = SCRIPT_DIR / "generate_phase3_evidence_packets.py"
SPEC = importlib.util.spec_from_file_location("generate_phase3_evidence_packets", MODULE_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(GENERATOR)


class Phase3EvidencePacketTest(unittest.TestCase):
    def test_hash_bound_ddl_disables_checkout_line_ending_conversion(self) -> None:
        relative = "specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql"
        attributes = subprocess.run(
            ["git", "check-attr", "text", "diff", "merge", "--", relative],
            cwd=REPOSITORY_ROOT,
            text=True,
            capture_output=True,
            check=True,
        ).stdout.strip()
        self.assertEqual(
            [
                f"{relative}: text: unset",
                f"{relative}: diff: set",
                f"{relative}: merge: unspecified",
            ],
            attributes.splitlines(),
        )

        ddl_sha256 = hashlib.sha256((REPOSITORY_ROOT / relative).read_bytes()).hexdigest().upper()
        evidence = json.loads(
            (REPOSITORY_ROOT / GENERATOR.DDL_EXECUTION_EVIDENCE).read_text(encoding="utf-8")
        )
        self.assertEqual(evidence["ddlSha256"], ddl_sha256)

    def test_hash_bound_ddl_remains_visible_as_text_diff(self) -> None:
        relative = Path(
            "specs/001-project-delivery-platform/appendices/"
            "project-order-physical-schema.mysql.sql"
        )
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / relative
            target.parent.mkdir(parents=True)
            (root / ".gitattributes").write_text(f"{relative.as_posix()} -text diff\n", encoding="utf-8")
            target.write_bytes(b"SELECT 1;\r\n")
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "add", ".gitattributes", relative.as_posix()], cwd=root, check=True)

            target.write_bytes(b"SELECT 2;\r\n")
            diff = subprocess.run(
                ["git", "diff", "--", relative.as_posix()],
                cwd=root,
                text=True,
                capture_output=True,
                check=True,
            ).stdout

        self.assertIn("-SELECT 1;", diff)
        self.assertIn("+SELECT 2;", diff)

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
        self.assertIn(GENERATOR.MODEL_DECISION_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertIn(GENERATOR.V17_DDL_DELTA_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertEqual("ACCEPTED", packets["P3-E09"]["confirmedFacts"]["v17DeltaStatus"])
        self.assertEqual("MODEL_BASELINE_READY", packets["P3-E09"]["confirmedFacts"]["modelDecisionStatus"])
        self.assertEqual("GO", packets["P3-E09"]["confirmedFacts"]["independentReviewResult"])
        self.assertNotIn("candidateCommit", packets["P3-E09"]["confirmedFacts"])
        self.assertNotIn("reviewDate", packets["P3-E09"]["confirmedFacts"])
        self.assertNotIn("reviewRange", packets["P3-E09"]["confirmedFacts"])
        self.assertEqual("INDEPENDENT_REVIEWER", packets["P3-E09"]["reviewOwner"])
        self.assertNotIn("approvedDdlSha256", packets["P3-E09"]["confirmedFacts"])
        self.assertEqual(
            {"HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"},
            set(packets["P3-E09"]["blocks"]),
        )
        self.assertNotIn("signoffs", packets["P3-E09"])
        self.assertIn("普通功能发布不触发AI-MIG-000", packets["P3-E09"]["usageRestriction"])
        self.assertEqual(
            GENERATOR.AI_MIG_RELEASE_APPLICABILITY,
            packets["P3-E09"]["confirmedFacts"]["releaseApplicability"],
        )
        self.assertEqual(
            GENERATOR.AI_MIG_EXECUTION_WINDOW_POLICY,
            packets["P3-E09"]["confirmedFacts"]["executionWindowPolicy"],
        )
        self.assertEqual(0, packets["P3-E09"]["confirmedFacts"]["deferredItemCount"])
        self.assertEqual("ACCEPTED", packets["P3-E09"]["confirmedFacts"]["requirementOwnerConfirmation"]["status"])
        self.assertEqual(9, packets["P3-E09"]["confirmedFacts"]["requirementOwnerConfirmation"]["groupCount"])
        self.assertIn(GENERATOR.CURRENT_HASH_REQUIREMENT_CONFIRMATION_REF, packets["P3-E09"]["evidenceRefs"])
        self.assertEqual(257, packets["P3-E09"]["confirmedFacts"]["q07Decision"]["technicalConstraintCount"])
        self.assertEqual(122, packets["P3-E09"]["confirmedFacts"]["q08Decision"]["candidateIndexCount"])
        self.assertTrue(packets["P3-E09"]["confirmedFacts"]["q08Decision"]["p3e06PerformanceValidationRequired"])
        self.assertEqual("PASS", packets["P3-E09"]["confirmedFacts"]["isolatedMysqlExecution"]["status"])
        self.assertTrue(any("ddl-mysql84-execution-evidence.json" in ref for ref in packets["P3-E09"]["evidenceRefs"]))
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
