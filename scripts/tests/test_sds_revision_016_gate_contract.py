"""Mutation tests for current design integrity and the non-forgeable GO boundary."""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import sds_gate_contract as gate
from generate_sds_revision_016_schema import render
from validate_domain_entity_migration_alignment import requirement_owners

FILES = [gate.PRD, gate.MODEL, gate.DDL, gate.MYSQL_EVIDENCE, 'docs/traceability/domain-object-table-map.json',
         'docs/traceability/phase2-contract-map.md', 'docs/design/09-database-design.md',
         'docs/design/10-api-design.md', 'docs/design/11-event-design.md']

class CurrentGateContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for path in FILES + [f'docs/engineering/gates/phase-{n}/gate-status.md' for n in (1,2,3)]:
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / path, target)

    def mutate_model(self, mutate, *, regenerate=True):
        path = self.root / gate.MODEL
        model = json.loads(path.read_text())
        mutate(model)
        path.write_text(json.dumps(model, ensure_ascii=False))
        if regenerate:
            (self.root / gate.DDL).write_text(render(model))

    def errors(self):
        return gate.validate_design(self.root)

    def test_current_design_passes_technical_integrity(self):
        self.assertEqual([], self.errors())
        self.assertEqual([], gate.validate_gate(self.root, 3, technical=True))

    def test_mysql_evidence_is_bound_to_exact_schema(self):
        self.assertEqual([], gate.validate_mysql_evidence(self.root))
        p=self.root/gate.DDL; p.write_text(p.read_text()+"\n-- changed schema\n")
        self.assertTrue(any('stale' in e for e in gate.validate_mysql_evidence(self.root)))

    def test_common_audit_actor_must_accept_platform_string_identity(self):
        def numeric_actor(model):
            for column in model['tables']['proj_delivery_definition_revision']['columns']:
                if column[0] == 'creator': column[1] = 'BIGINT NOT NULL'
        self.mutate_model(numeric_actor)
        self.assertTrue(any('creator: incompatible database common field' in e for e in self.errors()))

    def test_mysql_evidence_cannot_drop_failed_or_negative_cases(self):
        p=self.root/gate.MYSQL_EVIDENCE; m=json.loads(p.read_text()); m['cases']=[c for c in m['cases'] if c['case']!='no_tracking_wrong_writer_rejected']
        p.write_text(json.dumps(m))
        self.assertTrue(any('negative cases' in e for e in gate.validate_mysql_evidence(self.root)))

    def test_mysql_evidence_rejects_failed_case(self):
        p=self.root/gate.MYSQL_EVIDENCE; m=json.loads(p.read_text()); m['cases'][0]['passed']=False; p.write_text(json.dumps(m))
        self.assertTrue(any('failed/unexecuted' in e for e in gate.validate_mysql_evidence(self.root)))

    def test_current_gate_does_not_fake_formal_approval(self):
        self.assertTrue(any('APPROVAL_REQUIRED' in e for e in gate.validate_gate(self.root, 3, technical=False)))

    def test_stale_prd_identity_is_rejected(self):
        self.mutate_model(lambda m: m.update(prdGitBlob='0'*40))
        self.assertTrue(any('identity is stale' in e for e in self.errors()))

    def test_reference_schema_cannot_claim_runtime_migration(self):
        self.mutate_model(lambda m: m.update(scope='DEPLOYED'))
        self.assertTrue(any('deployed migration' in e for e in self.errors()))

    def test_scope_owner_cannot_be_silently_moved_to_project(self):
        path=self.root/'docs/traceability/domain-object-table-map.json'
        model=json.loads(path.read_text()); model['objects']['ProjectScopeVersion']['owner']='PROJ'
        path.write_text(json.dumps(model))
        self.assertTrue(any('ProjectScopeVersion' in e for e in self.errors()))

    def test_obsolete_pm06_group_does_not_reenter_current_map(self):
        path=self.root/'docs/traceability/domain-object-table-map.json'
        model=json.loads(path.read_text()); model['objects']['MultiPhaseProjectGroup']={'owner':'PROJ'}
        path.write_text(json.dumps(model))
        self.assertTrue(any('obsolete multi-phase' in e for e in self.errors()))

    def test_missing_graph_field_is_rejected_even_after_regeneration(self):
        self.mutate_model(lambda m: m['tables']['proj_project_stage_transition']['columns'].__setitem__(slice(None), [c for c in m['tables']['proj_project_stage_transition']['columns'] if c[0]!='graph_version']))
        self.assertTrue(any('missing critical columns' in e for e in self.errors()))

    def test_nullable_tenant_is_rejected(self):
        def mutate(m):
            for c in m['tables']['acc_acceptance_report']['columns']:
                if c[0]=='tenant_id': c[1]='BIGINT UNSIGNED NULL'
        self.mutate_model(mutate)
        self.assertTrue(any('tenant key' in e for e in self.errors()))

    def test_tenantless_uniqueness_is_rejected(self):
        self.mutate_model(lambda m: m['tables']['acc_acceptance_report']['constraints'].append('UNIQUE KEY `bad` (`project_id`)'))
        self.assertTrue(any('tenant-less' in e for e in self.errors()))

    def test_cross_owner_foreign_key_is_rejected(self):
        self.mutate_model(lambda m: m['tables']['acc_acceptance_report']['constraints'].append('CONSTRAINT `bad_fk` FOREIGN KEY (`project_id`) REFERENCES `proj_project` (`id`)'))
        self.assertTrue(any('cross-Context' in e for e in self.errors()))

    def test_missing_current_binding_constraint_is_rejected(self):
        self.mutate_model(lambda m: m['tables']['proj_project_stage_execution_contract']['constraints'].__setitem__(slice(None), [c for c in m['tables']['proj_project_stage_execution_contract']['constraints'] if 'uk_psec_current' not in c]))
        self.assertTrue(any('uk_psec_current' in e for e in self.errors()))

    def test_same_constraint_name_with_weakened_predicate_is_rejected(self):
        def mutate(m):
            cs=m['tables']['proj_project_exit_record']['constraints']
            cs[:]=[c.replace("CHECK ((closure_type IN ('NORMAL','NO_TRACKING') AND source_context='ACC') OR (closure_type='EXCEPTION' AND source_context='PROJ'))", 'CHECK (1=1)') for c in cs]
        self.mutate_model(mutate)
        self.assertTrue(any('weakened invariant' in e for e in self.errors()))

    def test_client_writable_current_marker_is_rejected(self):
        def mutate(m):
            for c in m['tables']['proj_project_stage_execution_contract']['columns']:
                if c[0]=='current_marker': c[1]='TINYINT NULL'
        self.mutate_model(mutate)
        self.assertTrue(any('uniqueness marker' in e for e in self.errors()))

    def test_sql_drift_is_rejected(self):
        path=self.root/gate.DDL; path.write_text(path.read_text()+'\n-- unreviewed edit\n')
        self.assertTrue(any('differs from canonical' in e for e in self.errors()))

    def test_noncanonical_phase2_objects_are_rejected(self):
        path=self.root/'docs/traceability/phase2-contract-map.md'
        path.write_text(path.read_text()+'\n- 数据对象：Stage/TaskWorkBinding\n')
        self.assertTrue(any('noncanonical' in e for e in self.errors()))

    def test_duplicate_gate_metadata_is_rejected(self):
        path=self.root/'docs/engineering/gates/phase-1/gate-status.md'
        path.write_text(path.read_text()+f'\n> PRD Blob：`{gate.blob(self.root/gate.PRD)}`\n')
        self.assertTrue(any('duplicate' in e for e in gate.validate_gate(self.root,1,technical=True)))

    def test_changing_to_approved_requires_real_review_even_in_technical_mode(self):
        path=self.root/'docs/engineering/gates/phase-1/gate-status.md'
        path.write_text(path.read_text().replace('审查状态：`REVALIDATION_REQUIRED`','审查状态：`APPROVED`'))
        errors=gate.validate_gate(self.root,1,technical=True)
        self.assertTrue(any('APPROVAL_REQUIRED' in e for e in errors))
        self.assertTrue(any('independent review evidence' in e for e in errors))

    def test_self_review_cannot_masquerade_as_independent_go(self):
        path=self.root/'docs/engineering/gates/phase-1/gate-status.md'
        text=path.read_text()
        for label,value in [('审查状态','APPROVED'),('机器门禁','PASS'),('需求方批准','GO'),('独立复审','GO')]:
            import re
            text=re.sub(r'^> '+label+r'：`[^`]+`',f'> {label}：`{value}`',text,flags=re.M)
        path.write_text(text+'\n> 当前复审证据：`review.md`\n')
        (self.root/'review.md').write_text(f'Reviewer: same author\nDecision: GO\nReview scope: design\nPRD_V1.8_REVISION_{gate.revision(self.root)}\n{gate.blob(self.root/gate.PRD)}\nSELF_REVIEW_ONLY\n')
        self.assertTrue(any('self-review' in e for e in gate.validate_gate(self.root,1,technical=False)))

    def test_owners_accept_precise_version_slice_keys(self):
        p=self.root/'owner.md'; p.write_text('| Requirement切片 | 名称 | 版本 | 领域 | Owner |\n| PM-08@V1 | a | b | c | PROJ（项目） |\n| PM-08@V2 | a | b | c | PROJ（项目） |\n')
        self.assertEqual({'PM-08':'PROJ'},requirement_owners(p))

    def test_conflicting_version_slice_owners_are_rejected(self):
        p=self.root/'owner.md'; p.write_text('| Requirement切片 | 名称 | 版本 | 领域 | Owner |\n| PM-08@V1 | a | b | c | PROJ |\n| PM-08@V2 | a | b | c | PLT |\n')
        with self.assertRaisesRegex(ValueError, 'conflicting Owners'): requirement_owners(p)

if __name__=='__main__': unittest.main()
