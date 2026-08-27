from __future__ import annotations

import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_domain_entity_migration_alignment.py"
SPEC = importlib.util.spec_from_file_location("validate_domain_entity_migration_alignment", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)
POLICY = sys.modules[VALIDATOR.validate_model_baseline.__module__]

GENERATOR_PATH = Path(__file__).parents[1] / "generate_domain_entity_migration_contract.py"
GENERATOR_SPEC = importlib.util.spec_from_file_location("generate_domain_entity_migration_contract", GENERATOR_PATH)
GENERATOR = importlib.util.module_from_spec(GENERATOR_SPEC)
assert GENERATOR_SPEC.loader is not None
GENERATOR_SPEC.loader.exec_module(GENERATOR)


class DomainEntityMigrationAlignmentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.impl = self.root / "implementation"
        (self.impl / "sql" / "migrations").mkdir(parents=True)
        (self.impl / "sql" / "migrations" / "V1__test.sql").write_text(
            "CREATE TABLE pms_eng_arrival (id bigint, renew_status varchar(20));\n", encoding="utf-8"
        )
        subprocess.run(["git", "init", "-q"], cwd=self.impl, check=True)
        subprocess.run(["git", "config", "user.name", "migration-test"], cwd=self.impl, check=True)
        subprocess.run(["git", "config", "user.email", "migration-test@example.invalid"], cwd=self.impl, check=True)
        self.pinned_commit = self._commit_implementation("seed migration")
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
        schema_records = [
            {"sheet": "项目管理", "row": 1, "cell": "A1", "tableName": "pm_project", "fieldName": "id"},
            {"sheet": "项目管理", "row": 2, "cell": "A2", "tableName": "pm_project", "fieldName": "customerName"},
            {"sheet": "项目管理", "row": 3, "cell": "A3", "tableName": "pm_project", "fieldName": "processHour"},
        ]
        (self.root / "specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl").write_text(
            "\n".join(json.dumps(record, ensure_ascii=False) for record in schema_records) + "\n", encoding="utf-8"
        )
        ddl = self.root / "specs/001-project-delivery-platform/appendices/test.sql"
        ddl.write_text(
            "CREATE TABLE proj_project (\n"
            "  id bigint,\n  source_project_id varchar(128),\n  customer_code varchar(64),\n"
            "  customer_name varchar(255),\n  duration_hours decimal(20,6),\n"
            "  direction_code varchar(32),\n  signed_adjustment_hours decimal(20,6)\n"
            ") ENGINE=InnoDB;\n",
            encoding="utf-8",
        )
        ddl_sha = hashlib.sha256(ddl.read_bytes()).hexdigest().upper()
        self.ddl_review = {
            "inputs": {"ddlPath": "specs/001-project-delivery-platform/appendices/test.sql", "currentDdlSha256": ddl_sha},
            "decisionPolicy": {"current": "DEFER"},
        }
        self._write_json("specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json", self.ddl_review)
        self._write_json(
            "docs/traceability/core-migration-schema-contract.json",
            {"v17Delta": {"objectTargetTables": {"Project": ["proj_project"]}}},
        )
        self.gate = {
            "modelEvidenceStatus": "MODEL_BASELINE_NOT_READY",
            "items": [{
                "id": "P3-E09", "status": "OPEN", "decisionOwner": None, "reviewOwner": None,
                "confirmedFacts": {}, "evidenceRefs": [],
                "blocks": ["HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"],
            }],
        }
        self._write_json("docs/engineering/gates/phase-3/phase3-evidence-register.json", self.gate)
        self.contract = {
            "implementationRepo": str(self.impl), "implementationCommit": self.pinned_commit,
            "implementationEvidenceMode": "PINNED_GIT_COMMIT",
            "excludedSources": [{
                "sourceType": "LEGACY_TABLE", "sourceObject": "pm_project_maintenance",
                "disposition": "EXCLUDED", "mappingStatus": "NO_MIGRATION",
                "transform": "NO_MIGRATION: requirement owner confirmation on 2026-08-13",
                "gate": "USER_CONFIRMED_EXCLUSION", "targetFieldBindings": [],
                "exclusionAudit": {
                    "decisionDate": "2026-08-13", "decisionSource": "REQUIREMENT_OWNER_CONFIRMATION",
                    "sourceTable": "pm_project_maintenance", "rowCount": None,
                    "extractionBatchSha256": None, "auditStatus": "PENDING_EXTRACTION_AUDIT",
                },
            }],
            "records": [
                {
                    "object": "Project", "owner": "PROJ", "requirementIds": ["PM-01"], "targetTables": ["proj_project"],
                    "sources": [{"sourceType": "LEGACY_TABLE", "sourceObject": "pm_project", "evidenceRef": "data-elements://schema-records.jsonl#table=pm_project", "disposition": "STRUCTURED", "transform": "map", "mappingStatus": "READY", "gate": "AI-MIG-000", "targetFieldBindings": [{"sourceField": "pm_project.id", "targetField": "proj_project.source_project_id", "transform": "EXTERNAL_KEY_MAPPING source-key preservation; target id is NEW_GENERATED", "evidenceRef": "data-elements://schema-records.jsonl#项目管理!A1"}], "statusMapping": {"policy": "NO_SOURCE_STATUS"}, "terminalDisposition": "MIGRATE_STRUCTURED_AND_PRESERVE_RAW"}],
                },
                {
                    "object": "ArrivalAcceptance", "owner": "IMP", "requirementIds": ["EXE-01"], "targetTables": ["imp_arrival_acceptance"],
                    "sources": [{"sourceType": "CURRENT_TABLE", "sourceObject": "pms_eng_arrival", "evidenceRef": f"implementation://{self.pinned_commit}/sql/migrations/V1__test.sql#table=pms_eng_arrival", "disposition": "CURRENT_FORWARD", "transform": "map", "mappingStatus": "READY", "gate": "NEXT_FLYWAY"}],
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

    def _commit_implementation(self, message: str) -> str:
        subprocess.run(["git", "add", "sql/migrations"], cwd=self.impl, check=True)
        subprocess.run(["git", "commit", "-q", "-m", message], cwd=self.impl, check=True)
        return subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.impl, check=True,
            text=True, encoding="utf-8", stdout=subprocess.PIPE,
        ).stdout.strip()

    def _save_contract(self) -> None:
        legacy_bindings = [
            binding
            for record in self.contract["records"]
            for source in record["sources"]
            if source["sourceType"] in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}
            for binding in source.get("targetFieldBindings", [])
            if binding["targetField"].split(".", 1)[0] == "proj_project"
        ]
        source_fields = [
            f"{table}.{field}"
            for binding in legacy_bindings
            for table, field in VALIDATOR.expanded_source_fields(binding["sourceField"])
        ]
        self.contract["bindingStatistics"] = {
            "allBindingCount": sum(
                len(source.get("targetFieldBindings", []))
                for record in self.contract["records"]
                for source in record["sources"]
            ),
            "v17BindingCount": len(legacy_bindings),
            "v17LegacyBindingCount": len(legacy_bindings),
            "v17LegacySourceTableCount": len({field.split(".", 1)[0] for field in source_fields}),
            "v17LegacySourceFieldCount": len(source_fields),
            "v17LegacyUniqueSourceFieldCount": len(set(source_fields)),
        }
        self._write_json("docs/traceability/domain-entity-migration-contract.json", self.contract)
        self._write_json("docs/traceability/domain-object-table-map.json", {"schemaVersion": 1, "objects": self.contract["objectTableMap"]})

    def _validate(self) -> list[str]:
        return VALIDATOR.validate(self.root, self.impl)

    def _enable_model_ready_without_migration_approval(self) -> None:
        ddl_sha = self.ddl_review["inputs"]["currentDdlSha256"]
        items = [{"itemId": "COLUMN:proj_project:id", "decision": "ACCEPT_CURRENT"}]
        items_sha = POLICY.canonical_items_sha256(items)
        register = {
            "currentDdlSha256": ddl_sha,
            "itemsSha256": items_sha,
            "items": items,
        }
        self._write_json("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json", register)
        review_ref = "docs/engineering/gates/phase-3/independent-review.md"
        (self.root / review_ref).write_text(
            "> status: `APPROVED`\n> conclusion: `GO`\n"
            f"> ddlSha256: `{ddl_sha}`\n> itemsSha256: `{items_sha}`\n> itemCount: `1`\n"
            "> deferCount: `0`\n> testResult: `PASS`\n",
            encoding="utf-8",
        )
        facts = {
            "modelDecisionStatus": "MODEL_BASELINE_READY",
            "currentDdlSha256": ddl_sha,
            "targetCatalogDdlSha256": ddl_sha,
            "mappingDdlSha256": ddl_sha,
            "validationDdlSha256": ddl_sha,
            "manifestDdlSha256": ddl_sha,
            "itemsSha256": items_sha,
            "itemIdsSha256": VALIDATOR.item_ids_sha256(register["items"]),
            "deferredItemCount": 0,
            "mysql84DdlSha256": ddl_sha,
            "isolatedMysqlExecution": {"status": "PASS"},
            "independentReviewResult": "GO",
            "independentReviewRef": review_ref,
        }
        item = self.gate["items"][0]
        item.update({
            "status": "VERIFIED", "decisionOwner": "requirement-owner",
            "reviewOwner": "independent-reviewer", "confirmedFacts": facts,
            "evidenceRefs": [review_ref],
        })
        self._write_json("docs/engineering/gates/phase-3/phase3-evidence-register.json", self.gate)

    def test_complete_contract_passes(self) -> None:
        self.assertEqual([], self._validate())

    def test_head_can_advance_without_changing_pinned_migrations(self) -> None:
        (self.impl / "README.md").write_text("later implementation work\n", encoding="utf-8")
        subprocess.run(["git", "add", "README.md"], cwd=self.impl, check=True)
        subprocess.run(["git", "commit", "-q", "-m", "advance head"], cwd=self.impl, check=True)
        self.assertEqual([], self._validate())

    def test_generator_reads_pinned_commit_instead_of_worktree(self) -> None:
        (self.impl / "sql/migrations/V1__test.sql").write_text(
            "CREATE TABLE pms_wrong_worktree_table (id bigint);\n", encoding="utf-8"
        )
        catalog = GENERATOR.git_table_catalog(self.impl, self.pinned_commit, "sql/migrations")
        self.assertIn("pms_eng_arrival", catalog)
        self.assertNotIn("pms_wrong_worktree_table", catalog)

    def test_generator_resolves_head_to_canonical_commit(self) -> None:
        self.assertEqual(self.pinned_commit, GENERATOR.resolve_git_commit(self.impl, "HEAD"))

    def test_validator_rejects_noncanonical_commit_references(self) -> None:
        subprocess.run(["git", "branch", "evidence-ref", self.pinned_commit], cwd=self.impl, check=True)
        for invalid_ref in ("HEAD", "evidence-ref", self.pinned_commit[:8]):
            with self.subTest(ref=invalid_ref):
                self.contract["implementationCommit"] = invalid_ref
                self._save_contract()
                self.assertTrue(any("canonical 40-character lowercase SHA" in error for error in self._validate()))

    def test_moving_ref_does_not_change_generated_contract_binding(self) -> None:
        subprocess.run(["git", "branch", "evidence-ref", self.pinned_commit], cwd=self.impl, check=True)
        resolved_commit = GENERATOR.resolve_git_commit(self.impl, "evidence-ref")
        self.assertEqual(self.pinned_commit, resolved_commit)
        (self.impl / "README.md").write_text("advance ref\n", encoding="utf-8")
        subprocess.run(["git", "add", "README.md"], cwd=self.impl, check=True)
        subprocess.run(["git", "commit", "-q", "-m", "advance ref target"], cwd=self.impl, check=True)
        subprocess.run(["git", "branch", "-f", "evidence-ref", "HEAD"], cwd=self.impl, check=True)
        self.assertNotEqual(resolved_commit, GENERATOR.resolve_git_commit(self.impl, "evidence-ref"))
        self.assertEqual(resolved_commit, self.contract["implementationCommit"])
        self.assertEqual([], self._validate())

    def test_incompatible_pinned_commit_fails(self) -> None:
        (self.impl / "sql/migrations/V1__test.sql").write_text(
            "CREATE TABLE pms_incompatible (id bigint);\n", encoding="utf-8"
        )
        incompatible_commit = self._commit_implementation("incompatible migration")
        self.contract["implementationCommit"] = incompatible_commit
        self.contract["records"][1]["sources"][0]["evidenceRef"] = (
            f"implementation://{incompatible_commit}/sql/migrations/V1__test.sql#table=pms_eng_arrival"
        )
        self._save_contract()
        self.assertTrue(any("current source table not found" in error for error in self._validate()))

    def test_missing_pinned_commit_fails(self) -> None:
        self.contract["implementationCommit"] = "0" * 40
        self._save_contract()
        self.assertTrue(any("frozen implementation commit does not exist" in error for error in self._validate()))

    def test_missing_migration_path_at_pinned_commit_fails(self) -> None:
        (self.impl / "sql/migrations/V1__test.sql").unlink()
        subprocess.run(["git", "add", "-u", "sql/migrations"], cwd=self.impl, check=True)
        subprocess.run(["git", "commit", "-q", "-m", "remove migrations"], cwd=self.impl, check=True)
        missing_path_commit = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=self.impl, check=True,
            text=True, encoding="utf-8", stdout=subprocess.PIPE,
        ).stdout.strip()
        self.contract["implementationCommit"] = missing_path_commit
        self._save_contract()
        self.assertTrue(any("migration path does not exist" in error for error in self._validate()))

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

    def test_feature_forward_migration_allows_declared_future_tables(self) -> None:
        errors = VALIDATOR.validate_target_table_policy(
            "CustomerServiceLevelRevision",
            "CUS",
            {"CUS-02"},
            ["cus_customer_service_level_revision"],
            {
                "targetTablePolicy": "FEATURE_FORWARD_MIGRATION",
                "featureRequirementId": "CUS-02",
            },
            "`cus_customer_service_level_revision`；物理表由CUS-02 Feature前向迁移确定",
            set(),
        )
        self.assertEqual([], errors)

    def test_feature_forward_migration_cannot_leak_into_current_core_ddl(self) -> None:
        errors = VALIDATOR.validate_target_table_policy(
            "CustomerServiceLevelRevision",
            "CUS",
            {"CUS-02"},
            ["cus_customer_service_level_revision"],
            {
                "targetTablePolicy": "FEATURE_FORWARD_MIGRATION",
                "featureRequirementId": "CUS-02",
            },
            "`cus_customer_service_level_revision`；物理表由CUS-02 Feature前向迁移确定",
            {"cus_customer_service_level_revision"},
        )
        self.assertTrue(any("leaked into current core DDL" in error for error in errors), errors)

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

    def test_structured_source_without_target_field_bindings_fails(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"] = []
        self._save_contract()
        self.assertTrue(any("zero target field bindings" in error for error in self._validate()))

    def test_source_business_key_cannot_bind_generated_target_id(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0]["targetField"] = "proj_project.id"
        self._save_contract()
        self.assertTrue(any("generated target id" in error for error in self._validate()))

    def test_binding_evidence_coordinate_must_match_source_field(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0]["evidenceRef"] = "data-elements://schema-records.jsonl#项目管理!A2"
        self._save_contract()
        self.assertTrue(any("evidence does not contain source field" in error for error in self._validate()))

    def test_binding_source_table_must_belong_to_source_object(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0]["sourceField"] = "unrelated.id"
        self._save_contract()
        self.assertTrue(any("outside declared sourceObject" in error for error in self._validate()))

    def test_name_field_cannot_bind_code_column(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0] = {
            "sourceField": "pm_project.customerName",
            "targetField": "proj_project.customer_code",
            "transform": "direct",
            "evidenceRef": "data-elements://schema-records.jsonl#项目管理!A2",
        }
        self._save_contract()
        self.assertTrue(any("name field cannot bind code column" in error for error in self._validate()))

    def test_duration_field_cannot_bind_signed_adjustment(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0] = {
            "sourceField": "pm_project.processHour",
            "targetField": "proj_project.signed_adjustment_hours",
            "transform": "direct",
            "evidenceRef": "data-elements://schema-records.jsonl#项目管理!A3",
        }
        self._save_contract()
        self.assertTrue(any("duration field cannot bind adjustment/direction" in error for error in self._validate()))

    def test_target_reference_id_requires_explicit_resolution_strategy(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["targetFieldBindings"][0]["transform"] = "direct"
        self._save_contract()
        self.assertTrue(any("requires explicit target-key resolution" in error for error in self._validate()))

    def test_pm_project_maintenance_can_only_be_excluded(self) -> None:
        source = self.contract["records"][0]["sources"][0]
        source["sourceObject"] = "pm_project_maintenance"
        self._save_contract()
        self.assertTrue(any("must not attach pm_project_maintenance" in error for error in self._validate()))

    def test_excluded_source_must_have_zero_target_bindings(self) -> None:
        self.contract["excludedSources"][0]["targetFieldBindings"] = [{"targetField": "proj_project.customer_name"}]
        self._save_contract()
        self.assertTrue(any("top-level exclusion audit is incomplete" in error for error in self._validate()))

    def test_binding_statistics_cannot_be_hard_coded(self) -> None:
        self._save_contract()
        self.contract["bindingStatistics"]["v17BindingCount"] = 30
        self._write_json("docs/traceability/domain-entity-migration-contract.json", self.contract)
        self.assertTrue(any("binding statistics must be derived" in error for error in self._validate()))

    def test_model_ready_without_migration_approval_is_valid(self) -> None:
        self._enable_model_ready_without_migration_approval()
        self.assertEqual([], self._validate())

    def test_model_ready_keeps_migration_and_cutover_blocked(self) -> None:
        self._enable_model_ready_without_migration_approval()
        self.gate["items"][0]["blocks"] = ["HISTORICAL_DATA_MIGRATION"]
        self._write_json("docs/engineering/gates/phase-3/phase3-evidence-register.json", self.gate)
        self.assertTrue(any("must keep historical migration and data cutover blocked" in error for error in self._validate()))


class CurrentV18PhysicalCarrierMigrationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repository_root = MODULE_PATH.parents[1]
        cls.contract = json.loads(
            (cls.repository_root / "docs/traceability/domain-entity-migration-contract.json").read_text(encoding="utf-8")
        )
        cls.records = {record["object"]: record for record in cls.contract["records"]}

    def test_v18_physical_carrier_objects_have_exact_target_tables(self) -> None:
        expected = {
            "TaskWorkBinding": ["proj_project_task_execution_contract"],
            "TaskCompletionRule": ["proj_project_task_execution_contract"],
            "TaskCompletionEvaluation": ["proj_project_task_completion_evaluation"],
            "CutoverChecklist": [
                "cut_cutover_checklist",
                "cut_cutover_checklist_item",
                "cut_cutover_checklist_item_result",
            ],
            "DynamicFormTemplate": ["plt_dynamic_form_template"],
            "DynamicFormTemplateRevision": ["plt_dynamic_form_template_revision"],
            "DynamicFormInstance": ["plt_dynamic_form_instance"],
        }

        self.assertEqual(90, len(self.records))
        for object_name, tables in expected.items():
            with self.subTest(object_name=object_name):
                self.assertEqual(tables, self.records[object_name]["targetTables"])
                self.assertEqual(tables, self.contract["objectTableMap"][object_name]["targetTables"])

    def test_v18_forward_initialization_does_not_infer_business_binding_or_cutover_results(self) -> None:
        binding_source = self.records["TaskWorkBinding"]["sources"][0]
        checklist_source = self.records["CutoverChecklist"]["sources"][0]

        self.assertEqual("NONE_NEW", binding_source["sourceType"])
        self.assertEqual("NEW_ONLY", binding_source["disposition"])
        self.assertIn("TASK_NATIVE", binding_source["transform"])
        self.assertIn("never infer", binding_source["transform"])
        self.assertEqual("pms_cut_risk", checklist_source["sourceObject"])
        self.assertEqual("PENDING_FIELD_MAPPING", checklist_source["mappingStatus"])
        self.assertIn("never infer", checklist_source["transform"])
        self.assertIn("CollectionTask", checklist_source["transform"])


if __name__ == "__main__":
    unittest.main()
