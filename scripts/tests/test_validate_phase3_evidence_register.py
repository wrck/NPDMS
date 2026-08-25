from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_phase3_evidence_register.py"
SPEC = importlib.util.spec_from_file_location("validate_phase3_evidence_register", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class Phase3EvidenceRegisterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "register.json"
        items = []
        for index in range(1, 10):
            identifier = f"P3-E{index:02d}"
            facts = {}
            refs = []
            if identifier == "P3-E08":
                facts = {"result": "FAIL", "exitCode": 1}
                refs = ["failure.md"]
            if identifier == "P3-E09":
                facts = {
                    "currentDdlSha256": "CURRENT",
                    "legacyCatalogDdlSha256": "OLD",
                    "releaseApplicability": "ONLY_IF_RELEASE_INCLUDES_HISTORICAL_MIGRATION_OR_DATA_CUTOVER",
                    "executionWindowPolicy": "APPROVED_WINDOW_ONLY",
                    "driftDecision": "DEFER",
                    "modelDecisionStatus": "PARTIALLY_ACCEPTED_RECONFIRMATION_REQUIRED",
                    "deferredItemCount": 1,
                    "q07Decision": {"status": "RECONFIRMATION_REQUIRED", **VALIDATOR.Q07_DECISION},
                    "q08Decision": {"status": "RECONFIRMATION_REQUIRED", **VALIDATOR.Q08_DECISION},
                }
                refs = ["drift.md", VALIDATOR.MODEL_DECISION_REF]
            expected_direction = VALIDATOR.DIRECTION_DECISIONS.get(identifier)
            decision_owner = None
            if expected_direction:
                facts.update({"directionDecision": expected_direction, "directionStatus": "ACCEPTED", "chosenDirection": "test direction"})
                refs.append(VALIDATOR.DECISION_REF)
                decision_owner = "REQUIREMENT_OWNER"
            if identifier in VALIDATOR.LOCAL_REPOSITORY_ASSESSMENTS:
                facts["localRepositoryAssessment"] = VALIDATOR.LOCAL_REPOSITORY_ASSESSMENTS[identifier]
                refs.append("docs/engineering/gates/phase-3/runtime-fact-inventory.md")
            if identifier in {"P3-E01", "P3-E04"}:
                facts["evidenceStage"] = "DEPLOYMENT_TIME"
                refs.append(VALIDATOR.DEPLOYMENT_TIME_SELECTION_REF)
            if identifier == "P3-E03":
                facts.update({"approvedRpo": "PT1H", "approvedRto": "PT4H", "businessObjectiveStatus": "ACCEPTED", "backupRetention": VALIDATOR.BACKUP_RETENTION_POLICY, "recoveryTopology": VALIDATOR.RECOVERY_TOPOLOGY, "recoveryExercisePolicy": VALIDATOR.RECOVERY_EXERCISE_POLICY, "switchAuthorization": VALIDATOR.RECOVERY_SWITCH_AUTHORIZATION})
                refs.extend([VALIDATOR.RECOVERY_OBJECTIVE_REF, VALIDATOR.BACKUP_RETENTION_REF, VALIDATOR.RECOVERY_TOPOLOGY_REF, VALIDATOR.RECOVERY_EXERCISE_REF, VALIDATOR.RECOVERY_SWITCH_AUTH_REF])
            if identifier == "P3-E05":
                facts["permanentAuditPolicy"] = VALIDATOR.PERMANENT_AUDIT_POLICY
                facts["networkSecurityLogRetention"] = VALIDATOR.NETWORK_SECURITY_RETENTION
                facts["traceRetention"] = VALIDATOR.TRACE_RETENTION
                facts["metricRetention"] = VALIDATOR.METRIC_RETENTION
                facts["debugLogRetention"] = VALIDATOR.DEBUG_LOG_RETENTION
                facts["traceSamplingPolicy"] = VALIDATOR.TRACE_SAMPLING_POLICY
                facts["exportAuthorizationPolicy"] = VALIDATOR.EXPORT_AUTHORIZATION_POLICY
                refs.append(VALIDATOR.PERMANENT_AUDIT_REF)
                refs.append(VALIDATOR.NETWORK_SECURITY_RETENTION_REF)
                refs.append(VALIDATOR.TRACE_RETENTION_REF)
                refs.append(VALIDATOR.METRIC_RETENTION_REF)
                refs.append(VALIDATOR.DEBUG_LOG_RETENTION_REF)
                refs.append(VALIDATOR.TRACE_SAMPLING_REF)
                refs.append(VALIDATOR.EXPORT_POLICY_REF)
                refs.append(VALIDATOR.EXPORT_EXPIRATION_REF)
            items.append({"id": identifier, "status": "OPEN", "decisionOwner": decision_owner, "reviewOwner": None, "confirmedFacts": facts, "evidenceRefs": refs, "blocks": sorted(VALIDATOR.EXPECTED_BLOCKS[identifier])})
        self.payload = {"schemaVersion": 2, "phase": "SDS_PHASE_3", "baseline": "PRD_V1.7", "decisionBaseline": VALIDATOR.DECISION_REF, "modelEvidenceStatus": "MODEL_BASELINE_NOT_READY", "items": items}
        self.write()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self) -> None:
        self.path.write_text(json.dumps(self.payload), encoding="utf-8")

    def test_open_register_is_structurally_valid(self) -> None:
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_verified_e08_closure_is_valid(self) -> None:
        e08 = next(item for item in self.payload["items"] if item["id"] == "P3-E08")
        e08.update({
            "status": "VERIFIED",
            "decisionOwner": "FRONTEND_OWNER",
            "reviewOwner": "INDEPENDENT_REVIEWER",
        })
        e08["confirmedFacts"] = {
            "result": "PASS",
            "exitCode": 0,
            "errorCount": 0,
            "browserRegressionEvidenceId": "P3-E08-BROWSER-TEST",
        }
        self.write()
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_verified_e08_requires_browser_evidence(self) -> None:
        e08 = next(item for item in self.payload["items"] if item["id"] == "P3-E08")
        e08.update({
            "status": "VERIFIED",
            "decisionOwner": "FRONTEND_OWNER",
            "reviewOwner": "INDEPENDENT_REVIEWER",
        })
        e08["confirmedFacts"] = {"result": "PASS", "exitCode": 0, "errorCount": 0}
        self.write()
        self.assertTrue(any("browser regression evidence" in error for error in VALIDATOR.validate(self.path)))

    def test_current_prd_v18_baseline_is_supported(self) -> None:
        self.payload["baseline"] = "PRD_V1.8"
        self.write()
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_model_readiness_does_not_allow_migration(self) -> None:
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        item = next(row for row in payload["items"] if row["id"] == "P3-E09")
        self.assertNotIn("DATA_MODEL_BASELINE", item["blocks"])
        self.assertNotIn("PHASE_3_BASELINE", item["blocks"])
        self.assertIn("HISTORICAL_DATA_MIGRATION", item["blocks"])
        self.assertIn("DATA_CUTOVER", item["blocks"])

    def test_ai_mig_release_applicability_is_required(self) -> None:
        e09 = next(item for item in self.payload["items"] if item["id"] == "P3-E09")
        del e09["confirmedFacts"]["releaseApplicability"]
        self.write()
        self.assertTrue(any("release applicability" in error for error in VALIDATOR.validate(self.path)))

    def test_ai_mig_only_runs_in_approved_window(self) -> None:
        e09 = next(item for item in self.payload["items"] if item["id"] == "P3-E09")
        e09["confirmedFacts"]["executionWindowPolicy"] = "ANY_TIME"
        self.write()
        self.assertTrue(any("approved window" in error for error in VALIDATOR.validate(self.path)))

    def test_only_model_affecting_e09_blocks_sds_readiness(self) -> None:
        errors = VALIDATOR.validate(self.path, require_ready=True)
        self.assertTrue(any("P3-E09" in error for error in errors))

    def test_downstream_open_items_do_not_block_sds_baseline(self) -> None:
        by_id = {item["id"]: item for item in self.payload["items"]}
        by_id["P3-E09"]["status"] = "VERIFIED"
        by_id["P3-E09"]["confirmedFacts"]["modelDecisionStatus"] = "MODEL_BASELINE_READY"
        self.assertTrue(VALIDATOR.model_evidence_ready(by_id))

    def test_p3e09_not_ready_blocks_sds_baseline(self) -> None:
        by_id = {item["id"]: item for item in self.payload["items"]}
        self.assertFalse(VALIDATOR.model_evidence_ready(by_id))

    def test_rejects_legacy_overall_status(self) -> None:
        self.payload["overallStatus"] = "READY_FOR_SDS_BASELINE"
        self.write()
        errors = VALIDATOR.validate(self.path)
        self.assertTrue(any("overallStatus is not allowed" in error for error in errors), errors)

    def test_gate_scope_drift_is_detected(self) -> None:
        self.payload["items"][0]["blocks"].append("PHASE_3_BASELINE")
        self.write()
        self.assertTrue(any("gate scope mismatch" in error for error in VALIDATOR.validate(self.path)))

    def test_verified_requires_owner_and_evidence(self) -> None:
        self.payload["items"][0]["status"] = "VERIFIED"
        self.write()
        self.assertTrue(any("VERIFIED requires" in error for error in VALIDATOR.validate(self.path)))

    def test_missing_item_is_detected(self) -> None:
        self.payload["items"].pop()
        self.write()
        self.assertTrue(any("coverage mismatch" in error for error in VALIDATOR.validate(self.path)))

    def test_e09_rejects_legacy_migration_approval_hash_fact(self) -> None:
        e09 = next(item for item in self.payload["items"] if item["id"] == "P3-E09")
        e09["confirmedFacts"]["approvedDdlSha256"] = None
        self.write()
        self.assertTrue(any("legacy migration approval field is not allowed" in error for error in VALIDATOR.validate(self.path)))

    def test_zero_defer_without_review_is_review_pending_not_ready(self) -> None:
        e09 = next(item for item in self.payload["items"] if item["id"] == "P3-E09")
        e09["confirmedFacts"].update({
            "deferredItemCount": 0,
            "driftDecision": "ACCEPT_CURRENT",
            "modelDecisionStatus": "MODEL_BASELINE_REVIEW_PENDING",
        })
        self.write()
        self.assertEqual([], VALIDATOR.validate(self.path))
        self.assertTrue(any("P3-E09" in error for error in VALIDATOR.validate(self.path, require_ready=True)))

    def test_manual_ready_without_review_evidence_fails(self) -> None:
        root = Path(self.temp.name)
        self.path = root / "docs/engineering/gates/phase-3/phase3-evidence-register.json"
        self.path.parent.mkdir(parents=True)
        register_path = root / "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json"
        register_path.parent.mkdir(parents=True)
        register_path.write_text(json.dumps({
            "currentDdlSha256": "CURRENT",
            "itemsSha256": "ITEMS",
            "items": [{"itemId": "COLUMN:t:id", "decision": "ACCEPT_CURRENT"}],
        }), encoding="utf-8")
        e09 = next(item for item in self.payload["items"] if item["id"] == "P3-E09")
        e09["confirmedFacts"].update({
            "deferredItemCount": 0,
            "driftDecision": "ACCEPT_CURRENT",
            "modelDecisionStatus": "MODEL_BASELINE_READY",
            "targetCatalogDdlSha256": "CURRENT",
            "mappingDdlSha256": "CURRENT",
            "validationDdlSha256": "CURRENT",
            "manifestDdlSha256": "CURRENT",
            "itemsSha256": "ITEMS",
            "itemIdsSha256": "invalid",
            "mysql84DdlSha256": "CURRENT",
        })
        self.write()
        errors = VALIDATOR.validate(self.path)
        self.assertTrue(any("independentReviewResult=GO" in error for error in errors))

    def test_wrong_direction_decision_is_detected(self) -> None:
        self.payload["items"][0]["confirmedFacts"]["directionDecision"] = "B"
        self.write()
        self.assertTrue(any("direction decision" in error for error in VALIDATOR.validate(self.path)))

    def test_local_development_evidence_cannot_be_promoted_to_production(self) -> None:
        self.payload["items"][1]["confirmedFacts"]["localRepositoryAssessment"] = "PRODUCTION_HA_VERIFIED"
        self.write()
        self.assertTrue(any("local repository evidence assessment" in error for error in VALIDATOR.validate(self.path)))

    def test_recovery_objective_drift_is_detected(self) -> None:
        self.payload["items"][2]["confirmedFacts"]["approvedRto"] = "PT8H"
        self.write()
        self.assertTrue(any("recovery objectives" in error for error in VALIDATOR.validate(self.path)))

    def test_backup_retention_drift_is_detected(self) -> None:
        self.payload["items"][2]["confirmedFacts"]["backupRetention"] = {**VALIDATOR.BACKUP_RETENTION_POLICY, "yearlyRetention": "P1Y"}
        self.write()
        self.assertTrue(any("backup retention" in error for error in VALIDATOR.validate(self.path)))

    def test_recovery_topology_drift_is_detected(self) -> None:
        self.payload["items"][2]["confirmedFacts"]["recoveryTopology"] = {**VALIDATOR.RECOVERY_TOPOLOGY, "primary": "COLD_ONLY"}
        self.write()
        self.assertTrue(any("recovery topology" in error for error in VALIDATOR.validate(self.path)))

    def test_recovery_exercise_frequency_drift_is_detected(self) -> None:
        self.payload["items"][2]["confirmedFacts"]["recoveryExercisePolicy"] = {**VALIDATOR.RECOVERY_EXERCISE_POLICY, "isolatedRestoreFrequency": "P6M"}
        self.write()
        self.assertTrue(any("recovery exercise frequency" in error for error in VALIDATOR.validate(self.path)))

    def test_recovery_switch_authorization_drift_is_detected(self) -> None:
        self.payload["items"][2]["confirmedFacts"]["switchAuthorization"] = {**VALIDATOR.RECOVERY_SWITCH_AUTHORIZATION, "requiredConfirmer": None}
        self.write()
        self.assertTrue(any("switch authorization" in error for error in VALIDATOR.validate(self.path)))

    def test_permanent_audit_policy_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["permanentAuditPolicy"] = "P3Y"
        self.write()
        self.assertTrue(any("permanent audit policy" in error for error in VALIDATOR.validate(self.path)))

    def test_network_security_log_retention_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["networkSecurityLogRetention"] = {
            **VALIDATOR.NETWORK_SECURITY_RETENTION,
            "onlineRetention": "P90D",
        }
        self.write()
        self.assertTrue(any("network/security log retention" in error for error in VALIDATOR.validate(self.path)))

    def test_trace_retention_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["traceRetention"] = {
            **VALIDATOR.TRACE_RETENTION,
            "standard": {**VALIDATOR.TRACE_RETENTION["standard"], "totalRetention": "P30D"},
        }
        self.write()
        self.assertTrue(any("trace retention" in error for error in VALIDATOR.validate(self.path)))

    def test_metric_retention_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["metricRetention"] = {
            **VALIDATOR.METRIC_RETENTION,
            "rawHighResolution": {**VALIDATOR.METRIC_RETENTION["rawHighResolution"], "retention": "P30D"},
        }
        self.write()
        self.assertTrue(any("metric retention" in error for error in VALIDATOR.validate(self.path)))

    def test_debug_log_retention_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["debugLogRetention"] = {
            **VALIDATOR.DEBUG_LOG_RETENTION,
            "maximumExceptionRetention": "P90D",
        }
        self.write()
        self.assertTrue(any("debug log retention" in error for error in VALIDATOR.validate(self.path)))

    def test_trace_sampling_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["traceSamplingPolicy"] = {
            **VALIDATOR.TRACE_SAMPLING_POLICY,
            "forcedSampleRate": 0.5,
        }
        self.write()
        self.assertTrue(any("trace sampling" in error for error in VALIDATOR.validate(self.path)))

    def test_export_authorization_drift_is_detected(self) -> None:
        self.payload["items"][4]["confirmedFacts"]["exportAuthorizationPolicy"] = {**VALIDATOR.EXPORT_AUTHORIZATION_POLICY, "approvalRequired": True}
        self.write()
        self.assertTrue(any("export authorization" in error for error in VALIDATOR.validate(self.path)))


if __name__ == "__main__":
    unittest.main()
