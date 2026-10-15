import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('identity', Path(__file__).parents[1] / 'dppms_project_identity_plan.py')
identity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(identity)


class ProjectIdentityPlanTest(unittest.TestCase):
    def row(self, source_id, project_type='10', code='A-0'):
        return dict(projectId=source_id, projectType=project_type, projectCode=code, disabled=0, effectiveTo=None)

    def test_invalid_latest_never_resurrects_an_older_valid_version(self):
        rows = [self.row(1), dict(self.row(3), effectiveTo='2020-01-01'), self.row(4, code='B-0')]
        plan = identity.plan_project_identities(rows, [])
        self.assertEqual({4: 4}, plan['canonicalByOriginalId'])
        self.assertEqual({4: 'B-0'}, plan['projectCodes'])

    def test_invalid_alias_is_excluded_but_closed_valid_project_is_retained(self):
        rows = [dict(self.row(1), disabled=1), dict(self.row(2), projectState='100')]
        self.assertEqual({2: 2}, identity.plan_project_identities(rows, [])['canonicalByOriginalId'])
        self.assertEqual({}, identity.plan_project_identities([dict(self.row(1), disabled=1)], [])['projectCodes'])

    def test_validity_evidence_is_required(self):
        row = self.row(1);row.pop('disabled')
        with self.assertRaisesRegex(ValueError, 'PROJECT_VALIDITY_EVIDENCE_REQUIRED'):
            identity.plan_project_identities([row], [])

    def test_largest_same_type_id_is_master_and_every_original_id_has_an_alias(self):
        plan = identity.plan_project_identities([self.row(7), self.row(2), self.row(5)], [])
        self.assertEqual({7: 7, 2: 7, 5: 7}, plan['canonicalByOriginalId'])
        self.assertEqual({7: 'A-0'}, plan['projectCodes'])
        self.assertEqual([], plan['codeChanges'])

    def test_cross_type_order_uses_canonical_id_and_skips_all_source_and_target_codes(self):
        rows = [self.row(9), self.row(1), self.row(4, 'afss'), self.row(20, 'afxx'), self.row(30, '10', 'A-1')]
        plan = identity.plan_project_identities(rows, ['A-2', 'A-4'])
        self.assertEqual({4: 'A-0', 9: 'A-3', 20: 'A-5', 30: 'A-1'}, plan['projectCodes'])
        self.assertEqual(9, plan['canonicalByOriginalId'][1])
        self.assertEqual(plan, identity.plan_project_identities(list(reversed(rows)), ['A-4', 'A-2']))

    def test_replay_uses_binding_code_even_after_later_codes_become_occupied(self):
        rows = [self.row(1), self.row(2, 'afss')]
        initial = identity.plan_project_identities(rows, [])
        replay = identity.plan_project_identities(rows, ['A-0', 'A-1', 'A-2'], initial['projectCodes'])
        self.assertEqual(initial, replay)

    def test_invalid_or_changed_bindings_and_source_identity_block(self):
        rows = [self.row(1), self.row(2, 'afss')]
        for bindings in [{1: 'A-1'}, {2: 'A-0'}, {2: 'B-1'}, {3: 'A-2'}]:
            with self.assertRaises(ValueError):
                identity.plan_project_identities(rows, [], bindings)
        with self.assertRaises(ValueError):
            identity.plan_project_identities([self.row(1), self.row(1)], [])
        with self.assertRaises(ValueError):
            identity.plan_project_identities([self.row(1, code='')], [])

    def test_invalid_suffix_blocks_instead_of_renaming_or_dropping(self):
        with self.assertRaises(ValueError):
            identity.plan_project_identities([self.row(1, code='A'), self.row(2, 'afss', 'A')], [])

    def test_plan_prepares_editable_task_without_changing_sql_filters_or_the_input(self):
        plan = identity.plan_project_identities([self.row(1), self.row(2, 'afss')], [])
        original = dict(adapter='DPPMS_PROJECT_MASTER', mode='ONCE', sources=[dict(
            object='pm_project_header', sql='editable SQL', parameters={'projectTypes': '10', 'projectIds': ''},
            mappings=[dict(target='migrationCodeAllocation', source='projectId', conversion='LOOKUP', values={}),
                      dict(target='projectName', source='projectName', conversion='STRING')])])
        prepared = identity.apply_code_plan_to_task(original, plan)
        self.assertEqual({}, original['sources'][0]['mappings'][0]['values'])
        self.assertEqual(plan['migrationCodeAllocations'], prepared['sources'][0]['mappings'][0]['values'])
        self.assertEqual(original['sources'][0]['sql'], prepared['sources'][0]['sql'])
        self.assertEqual(original['sources'][0]['parameters'], prepared['sources'][0]['parameters'])
        self.assertEqual(prepared, identity.apply_code_plan_to_task(prepared, plan))
        altered = identity.plan_project_identities([self.row(1), self.row(2, 'afss')], ['A-1'])
        with self.assertRaises(ValueError):
            identity.apply_code_plan_to_task(prepared, altered)


if __name__ == '__main__':
    unittest.main()
