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

    def build_fixture(self, root: Path) -> Path:
        design = root / "docs" / "design"
        traceability = root / "docs" / "traceability"
        design.mkdir(parents=True)
        traceability.mkdir(parents=True)
        metadata = (
            "# Test\n\n"
            "> 文档状态：`BASELINE`  \n"
            "> 适用基线：PRD V1.6  \n"
            "> Requirement ID：REQ-001  \n"
            "> Owner：Test  \n\n"
            "## Scope\n\n"
            "Thing pms_test /test\n"
        )
        for name in MODULE.PHASE2_DOCS:
            (design / name).write_text(metadata, encoding="utf-8")

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
        for number in range(1, 116):
            identifier = f"REQ-{number:03d}"
            rows.append(
                f"| {identifier} | {required} / "
                f"[P2契约](phase2-contract-map.md#{identifier.lower()}) | SDS-P2-BASELINE |"
            )
            contract_blocks.append(
                f"### {identifier}\n\n"
                "- 需求名称：Test\n"
                "- 数据对象：Thing\n"
                "- 数据表：pms_test\n"
                "- API：/test\n"
                "- 事件：N/A（同步命令或查询）\n"
                "- 外部集成：N/A（平台内部契约）\n"
                "- 文件契约：N/A（无文件）\n"
                "- 工作流/状态：测试状态守卫\n"
                "- 授权与数据范围：测试数据范围\n"
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
            matrix = matrix.replace("REQ-115", "REQ-114")
            matrix = matrix.replace("../design/10-api-design.md", "../design/missing-api.md", 1)
            matrix_path.write_text(matrix, encoding="utf-8")

            errors = MODULE.validate(root)
            self.assertTrue(any("expected 115 unique rows" in error for error in errors))
            self.assertTrue(any("trace link target missing" in error for error in errors))
            self.assertTrue(any("REQ-001 missing required Phase 2 trace link" in error for error in errors))

    def test_missing_explicit_contract_and_undefined_symbol_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
            content = contract_path.read_text(encoding="utf-8")
            content = content.replace("### REQ-115", "### REQ-999", 1)
            content = content.replace("- 数据表：pms_test", "- 数据表：pms_missing", 1)
            contract_path.write_text(content, encoding="utf-8")

            errors = MODULE.validate(root)
            self.assertTrue(any("contract IDs must exactly match" in error for error in errors))
            self.assertTrue(any("undefined table contract: pms_missing" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
