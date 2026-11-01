import re
import subprocess
import unittest
from pathlib import Path

from build_complete_initialization import NEW_TABLES, SOURCES, render


class CompleteInitializationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[2]
        cls.sources = {key: subprocess.check_output([
            'git', 'show', '77bc691cbc5ad1e58bb51252f2a31dd5047fd6d6:sql/migrations/' + filename],
            cwd=root).decode('utf8') for key, filename in SOURCES.items()}

    def test_no_unrelated_source_tables_or_seed_replay(self):
        sql = render(self.sources)
        created = set(re.findall(r'^CREATE TABLE (?:IF NOT EXISTS )?(\w+)', sql, re.M))
        self.assertEqual(created, {n for group in NEW_TABLES.values() for n in group})
        self.assertNotIn('sol_engineering_briefing_retired', sql)
        self.assertNotIn('CREATE TABLE ast_device_software_version_record', sql)
        self.assertIsNone(re.search(r'^(?:INSERT|UPDATE|DELETE|TRUNCATE)\b', sql, re.M))

    def test_missing_source_statement_fails_instead_of_partial_release(self):
        sources = dict(self.sources, identity='-- missing approved index')
        with self.assertRaisesRegex(ValueError, 'exactly one source statement'):
            render(sources)

    def test_ambiguous_duplicate_source_is_rejected(self):
        sources = dict(self.sources, contacts=self.sources['contacts'] + '\n' + self.sources['contacts'])
        with self.assertRaisesRegex(ValueError, 'exactly one source statement'):
            render(sources)

    def test_binary_checksum_semantics_survive_charset_upgrade(self):
        sql = render(self.sources)
        self.assertNotIn('CHARACTER SET ascii', sql)
        self.assertEqual(sql.count('source_checksum CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin'), 3)
        self.assertEqual(sql.count('source_key VARCHAR(128) COLLATE utf8mb4_bin'), 3)


if __name__ == '__main__':
    unittest.main()
