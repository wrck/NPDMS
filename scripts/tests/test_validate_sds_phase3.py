from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_sds_phase3.py"
SPEC = importlib.util.spec_from_file_location("validate_sds_phase3", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class Phase3ValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "docs" / "design").mkdir(parents=True)
        (self.root / "docs" / "traceability").mkdir(parents=True)
        (self.root / "docs" / "engineering" / "gates" / "phase-3").mkdir(parents=True)

        common = (
            "> 文档状态：`IN_REVIEW`\n> 适用基线：PRD V1.7\n"
            "> Requirement ID：测试\n> Owner：测试Owner\n"
        )
        contents = {
            "14-security-design.md": "AES-256 密钥材料与业务数据分离 五元组 临时输入 不落库 秘密扫描 fail closed 50MB SSRF 服务端",
            "17-audit-and-observability.md": "operationId correlationId traceId Outbox P95 ≤0.5% ≥99% ≤60秒 runbook 高风险",
            "18-deployment-design.md": "JDK 25 pnpm 9.15.5 Expand -> Backfill -> Verify -> Switch -> Contract --frozen-lockfile 前向迁移 上一JAR 制品hash releaseId 不得修改已执行迁移 恢复 AI-MIG-000 approvedDdlSha256",
            "19-performance-design.md": "P95≤2秒 ≤0.5% 50个并发登录用户 持续30分钟 ≥10000 50MB 20万 200万 1万 5万 2000 深度30 ≤30秒 ≥99% ≤60秒 dataSetVersion",
            "20-test-design.md": "正常 异常 权限拒绝 幂等 并发 Chrome Edge Firefox 1920×1080 1440×900 1366×768 1024×768 Playwright trace 秘密扫描0命中 ≥10000 ≤0.5% P95≤2秒 ≥99% ≤60秒",
        }
        for name, body in contents.items():
            (self.root / "docs" / "design" / name).write_text(common + body, encoding="utf-8")

        blocks = []
        exact = {
            "PM-05": "部分失败 逐项重试",
            "PM-06": "无环 唯一期次",
            "PM-11": "5万节点 2000直接子节点 深度30",
            "INT-12": "五元组 临时明文不落库 原子切换 秘密扫描零命中",
            "NFR-01": "50并发用户30分钟 10000请求 P95 Playwright trace",
            "NFR-02": "AES-256 密钥轮换 秘密扫描",
            "NFR-03": "99% 60秒",
            "PLT-02": "50MB 恶意内容 权限",
        }
        synthetic_count = VALIDATOR.EXPECTED_REQUIREMENT_COUNT - len(exact)
        identifiers = list(exact) + [f"REQ-{index:03d}" for index in range(1, synthetic_count + 1)]
        for identifier in identifiers:
            detail = exact.get(identifier, "业务规则")
            blocks.append(
                f"### {identifier}\n- Phase 3测试类别：{detail}\n"
                f"- Phase 3证据类型：自动化证据 {detail}\n"
            )
        (self.root / "docs" / "traceability" / "phase2-contract-map.md").write_text(
            "> Phase 3验证注记状态：`IN_REVIEW`\n\n" + "\n".join(blocks), encoding="utf-8"
        )
        (self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md").write_text(
            "IN_REVIEW NOT_READY_FOR_SDS_BASELINE DOWNSTREAM-GATED BLOCKED_BY_REVIEW "
            "P3-E01 P3-E02 P3-E03 P3-E04 P3-E05 P3-E06 P3-E08 P3-E09 AI-MIG-000",
            encoding="utf-8",
        )
        evidence_items = []
        direction_decisions = {
            "P3-E01": "A", "P3-E02": "A", "P3-E03": "A", "P3-E04": "A",
            "P3-E05": "A", "P3-E06": "A", "P3-E07": "B", "P3-E09": "A",
        }
        decision_ref = "docs/decisions/0004-phase3-production-assurance-directions.md"
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
                    "driftDecision": "DEFER",
                    "modelDecisionStatus": "PARTIALLY_ACCEPTED_RECONFIRMATION_REQUIRED",
                    "deferredItemCount": 1,
                    "approvedDdlSha256": None,
                    "q07Decision": {
                        "status": "RECONFIRMATION_REQUIRED", "technicalConstraintCount": 257,
                        "primaryKeyCount": 60, "tenantReferenceKeyCount": 60,
                        "primaryKeyShape": {"singleId": 59, "compositeProjection": 1},
                        "sameDomainForeignKeyCount": 48, "stableTechnicalCheckCount": 89,
                    },
                    "q08Decision": {
                        "status": "RECONFIRMATION_REQUIRED", "candidateIndexCount": 122,
                        "featureQueryPlanValidationRequired": True,
                        "p3e06PerformanceValidationRequired": True,
                        "adjustmentPolicy": "FORWARD_MIGRATION_ONLY",
                    },
                }
                refs = ["drift.md", "docs/decisions/0023-p3-e09-key-collation-and-state-guard-policy.md"]
            decision_owner = None
            if identifier in direction_decisions:
                facts.update({"directionDecision": direction_decisions[identifier], "directionStatus": "ACCEPTED", "chosenDirection": "test direction"})
                refs.append(decision_ref)
                decision_owner = "REQUIREMENT_OWNER"
            local_assessments = {
                "P3-E01": "NO_PRODUCTION_EVIDENCE", "P3-E02": "DEVELOPMENT_SINGLE_NODE_ONLY",
                "P3-E03": "NO_RECOVERY_EXERCISE_EVIDENCE", "P3-E04": "NO_ENTERPRISE_KMS_EVIDENCE",
                "P3-E05": "CAPABILITY_ONLY_NO_PRODUCTION_BACKEND_EVIDENCE", "P3-E06": "LOCAL_HEALTH_SCRIPTS_ONLY",
            }
            if identifier in local_assessments:
                facts["localRepositoryAssessment"] = local_assessments[identifier]
                refs.append("docs/engineering/gates/phase-3/runtime-fact-inventory.md")
            if identifier in {"P3-E01", "P3-E04"}:
                facts["evidenceStage"] = "DEPLOYMENT_TIME"
                refs.append("docs/decisions/0018-deployment-time-environment-and-kms-selection.md")
            if identifier == "P3-E03":
                facts.update({"approvedRpo": "PT1H", "approvedRto": "PT4H", "businessObjectiveStatus": "ACCEPTED", "backupRetention": {"dailyRetention": "P35D", "monthlyRetention": "P13M", "yearlyRetention": "P7Y", "continuousLogMaxGap": "PT1H"}, "recoveryTopology": {"primary": "METRO_WARM_STANDBY", "fallback": "OFFLINE_COLD_BACKUP_FALLBACK", "approvedRto": "PT4H"}, "recoveryExercisePolicy": {"isolatedRestoreFrequency": "P3M", "fullWarmStandbySwitchFrequency": "P1Y"}, "switchAuthorization": {"initiator": "OPERATIONS_OWNER", "requiredConfirmer": "BUSINESS_OWNER", "securityIncidentAdditionalConfirmer": "SECURITY_OWNER", "auditPolicy": "PERMANENT_NON_DELETABLE"}})
                refs.extend(["docs/decisions/0005-production-recovery-objectives.md", "docs/decisions/0012-production-backup-retention.md", "docs/decisions/0013-warm-standby-and-offline-cold-backup.md", "docs/decisions/0015-recovery-exercise-frequency.md", "docs/decisions/0017-disaster-recovery-switch-authorization.md"])
            if identifier == "P3-E05":
                facts["permanentAuditPolicy"] = "PERMANENT_NON_DELETABLE"
                facts["networkSecurityLogRetention"] = {
                    "policyCode": "NETWORK_SECURITY_LOG_P1Y",
                    "totalRetention": "P1Y",
                    "onlineRetention": "P180D",
                    "immutableColdRetention": "P185D",
                }
                facts["traceRetention"] = {
                    "standard": {"policyCode": "TRACE_STANDARD_P90D", "totalRetention": "P90D", "onlineRetention": "P30D", "coldRetention": "P60D"},
                    "errorHighRisk": {"policyCode": "TRACE_ERROR_HIGH_RISK_P180D", "totalRetention": "P180D", "onlineRetention": "P30D", "coldRetention": "P150D"},
                }
                facts["metricRetention"] = {
                    "rawHighResolution": {"policyCode": "METRIC_RAW_P90D", "retention": "P90D"},
                    "fiveMinuteHourlyAggregate": {"policyCode": "METRIC_AGGREGATE_P13M", "retention": "P13M"},
                }
                facts["debugLogRetention"] = {
                    "policyCode": "DEBUG_LOG_DEFAULT_P7D", "defaultRetention": "P7D",
                    "exceptionPolicyCode": "DEBUG_LOG_EXCEPTION_MAX_P30D", "maximumExceptionRetention": "P30D",
                    "exceptionRequiredFields": ["reason", "owner", "expiresAt"],
                }
                facts["traceSamplingPolicy"] = {
                    "standardSuccessSampleRate": 0.10, "forcedSampleRate": 1.0,
                    "forcedCategories": ["ERROR", "HIGH_RISK_SECURITY_OPERATION", "AUDIT_WRITE_FAILURE", "RELEASE_MIGRATION"],
                    "unsampledStillProducesMetrics": True, "unsampledStillProducesPermanentAudit": True,
                }
                facts["exportAuthorizationPolicy"] = {"approvalRequired": False, "requiredControls": ["EXPORT_FUNCTION_PERMISSION", "DATA_SCOPE", "FIELD_PERMISSION", "REAL_TIME_DOWNLOAD_RECHECK"], "exportAuditPolicy": "PERMANENT_NON_DELETABLE", "exportFileTtl": "PT24H", "exportRecordRetention": "PERMANENT_NON_DELETABLE"}
                refs.append("docs/decisions/0006-permanent-business-audit-retention.md")
                refs.append("docs/decisions/0007-network-security-log-retention.md")
                refs.append("docs/decisions/0008-trace-retention.md")
                refs.append("docs/decisions/0009-metric-retention.md")
                refs.append("docs/decisions/0010-debug-log-retention.md")
                refs.append("docs/decisions/0011-production-trace-sampling.md")
                refs.append("docs/decisions/0014-permission-driven-business-data-export.md")
                refs.append("docs/decisions/0016-export-file-expiration.md")
            evidence_blocks = {
                "P3-E01": ["SECURITY_TRUST_BOUNDARY", "PRODUCTION_DEPLOYMENT", "PRODUCTION_RELEASE"],
                "P3-E02": ["PRODUCTION_DEPLOYMENT", "PERFORMANCE_ACCEPTANCE", "PRODUCTION_RELEASE"],
                "P3-E03": ["RECOVERY_ACCEPTANCE", "PRODUCTION_RELEASE"],
                "P3-E04": ["NFR_02", "DEVICE_CREDENTIAL_RELEASE", "PRODUCTION_RELEASE"],
                "P3-E05": ["OBSERVABILITY_ACCEPTANCE", "HIGH_RISK_AUDIT", "PRODUCTION_RELEASE"],
                "P3-E06": ["PERFORMANCE_ACCEPTANCE", "PRODUCTION_RELEASE"],
                "P3-E07": ["FEATURE_INTEGRATION", "FEATURE_RELEASE"],
                "P3-E08": ["FRONTEND_FEATURE_ACCEPTANCE", "FRONTEND_RELEASE"],
                "P3-E09": ["PHASE_3_BASELINE", "DATA_MODEL_BASELINE", "HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"],
            }
            evidence_items.append({"id": identifier, "status": "OPEN", "decisionOwner": decision_owner, "reviewOwner": None, "confirmedFacts": facts, "evidenceRefs": refs, "blocks": evidence_blocks[identifier]})
        (self.root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json").write_text(
            json.dumps({"schemaVersion": 1, "phase": "SDS_PHASE_3", "baseline": "PRD_V1.7", "decisionBaseline": decision_ref, "overallStatus": "NOT_READY_FOR_SDS_BASELINE", "items": evidence_items}),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_valid_fixture_passes(self) -> None:
        self.assertEqual([], VALIDATOR.validate(self.root))

    def test_missing_performance_threshold_fails(self) -> None:
        path = self.root / "docs" / "design" / "19-performance-design.md"
        path.write_text(path.read_text(encoding="utf-8").replace("P95≤2秒", "P95待定"), encoding="utf-8")
        self.assertTrue(any("P95≤2秒" in item for item in VALIDATOR.validate(self.root)))

    def test_missing_requirement_mapping_fails(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        path.write_text(path.read_text(encoding="utf-8").replace("### REQ-095", "### REQ-094"), encoding="utf-8")
        self.assertTrue(any("expected 103" in item for item in VALIDATOR.validate(self.root)))

    def test_missing_rollback_and_secret_scan_fail(self) -> None:
        deploy = self.root / "docs" / "design" / "18-deployment-design.md"
        deploy.write_text(deploy.read_text(encoding="utf-8").replace("上一JAR", "旧制品待定"), encoding="utf-8")
        security = self.root / "docs" / "design" / "14-security-design.md"
        security.write_text(security.read_text(encoding="utf-8").replace("秘密扫描", "扫描待定"), encoding="utf-8")
        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("上一JAR" in item for item in errors))
        self.assertTrue(any("秘密扫描" in item for item in errors))


if __name__ == "__main__":
    unittest.main()
