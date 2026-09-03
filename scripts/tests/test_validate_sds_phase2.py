from __future__ import annotations

import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate_sds_phase2.py"
SPEC = importlib.util.spec_from_file_location("validate_sds_phase2", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ValidateSdsPhase2Test(unittest.TestCase):

    def test_requirement_table_scope_expands_ranges_and_compact_ids(self) -> None:
        text = """| Owner | Requirement | API |
|---|---|---|
| AST | EQP-01～EQP-07 | /devices |
| ANA | RPT-01/02/04 | /analytics |
"""

        identifiers = MODULE.active_requirement_ids(text)

        self.assertTrue({"EQP-01", "EQP-06", "EQP-07", "RPT-01", "RPT-02", "RPT-04"} <= identifiers)

    def build_fixture(self, root: Path) -> Path:
        design = root / "docs" / "design"
        baseline = root / "docs" / "baseline"
        traceability = root / "docs" / "traceability"
        design.mkdir(parents=True)
        baseline.mkdir(parents=True)
        traceability.mkdir(parents=True)
        (traceability / "core-migration-schema-contract.json").write_text(
            json.dumps(
                {
                    "forbiddenV1V2Tables": [
                        "srv_historical_time_record",
                        "srv_historical_work_order",
                        "srv_time_adjustment",
                        "srv_time_claim",
                        "srv_work_order",
                        "srv_work_order_handling_record",
                        "srv_work_order_sla",
                    ]
                }
            ),
            encoding="utf-8",
        )
        metadata = (
            "# Test\n\n"
            "> 文档状态：`BASELINE`  \n"
            "> 适用基线：PRD V1.7  \n"
            "> Requirement ID：REQ-001  \n"
            "> Owner：Test  \n\n"
            "## Scope\n\n"
            "Thing proj_test /test\n"
        )
        for name in MODULE.PHASE2_DOCS:
            content = metadata
            if name == "08a-domain-entity-migration-alignment.md":
                content = content.replace("文档状态：`BASELINE`", "文档状态：`BASELINE ADDENDUM`")
            if name == "13-file-design.md":
                content += "\n| 门禁项 | 结论 | 落位 |\n|---|---|---|\n| 保留期限和灾备数值 | DEFERRED_TO_PHASE_3 | Phase 3登记 |\n"
            if name == "15-cache-and-concurrency.md":
                content += "\n| 门禁项 | 结论 | 落位 |\n|---|---|---|\n| 容量和 TTL 数值 | DEFERRED_TO_PHASE_3 | Phase 3登记 |\n"
            (design / name).write_text(content, encoding="utf-8")
        (design / "01-requirement-traceability.md").write_text(
            "# Traceability\n\n"
            "- 范围统计：V1 55 项、V2 48 项、V1/V2 103 项；V3 30 项、OUT_OF_SCOPE 9 项。\n",
            encoding="utf-8",
        )

        required = " / ".join(
            f"[doc](../design/{name})"
            for name in (
                "08-data-model.md",
                "09-database-design.md",
                "10-api-design.md",
                "15-cache-and-concurrency.md",
                "16-exception-and-idempotency.md",
            )
        )
        rows = []
        contract_blocks = []
        prd_blocks = []
        for number in range(1, MODULE.EXPECTED_REQUIREMENT_COUNT + 1):
            identifier = f"REQ-{number:03d}"
            rows.append(
                f"| {identifier} | {required} / "
                f"[P2契约](phase2-contract-map.md#{identifier.lower()}) | SDS-P2-BASELINE |"
            )
            contract_blocks.append(
                f"### {identifier}\n\n"
                "- 需求名称：Test\n"
                "- 数据对象：Thing\n"
                "- 数据表：proj_test\n"
                "- API：/test\n"
                "- 事件：N/A（同步命令或查询）\n"
                "- 外部集成：N/A（平台内部契约）\n"
                "- 文件契约：N/A（无文件）\n"
                "- 工作流/状态：测试状态守卫\n"
                "- 授权与数据范围：测试数据范围\n"
            )
            prd_blocks.append(
                f"### {identifier} Test\n\n"
                f"| 需求编号 | {identifier} |\n"
                f"| 目标版本 | {'V1' if number <= 55 else 'V2'} |\n"
            )
        (baseline / "prd-v1.7.md").write_text(
            "# PRD V1.7\n\n"
            + "\n".join(prd_blocks)
            + "\n### A.3 V3演进索引\n\n"
            + "#### A.3.1 已编号演进项\n\n"
            + "\n".join(f"| EV-V3-{number:02d} | Future | P3 | Deferred |" for number in range(1, 31))
            + "\n\n#### A.3.2 跨需求演进方向\n\n"
            + "### A.4 OUT_OF_SCOPE索引\n\n"
            + "\n".join(f"| OOS-{number:03d} | Excluded | Reason |" for number in range(1, 10))
            + "\n\n## 附录B\n",
            encoding="utf-8",
        )
        matrix = "# Matrix\n\n" + "\n".join(rows) + "\n"
        matrix_path = traceability / "requirement-matrix.md"
        matrix_path.write_text(matrix, encoding="utf-8")
        (traceability / "phase2-contract-map.md").write_text(
            "# Contracts\n\n" + "\n".join(contract_blocks), encoding="utf-8"
        )
        return matrix_path

    def test_valid_fixture_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            self.assertEqual([], MODULE.validate(root))

    def test_missing_target_and_duplicate_requirement_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            matrix_path = self.build_fixture(root)
            matrix = matrix_path.read_text(encoding="utf-8")
            matrix = matrix.replace("REQ-103", "REQ-102")
            matrix = matrix.replace("../design/10-api-design.md", "../design/missing-api.md", 1)
            matrix_path.write_text(matrix, encoding="utf-8")

            errors = MODULE.validate(root)
            self.assertTrue(any("expected 103 unique rows" in error for error in errors))
            self.assertTrue(any("trace link target missing" in error for error in errors))
            self.assertTrue(any("REQ-001 missing required Phase 2 trace link" in error for error in errors))

    def test_missing_explicit_contract_and_undefined_symbol_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
            content = contract_path.read_text(encoding="utf-8")
            content = content.replace("### REQ-103", "### REQ-999", 1)
            content = content.replace("- 数据表：proj_test", "- 数据表：proj_missing", 1)
            contract_path.write_text(content, encoding="utf-8")

            errors = MODULE.validate(root)
            self.assertTrue(any("contract IDs must exactly match" in error for error in errors))
            self.assertTrue(any("undefined table contract: proj_missing" in error for error in errors))

    def test_matrix_and_contract_cannot_replace_formal_id_with_v3_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            matrix_path = self.build_fixture(root)
            contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
            matrix_path.write_text(
                matrix_path.read_text(encoding="utf-8")
                .replace("REQ-103", "EQP-06")
                .replace("req-103", "eqp-06"),
                encoding="utf-8",
            )
            contract_path.write_text(
                contract_path.read_text(encoding="utf-8").replace("REQ-103", "EQP-06"),
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("PRD formal Requirement IDs" in error for error in errors), errors)

    def test_out_of_scope_requirement_in_active_scope_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "08-data-model.md"
            original = target.read_text(encoding="utf-8")
            for identifier in ("EQP-06", "RPT-01", "RPT-04"):
                with self.subTest(identifier=identifier):
                    target.write_text(
                        original + f"\n适用 Requirement：{identifier}。\n",
                        encoding="utf-8",
                    )
                    errors = MODULE.validate(root)
                    self.assertTrue(
                        any(identifier in error and "active scope" in error for error in errors),
                        errors,
                    )
            target.write_text(original, encoding="utf-8")

    def test_active_requirement_disclaimer_cannot_hide_non_formal_id(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "08-data-model.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n适用 Requirement：EQP-06（当前启用，不属于历史排除）。\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("EQP-06" in error and "active scope" in error for error in errors), errors)

    def test_project_conversion_work_order_consumer_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "11-event-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| `ProjectConversionCompleted` | Project Delivery | IMP/CUT/WO/AST | test | test |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)
            self.assertTrue(any("ProjectConversionCompleted" in error and "WO" in error for error in errors), errors)

    def test_active_work_order_consumer_disclaimer_cannot_bypass_forbidden_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "11-event-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| `ProjectConversionCompleted` | Project Delivery | IMP/CUT/WO/AST | 未来另行优化 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("ProjectConversionCompleted" in error and "WO" in error for error in errors), errors)

    def test_historical_work_order_consumer_exclusion_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "11-event-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 历史排除 | `ProjectConversionCompleted` | WO | 不属于当前V1/V2消费者 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertFalse(any("ProjectConversionCompleted" in error and "WO" in error for error in errors), errors)

    def test_dingtalk_clock_in_fact_contract_fails(self) -> None:
        injections = {
            "12-integration-design.md": "| 钉钉 | V1 | 双向 | 打卡原始事实、消息交付 | WO/PLT | 打卡记录ID+打卡人 | 平台记录 |",
            "16-exception-and-idempotency.md": "| 钉钉打卡 | 打卡记录ID+打卡人 | tenant + DingTalk | 更新同步事实 |",
        }
        for name, injection in injections.items():
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(name=name):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / name
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n{injection}\n",
                        encoding="utf-8",
                    )
                    errors = MODULE.validate(root)
                    self.assertTrue(any("DingTalk clock-in" in error for error in errors), errors)

    def test_active_dingtalk_disclaimer_cannot_bypass_forbidden_check(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "12-integration-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 钉钉打卡 | V1 | 双向 | 打卡原始事实 | 当前启用，不属于历史排除 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("DingTalk clock-in" in error for error in errors), errors)

    def test_explicit_historical_dingtalk_clock_in_exclusion_is_allowed(self) -> None:
        fragments = (
            "| 历史排除 | 钉钉打卡 | 不属于当前V1/V2契约 |",
            "| 类型 | 事实 | 处置 | 说明 |\n"
            "|---|---|---|---|\n"
            "| 历史来源 | 钉钉打卡候选 | 不进入当前 | 仅保留来源证据 |",
        )
        for fragment in fragments:
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(fragment=fragment):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / "12-integration-design.md"
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n{fragment}\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertFalse(any("DingTalk clock-in" in error for error in errors), errors)

    def test_pending_integration_config_does_not_exclude_dingtalk_clock_in(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "12-integration-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 事实 | status | 说明 |\n"
                + "|---|---|---|\n"
                + "| 钉钉打卡原始事实 | PENDING_INTEGRATION_CONFIG | 待配置 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("DingTalk clock-in" in error for error in errors), errors)

    def test_pending_field_mapping_does_not_exclude_work_order_model(self) -> None:
        for status in ("PENDING", "PENDING_FIELD_MAPPING"):
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(status=status):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / "09-database-design.md"
                    target.write_text(
                        target.read_text(encoding="utf-8")
                        + "\n| 数据对象 | 数据表 | status |\n"
                        + "|---|---|---|\n"
                        + f"| WorkOrder | srv_work_order | {status} |\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertTrue(any("srv_work_order" in error for error in errors), errors)

    def test_pending_review_does_not_exclude_work_order_event_consumer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "11-event-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 事件 | Consumer | status |\n"
                + "|---|---|---|\n"
                + "| ProjectConversionCompleted | WO | PENDING_REVIEW |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("ProjectConversionCompleted" in error and "WO" in error for error in errors), errors)

    def test_scope_statistics_are_validated_from_prd_indexes(self) -> None:
        mutations = {
            "V1": ("| 目标版本 | V1 |", "| 目标版本 | V2 |"),
            "V3": ("| EV-V3-30 | Future | P3 | Deferred |", ""),
            "OUT_OF_SCOPE": ("| OOS-009 | Excluded | Reason |", ""),
        }
        for label, (old, new) in mutations.items():
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(label=label):
                    root = Path(temporary)
                    self.build_fixture(root)
                    prd = root / "docs" / "baseline" / "prd-v1.7.md"
                    prd.write_text(prd.read_text(encoding="utf-8").replace(old, new, 1), encoding="utf-8")

                    errors = MODULE.validate(root)

                    self.assertTrue(any("scope statistics" in error for error in errors), errors)

    def test_phase1_traceability_scope_statistics_are_machine_checked(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "01-requirement-traceability.md"
            target.write_text(
                target.read_text(encoding="utf-8").replace("V3 30 项", "V3 29 项"),
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("scope statistics" in error for error in errors), errors)

    def test_baseline_phase2_design_must_not_retain_in_review_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "13-file-design.md"
            target.write_text(
                target.read_text(encoding="utf-8") + "\n| 保留期限和灾备数值 | IN_REVIEW | Phase 3登记 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("unresolved IN_REVIEW" in error for error in errors), errors)

    def test_phase3_deferred_markers_are_required_for_runtime_values(self) -> None:
        mutations = {
            "13-file-design.md": "| 保留期限和灾备数值 | DEFERRED_TO_PHASE_3 |",
            "15-cache-and-concurrency.md": "| 容量和 TTL 数值 | DEFERRED_TO_PHASE_3 |",
        }
        for name, marker in mutations.items():
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(name=name):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / name
                    target.write_text(
                        target.read_text(encoding="utf-8").replace(marker, ""),
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertTrue(any("missing Phase 3 deferral marker" in error for error in errors), errors)

    def test_historical_work_order_and_time_user_apis_fail(self) -> None:
        for api in ("/historical-work-orders", "/historical-time-records"):
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(api=api):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / "10-api-design.md"
                    target.write_text(
                        target.read_text(encoding="utf-8")
                        + f"\n| Historical Service Records | `{api}` | read/export |\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertTrue(any(api in error and "must not expose" in error for error in errors), errors)

    def test_current_work_order_file_context_fails(self) -> None:
        mutations = (
            lambda content: content
            + "\n| Work Order/Inspection | 工单证据、巡检报告 | 当前V1/V2文件入口 |\n",
            lambda content: content.replace(
                "Requirement ID：REQ-001",
                "Requirement ID：REQ-001、WO",
            ),
        )
        for mutate in mutations:
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                self.build_fixture(root)
                target = root / "docs" / "design" / "13-file-design.md"
                target.write_text(mutate(target.read_text(encoding="utf-8")), encoding="utf-8")

                errors = MODULE.validate(root)

                self.assertTrue(any("Work Order file context" in error for error in errors), errors)

    def test_historical_work_order_objects_and_tables_cannot_return(self) -> None:
        injections = {
            "08a-domain-entity-migration-alignment.md": "HistoricalWorkOrder",
            "08-data-model.md": "HistoricalTimeRecord",
            "09-database-design.md": "srv_historical_work_order",
            "10-api-design.md": "srv_historical_time_record",
        }
        for name, token in injections.items():
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(name=name, token=token):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / name
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n当前对象：`{token}`。\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertTrue(
                        any(token in error and "forbidden active WorkOrder/time model token" in error for error in errors),
                        errors,
                    )

    def test_chinese_current_work_order_inspection_file_context_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "13-file-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| Context | 文件用途 | 范围状态 |\n"
                + "|---|---|---|\n"
                + "| 工单/巡检 | 工单附件和巡检报告 | 当前V1/V2写模型 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("Work Order file context" in error for error in errors), errors)

    def test_work_order_current_write_model_disclaimer_cannot_bypass_guard(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "08-data-model.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 数据对象 | 数据表 | 范围状态 |\n"
                + "|---|---|---|\n"
                + "| WorkOrder | srv_work_order | 当前V1/V2写模型 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertTrue(any("WorkOrder" in error or "srv_work_order" in error for error in errors), errors)

    def test_every_core_contract_work_order_time_table_is_forbidden_in_active_scope(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        contract = json.loads(
            (repository_root / "docs" / "traceability" / "core-migration-schema-contract.json")
            .read_text(encoding="utf-8")
        )
        forbidden = [
            table
            for table in contract["forbiddenV1V2Tables"]
            if "work_order" in table or table.startswith("srv_time_") or "historical_time" in table
        ]
        self.assertGreaterEqual(len(forbidden), 7)

        for table in forbidden:
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(table=table):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / "09-database-design.md"
                    target.write_text(
                        target.read_text(encoding="utf-8")
                        + "\n| 数据对象 | 数据表 | 范围状态 |\n"
                        + "|---|---|---|\n"
                        + f"| CurrentServiceFact | {table} | 当前V1/V2写模型 |\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertTrue(any(table in error for error in errors), errors)

    def test_future_independent_work_order_change_explanation_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "08-data-model.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n未来如需WorkOrder或srv_work_order，必须通过独立PRD/Feature变更；"
                + "该说明不属于当前V1/V2写模型。\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertFalse(any("WorkOrder" in error or "srv_work_order" in error for error in errors), errors)

    def test_structured_historical_model_exclusion_evidence_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / "docs" / "design" / "09-database-design.md"
            target.write_text(
                target.read_text(encoding="utf-8")
                + "\n| 对象 | 目标表 | status | 说明 |\n"
                + "|---|---|---|---|\n"
                + "| HistoricalWorkOrder | srv_historical_work_order | EXCLUDED | 不进入当前契约 |\n",
                encoding="utf-8",
            )

            errors = MODULE.validate(root)

            self.assertEqual([], errors)

    def test_prd_immutable_history_governance_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            prd = root / "docs" / "baseline" / "prd-v1.7.md"
            prd.write_text(
                prd.read_text(encoding="utf-8")
                + "\n已有历史工单、工时、附件、审批和明确要求记录的操作属于不可删除业务事实。\n",
                encoding="utf-8",
            )

            self.assertEqual([], MODULE.validate(root))

    def test_current_v18_baseline_state_is_coherent_and_ready_for_phase3_design(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        gate_path = repository_root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
        gate = gate_path.read_text(encoding="utf-8")

        self.assertEqual([], MODULE.validate(repository_root))
        errors = MODULE.validate_v18_revalidation(
            repository_root,
            gate.replace("READY_FOR_PHASE_3_V1.8", "NOT_READY_FOR_PHASE_3_V1.8"),
            approved=True,
        )
        self.assertTrue(any("READY_FOR_PHASE_3_V1.8" in error for error in errors), errors)

    def test_current_v18_physical_carrier_contract_is_complete(self) -> None:
        repository_root = MODULE_PATH.parents[1]

        self.assertEqual([], MODULE.validate_v18_physical_carriers(repository_root))

    def test_current_v18_migration_gate_evidence_matches_generated_contract(self) -> None:
        repository_root = MODULE_PATH.parents[1]

        self.assertEqual([], MODULE.validate_v18_migration_gate_evidence(repository_root))

    def test_current_fcom001_v70_required_target_mappings_are_complete(self) -> None:
        repository_root = MODULE_PATH.parents[1]

        self.assertEqual([], MODULE.validate_fcom001_v70_required_mappings(repository_root))

    def test_current_fcom001_acceptance_stage_binding_contract_is_complete(self) -> None:
        repository_root = MODULE_PATH.parents[1]

        self.assertEqual([], MODULE.validate_fcom001_acceptance_stage_binding(repository_root))

    def test_current_fcom001_contract_admin_scope_is_complete(self) -> None:
        repository_root = MODULE_PATH.parents[1]

        self.assertEqual([], MODULE.validate_fcom001_contract_admin_scope(repository_root))

    def test_fcom001_contract_admin_scope_rejects_each_missing_rule(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        for relative, snippets in MODULE.FCOM001_CONTRACT_ADMIN_SCOPE_REQUIRED_SNIPPETS.items():
            for snippet in snippets:
                with self.subTest(relative=relative, snippet=snippet), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    for source_relative in MODULE.FCOM001_CONTRACT_ADMIN_SCOPE_REQUIRED_SNIPPETS:
                        source = repository_root / source_relative
                        target = root / source_relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source, target)
                    target = root / relative
                    target.write_text(
                        target.read_text(encoding="utf-8").replace(snippet, "REMOVED_RULE"),
                        encoding="utf-8",
                    )

                    errors = MODULE.validate_fcom001_contract_admin_scope(root)

                    self.assertTrue(any(snippet in error for error in errors), errors)

    def test_fcom001_contract_admin_scope_rejects_blocked_rules(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        for relative, snippets in MODULE.FCOM001_CONTRACT_ADMIN_SCOPE_FORBIDDEN_SNIPPETS.items():
            for snippet in snippets:
                with self.subTest(relative=relative, snippet=snippet), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    for source_relative in MODULE.FCOM001_CONTRACT_ADMIN_SCOPE_REQUIRED_SNIPPETS:
                        source = repository_root / source_relative
                        target = root / source_relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source, target)
                    target = root / relative
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n{snippet}\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate_fcom001_contract_admin_scope(root)

                    self.assertTrue(any(snippet in error for error in errors), errors)

    def test_fcom001_acceptance_stage_binding_rejects_each_missing_rule(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        for relative, snippets in MODULE.FCOM001_ACCEPTANCE_STAGE_REQUIRED_SNIPPETS.items():
            for snippet in snippets:
                with self.subTest(relative=relative, snippet=snippet), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    for source_relative in MODULE.FCOM001_ACCEPTANCE_STAGE_REQUIRED_SNIPPETS:
                        source = repository_root / source_relative
                        target = root / source_relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source, target)
                    target = root / relative
                    target.write_text(
                        target.read_text(encoding="utf-8").replace(snippet, "REMOVED_RULE"),
                        encoding="utf-8",
                    )

                    errors = MODULE.validate_fcom001_acceptance_stage_binding(root)

                    self.assertTrue(any(snippet in error for error in errors), errors)

    def test_fcom001_acceptance_stage_binding_rejects_superseded_rules(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        for relative, snippets in MODULE.FCOM001_ACCEPTANCE_STAGE_FORBIDDEN_SNIPPETS.items():
            for snippet in snippets:
                with self.subTest(relative=relative, snippet=snippet), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    for source_relative in MODULE.FCOM001_ACCEPTANCE_STAGE_REQUIRED_SNIPPETS:
                        source = repository_root / source_relative
                        target = root / source_relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source, target)
                    target = root / relative
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n{snippet}\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate_fcom001_acceptance_stage_binding(root)

                    self.assertTrue(any(snippet in error for error in errors), errors)

    def test_fcom001_v70_required_target_mapping_rejects_each_missing_field(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        contract_text = (
            repository_root / "docs" / "traceability" / "domain-entity-migration-contract.json"
        ).read_text(encoding="utf-8")
        for object_name, expected in MODULE.FCOM001_V70_REQUIRED_TARGET_MAPPINGS.items():
            for target_field in expected:
                with self.subTest(object_name=object_name, target_field=target_field), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    contract_path = root / "docs" / "traceability" / "domain-entity-migration-contract.json"
                    contract_path.parent.mkdir(parents=True)
                    payload = json.loads(contract_text)
                    record = next(item for item in payload["records"] if item["object"] == object_name)
                    source = next(item for item in record["sources"] if item.get("gate") == "F-COM-001")
                    source["requiredTargetMappings"].pop(target_field)
                    contract_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

                    errors = MODULE.validate_fcom001_v70_required_mappings(root)

                    self.assertTrue(any(target_field in error for error in errors), errors)

    def test_v18_migration_gate_evidence_rejects_stale_phase2_summary(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "domain-entity-migration-contract.json",
                root / "docs" / "traceability" / "domain-entity-migration-contract.json",
            )
            shutil.copytree(
                repository_root / "docs" / "engineering" / "gates" / "phase-2",
                root / "docs" / "engineering" / "gates" / "phase-2",
            )
            gate = root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
            gate.write_text(
                gate.read_text(encoding="utf-8").replace(
                    "94对象/107来源绑定/1排除源",
                    "93对象/106来源绑定/1排除源",
                    1,
                ),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_migration_gate_evidence(root)

            self.assertTrue(any("gate-status.md" in error for error in errors), errors)

    def test_v18_physical_carrier_contract_rejects_missing_table(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            contract = root / "docs" / "traceability" / "phase2-contract-map.md"
            contract.write_text(
                contract.read_text(encoding="utf-8").replace(
                    "proj_project_task_execution_contract",
                    "removed_project_task_execution_contract",
                ),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertTrue(any("proj_project_task_execution_contract" in error for error in errors), errors)

    def test_v18_cutover_checklist_must_not_copy_dac_status(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            database = root / "docs" / "design" / "09-database-design.md"
            database.write_text(
                database.read_text(encoding="utf-8").replace(
                    "result_source_code",
                    "mapped_status_code",
                    1,
                ),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertTrue(any("DAC technical status" in error for error in errors), errors)

    def test_v18_cutover_checklist_rejects_renamed_dispatch_status(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            database = root / "docs" / "design" / "09-database-design.md"
            database.write_text(
                database.read_text(encoding="utf-8").replace(
                    "result_source_code/answer_snapshot",
                    "result_source_code/dispatch_status_code/answer_snapshot",
                    1,
                ),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertTrue(any("DAC technical status" in error for error in errors), errors)

    def test_v18_revalidation_keeps_previously_accepted_adr(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            gate = root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
            gate.write_text(
                gate.read_text(encoding="utf-8").replace("> 审查状态：`APPROVED`", "> 审查状态：`REVALIDATION_REQUIRED`", 1),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertEqual([], errors)

    def test_v18_revalidation_rejects_blocked_by_design_in_alignment(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            alignment = root / "docs" / "design" / "08a-domain-entity-migration-alignment.md"
            alignment.write_text(
                alignment.read_text(encoding="utf-8") + "\nBLOCKED_BY_DESIGN\n",
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertTrue(any("still contains BLOCKED_BY_DESIGN" in error for error in errors), errors)

    def test_v18_approved_gate_accepts_reviewed_adr(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            decision = root / "docs" / "decisions" / "0030-project-task-execution-contract-and-cutover-checklist-carriers.md"
            decision.write_text(
                decision.read_text(encoding="utf-8").replace("`PROPOSED_FOR_REVIEW`", "`ACCEPTED`", 1),
                encoding="utf-8",
            )
            gate = root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
            gate.write_text(
                gate.read_text(encoding="utf-8").replace(
                    "> 审查状态：`REVALIDATION_REQUIRED`",
                    "> 审查状态：`APPROVED`",
                    1,
                ),
                encoding="utf-8",
            )

            self.assertEqual([], MODULE.validate_v18_physical_carriers(root))

    def test_v18_cutover_result_requires_selection_interval(self) -> None:
        repository_root = MODULE_PATH.parents[1]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(repository_root / "docs" / "design", root / "docs" / "design")
            shutil.copytree(repository_root / "docs" / "decisions", root / "docs" / "decisions")
            shutil.copytree(repository_root / "docs" / "engineering" / "gates" / "phase-2", root / "docs" / "engineering" / "gates" / "phase-2")
            (root / "docs" / "traceability").mkdir(parents=True)
            shutil.copy2(
                repository_root / "docs" / "traceability" / "phase2-contract-map.md",
                root / "docs" / "traceability" / "phase2-contract-map.md",
            )
            database = root / "docs" / "design" / "09-database-design.md"
            database.write_text(
                database.read_text(encoding="utf-8").replace("selection_ended_at/", "", 1),
                encoding="utf-8",
            )

            errors = MODULE.validate_v18_physical_carriers(root)

            self.assertTrue(any("selection interval" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
