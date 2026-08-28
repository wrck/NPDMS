from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts/generate_requirement_traceability.py"
PRD = REPOSITORY_ROOT / "docs/baseline/prd-v1.8.md"
DOMAINS = REPOSITORY_ROOT / "specs/001-project-delivery-platform/domains"
MATRIX = REPOSITORY_ROOT / "docs/traceability/requirement-matrix.md"


class GenerateRequirementTraceabilityTest(unittest.TestCase):

    def run_generator(self, output: Path, *, check: bool) -> subprocess.CompletedProcess[str]:
        command = [
            sys.executable,
            str(SCRIPT),
            "--prd",
            str(PRD),
            "--domains",
            str(DOMAINS),
            "--output",
            str(output),
        ]
        if check:
            command.append("--check")
        return subprocess.run(command, cwd=REPOSITORY_ROOT, text=True, capture_output=True, check=False)

    def requirement_row(self, content: str, requirement_id: str) -> str:
        prefix = f"| {requirement_id} |"
        return next(line for line in content.splitlines() if line.startswith(prefix))

    def test_current_matrix_passes_read_only_check(self) -> None:
        result = self.run_generator(MATRIX, check=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS", result.stdout)

    def test_check_detects_generated_column_drift_without_rewriting_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)
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

    def test_customer_and_asset_feature_contracts_are_generator_owned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            self.assertIn("Customer本地生命周期命令；CRM来源同步处理流", content)
            self.assertIn("Device无独立生命周期状态机；来源同步状态与归属时态命令", content)
            self.assertIn("MES来源同步批次/映射处理流；不直接改写Device业务状态", content)
            self.assertIn("ITR来源同步批次/映射处理流；不直接改写Device业务状态", content)
            self.assertIn("CRM同步批次/单项处理流；不直接改写Customer本地生命周期", content)
            self.assertIn("ITR技术公告来源同步批次/版本映射流", content)
            self.assertIn("SPEC-FCUS001-FEATURE-READY-20260825-01", content)
            self.assertIn("SPEC-FAST001-FEATURE-READY-20260825-01", content)

    def test_known_requirement_slices_are_not_closed_by_partial_feature_completion(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            shutil.copyfile(MATRIX, output)

            result = self.run_generator(output, check=False)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            for requirement_id in ("PM-04", "PRE-04", "SOL-01"):
                row = self.requirement_row(content, requirement_id)
                self.assertIn("IMPLEMENTATION_PARTIAL", row)
            self.assertIn("其余业务对象接入未完成", self.requirement_row(content, "PM-04"))
            pm08_row = self.requirement_row(content, "PM-08")
            self.assertIn("V1（人工指派） / V2（自动指派）", pm08_row)
            self.assertIn("V1：IMPLEMENTATION_COMPLETE（人工指派）", pm08_row)
            self.assertIn("V2：NOT_STARTED（自动指派）", pm08_row)
            self.assertIn("F-SOL-003与SCH-01贯通未完成", self.requirement_row(content, "PRE-04"))
            self.assertIn("完整SOL-01未完成", self.requirement_row(content, "SOL-01"))


if __name__ == "__main__":
    unittest.main()
