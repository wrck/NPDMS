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
            "> 文档状态：`IN_REVIEW`\n> 适用基线：PRD V1.6\n"
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
        identifiers = list(exact) + [f"REQ-{index:03d}" for index in range(1, 108)]
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
            "IN_REVIEW NOT_READY_FOR_SDS_BASELINE BLOCKED_BY_EVIDENCE "
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
                facts = {"currentDdlSha256": "CURRENT", "legacyCatalogDdlSha256": "OLD", "driftDecision": "DEFER"}
                refs = ["drift.md"]
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
            evidence_items.append({"id": identifier, "status": "OPEN", "decisionOwner": decision_owner, "reviewOwner": None, "confirmedFacts": facts, "evidenceRefs": refs, "blocks": ["GATE"]})
        (self.root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json").write_text(
            json.dumps({"schemaVersion": 1, "phase": "SDS_PHASE_3", "baseline": "PRD_V1.6", "decisionBaseline": decision_ref, "overallStatus": "NOT_READY_FOR_SDS_BASELINE", "items": evidence_items}),
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
        path.write_text(path.read_text(encoding="utf-8").replace("### REQ-107", "### REQ-106"), encoding="utf-8")
        self.assertTrue(any("expected 115" in item for item in VALIDATOR.validate(self.root)))

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
