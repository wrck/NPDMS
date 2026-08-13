from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).parents[1] / "validate_domain_entity_migration_alignment.py"
SPEC = importlib.util.spec_from_file_location("validate_domain_entity_migration_alignment", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class DomainEntityMigrationAlignmentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.impl = self.root / "implementation"
        (self.impl / "sql" / "migrations").mkdir(parents=True)
        (self.impl / "sql" / "migrations" / "V1__test.sql").write_text(
            "CREATE TABLE pms_eng_arrival (id bigint, renew_status varchar(20));\n", encoding="utf-8"
        )
        for relative in (
            "docs/traceability", "docs/design", "docs/engineering/gates/phase-3",
            "specs/001-project-delivery-platform/evidence/data-elements",
            "specs/001-project-delivery-platform/evidence/migration",
            "specs/001-project-delivery-platform/appendices",
        ):
            (self.root / relative).mkdir(parents=True, exist_ok=True)
        (self.root / "docs/traceability/phase2-contract-map.md").write_text(
            "### PM-01\n- 数据对象：Project\n- 数据表：pms_project\n\n"
            "### EXE-01\n- 数据对象：ArrivalAcceptance\n- 数据表：pms_imp_arrival_acceptance\n",
            encoding="utf-8",
        )
        (self.root / "docs/traceability/requirement-matrix.md").write_text(
            "| Requirement | 名称 | Owner |\n|---|---|---|\n"
            "| PM-01 | 项目 | PROJ（项目治理） |\n| EXE-01 | 到货 | IMP（现场实施） |\n",
            encoding="utf-8",
        )
        (self.root / "docs/design/02-domain-model.md").write_text("Project ArrivalAcceptance\n", encoding="utf-8")
        (self.root / "docs/design/08-data-model.md").write_text("Project ArrivalAcceptance\n", encoding="utf-8")
        (self.root / "docs/design/09-database-design.md").write_text("`proj_project` `imp_arrival_acceptance`\n", encoding="utf-8")
        (self.root / "docs/design/12-integration-design.md").write_text("CRM 现有采集平台 钉钉 ITR MES\n", encoding="utf-8")
        (self.root / "docs/design/08a-domain-entity-migration-alignment.md").write_text("AI-MIG-000\n", encoding="utf-8")
        (self.root / "docs/traceability/domain-entity-migration-contract.md").write_text("generated\n", encoding="utf-8")
        schema = {"tableName": "pm_project", "fieldName": "id"}
        (self.root / "specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl").write_text(
            json.dumps(schema) + "\n", encoding="utf-8"
        )
        ddl = self.root / "specs/001-project-delivery-platform/appendices/test.sql"
        ddl.write_text("CREATE TABLE proj_project (id bigint);\n", encoding="utf-8")
        ddl_sha = hashlib.sha256(ddl.read_bytes()).hexdigest().upper()
        self.ddl_review = {
            "inputs": {"ddlPath": "specs/001-project-delivery-platform/appendices/test.sql", "currentDdlSha256": ddl_sha},
            "decisionPolicy": {"current": "DEFER", "approvedDdlSha256": None},
        }
        self._write_json("specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json", self.ddl_review)
        self.gate = {"overallStatus": "NOT_READY_FOR_SDS_BASELINE", "items": [{"id": "P3-E09", "status": "OPEN"}]}
        self._write_json("docs/engineering/gates/phase-3/phase3-evidence-register.json", self.gate)
        self.contract = {
            "implementationRepo": str(self.impl), "implementationCommit": "TEST_COMMIT", "implementationTreeState": "CLEAN",
            "records": [
                {
                    "object": "Project", "owner": "PROJ", "requirementIds": ["PM-01"], "targetTables": ["proj_project"],
                    "sources": [{"sourceType": "LEGACY_TABLE", "sourceObject": "pm_project", "evidenceRef": "data-elements://schema-records.jsonl#table=pm_project", "disposition": "STRUCTURED", "transform": "map", "mappingStatus": "READY", "gate": "AI-MIG-000"}],
                },
                {
                    "object": "ArrivalAcceptance", "owner": "IMP", "requirementIds": ["EXE-01"], "targetTables": ["imp_arrival_acceptance"],
                    "sources": [{"sourceType": "CURRENT_TABLE", "sourceObject": "pms_eng_arrival", "evidenceRef": "implementation://TEST_COMMIT/sql/migrations/V1__test.sql#table=pms_eng_arrival", "disposition": "CURRENT_FORWARD", "transform": "map", "mappingStatus": "READY", "gate": "NEXT_FLYWAY"}],
                },
            ],
        }
        self.contract["objectTableMap"] = {
            record["object"]: {"owner": record["owner"], "requirementIds": record["requirementIds"], "targetTables": record["targetTables"]}
            for record in self.contract["records"]
        }
        self._save_contract()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _write_json(self, relative: str, value: dict) -> None:
        (self.root / relative).write_text(json.dumps(value), encoding="utf-8")

    def _save_contract(self) -> None:
        self._write_json("docs/traceability/domain-entity-migration-contract.json", self.contract)
        self._write_json("docs/traceability/domain-object-table-map.json", {"schemaVersion": 1, "objects": self.contract["objectTableMap"]})

    def _validate(self) -> list[str]:
        commit_result = type("Completed", (), {"stdout": "TEST_COMMIT\n"})()
        clean_result = type("Completed", (), {"stdout": ""})()
        with patch.object(VALIDATOR.subprocess, "run", side_effect=[commit_result, clean_result]):
            return VALIDATOR.validate(self.root, self.impl)

    def test_complete_contract_passes(self) -> None:
        self.assertEqual([], self._validate())

    def test_wrong_owner_fails(self) -> None:
        self.contract["records"][0]["owner"] = "IMP"
        self._save_contract()
        self.assertTrue(any("Owner is not backed" in error for error in self._validate()))

    def test_cross_domain_reference_does_not_take_object_ownership(self) -> None:
        self.assertEqual("PLT", VALIDATOR.expected_object_owner("FileArtifact", {"PLT", "SOL"}))

    def test_undeclared_target_table_fails(self) -> None:
        self.contract["records"][0]["targetTables"] = ["proj_unknown"]
        self._save_contract()
        self.assertTrue(any("target table not declared" in error for error in self._validate()))

    def test_feature_forward_migration_allows_logical_object_without_current_table(self) -> None:
        errors = VALIDATOR.validate_target_table_policy(
            "TechnicalNoticeReference",
            "KNO",
            {"INT-04"},
            [],
            {
                "targetTablePolicy": "FEATURE_FORWARD_MIGRATION",
                "featureRequirementId": "INT-04",
            },
            "| Knowledge | `TechnicalNoticeReference`逻辑对象；物理表由INT-04 Feature前向迁移确定 |",
        )
        self.assertEqual([], errors)

    def test_existing_table_owned_by_another_object_fails(self) -> None:
        self.contract["records"][0]["targetTables"] = ["imp_arrival_acceptance"]
        self._save_contract()
        self.assertTrue(any("do not exactly match" in error for error in self._validate()))

    def test_missing_current_source_fails(self) -> None:
        self.contract["records"][1]["sources"][0]["sourceObject"] = "pms_missing"
        self._save_contract()
        self.assertTrue(any("current source table not found" in error for error in self._validate()))

    def test_legacy_system_prefix_target_fails(self) -> None:
        self.contract["records"][0]["targetTables"] = ["pms_project"]
        self._save_contract()
        self.assertTrue(any("legacy system prefix" in error for error in self._validate()))

    def test_wrong_domain_prefix_target_fails(self) -> None:
        self.contract["records"][0]["targetTables"] = ["ast_project"]
        self._save_contract()
        self.assertTrue(any("owner prefix mismatch" in error for error in self._validate()))

    def test_missing_legacy_source_fails(self) -> None:
        self.contract["records"][0]["sources"][0]["sourceObject"] = "pm_missing"
        self._save_contract()
        self.assertTrue(any("absent from structured data elements" in error for error in self._validate()))

    def test_combined_disposition_fails(self) -> None:
        self.contract["records"][0]["sources"][0]["disposition"] = "STRUCTURED+EXCLUDED"
        self._save_contract()
        self.assertTrue(any("combined disposition" in error for error in self._validate()))

    def test_unapproved_ddl_cannot_close_gate(self) -> None:
        self.gate["overallStatus"] = "READY_FOR_SDS_BASELINE"
        self.gate["items"][0]["status"] = "CLOSED"
        self._write_json("docs/engineering/gates/phase-3/phase3-evidence-register.json", self.gate)
        self.assertTrue(any("must keep P3-E09 OPEN" in error for error in self._validate()))


if __name__ == "__main__":
    unittest.main()
