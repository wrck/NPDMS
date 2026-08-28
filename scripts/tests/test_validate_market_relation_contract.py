from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_market_relation_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_market_relation_contract", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class MarketRelationContractValidatorTest(unittest.TestCase):
    def test_table_body_is_exact(self) -> None:
        ddl = "CREATE TABLE cus_market_relation (id BIGINT, market_code VARCHAR(64)) ENGINE = InnoDB;"
        body = VALIDATOR.table_body(ddl, "cus_market_relation")
        self.assertIn("market_code", body)

    def test_required_field_set_preserves_expend_name(self) -> None:
        self.assertEqual(8, len(VALIDATOR.FIELDS))
        self.assertIn("expend_code", VALIDATOR.FIELDS)
        self.assertNotIn("expansion_code", VALIDATOR.FIELDS)


if __name__ == "__main__":
    unittest.main()
