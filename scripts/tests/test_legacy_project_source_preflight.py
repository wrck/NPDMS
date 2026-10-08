import copy
import importlib.util
import os
from pathlib import Path
import unittest

script = Path(os.environ.get('PILOT_PREFLIGHT_SCRIPT', Path(__file__).resolve().parents[1] / 'legacy_project_source_preflight.py'))
spec = importlib.util.spec_from_file_location('pilot_preflight', script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture():
    return {'project': {'projectId': 1, 'projectCode': 'P1', 'projectState': 'OLD_CLOSED'},
            'groups': [{'projectCode': 'P1', 'projectGroupCode': 'G1'}],
            'project_contracts': [{'projectGroupCode': 'G1', 'contractNo': 'C1'}],
            'orders': [{'id': 2, 'source': 'D365', 'compCode': '01', 'orderType': 0,
                        'orderNumber': 'O1', 'contractNo': 'C1', 'orderExecNumber': 'E1'}],
            'executions': [{'id': 3, 'dataSource': 'CRM', 'corporationCode': '01', 'orderExecNumber': 'E1'}],
            'order_lines': [{'id': 4, 'source': 'D365', 'compCode': '01', 'lineType': 0, 'orderNumber': 'O1',
                             'lineNum': '1', 'itemCode': 'I1', 'orderQuantity': 2, 'openQuantity': 0,
                             'customInfo': {'inventTransId': '70001'}}],
            'shipment_lines': [{'id': 5, 'orderNumber': 'O1', 'pack_id': 'PACK1', 'lineNum': 70001,
                                'item': 'I1', 'isRMA': 0}],
            'shipment_headers': [{'packlist_id': 'PACK1', 'con_id': 'FC1'}],
            'shipment_contracts': [{'contract_id': 'FC1', 'contract_code': 'C1'}],
            'products': [{'id': 6, 'projectId': 1, 'contractNo': 'C1', 'orderNumber': 'O1',
                          'lineNum': 1, 'itemCode': 'I1', 'orderQuantity': 2, 'openQuantity': 0, 'deliverQuantity': 2}],
            'members': [{'id': 7, 'projectId': 1}], 'contract_amounts': [{'id': 8, 'contract_num': 'C1', 'contract_money_amount': '0.00'}]}


def explicit_shipment_fixture():
    bundle = fixture()
    bundle['shipment_lines'][0].update(barcode='SYNTHETIC-SERIAL', item='PHYSICAL-I2')
    bundle['shipment_order_links'] = [{'pack_id': 'PACK1', 'barcode': 'SYNTHETIC-SERIAL',
                                     'orderNumber': 'O1', 'contractNo': 'C1', 'lineNum': 70001,
                                     'orderExecNumber': None}]
    return bundle


class SourcePreflightTest(unittest.TestCase):
    def test_different_products_require_explicit_source_relationship(self):
        bundle = explicit_shipment_fixture(); bundle.pop('shipment_order_links')
        self.assertIn('SHIPMENT_ORDER_LINE_NOT_UNIQUE', module.reconcile_source(bundle)['issueCounts'])

    def test_explicit_relationship_preserves_both_product_codes(self):
        result = module.reconcile_source(explicit_shipment_fixture())
        self.assertTrue(result['sourceComplete'])
        self.assertEqual('EXPLICIT_SHIPMENT_ORDER_LINE', result['shipmentLinks'][0]['linkBasis'])
        self.assertEqual('I1', result['shipmentLinks'][0]['orderProductCode'])
        self.assertEqual('PHYSICAL-I2', result['shipmentLinks'][0]['shipmentProductCode'])

    def test_explicit_relationship_must_agree_with_all_parent_keys(self):
        for field, value in [('contractNo', 'OTHER'), ('orderNumber', 'OTHER'),
                             ('lineNum', 999), ('orderExecNumber', 'OTHER')]:
            with self.subTest(field=field):
                bundle = explicit_shipment_fixture(); bundle['shipment_order_links'][0][field] = value
                self.assertIn('SHIPMENT_EXPLICIT_LINK_MISMATCH', module.reconcile_source(bundle)['issueCounts'])

    def test_duplicate_explicit_relationship_is_quarantined(self):
        bundle = explicit_shipment_fixture()
        bundle['shipment_order_links'].append(copy.deepcopy(bundle['shipment_order_links'][0]))
        self.assertIn('SHIPMENT_EXPLICIT_LINK_NOT_UNIQUE', module.reconcile_source(bundle)['issueCounts'])

    def test_explicit_relationship_does_not_replace_quantity_reconciliation(self):
        bundle = explicit_shipment_fixture(); bundle['products'][0]['deliverQuantity'] = 1
        self.assertIn('PROJECT_PRODUCT_QUANTITY_DIFF', module.reconcile_source(bundle)['issueCounts'])

    def test_explicit_relationship_without_physical_product_is_invalid(self):
        bundle = explicit_shipment_fixture(); bundle['shipment_lines'][0]['item'] = ''
        self.assertIn('SHIPMENT_PRODUCT_MISSING', module.reconcile_source(bundle)['issueCounts'])

    def test_d365_inventory_key_and_legacy_state_are_preserved(self):
        result = module.reconcile_source(fixture())
        self.assertTrue(result['sourceComplete'])
        self.assertEqual('OLD_CLOSED', result['legacyState'])
        self.assertNotIn('lifecycleStatus', result)
        self.assertEqual(0, result['databaseWrites'])
        self.assertEqual('NOT_RUN', result['runtimeAcceptance'])

    def test_display_line_number_cannot_replace_inventory_identity(self):
        bundle = fixture(); bundle['shipment_lines'][0]['lineNum'] = 1
        self.assertIn('SHIPMENT_ORDER_LINE_NOT_UNIQUE', module.reconcile_source(bundle)['issueCounts'])

    def test_same_order_number_in_another_company_is_rejected(self):
        bundle = fixture(); bundle['order_lines'][0]['compCode'] = '02'
        self.assertIn('ORDER_LINE_PARENT_MISMATCH', module.reconcile_source(bundle)['issueCounts'])

    def test_shipment_must_belong_to_the_same_contract(self):
        bundle = fixture(); bundle['shipment_contracts'][0]['contract_code'] = 'C2'
        self.assertIn('SHIPMENT_CONTRACT_MISMATCH', module.reconcile_source(bundle)['issueCounts'])

    def test_ambiguous_execution_is_not_selected_by_recency(self):
        bundle = fixture(); bundle['executions'].append({**bundle['executions'][0], 'id': 30})
        self.assertIn('EXECUTION_NOT_UNIQUE', module.reconcile_source(bundle)['issueCounts'])

    def test_quantity_snapshot_difference_is_reported(self):
        bundle = fixture(); bundle['products'][0]['deliverQuantity'] = 1
        self.assertIn('PROJECT_PRODUCT_QUANTITY_DIFF', module.reconcile_source(bundle)['issueCounts'])

    def test_rma_does_not_become_a_normal_shipment(self):
        bundle = fixture(); bundle['shipment_lines'][0]['rma_no'] = 'RMA-SYNTHETIC'
        self.assertIn('RMA_REQUIRES_SEPARATE_RULE', module.reconcile_source(bundle)['issueCounts'])

    def test_foreign_member_and_contract_amount_are_rejected(self):
        bundle = fixture(); bundle['members'][0]['projectId'] = 2; bundle['contract_amounts'][0]['contract_num'] = 'C2'
        issues = module.reconcile_source(bundle)['issueCounts']
        self.assertIn('MEMBER_PROJECT_MISMATCH', issues)
        self.assertIn('CONTRACT_AMOUNT_PARENT_MISMATCH', issues)

    def test_duplicate_shipment_source_id_is_rejected(self):
        bundle = fixture(); bundle['shipment_lines'].append(copy.deepcopy(bundle['shipment_lines'][0]))
        self.assertIn('SHIPMENT_SOURCE_IDENTITY_DUPLICATE_OR_MISSING', module.reconcile_source(bundle)['issueCounts'])

    def test_checksum_is_stable_for_row_order_and_changes_with_source_facts(self):
        bundle = fixture(); bundle['members'].append({'id': 70, 'projectId': 1})
        repeated = copy.deepcopy(bundle); repeated['members'].reverse()
        self.assertEqual(module.source_checksum(bundle), module.source_checksum(repeated))
        repeated['orders'][0]['contractNo'] = 'C2'
        self.assertNotEqual(module.source_checksum(bundle), module.source_checksum(repeated))


if __name__ == '__main__':
    unittest.main()
