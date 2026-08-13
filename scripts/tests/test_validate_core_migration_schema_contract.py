from __future__ import annotations

import hashlib
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "validate_core_migration_schema_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_core_migration_schema_contract", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
sys.path.insert(0, str(ROOT / "scripts"))
APPLY_PATH = ROOT / "scripts" / "apply_core_migration_schema_contract.py"
APPLY_SPEC = importlib.util.spec_from_file_location("apply_core_migration_schema_contract", APPLY_PATH)
APPLIER = importlib.util.module_from_spec(APPLY_SPEC)
assert APPLY_SPEC.loader is not None
APPLY_SPEC.loader.exec_module(APPLIER)


class CoreMigrationSchemaContractTest(unittest.TestCase):
    def valid_contract(self) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "decisionRef": "ADR-0022",
            "coverage": "CORE_MIGRATION_SUBSET",
            "crossDomainReferencePolicy": "LOGICAL_REFERENCE",
            "v3DesignOnlyTables": sorted(MODULE.V3_DESIGN_ONLY_TABLES),
            "externalKeyMapping": {
                "table": "plt_external_key_mapping",
                "targetRoleColumn": "target_role",
                "targetSequenceColumn": "target_sequence",
                "defaultTargetRole": "PRIMARY",
                "minimumTargetSequence": 0,
            },
            "acceptedDdlItems": sorted(MODULE.EXPECTED_ACCEPTED_DDL_ITEMS),
            "normalization": {
                "businessCode": "TRIM_UPPERCASE",
                "opaqueExternalKey": "BINARY_EXACT",
                "hash": "BINARY_EXACT",
                "displayName": "UNICODE_CASE_INSENSITIVE",
            },
            "permanentKeys": {
                "reuseAllowed": False,
                "objects": ["PROJECT", "CONTRACT", "SALES_ORDER", "DEVICE_SN", "SOURCE_KEY"],
                "uniqueKeyIncludesDeleted": False,
                "ddlUniqueKeys": ["uk_project_code", "uk_contract_business", "uk_sales_order_business", "uk_device_sn"],
            },
            "currentRecordUniqueness": "GENERATED_CURRENT_MARKER",
            "historicalAnomalyPolicy": "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE",
        }

    def valid_ddl(self) -> str:
        return """
CREATE TABLE proj_project (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_code VARCHAR(64) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (tenant_id, project_code)
) ENGINE = InnoDB;
CREATE TABLE ast_device_project_assignment (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  effective_to DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  current_device_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND effective_to IS NULL THEN device_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_current_device (tenant_id, current_device_id)
) ENGINE = InnoDB;
CREATE TABLE plt_external_key_mapping (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_pk VARCHAR(128) NOT NULL,
  target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
  target_sequence INT UNSIGNED NOT NULL DEFAULT 0,
  target_table VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_external_key_source_target (
    tenant_id, source_system, source_table, source_pk,
    target_role, target_sequence, target_table, target_id
  ),
  CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)
) ENGINE = InnoDB;
"""

    def test_accepts_confirmed_contract_and_safe_ddl(self) -> None:
        self.assertEqual([], MODULE.validate_contract(self.valid_contract(), self.valid_ddl()))

    def test_rejects_full_platform_claim_and_v3_table(self) -> None:
        contract = self.valid_contract()
        contract["coverage"] = "FULL_PLATFORM_MODEL"
        ddl = self.valid_ddl() + "\nCREATE TABLE kno_technical_advisory (id BIGINT) ENGINE = InnoDB;"
        errors = MODULE.validate_contract(contract, ddl)
        self.assertTrue(any("CORE_MIGRATION_SUBSET" in error for error in errors))
        self.assertTrue(any("V3 design-only" in error for error in errors))

    def test_rejects_cross_domain_foreign_key(self) -> None:
        ddl = self.valid_ddl().replace(
            "PRIMARY KEY (id),\n  UNIQUE KEY uk_current_device",
            "PRIMARY KEY (id),\n  CONSTRAINT fk_assignment_project FOREIGN KEY (tenant_id, device_id) REFERENCES proj_project (tenant_id, id),\n  UNIQUE KEY uk_current_device",
        )
        self.assertTrue(any("cross-domain foreign key" in error for error in MODULE.validate_contract(self.valid_contract(), ddl)))

    def test_rejects_missing_mapping_role_or_sequence(self) -> None:
        ddl = self.valid_ddl().replace("  target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',\n", "")
        self.assertTrue(any("target_role" in error for error in MODULE.validate_contract(self.valid_contract(), ddl)))

    def test_rejects_reusable_business_key_and_nullable_current_key(self) -> None:
        ddl = self.valid_ddl().replace(
            "UNIQUE KEY uk_project_code (tenant_id, project_code)",
            "UNIQUE KEY uk_project_code (tenant_id, project_code, deleted)",
        ).replace(
            "UNIQUE KEY uk_current_device (tenant_id, current_device_id)",
            "UNIQUE KEY uk_current_device (tenant_id, device_id, effective_to)",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("deleted" in error for error in errors))
        self.assertTrue(any("effective_to" in error for error in errors))

    def test_rejects_trailing_comma_before_table_close(self) -> None:
        ddl = self.valid_ddl().replace(
            "UNIQUE KEY uk_project_code (tenant_id, project_code)\n) ENGINE",
            "UNIQUE KEY uk_project_code (tenant_id, project_code),\n) ENGINE",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("trailing comma" in error for error in errors))

    def test_execution_evidence_must_match_current_ddl_hash_and_mysql_version(self) -> None:
        ddl = self.valid_ddl().encode("utf-8")
        evidence = {
            "status": "PASS",
            "purpose": "P3_E09_ISOLATED_MYSQL_DDL_EXECUTION",
            "ddlSha256": hashlib.sha256(ddl).hexdigest().upper(),
            "expectedTableCount": 3,
            "tableCount": 3,
            "mysqlVersion": "8.4.10",
        }
        self.assertEqual([], MODULE.validate_execution_evidence(evidence, ddl, 3))
        evidence["ddlSha256"] = "STALE"
        self.assertTrue(any("stale" in error for error in MODULE.validate_execution_evidence(evidence, ddl, 3)))

    def test_transform_removes_v3_tables_and_cross_domain_fk(self) -> None:
        ddl = self.valid_ddl() + """
CREATE TABLE kno_technical_advisory (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_technical_advisory_read_record (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_technical_advisory_product_relation (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_device_technical_advisory_match (id BIGINT) ENGINE = InnoDB;
"""
        transformed, summary = APPLIER.transform_ddl(ddl)
        self.assertEqual(4, summary["removedTables"])
        self.assertEqual(0, summary["removedCrossDomainForeignKeys"])
        self.assertNotIn("CREATE TABLE kno_", transformed)


if __name__ == "__main__":
    unittest.main()
