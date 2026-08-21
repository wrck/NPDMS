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


if __name__ == "__main__":
    unittest.main()
