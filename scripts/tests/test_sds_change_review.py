"""Change-scoped admission must not silently run or approve a whole SDS phase."""
from __future__ import annotations

from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import sds_gate_contract as gate
import validate_sds_phase1 as phase1
import validate_sds_phase2 as phase2


class ChangeScopedReviewTest(unittest.TestCase):
    def test_default_commands_do_not_run_or_claim_full_audit(self):
        for module in (phase1, phase2):
            with self.subTest(module=module.__name__), tempfile.TemporaryDirectory() as directory:
                output = StringIO()
                with patch.object(module, "validate", side_effect=AssertionError("full audit ran")), redirect_stdout(output):
                    self.assertEqual(0, module.main(["--root", directory]))
                self.assertIn("NOT_RUN", output.getvalue())
                self.assertNotIn("PASS", output.getvalue())

    def test_explicit_audit_keeps_real_failure_exit_code(self):
        for module in (phase1, phase2):
            with self.subTest(module=module.__name__):
                output = StringIO()
                with patch.object(module, "validate", return_value=["tenant key missing"]) as validate, redirect_stdout(output):
                    self.assertEqual(1, module.main(["--audit"]))
                validate.assert_called_once()
                self.assertIn("tenant key missing", output.getvalue())
                self.assertIn("AUDIT-FAIL", output.getvalue())
                self.assertNotIn("AUDIT-PASS", output.getvalue())

    def test_explicit_audit_success_is_not_phase_approval(self):
        for module in (phase1, phase2):
            with self.subTest(module=module.__name__):
                output = StringIO()
                with patch.object(module, "validate", return_value=[]) as validate, redirect_stdout(output):
                    self.assertEqual(0, module.main(["--audit"]))
                validate.assert_called_once()
                self.assertIn("AUDIT-PASS", output.getvalue())
                self.assertNotIn("READY_FOR_PHASE", output.getvalue())
                self.assertNotIn("ready for Phase", output.getvalue())

    def test_technical_is_an_explicit_audit_alias(self):
        for module in (phase1, phase2):
            with self.subTest(module=module.__name__):
                with patch.object(module, "validate", return_value=["invalid Owner"]) as validate, redirect_stdout(StringIO()):
                    self.assertEqual(1, module.main(["--technical"]))
                validate.assert_called_once()

    def test_retired_phases_do_not_require_metadata_or_approval(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for phase in (1, 2):
                for technical in (False, True):
                    self.assertEqual([], gate.validate_gate(root, phase, technical=technical))
            self.assertTrue(gate.validate_gate(root, 3, technical=False))

    def test_phase1_inputs_do_not_include_phase2_physical_contracts(self):
        forbidden = {gate.MODEL, gate.DDL, "docs/traceability/domain-object-table-map.json"}
        self.assertFalse(forbidden.intersection(phase1.REQUIRED_FILES))
        self.assertFalse(any("gates/phase-2/" in name for name in phase1.REQUIRED_FILES))

    def test_phase1_content_audit_ignores_retired_gate_and_physical_audit(self):
        with patch.object(phase1, "validate_current_design", side_effect=AssertionError("cross-phase audit"), create=True):
            self.assertEqual([], phase1.validate(ROOT, technical=True))

    def test_schema_integrity_is_not_tied_to_whole_prd_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative in (gate.PRD, gate.MODEL, gate.DDL,
                             "docs/traceability/domain-object-table-map.json",
                             "docs/traceability/phase2-contract-map.md",
                             "docs/design/09-database-design.md", "docs/design/10-api-design.md",
                             "docs/design/11-event-design.md"):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, path)
            path = root / gate.MODEL
            model = json.loads(path.read_text(encoding="utf-8"))
            model["prdGitBlob"] = "0" * 40  # Test fixture only; do not rewrite live provenance.
            path.write_text(json.dumps(model), encoding="utf-8")
            self.assertEqual([], gate.validate_design(root))
            self.assertTrue(any("PRD identity" in error for error in gate.validate_design(root, check_prd_identity=True)))


if __name__ == "__main__":
    unittest.main()
