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
        (self.root / "docs" / "baseline").mkdir(parents=True)
        (self.root / "docs" / "traceability").mkdir(parents=True)
        (self.root / "docs" / "engineering" / "gates" / "phase-3").mkdir(parents=True)
        for relative_path in VALIDATOR.P3E09_STATE_ASSETS:
            path = self.root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("正式独立复审已GO、模型基线已发布；AI-MIG-000仅阻断纳入Release范围的历史迁移和数据切换，普通功能发布不适用。", encoding="utf-8")

        common = (
            "> 文档状态：`BASELINE`\n> 适用基线：PRD V1.8\n"
            "> Requirement ID：测试\n> Owner：测试Owner\n"
        )
        contents = {
            "14-security-design.md": "AES-256 密钥材料与业务数据分离 五元组 临时输入 不落库 秘密扫描 fail closed 50MB SSRF 服务端",
            "17-audit-and-observability.md": "operationId correlationId traceId Outbox P95 ≤0.5% ≥99% ≤60秒 runbook 高风险",
            "18-deployment-design.md": "JDK 25 pnpm 9.15.5 Expand -> Backfill -> Verify -> Switch -> Contract --frozen-lockfile 前向迁移 上一JAR 制品hash releaseId 不得修改已执行迁移 恢复 AI-MIG-000 不定义迁移批准哈希",
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
            "CUS-02": "CustomerServiceLevelRevision cus_customer_service_level_revision /customers/{id}/service-level-revisions 结束原等级区间并生成新版本 等级与策略快照 历史业务快照不回写",
            "CUT-07": "CutoverConfigurationRevision cut_cutover_configuration_revision /cutover-config/checklist-items 草稿→已发布→已停用 稳定编码 动态维度 已生成实例继续按消费版本解释",
            "INT-12": "五元组 临时明文不落库 原子切换 秘密扫描零命中",
            "NFR-01": "50并发用户30分钟 10000请求 P95 错误率不高于0.5% 50MB 20万项目 200万任务 Chrome/Edge/Firefox Playwright trace",
            "NFR-02": "AES-256 任务级短期取密 临时输入 密码记录数为0 撤销 明文命中数为0 密钥轮换 秘密扫描",
            "NFR-03": "99% 60秒",
            "PLT-02": "50MB 恶意内容 权限",
        }
        synthetic_count = 100 - len(exact)
        identifiers = list(exact) + [f"REQ-{index:03d}" for index in range(1, synthetic_count + 1)]
        object_table_map = {
            "objects": {
                "测试对象": {
                    "owner": "TEST",
                    "requirementIds": identifiers,
                    "targetTables": ["测试表"],
                },
                "CustomerServiceLevelRevision": {
                    "owner": "CUS",
                    "requirementIds": ["CUS-02"],
                    "targetTables": ["cus_customer_service_level_revision"],
                    "targetTablePolicy": "FEATURE_FORWARD_MIGRATION",
                },
                "CutoverConfigurationRevision": {
                    "owner": "CUT",
                    "requirementIds": ["CUT-07"],
                    "targetTables": [
                        "cut_cutover_configuration_revision",
                        "cut_cutover_checklist_item_definition_revision",
                        "cut_cutover_checklist_binding_rule_revision",
                    ],
                    "targetTablePolicy": "FEATURE_FORWARD_MIGRATION",
                },
                "技术支撑对象": {
                    "owner": "PLT",
                    "requirementIds": [],
                    "targetTables": sorted({
                        table
                        for tables in VALIDATOR.CROSS_CONTEXT_TABLE_REFERENCES.values()
                        for table in tables
                    }),
                },
            }
        }
        (self.root / "docs" / "traceability" / "domain-object-table-map.json").write_text(
            json.dumps(object_table_map, ensure_ascii=False), encoding="utf-8"
        )
        prd_blocks = [
            f"| 需求编号 | {identifier} |\n| 目标版本 | V1 |\n\n"
            "**业务验收标准：**\n\n"
            f"- **WHEN** 执行 {identifier} 有效业务请求\n"
            f"- **THEN** 返回 {identifier} 可观察业务结果\n\n"
            "**权限与数据范围：**\n\n测试范围\n"
            for identifier in identifiers
        ]
        (self.root / "docs" / "baseline" / "prd-v1.8.md").write_text(
            "\n".join(prd_blocks), encoding="utf-8"
        )
        for identifier in identifiers:
            detail = exact.get(identifier, "业务规则")
            acceptance = (
                f"WHEN 执行 {identifier} 有效业务请求；"
                f"THEN 返回 {identifier} 可观察业务结果"
            )
            blocks.append(
                f"### {identifier}\n"
                "- 数据对象：测试对象\n"
                "- 数据表：测试表\n"
                "- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；"
                "服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；"
                f"{detail}\n"
                f"- Phase 3 PRD验收基线：{acceptance}\n"
                "- Phase 3授权拒绝断言：越权按“测试权限”拒绝，不返回未授权业务事实且不产生业务副作用\n"
                f"- Phase 3业务守卫断言：按“{detail}”执行；非法状态由对应业务守卫拒绝，原有效业务事实保持不变\n"
                "- Phase 3副作用断言：成功仅按契约写入/引用数据对象“测试对象”及数据表“测试表”；"
                "事件边界为“N/A”，文件边界为“N/A”，外部集成为“N/A”。授权拒绝或业务守卫失败"
                "不得新增有效业务版本、事件、文件引用或外部完成事实。\n"
                "- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；"
                f"数据库迁移/约束验证记录；{detail}\n"
            )
        (self.root / "docs" / "traceability" / "phase2-contract-map.md").write_text(
            "> Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`\n\n" + "\n".join(blocks), encoding="utf-8"
        )
        (self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md").write_text(
            "> 审查状态：`APPROVED`\n> 结论：`READY_FOR_SDS_BASELINE_V1.8`\n"
            "DOWNSTREAM-GATED MODEL_BASELINE_READY "
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
                    "releaseApplicability": "ONLY_IF_RELEASE_INCLUDES_HISTORICAL_MIGRATION_OR_DATA_CUTOVER",
                    "executionWindowPolicy": "APPROVED_WINDOW_ONLY",
                    "driftDecision": "DEFER",
                    "modelDecisionStatus": "PARTIALLY_ACCEPTED_RECONFIRMATION_REQUIRED",
                    "deferredItemCount": 1,
                    "q07Decision": {
                        "status": "RECONFIRMATION_REQUIRED", "technicalConstraintCount": 282,
                        "primaryKeyCount": 66, "tenantReferenceKeyCount": 66,
                        "primaryKeyShape": {"singleId": 65, "compositeProjection": 1},
                        "sameDomainForeignKeyCount": 52, "stableTechnicalCheckCount": 98,
                    },
                    "q08Decision": {
                        "status": "RECONFIRMATION_REQUIRED", "candidateIndexCount": 130,
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
                "P3-E09": ["HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"],
            }
            evidence_items.append({"id": identifier, "status": "OPEN", "decisionOwner": decision_owner, "reviewOwner": None, "confirmedFacts": facts, "evidenceRefs": refs, "blocks": evidence_blocks[identifier]})
        (self.root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json").write_text(
            json.dumps({"schemaVersion": 2, "phase": "SDS_PHASE_3", "baseline": "PRD_V1.7", "decisionBaseline": decision_ref, "modelEvidenceStatus": "MODEL_BASELINE_NOT_READY", "items": evidence_items}),
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
        path.write_text(path.read_text(encoding="utf-8").replace("### REQ-001", "### REQ-002", 1), encoding="utf-8")
        self.assertTrue(any("expected 100" in item for item in VALIDATOR.validate(self.root)))

    def test_equal_count_non_formal_requirement_substitution_fails(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        text = text.replace("### REQ-001", "### EQP-06", 1)
        text = text.replace("PRD REQ-001", "PRD EQP-06", 1)
        path.write_text(text, encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("must exactly match PRD V1.8" in item for item in errors), errors)

    def test_missing_requirement_acceptance_assertion_fails(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace("- Phase 3 PRD验收基线：WHEN 执行 PM-05", "- 验收说明：WHEN 执行 PM-05", 1),
            encoding="utf-8",
        )

        self.assertTrue(any("PM-05 Phase 3 PRD acceptance" in item for item in VALIDATOR.validate(self.root)))

    def test_generic_test_and_evidence_placeholders_fail(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        text = text.replace(
            "业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；部分失败 逐项重试",
            "占位测试A",
            1,
        )
        text = text.replace(
            "自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；部分失败 逐项重试",
            "占位证据A",
            1,
        )
        path.write_text(text, encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("PM-05 missing unique Phase 3 test categories" in item for item in errors), errors)
        self.assertTrue(any("PM-05 missing unique Phase 3 evidence types" in item for item in errors), errors)

    def test_invented_object_and_table_fail_independent_contract_check(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        text = text.replace(
            "数据对象“测试对象”及数据表“测试表”",
            "数据对象“InventedAggregate”及数据表“invented_table”",
            1,
        )
        path.write_text(text, encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("InventedAggregate" in item for item in errors), errors)
        self.assertTrue(any("invented_table" in item for item in errors), errors)

    def test_object_cannot_be_reassigned_to_another_requirement(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        text = text.replace(
            "数据对象“测试对象”及数据表“测试表”",
            "数据对象“CustomerServiceLevelRevision”及数据表“cus_customer_service_level_revision”",
            1,
        )
        path.write_text(text, encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("CustomerServiceLevelRevision" in item and "PM-05" in item for item in errors), errors)

    def test_known_table_cannot_be_swapped_across_objects(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        start = text.index("### CUS-02")
        end = text.index("\n### ", start + 1)
        block = text[start:end]
        block = block.replace("- 数据对象：测试对象", "- 数据对象：CustomerServiceLevelRevision")
        block = block.replace("- 数据表：测试表", "- 数据表：cut_cutover_configuration_revision")
        block = block.replace(
            "数据对象“测试对象”及数据表“测试表”",
            "数据对象“CustomerServiceLevelRevision”及数据表“cut_cutover_configuration_revision”",
        )
        path.write_text(text[:start] + block + text[end:], encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(
            any("CUS-02 declares unknown target table" in item and "cut_cutover_configuration_revision" in item for item in errors),
            errors,
        )

    def test_formal_object_table_rows_must_match_side_effect_assertion(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        start = text.index("### CUS-02")
        end = text.index("\n### ", start + 1)
        block = text[start:end]
        block = block.replace("- 数据对象：测试对象", "- 数据对象：InventedAggregate")
        block = block.replace("- 数据表：测试表", "- 数据表：invented_table")
        path.write_text(text[:start] + block + text[end:], encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("CUS-02 formal data objects differ" in item for item in errors), errors)
        self.assertTrue(any("CUS-02 formal data tables differ" in item for item in errors), errors)

    def test_empty_object_table_contract_fails_closed(self) -> None:
        path = self.root / "docs" / "traceability" / "domain-object-table-map.json"
        path.write_text(json.dumps({"objects": {}}), encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("objects mapping must not be empty" in item for item in errors), errors)
        self.assertTrue(any("declares unknown domain object" in item for item in errors), errors)

    def test_declared_event_requires_specialty_test_and_evidence(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        start = text.index("### CUS-02")
        end = text.index("\n### ", start + 1)
        block = text[start:end].replace(
            "事件边界为“N/A”",
            "事件边界为“CustomerServiceLevelChanged”",
        )
        path.write_text(text[:start] + block + text[end:], encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("CUS-02 missing event specialty" in item for item in errors), errors)

    def test_declared_integration_requires_specialty_test_and_evidence(self) -> None:
        path = self.root / "docs" / "traceability" / "phase2-contract-map.md"
        text = path.read_text(encoding="utf-8")
        start = text.index("### CUT-07")
        end = text.index("\n### ", start + 1)
        block = text[start:end].replace(
            "外部集成为“N/A”",
            "外部集成为“基础平台字典”",
        )
        path.write_text(text[:start] + block + text[end:], encoding="utf-8")

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("CUT-07 missing integration specialty" in item for item in errors), errors)

    def test_missing_rollback_and_secret_scan_fail(self) -> None:
        deploy = self.root / "docs" / "design" / "18-deployment-design.md"
        deploy.write_text(deploy.read_text(encoding="utf-8").replace("上一JAR", "旧制品待定"), encoding="utf-8")
        security = self.root / "docs" / "design" / "14-security-design.md"
        security.write_text(security.read_text(encoding="utf-8").replace("秘密扫描", "扫描待定"), encoding="utf-8")
        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("上一JAR" in item for item in errors))
        self.assertTrue(any("秘密扫描" in item for item in errors))

    def test_premature_p3e09_baseline_claim_fails(self) -> None:
        gate = self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
        gate.write_text(
            gate.read_text(encoding="utf-8").replace("MODEL_BASELINE_READY", "MODEL_BASELINE_REVIEW_PENDING")
            + "\nP3-E09模型基线已发布\n",
            encoding="utf-8",
        )
        self.assertTrue(any("premature P3-E09 baseline claim" in item for item in VALIDATOR.validate(self.root)))

    def test_pending_state_requires_fresh_review_notice(self) -> None:
        gate = self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
        gate.write_text(gate.read_text(encoding="utf-8").replace("MODEL_BASELINE_READY", "MODEL_BASELINE_REVIEW_PENDING"), encoding="utf-8")
        self.assertTrue(any("pending state" in item for item in VALIDATOR.validate(self.root)))

    def test_current_v18_baseline_state_is_coherent_and_ready(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        gate_path = repository_root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
        gate = gate_path.read_text(encoding="utf-8")

        self.assertEqual([], VALIDATOR.validate(repository_root))
        self.assertIn("> 审查状态：`APPROVED`", gate)
        self.assertIn("> 结论：`READY_FOR_SDS_BASELINE_V1.8`", gate)

    def test_v18_revalidation_gate_does_not_bypass_design_validation(self) -> None:
        gate = self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
        gate.write_text(
            "> 审查状态：`IN_REVIEW`\n"
            "> 结论：`NOT_READY_FOR_SDS_BASELINE_V1.8`\n"
            "P3-E09 AI-MIG-000 Q08候选索引 | Phase 1/2前置 | PASS |",
            encoding="utf-8",
        )
        security = self.root / "docs" / "design" / "14-security-design.md"
        security.write_text(
            security.read_text(encoding="utf-8").replace("秘密扫描", "安全验证待补"),
            encoding="utf-8",
        )

        self.assertTrue(any("秘密扫描" in error for error in VALIDATOR.validate(self.root)))

    def test_gate_narrative_cannot_override_header_state(self) -> None:
        gate = self.root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
        gate.write_text(
            gate.read_text(encoding="utf-8")
            .replace("`APPROVED`", "`REVALIDATION_REQUIRED`", 1)
            + "\n历史结论曾为 IN_REVIEW / NOT_READY_FOR_SDS_BASELINE_V1.8。\n",
            encoding="utf-8",
        )

        errors = VALIDATOR.validate(self.root)
        self.assertTrue(any("invalid review state" in error for error in errors), errors)

    def test_v18_design_metadata_rejects_v17_baseline(self) -> None:
        security = self.root / "docs" / "design" / "14-security-design.md"
        security.write_text(
            security.read_text(encoding="utf-8").replace("PRD V1.8", "PRD V1.7"),
            encoding="utf-8",
        )

        self.assertTrue(any("PRD V1.8" in error for error in VALIDATOR.validate(self.root)))

    def test_v18_design_rejects_stale_revalidation_prose(self) -> None:
        deployment = self.root / "docs" / "design" / "18-deployment-design.md"
        deployment.write_text(
            deployment.read_text(encoding="utf-8") + "\nV1.8差量复审尚未完成。\n",
            encoding="utf-8",
        )

        self.assertTrue(any("stale V1.8 design state" in error for error in VALIDATOR.validate(self.root)))


if __name__ == "__main__":
    unittest.main()
