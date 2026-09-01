from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
import unittest
import json
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts/generate_requirement_traceability.py"
PRD = REPOSITORY_ROOT / "docs/baseline/prd-v1.8.md"
DOMAINS = REPOSITORY_ROOT / "specs/001-project-delivery-platform/domains"
MATRIX = REPOSITORY_ROOT / "docs/traceability/requirement-matrix.md"
COVERAGE = REPOSITORY_ROOT / "docs/traceability/requirement-version-coverage.json"
FEATURE_INDEX = REPOSITORY_ROOT / "specs/features/README.md"


class GenerateRequirementTraceabilityTest(unittest.TestCase):

    def run_generator(
        self,
        output: Path,
        *,
        check: bool,
        feature_index: Path = FEATURE_INDEX,
    ) -> subprocess.CompletedProcess[str]:
        coverage_output = output.with_name("requirement-version-coverage.json")
        command = [
            sys.executable,
            str(SCRIPT),
            "--prd",
            str(PRD),
            "--domains",
            str(DOMAINS),
            "--output",
            str(output),
            "--coverage-output",
            str(coverage_output),
            "--feature-index",
            str(feature_index),
        ]
        if check:
            command.append("--check")
        return subprocess.run(command, cwd=REPOSITORY_ROOT, text=True, capture_output=True, check=False)

    def test_check_detects_feature_index_status_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            feature_index = Path(temporary) / "feature-index.md"
            shutil.copyfile(MATRIX, output)
            shutil.copyfile(COVERAGE, output.with_name("requirement-version-coverage.json"))
            lines = FEATURE_INDEX.read_text(encoding="utf-8-sig").splitlines()
            row_index = next(
                index for index, line in enumerate(lines) if line.startswith("| [F-SOL-003]")
            )
            self.assertIn("IMPLEMENTATION_COMPLETE", lines[row_index])
            lines[row_index] = lines[row_index].replace(
                "IMPLEMENTATION_COMPLETE", "NOT_STARTED", 1
            )
            feature_index.write_text("\n".join(lines) + "\n", encoding="utf-8")

            result = self.run_generator(output, check=True, feature_index=feature_index)

            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("FEATURE INDEX DRIFT", result.stdout + result.stderr)

    def requirement_row(self, content: str, slice_key: str) -> str:
        prefix = f"| {slice_key} |"
        return next(line for line in content.splitlines() if line.startswith(prefix))

    def test_current_matrix_passes_read_only_check(self) -> None:
        result = self.run_generator(MATRIX, check=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS", result.stdout)

    def test_check_detects_generated_column_drift_without_rewriting_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)
            shutil.copyfile(COVERAGE, output.with_name("requirement-version-coverage.json"))
            content = output.read_text(encoding="utf-8-sig")
            self.assertIn("ServiceHandoverReference", content)
            drifted = content.replace("ServiceHandoverReference", "ServiceHandover", 1)
            output.write_text(drifted, encoding="utf-8", newline="\n")

            result = self.run_generator(output, check=True)

            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("DRIFT", result.stdout + result.stderr)
            self.assertEqual(drifted, output.read_text(encoding="utf-8"))

    def test_check_fails_when_output_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "missing.md"
            result = self.run_generator(output, check=True)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertFalse(output.exists())

    def test_aligned_requirement_attribute_tables_are_parsed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            cut01_row = self.requirement_row(output.read_text(encoding="utf-8"), "CUT-01@V1")
            self.assertIn("割接专项P1～P6", cut01_row)

    def test_current_prd_rebaseline_status_is_generator_owned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            self.assertIn("CHG-PRD-2026-08-28-005", content)
            self.assertIn("CHG-PRD-2026-08-29-006", content)
            self.assertIn("CHG-PRD-2026-08-29-007", content)
            self.assertIn("111个正式目标版本切片", content)
            self.assertIn("VS-001～VS-011均已裁决关闭", content)

    def test_customer_and_asset_feature_contracts_are_generator_owned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)
            shutil.copyfile(COVERAGE, output.with_name("requirement-version-coverage.json"))

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            self.assertIn("Customer本地生命周期命令；CRM来源同步处理流", content)
            self.assertIn("Device无独立生命周期状态机；来源同步状态与归属时态命令", content)
            self.assertIn("MES来源同步批次/映射处理流；不直接改写Device业务状态", content)
            self.assertIn("ITR来源同步批次/映射处理流；不直接改写Device业务状态", content)
            self.assertIn("CRM同步批次/单项处理流；不直接改写Customer本地生命周期", content)
            self.assertIn("ITR技术公告来源同步批次/版本映射流", content)
            self.assertIn("F-CUS-001", self.requirement_row(content, "CUS-03@V1"))
            self.assertIn("F-AST-001", self.requirement_row(content, "EQP-01@V1"))

    def test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)
            shutil.copyfile(COVERAGE, output.with_name("requirement-version-coverage.json"))

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            for slice_key in (
                "PM-01@V1",
                "PM-03@V1",
                "PM-04@V1",
                "PM-07@V1",
                "PM-08@V1",
                "PM-11@V1",
                "PRE-04@V1",
                "SOL-01@V2",
                "CUS-03@V1",
            ):
                row = self.requirement_row(content, slice_key)
                self.assertIn("IMPLEMENTATION_PARTIAL", row)
            for slice_key in ("PM-02@V1", "PM-10@V1", "PRE-01@V1", "PRE-02@V1", "PLT-02@V1"):
                self.assertIn("IMPLEMENTATION_COMPLETE", self.requirement_row(content, slice_key))
            self.assertIn("NOT_STARTED", self.requirement_row(content, "PM-08@V2"))
            asset_row = self.requirement_row(content, "EQP-01@V1")
            self.assertIn("F-AST-001 Task", asset_row)
            self.assertIn("IN_PROGRESS", asset_row)
            self.assertTrue(asset_row.endswith("| NOT_STARTED | NOT_STARTED |"), asset_row)

    def test_coverage_json_contains_all_111_unique_slices(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            coverage = json.loads(output.with_name("requirement-version-coverage.json").read_text(encoding="utf-8"))
            self.assertEqual(100, coverage["counts"]["requirements"])
            self.assertEqual({"V1": 53, "V2": 47}, coverage["counts"]["mainVersions"])
            self.assertEqual(111, coverage["counts"]["versionSlices"])
            self.assertEqual({"V1": 53, "V2": 58}, coverage["counts"]["slicesByVersion"])
            keys = [item["sliceKey"] for item in coverage["slices"]]
            self.assertEqual(111, len(keys))
            self.assertEqual(111, len(set(keys)))
            self.assertIn("PM-08@V2", keys)
            self.assertIn("NFR-02@V2", keys)


if __name__ == "__main__":
    unittest.main()
