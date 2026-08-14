from __future__ import annotations

import importlib.util
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
            (design / name).write_text(content, encoding="utf-8")

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
                "| 目标版本 | V1 |\n"
            )
        (baseline / "prd-v1.7.md").write_text(
            "# PRD V1.7\n\n" + "\n".join(prd_blocks), encoding="utf-8"
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

    def test_historical_and_pending_dingtalk_clock_in_evidence_is_allowed(self) -> None:
        rows = (
            "| 历史排除 | 钉钉打卡 | 不属于当前V1/V2契约 |",
            "| A+B摘要 | 钉钉打卡候选 | PENDING_SOURCE_CONFIRMATION | 不进入当前契约 |",
        )
        for row in rows:
            with tempfile.TemporaryDirectory() as temporary:
                with self.subTest(row=row):
                    root = Path(temporary)
                    self.build_fixture(root)
                    target = root / "docs" / "design" / "12-integration-design.md"
                    target.write_text(
                        target.read_text(encoding="utf-8") + f"\n{row}\n",
                        encoding="utf-8",
                    )

                    errors = MODULE.validate(root)

                    self.assertFalse(any("DingTalk clock-in" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
