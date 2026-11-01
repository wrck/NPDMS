import importlib.util
from pathlib import Path
import unittest
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from initialization_policy import validate_configuration
from verify_database import administrator_initialized
spec = importlib.util.spec_from_file_location('initialization', ROOT / 'build_initialization.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class InitializationTests(unittest.TestCase):
    def test_same_row_count_different_config_is_detected(self):
        self.assertNotEqual(build.row_digest([{'id': 1, 'status': 0}]),
                            build.row_digest([{'id': 1, 'status': 1}]))
        self.assertEqual(build.row_digest([{'id': 1}, {'id': 2}]),
                         build.row_digest([{'id': 2}, {'id': 1}]))

    def test_schema_comparison_retains_collation_and_constraints(self):
        source = 'varchar(64) COLLATE utf8mb3_bin NOT NULL'
        self.assertEqual(build.normalized_ddl(source),
                         build.normalized_ddl('varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL'))
        self.assertNotEqual(build.normalized_ddl(source), build.normalized_ddl(source.replace('bin', 'general_ci')))
        self.assertNotEqual(build.normalized_ddl(source), build.normalized_ddl(source.replace('NOT NULL', 'NULL')))

    def test_generated_column_is_not_inserted_but_expression_is_preserved(self):
        ddl = 'CREATE TABLE `plt_delivery_type` (`id` int,`marker` int GENERATED ALWAYS AS (id+1) STORED)'
        structure = {'endpoint': '172.17.90.178:13306/npdms', 'readOnly': True, 'server': {'db': 'npdms', 'version': '8.4.11'},
                     'tables': {'plt_delivery_type': {'TABLE_TYPE': 'BASE TABLE', 'ddl': ddl}},
                     'columns': [{'TABLE_NAME': 'plt_delivery_type', 'COLUMN_NAME': 'id', 'EXTRA': ''},
                                 {'TABLE_NAME': 'plt_delivery_type', 'COLUMN_NAME': 'marker', 'EXTRA': 'STORED GENERATED'}],
                     'triggers': []}
        configuration = {'source': '172.17.90.178:13306/npdms', 'readOnly': True, 'tables': {'plt_delivery_type': [{'id': 1, 'marker': 2}]}}
        sql, _ = build.render(structure, configuration)
        self.assertIn(ddl, sql)
        self.assertIn('INSERT INTO `plt_delivery_type` (`id`) VALUES (1);', sql)
        self.assertNotIn('INSERT INTO `plt_delivery_type` (`id`,`marker`)', sql)
        structure['endpoint'] = configuration['source'] = '179'
        with self.assertRaisesRegex(ValueError, 'read-only source'):
            build.render(structure, configuration)

    def test_reject_business_rows_examples_people_and_nested_secrets(self):
        invalid = [
            {'proj_project': [{'id': 1}]},
            {'system_menu': [{'id': 2479, 'permission': 'infra:demo01-contact:query'}]},
            {'acc_satisfaction_questionnaire_template': [{'template_code': 'SAT_MANUAL_EXAMPLE'}]},
            {'system_dict_type': [{'type': 'bpm_oa_leave_type'}]},
            {'system_users': [{'id': 2, 'username': 'test'}]},
            {'system_dept': [{'leader_user_id': 2}]},
            {'infra_config': [{'value': '{"api_secret":"test-secret"}'}]},
        ]
        for tables in invalid:
            with self.subTest(tables=list(tables)), self.assertRaises(ValueError):
                validate_configuration({'tables': tables})

    def test_administrator_requires_original_identity_and_bcrypt(self):
        admin = {'id': 1, 'tenant_id': 1, 'username': 'admin', 'status': 0,
                 'deleted': b'\x00', 'password': '$2b$12$' + 'a' * 53}
        self.assertTrue(administrator_initialized([admin]))
        for field, value in [('password', 'plaintext'), ('password', '!BOOTSTRAP_REQUIRED!'),
                             ('password', '$2b$04$' + 'a' * 53), ('username', 'test'),
                             ('status', 1), ('tenant_id', 2), ('deleted', b'\x01')]:
            with self.subTest(field=field, value=value):
                self.assertFalse(administrator_initialized([dict(admin, **{field: value})]))
        self.assertFalse(administrator_initialized([admin, admin]))

    def test_business_test_terminology_and_diagnostic_actions_are_allowed(self):
        validate_configuration({'tables': {
            'system_dept': [{'name': '测试中心', 'leader_user_id': None}],
            'system_menu': [{'id': 1106, 'name': '发送测试短信', 'permission': 'system:sms-template:send-sms'}],
            'system_dict_data': [{'label': '售前测试项目'}],
        }})

    def test_credential_text_is_never_used_as_sql_syntax(self):
        value = "a'); DROP TABLE t; -- ${literal}"
        literal = build.literal(value)
        self.assertNotIn('DROP TABLE', literal)
        self.assertNotIn('${literal}', literal)
        self.assertIn(value.encode().hex(), literal)


if __name__ == '__main__':
    unittest.main()
