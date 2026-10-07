import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('detail', Path(__file__).parents[1] / 'dppms_project_detail_sync.py')
detail = importlib.util.module_from_spec(spec)
spec.loader.exec_module(detail)


class DetailEvidenceTest(unittest.TestCase):
    def reader(self):
        reader = detail.DppmsDetailReader(None)
        reader.rows = {code: [] for code in reader.domains}
        reader.rows['project'] = [dict(projectId=101, projectName='Synthetic')]
        return reader

    def bundle(self, reader):
        return reader.bundle(dict(sourceProjectKey='101', sourceContractNo='SYNTHETIC'), 'synthetic-batch', None)

    def test_source_values_are_redacted_before_becoming_payload_or_composite_key(self):
        self.assertEqual(detail.REDACTED, detail.text('Authorization: Bearer synthetic-value'))
        self.assertEqual(detail.REDACTED, detail.text('口令：synthetic-value'))
        self.assertEqual('0', detail.text(0))

    def test_same_numeric_key_in_different_tables_does_not_drop_parent_link(self):
        reader = self.reader()
        reader.rows['groups'] = [dict(id=9, projectGroupCode='SYNTHETIC-GROUP')]
        reader.rows['contracts'] = [dict(id=9, contractNo='SYNTHETIC', projectGroupCode='SYNTHETIC-GROUP')]
        contract = next(row for row in self.bundle(reader)['records'] if row['domain'] == 'contracts')
        self.assertEqual(('groups', '9'), (contract['parentDomain'], contract['parentSourceKey']))

    def test_duplicate_keyless_source_rows_are_preserved_as_separate_occurrences(self):
        reader = self.reader()
        reader.rows['shipment-logistics'] = [dict(packId=1, contract_code='SYNTHETIC')] * 2
        rows = [row for row in self.bundle(reader)['records'] if row['domain'] == 'shipment-logistics']
        self.assertEqual(2, len(rows))
        self.assertNotEqual(rows[0]['sourceKey'], rows[1]['sourceKey'])
        self.assertEqual(2, self.bundle(reader)['domainCounts']['shipment-logistics'])

    def test_order_lines_are_linked_by_company_and_source_in_addition_to_number(self):
        reader = self.reader()
        reader.rows['orders'] = [dict(id=1, orderNumber='SYNTHETIC', compCode='A', source='ERP'),
                                 dict(id=2, orderNumber='SYNTHETIC', compCode='B', source='ERP')]
        reader.rows['order-lines'] = [dict(id=3, orderNumber='SYNTHETIC', compCode='B', source='ERP')]
        row = next(row for row in self.bundle(reader)['records'] if row['domain'] == 'order-lines')
        self.assertEqual(('orders', '2'), (row['parentDomain'], row['parentSourceKey']))

    def test_shipment_contract_scope_preserves_displayed_and_old_source_numbers(self):
        self.assertEqual({'SYNTHETIC-L', 'SYNTHETIC-C', 'SYNTHETIC'},
                         detail.shipment_contracts(['SYNTHETIC-L', 'SYNTHETIC-C']))

    def test_question_templates_questions_and_options_preserve_their_exact_links(self):
        reader = self.reader()
        reader.rows['questionnaire-templates'] = [dict(id=4)]
        reader.rows['questionnaire-questions'] = [dict(id=5, quesnaireTemplateHeaderId=4, questionContent='Synthetic question')]
        reader.rows['questionnaire-options'] = [dict(id=6, questionId=5, questionOptionsContent='Synthetic option')]
        rows = {r['domain']: r for r in self.bundle(reader)['records']}
        self.assertEqual(('questionnaire-templates', '4'), (rows['questionnaire-questions']['parentDomain'], rows['questionnaire-questions']['parentSourceKey']))
        self.assertEqual(('questionnaire-questions', '5'), (rows['questionnaire-options']['parentDomain'], rows['questionnaire-options']['parentSourceKey']))


if __name__ == '__main__':
    unittest.main()
