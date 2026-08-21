from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_project_code_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_project_code_contract", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class ProjectCodeContractValidatorTest(unittest.TestCase):
    def valid_contract(self) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "status": "ACCEPTED",
            "decisionRef": "ADR-0020",
            "requirementIds": sorted(VALIDATOR.EXPECTED_REQUIREMENTS),
            "rules": copy.deepcopy(VALIDATOR.EXPECTED_RULES),
            "columns": [
                {"table": "proj_project", "name": name}
                for name in sorted(VALIDATOR.EXPECTED_COLUMNS)
            ],
            "acceptedDdlItems": sorted(VALIDATOR.EXPECTED_DDL_ITEMS),
        }

    def valid_ddl(self) -> str:
        return """
CREATE TABLE proj_project (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_code VARCHAR(64) NOT NULL,
    code_root_id BIGINT NOT NULL,
    project_sequence INT UNSIGNED NOT NULL DEFAULT 0,
    code_rule_version VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_project_code (tenant_id, project_code),
    UNIQUE KEY uk_project_code_sequence (tenant_id, code_root_id, project_sequence),
    CONSTRAINT fk_project_code_root FOREIGN KEY (tenant_id, code_root_id) REFERENCES proj_project (tenant_id, id),
    CONSTRAINT chk_project_code_namespace CHECK (
        (project_sequence = 0 AND code_root_id = id)
        OR project_sequence > 0
    )
) ENGINE = InnoDB;
"""

    def test_accepts_confirmed_contract_and_ddl(self) -> None:
        self.assertEqual([], VALIDATOR.validate_payload(self.valid_contract()))
        self.assertEqual([], VALIDATOR.validate_ddl(self.valid_ddl()))

    def test_rejects_contract_rule_drift(self) -> None:
        contract = self.valid_contract()
        contract["rules"]["multipleContractsOrOrdersCreateNewCode"] = True
        self.assertIn("rules differ", "\n".join(VALIDATOR.validate_payload(contract)))

    def test_rejects_project_type_in_code_unique_key(self) -> None:
        ddl = self.valid_ddl().replace(
            "uk_project_code (tenant_id, project_code)",
            "uk_project_code (tenant_id, project_type, project_code)",
        )
        errors = "\n".join(VALIDATOR.validate_ddl(ddl))
        self.assertIn("project code tenant uniqueness", errors)
        self.assertIn("project_type must not participate", errors)

    def test_rejects_missing_namespace_constraint(self) -> None:
        ddl = self.valid_ddl().replace("CONSTRAINT chk_project_code_namespace", "CONSTRAINT chk_other")
        self.assertIn("code namespace check", "\n".join(VALIDATOR.validate_ddl(ddl)))


if __name__ == "__main__":
    unittest.main()
