from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_database_naming_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_database_naming_contract", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class DatabaseNamingContractValidatorTest(unittest.TestCase):
    def valid_contract(self) -> dict[str, object]:
        tables = [
            {"source": f"pms_table_{index}", "target": f"proj_table_{index}", "owner": "PROJ"}
            for index in range(52)
        ]
        fields = [
            {
                "id": f"NAM-00{index}",
                "sourceTable": tables[index - 1]["source"],
                "sourceColumn": f"old_{index}",
                "targetTable": tables[index - 1]["target"],
                "targetColumn": f"new_{index}",
                "evidence": "test",
            }
            for index in range(1, 7)
        ]
        return {
            "schemaVersion": 1,
            "status": "ACCEPTED",
            "decisionRef": "ADR-0019",
            "domainCodes": sorted(VALIDATOR.EXPECTED_DOMAINS),
            "allowedTableAbbreviations": {"configuration": "config", "serial_number": "sn"},
            "forbiddenTableTokens": ["rel", "ref", "map"],
            "tableExtensions": [{"source": "pm_project_market_relations_from_sms", "target": "cus_market_relation", "owner": "CUS", "decisionRef": "ADR-0021"}],
            "modelExtensions": copy.deepcopy(VALIDATOR.EXPECTED_MODEL_EXTENSIONS),
            "implementationScope": {"coverage": "CORE_MIGRATION_SUBSET", "decisionRef": "ADR-0022", "excludedTargets": []},
            "tables": tables,
            "fields": fields,
        }

    def test_rejects_duplicate_target_table(self) -> None:
        contract = self.valid_contract()
        contract["tables"][1]["target"] = contract["tables"][0]["target"]
        self.assertIn("duplicate target table", "\n".join(VALIDATOR.validate_payload(contract)))

    def test_rejects_unapproved_table_abbreviation(self) -> None:
        contract = self.valid_contract()
        contract["tables"][0]["target"] = "proj_order_contract_rel"
        self.assertIn("unapproved table abbreviation", "\n".join(VALIDATOR.validate_payload(contract)))

    def test_accepts_config_and_sn_table_abbreviations(self) -> None:
        contract = self.valid_contract()
        contract["tables"][0]["target"] = "proj_device_sn"
        contract["tables"][1]["target"] = "proj_execution_config"
        contract["fields"][0]["targetTable"] = "proj_device_sn"
        contract["fields"][1]["targetTable"] = "proj_execution_config"
        self.assertEqual([], VALIDATOR.validate_payload(contract))

    def test_rejects_field_table_mapping_mismatch(self) -> None:
        contract = self.valid_contract()
        contract["fields"][0]["targetTable"] = contract["tables"][1]["target"]
        self.assertIn("field decision table mapping mismatch", "\n".join(VALIDATOR.validate_payload(contract)))

    def test_ddl_must_apply_table_and_field_decisions(self) -> None:
        contract = self.valid_contract()
        ddl = "\n".join(
            f"CREATE TABLE {item['target']} (id BIGINT) ENGINE = InnoDB;"
            for item in contract["tables"] + contract["tableExtensions"] + contract["modelExtensions"]
        )
        first = contract["fields"][0]
        ddl = ddl.replace(
            f"CREATE TABLE {first['targetTable']} (id BIGINT)",
            f"CREATE TABLE {first['targetTable']} ({first['sourceColumn']} BIGINT)",
        )
        self.assertIn("field naming decision not applied", "\n".join(VALIDATOR.validate_ddl(contract, ddl)))

    def test_ddl_may_exclude_registered_v3_design_table(self) -> None:
        contract = self.valid_contract()
        excluded = contract["tables"][-1]["target"]
        contract["implementationScope"]["excludedTargets"] = [excluded]
        ddl = "\n".join(
            f"CREATE TABLE {item['target']} (id BIGINT) ENGINE = InnoDB;"
            for item in contract["tables"][:-1] + contract["tableExtensions"] + contract["modelExtensions"]
        )
        self.assertNotIn("DDL table set differs", "\n".join(VALIDATOR.validate_ddl(contract, ddl)))


if __name__ == "__main__":
    unittest.main()
