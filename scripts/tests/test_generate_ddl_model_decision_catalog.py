from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "generate_ddl_model_decision_catalog.py"
SPEC = importlib.util.spec_from_file_location("generate_ddl_model_decision_catalog", MODULE_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(GENERATOR)


class DdlModelDecisionCatalogTest(unittest.TestCase):
    def test_catalog_has_complete_concrete_scope(self) -> None:
        rendered = GENERATOR.render(ROOT)
        self.assertEqual(53, rendered.count("|T-"))
        self.assertEqual(53, rendered.count("|O-"))
        self.assertEqual(53, rendered.count("|PK-"))
        self.assertEqual(79, rendered.count("|FK-"))
        self.assertEqual(110, rendered.count("|IX-"))
        self.assertEqual(108, rendered.count("|UK-"))
        self.assertEqual(83, rendered.count("|CK-"))
        self.assertIn("1,113", rendered)


if __name__ == "__main__":
    unittest.main()
