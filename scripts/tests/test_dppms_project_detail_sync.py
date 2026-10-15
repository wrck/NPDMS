import importlib.util
import copy
import json
from pathlib import Path
import unittest
import sqlite3

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

    def test_same_number_sales_and_return_lines_keep_their_distinct_parents(self):
        reader = self.reader()
        reader.rows['orders'] = [dict(id=1, orderNumber='SYNTHETIC', compCode='01', source='SAP', orderType='0'),
                                 dict(id=2, orderNumber='SYNTHETIC', compCode='01', source='SAP', orderType='1')]
        reader.rows['order-lines'] = [dict(id=3, orderNumber='SYNTHETIC', compCode='01', source='SAP', lineType='0'),
                                      dict(id=4, orderNumber='SYNTHETIC', compCode='01', source='SAP', lineType='1')]
        lines = {row['sourceKey']: row for row in self.bundle(reader)['records'] if row['domain'] == 'order-lines'}
        self.assertEqual(('orders', '1'), (lines['3']['parentDomain'], lines['3']['parentSourceKey']))
        self.assertEqual(('orders', '2'), (lines['4']['parentDomain'], lines['4']['parentSourceKey']))

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



def schema_rows():
    contract = json.loads(detail.SOURCE_SCHEMA.read_text('utf8'))
    return [dict(TABLE_NAME=table_name, ORDINAL_POSITION=column['position'], COLUMN_NAME=column['name'],
                 COLUMN_TYPE=column['type'], IS_NULLABLE=column['nullable'], COLUMN_KEY=column['key'],
                 TABLE_TYPE=table['tableType'])
            for table_name, table in contract['tables'].items() for column in table['columns']]


def source_table(code):
    return next(domain['sourceTable'] for domain in json.loads(detail.CATALOG.read_text('utf8')) if domain['code'] == code)


class SourceCursor:
    def __init__(self, connection):
        self.connection = connection
        self.result = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, sql, params):
        self.connection.queries.append((sql, tuple(params)))
        if 'information_schema.COLUMNS' in sql:
            self.result = self.connection.metadata
            return
        if ') valid_project WHERE projectId=' in sql:
            self.result = [row for row in self.connection.valid_scope if str(row['projectId']) == str(params[-1])]
            return
        if ') valid_candidate ' in sql:
            self.result = self.connection.data.get(source_table('project'), [])
            return
        source_name = sql.split(' FROM `', 1)[1].split('`', 1)[0]
        self.result = self.connection.data.get(source_name, [])

    def fetchall(self):
        return self.result


class SourceConnection:
    def __init__(self, metadata=None, data=None):
        self.metadata = copy.deepcopy(schema_rows() if metadata is None else metadata)
        self.data = {source_table(code): rows for code, rows in (data or {}).items()}
        self.queries = []
        self.valid_scope = [dict(projectId=row['projectId'], canonicalProjectId=row['projectId'])
                            for row in self.data.get(source_table('project'), [])]

    def cursor(self):
        return SourceCursor(self)


class AssociatedProjectCodeTest(unittest.TestCase):
    def reader(self, candidates):
        connection = SourceConnection(data={
            'project': candidates,
            'lease-products': [dict(projectCode='BASE', productSubCode='SKU', num=3, leaseDuration='12.50')],
            'configured-products': [dict(projectCode='BASE', itemCode='CHILD', bomPaths='ROOT,CHILD', level=2)]
        })
        reader = detail.DppmsDetailReader(connection)
        reader.rows = {code: [] for code in reader.domains}
        return reader, connection

    def test_original_code_split_reads_all_rows_and_requires_one_original_project(self):
        reader, connection = self.reader([dict(projectId=101, projectCode='BASE-0')])
        reader.collect_project_code_children('101', 'BASE-0')
        self.assertEqual(1, len(reader.rows['lease-products']))
        self.assertEqual(1, len(reader.rows['configured-products']))
        for sql, params in connection.queries:
            if 'FROM `pm_project_product_' in sql:
                self.assertEqual(('BASE', 'BASE'), params)
                self.assertNotIn('MAX(', sql)
                self.assertIn('BINARY', sql)

    def test_shared_or_wrong_project_is_blocked_without_picking_a_split_project(self):
        for candidates in [[dict(projectId=101), dict(projectId=102)], [dict(projectId=102)], []]:
            reader, _ = self.reader(candidates)
            with self.assertRaisesRegex(ValueError, 'SOURCE_ASSOCIATED_PROJECT_LINK_NOT_UNIQUE'):
                reader.collect_project_code_children('101', 'BASE-0')

    def test_empty_lists_do_not_read_unrelated_project_headers(self):
        reader, connection = self.reader([])
        connection.data[source_table('lease-products')] = []
        connection.data[source_table('configured-products')] = []
        reader.collect_project_code_children('101', 'BASE-0')
        self.assertFalse(any('FROM `pm_project_header`' in sql for sql, _ in connection.queries))


class RealProductUpstreamTest(unittest.TestCase):
    def reader(self, matches):
        fields = ['projectCode', 'orderExecNumber', 'productFirstName', 'productName',
                  'productSubCode', 'productSubModel', 'productSubName', 'num', 'memo']
        original = dict(zip(fields, ['Code', 'X-V1', None, '', 'SKU', 'Model', 'Name', -2, None]))
        original.update(id=301, projectId=101, contractNo='')
        connection = SourceConnection()
        connection.data['pm_project_real_product_line_from_sms'] = matches
        reader = detail.DppmsDetailReader(connection)
        reader.rows = {code: [] for code in reader.domains}
        reader.rows['real-products'] = [original]
        return reader, connection, original

    def test_upstream_has_its_own_ten_original_fields_and_exact_null_safe_query(self):
        reader, connection, original = self.reader([])
        upstream = {k: v for k, v in original.items() if k not in ['id', 'projectId', 'contractNo']}
        upstream['dataSource'] = 'SMS'
        connection.data['pm_project_real_product_line_from_sms'] = [upstream]
        reader.collect_real_product_upstream()
        bundle = reader.bundle(dict(sourceProjectKey='101', sourceContractNo='SYNTHETIC'), 'synthetic', None)
        records = {r['domain']: r for r in bundle['records']}
        self.assertEqual(12, len(records['real-products']['values']))
        self.assertNotIn('dataSource', records['real-products']['values'])
        self.assertEqual(10, len(records['real-product-upstream']['values']))
        self.assertEqual('SMS', records['real-product-upstream']['values']['dataSource'])
        self.assertIsNone(records['real-product-upstream']['values']['memo'])
        sql, params = connection.queries[-1]
        self.assertIn('BINARY `projectCode` <=> BINARY %s', sql)
        self.assertIn('`num` <=> %s', sql)
        self.assertEqual(('Code', 'X-V1', None, '', 'SKU', 'Model', 'Name', -2, None), params)

    def test_unmatched_and_multiple_upstream_records_block_collection(self):
        for matches in [[], [dict(dataSource='SMS'), dict(dataSource='SMS')]]:
            with self.subTest(count=len(matches)):
                reader, _, _ = self.reader(matches)
                with self.assertRaisesRegex(ValueError, 'SOURCE_REAL_PRODUCT_UPSTREAM_NOT_UNIQUE'):
                    reader.collect_real_product_upstream()

    def test_empty_main_list_does_not_read_unrelated_global_upstream_rows(self):
        reader, connection, _ = self.reader([dict(dataSource='SMS')])
        reader.rows['real-products'] = []
        reader.collect_real_product_upstream()
        self.assertEqual([], reader.rows['real-product-upstream'])
        self.assertEqual([], connection.queries)


class FullSourceContractTest(unittest.TestCase):
    def test_invalid_project_stops_before_any_child_or_sensitive_detail_read(self):
        connection = self.source();connection.valid_scope = []
        with self.assertRaisesRegex(ValueError, 'SOURCE_PROJECT_NOT_EFFECTIVE'):
            self.collect(connection)
        self.assertEqual(2, len(connection.queries))
    def selection(self):
        return dict(sourceProjectKey='101', sourceContractNo='SYNTHETIC', sourceOrderIds=[1],
                    sourceExecutionIds=[2], sourceShipmentIds=[3])

    def source(self):
        return SourceConnection(data={
            'project': [dict(projectId=101, projectCode='PROJECT')],
            'groups': [dict(id=4, projectGroupCode='GROUP', smsProjectCode='CRM')],
            'contracts': [dict(id=5, contractNo='SYNTHETIC', projectGroupCode='GROUP')],
            'orders': [dict(id=1, orderNumber='ORDER', compCode='COMPANY', source='ERP', orderType='0',
                            contractNo='SYNTHETIC', orderExecNumber='EXECUTION')],
            'executions': [dict(id=2, orderExecNumber='EXECUTION', projectCode='CRM')],
            'shipment-facts': [dict(id=3, barcode='Raw-Sn', barcode2='Sub-sN', pack_id='PACK',
                                   item='SKU-ONE', item2='SKU-TWO', rma_no='RMA-SYNTHETIC')],
            'installations': [dict(id=6, projectId=101, barcode='Install-SN')],
            'warranty-services': [dict(id=None, barcode='Raw-Sn', state=0)],
            'warranty-registrations': [dict(id=7, barCode='Raw-Sn')],
            'warranty-changes': [dict(id=8, barcode='Raw-Sn')],
            'shipment-materials': [dict(id=None, item='SKU-ONE', describe_='产品一', itemname='MODEL-ONE'),
                                  dict(id=None, item='SKU-TWO', describe_='产品二', itemname='MODEL-TWO')]
        })

    def collect(self, connection):
        return detail.DppmsDetailReader(connection).collect(self.selection(), 'synthetic-batch')

    def test_line_query_executes_with_exact_parent_type_and_excludes_the_other_type(self):
        class TypedLineCursor(SourceCursor):
            def execute(cursor, sql, params):
                if ' FROM `pm_order_line_from_erp`' not in sql:
                    return super().execute(sql, params)
                cursor.connection.queries.append((sql, tuple(params)))
                cursor.result = [dict(row) for row in cursor.connection.line_db.execute(sql.replace('%s', '?'), params).fetchall()]

        for order_type, expected_line in [('0', 3), ('1', 4)]:
            with self.subTest(order_type=order_type):
                connection = self.source()
                connection.data[source_table('orders')][0]['orderType'] = order_type
                connection.line_db = sqlite3.connect(':memory:')
                connection.line_db.row_factory = sqlite3.Row
                columns = [field['key'] for domain in json.loads(detail.CATALOG.read_text('utf8'))
                           if domain['code'] == 'order-lines' for field in domain['fields']]
                connection.line_db.execute('CREATE TABLE pm_order_line_from_erp (' + ','.join('`' + column + '` TEXT' for column in columns) + ')')
                connection.line_db.executemany('INSERT INTO pm_order_line_from_erp(id,orderNumber,compCode,source,lineType) VALUES(?,?,?,?,?)',
                                              [(3, 'ORDER', 'COMPANY', 'ERP', '0'), (4, 'ORDER', 'COMPANY', 'ERP', '1')])
                connection.cursor = lambda: TypedLineCursor(connection)
                try:
                    bundle = self.collect(connection)
                    lines = [row for row in bundle['records'] if row['domain'] == 'order-lines']
                    self.assertEqual(1, len(lines))
                    self.assertEqual(str(expected_line), lines[0]['sourceKey'])
                    self.assertEqual(order_type, lines[0]['values']['lineType'])
                    self.assertEqual(('orders', '1'), (lines[0]['parentDomain'], lines[0]['parentSourceKey']))
                finally:
                    connection.line_db.close()

    def test_contract_covers_all_52_tables_and_770_original_columns(self):
        contract = json.loads(detail.SOURCE_SCHEMA.read_text('utf8'))
        self.assertEqual(52, len(contract['tables']))
        self.assertEqual(770, sum(len(table['columns']) for table in contract['tables'].values()))
        detail.DppmsDetailReader(SourceConnection()).validate_source_schema()

    def test_added_column_blocks_before_any_business_data_is_read(self):
        connection = self.source()
        connection.metadata.append(dict(connection.metadata[0], COLUMN_NAME='newBusinessField', ORDINAL_POSITION=1000))
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_COLUMN_DRIFT'):
            self.collect(connection)
        self.assertEqual(1, len(connection.queries))
        self.assertIn('information_schema.COLUMNS', connection.queries[0][0])

    def test_removed_secret_column_is_still_schema_drift(self):
        connection = self.source()
        connection.metadata = [row for row in connection.metadata if row['COLUMN_NAME'] != 'licenseCode']
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_COLUMN_DRIFT'):
            self.collect(connection)
        self.assertEqual(1, len(connection.queries))

    def test_changed_original_column_type_blocks_before_payload_capture(self):
        connection = self.source()
        connection.metadata[0]['COLUMN_TYPE'] = 'text'
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_TYPE_OR_CONSTRAINT_DRIFT'):
            self.collect(connection)
        self.assertEqual(1, len(connection.queries))

    def test_missing_underlying_view_dependency_is_not_silently_ignored(self):
        connection = self.source()
        connection.metadata = [row for row in connection.metadata if row['TABLE_NAME'] != 'fb_items']
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_TABLE_DRIFT'):
            self.collect(connection)
        self.assertEqual(1, len(connection.queries))

    def test_nullable_or_unique_key_changes_require_review_of_keyless_identity(self):
        for field, value in [('IS_NULLABLE', 'NO'), ('COLUMN_KEY', 'UNI')]:
            with self.subTest(field=field):
                connection = self.source()
                row = next(row for row in connection.metadata if row['TABLE_NAME'] == 'fb_service' and row['COLUMN_NAME'] == 'id')
                row[field] = value
                with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_TYPE_OR_CONSTRAINT_DRIFT'):
                    self.collect(connection)
                self.assertEqual(1, len(connection.queries))

    def test_replacing_a_view_with_a_materialized_table_is_schema_drift(self):
        connection = self.source()
        for row in connection.metadata:
            if row['TABLE_NAME'] == 'view_shipment_info_4_pm':
                row['TABLE_TYPE'] = 'BASE TABLE'
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCHEMA_TYPE_OR_CONSTRAINT_DRIFT'):
            self.collect(connection)
        self.assertEqual(1, len(connection.queries))

    def test_catalog_cannot_omit_a_nonsecret_original_column(self):
        catalog = json.loads(detail.CATALOG.read_text('utf8'))
        next(domain for domain in catalog if domain['code'] == 'shipment-facts')['fields'].pop()
        with self.assertRaisesRegex(ValueError, 'SOURCE_CATALOG_COLUMNS_INCOMPLETE:shipment-facts'):
            detail.DppmsDetailReader(SourceConnection(), catalog=catalog)

    def test_catalog_cannot_read_the_excluded_license_secret_value(self):
        catalog = json.loads(detail.CATALOG.read_text('utf8'))
        next(domain for domain in catalog if domain['code'] == 'licenses')['fields'].append(dict(key='licenseCode', label='Secret'))
        with self.assertRaisesRegex(ValueError, 'SOURCE_CATALOG_COLUMNS_INCOMPLETE:licenses'):
            detail.DppmsDetailReader(SourceConnection(), catalog=catalog)

    def test_precise_warranty_queries_use_all_source_serials_and_exact_original_column_case(self):
        connection = self.source()
        bundle = self.collect(connection)
        self.assertIn('information_schema.COLUMNS', connection.queries[0][0])
        for table, field in [('fb_service', 'barcode'), ('warranty_info', 'barCode'), ('warranty_change_logs', 'barcode')]:
            sql, params = next(query for query in connection.queries if 'FROM `' + table + '`' in query[0])
            self.assertIn('WHERE `' + field + '` IN (', sql)
            self.assertEqual({'Raw-Sn', 'Sub-sN', 'Install-SN'}, set(params))
            self.assertNotIn('ast_device', sql)
        self.assertEqual(1, bundle['domainCounts']['warranty-services'])
        row = next(row for row in bundle['records'] if row['domain'] == 'warranty-services')
        self.assertIsNone(row['values']['id'])
        self.assertEqual('0', row['values']['state'])
        self.assertEqual('Raw-Sn', row['values']['barcode'])

    def test_empty_source_serial_scope_never_reads_whole_warranty_or_factory_tables(self):
        connection = self.source()
        connection.data[source_table('shipment-facts')][0].update(barcode=None, barcode2='')
        connection.data[source_table('installations')] = []
        bundle = self.collect(connection)
        for table in ['fb_service', 'warranty_info', 'warranty_change_logs', 'fb_soft_version']:
            self.assertFalse(any('FROM `' + table + '`' in sql for sql, _ in connection.queries))
        self.assertEqual(0, bundle['domainCounts']['warranty-services'])

    def test_rma_fact_is_preserved_and_materials_are_read_when_filtered_view_is_empty(self):
        connection = self.source()
        bundle = self.collect(connection)
        self.assertEqual(1, bundle['domainCounts']['shipment-facts'])
        self.assertEqual(0, bundle['domainCounts']['shipment-products'])
        self.assertEqual(2, bundle['domainCounts']['shipment-materials'])
        sql, params = next(query for query in connection.queries if 'FROM `fb_items`' in query[0])
        self.assertIn('WHERE `item` IN (', sql)
        self.assertEqual({'SKU-ONE', 'SKU-TWO'}, set(params))
        row = next(row for row in bundle['records'] if row['domain'] == 'shipment-facts')
        self.assertEqual('RMA-SYNTHETIC', row['values']['rma_no'])

    def test_none_and_whitespace_scope_values_do_not_create_an_unrestricted_query(self):
        connection = self.source()
        reader = detail.DppmsDetailReader(connection)
        self.assertEqual([], reader.select_in('warranty-services', 'barcode', [None, '', '   ']))
        self.assertEqual([], connection.queries)

    def test_unreviewed_predicate_column_is_rejected(self):
        reader = detail.DppmsDetailReader(SourceConnection())
        with self.assertRaisesRegex(ValueError, 'SOURCE_SCOPE_FIELD_UNAUDITED'):
            reader.select_in('warranty-registrations', 'barcode', ['Raw-Sn'])


class KeylessWarrantyIdentityTest(unittest.TestCase):
    reader = DetailEvidenceTest.reader
    bundle = DetailEvidenceTest.bundle
    def services(self, source_rows):
        reader = self.reader()
        reader.rows['warranty-services'] = source_rows
        return [row for row in self.bundle(reader)['records'] if row['domain'] == 'warranty-services']

    def test_nullable_and_repeated_service_ids_do_not_collapse_source_occurrences(self):
        rows = self.services([dict(id=None, barcode='SN', grade='G'), dict(id=None, barcode='SN', grade='G'),
                              dict(id='shared', barcode='SN', grade='A'), dict(id='shared', barcode='SN', grade='B')])
        self.assertEqual(4, len(rows))
        self.assertEqual(4, len({row['sourceKey'] for row in rows}))
        self.assertIsNone(rows[0]['values']['id'])
        self.assertEqual({':1', ':2'}, {row['sourceKey'][-2:] for row in rows[:2]})

    def test_sql_row_order_does_not_change_the_keyless_multiset_identity(self):
        source = [dict(id=None, barcode='SN', grade='G'), dict(id='shared', barcode='SN', grade='A'),
                  dict(id=None, barcode='SN', grade='G')]
        first = {row['sourceKey'] for row in self.services(source)}
        second = {row['sourceKey'] for row in self.services(list(reversed(source)))}
        self.assertEqual(first, second)

    def test_null_and_empty_original_values_remain_distinct(self):
        rows = self.services([dict(id='same', barcode='SN', remark=None), dict(id='same', barcode='SN', remark='')])
        self.assertNotEqual(rows[0]['sourceKey'], rows[1]['sourceKey'])
        self.assertIsNone(rows[0]['values']['remark'])
        self.assertEqual('', rows[1]['values']['remark'])


if __name__ == '__main__':
    unittest.main()
