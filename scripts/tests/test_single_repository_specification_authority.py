"""Engineering authority: no second specification repository or snapshot state."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SingleRepositorySpecificationAuthorityTest(unittest.TestCase):
    def test_formal_sources_and_generator_are_in_this_repository(self):
        for relative in (
            "docs/baseline/prd-v1.8.md", "docs/engineering/00-engineering-chain.md",
            "docs/README.md", "scripts/generate_requirement_traceability.py",
            "specs/features/README.md", "tasks/features/README.md",
        ):
            with self.subTest(path=relative):
                self.assertTrue((ROOT / relative).is_file())

    def test_obsolete_external_snapshot_runtime_is_not_reintroduced(self):
        self.assertFalse((ROOT / "scripts/specification_baseline.py").exists())
        self.assertFalse((ROOT / "docs/specification-baseline/allowlist.json").exists())
        self.assertIn("不再维护外部规格仓快照", (ROOT / "AGENTS.md").read_text(encoding="utf-8"))

    def test_requirement_coverage_remains_a_generated_nonempty_projection(self):
        value = json.loads((ROOT / "docs/traceability/requirement-version-coverage.json").read_text(encoding="utf-8"))
        self.assertIsInstance(value, dict)
        self.assertTrue(value)
        self.assertIn("Implementation Done只从", (ROOT / "docs/engineering/00-engineering-chain.md").read_text(encoding="utf-8"))
