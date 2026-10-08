import copy
import importlib.util
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('archive', ROOT / 'legacy_project_archive.py')
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)
FIXTURE = Path(__file__).parent / 'fixtures/legacy_project_archive.synthetic.jsonl'

class LegacyProjectArchiveTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        archive.initialize(self.db)
        self.addCleanup(self.db.close)
        self.bundle = json.loads(FIXTURE.read_text(encoding='utf-8').splitlines()[0])

    def load(self, bundle=None):
        return archive.import_bytes(self.db, archive.canonical(bundle or self.bundle).encode(), 1, 'PMS_STRUTS')

    def test_synthetic_reconciliation_and_unknown_state_preserved(self):
        result = archive.import_bytes(self.db, FIXTURE.read_bytes(), 1, 'PMS_STRUTS')
        self.assertEqual((result['retained'], result['replayed'], result['quarantined']), (2, 1, 2))
        self.assertTrue(result['reconciled'])
        self.assertEqual(0, result['runtimeProjectsCreated'])
        value = json.loads(self.db.execute("SELECT payload_json FROM archive_project WHERE source_pk='910002'").fetchone()[0])
        self.assertEqual('UNKNOWN-LEGACY-STATE', value['header']['projectState'])
        self.assertNotIn('lifecycleStatus', value)

    def test_replay_and_changed_identity_never_overwrite_history(self):
        first = self.load()
        self.assertTrue(self.load()['batchReplay'])
        changed = copy.deepcopy(self.bundle)
        changed['header']['projectState'] = 'CHANGED'
        result = self.load(changed)
        self.assertEqual('SOURCE_IDENTITY_CONTENT_CONFLICT', result['issues'][0]['code'])
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])
        self.assertEqual(archive.canonical(self.bundle), self.db.execute('SELECT payload_json FROM archive_project').fetchone()[0])
        self.assertFalse(first['withdrawn'])

    def test_orphan_product_is_isolated_and_no_partial_bundle_written(self):
        self.bundle['products'][0]['contractNo'] = 'MISSING'
        result = self.load()
        self.assertEqual('ORPHAN_PRODUCT_CONTRACT', result['issues'][0]['code'])
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])

    def test_unknown_secret_fields_are_not_stored_in_quarantine(self):
        self.bundle['header']['password'] = 'NEVER-PERSIST-THIS'
        result = self.load()
        self.assertEqual('UNMAPPED_OR_SENSITIVE_FIELD', result['issues'][0]['code'])
        self.assertNotIn('NEVER-PERSIST-THIS', '\n'.join(self.db.iterdump()))

    def test_readonly_history_and_append_only_withdrawal(self):
        batch = self.load()['batchId']
        for query in ['UPDATE archive_project SET source_pk=99', 'DELETE FROM archive_project', 'DELETE FROM archive_batch']:
            with self.assertRaisesRegex(sqlite3.IntegrityError, 'IMMUTABLE_ARCHIVE'):
                self.db.execute(query)
        archive.withdraw(self.db, batch, '合成批次回滚')
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM active_archive_project').fetchone()[0])
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])
        self.assertTrue(self.load()['withdrawn'])
        self.assertTrue(archive.withdraw(self.db, batch, '合成批次回滚')['withdrawn'])
        with self.assertRaisesRegex(ValueError, 'IDEMPOTENCY_CONFLICT'):
            archive.withdraw(self.db, batch, '不同原因')

    def test_withdrawing_one_batch_preserves_other_active_reference(self):
        first = self.load()['batchId']
        archive.import_bytes(self.db, (archive.canonical(self.bundle) + '\n').encode(), 1, 'PMS_STRUTS')
        archive.withdraw(self.db, first, '撤回一批')
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM active_archive_project').fetchone()[0])

    def test_default_dryrun_never_creates_or_changes_file(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'archive.sqlite'
            db = archive.connect(path, False)
            archive.import_bytes(db, FIXTURE.read_bytes(), 1, 'PMS_STRUTS'); db.close()
            self.assertFalse(path.exists())
            db = archive.connect(path, True)
            archive.import_bytes(db, archive.canonical(self.bundle).encode(), 1, "PMS_STRUTS"); db.close()
            before = path.read_bytes()
            db = archive.connect(path, False)
            archive.import_bytes(db, FIXTURE.read_bytes(), 1, 'PMS_STRUTS'); db.close()
            self.assertEqual(before, path.read_bytes())

    def test_duplicate_json_keys_cannot_silently_replace_evidence(self):
        content = archive.canonical(self.bundle).replace('"projectState":"31"', '"projectState":"31","projectState":"32"')
        result = archive.import_bytes(self.db, content.encode(), 1, 'PMS_STRUTS')
        self.assertEqual('DUPLICATE_JSON_KEY', result['issues'][0]['code'])
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])

    def test_invalid_json_and_duplicate_child_keys_quarantined(self):
        result = archive.import_bytes(self.db, b'not-json', 1, 'PMS_STRUTS')
        self.assertEqual('INVALID_JSON', result['issues'][0]['code'])
        self.bundle['members'] *= 2
        self.assertEqual('RELATION_ID_MISSING_OR_DUPLICATED', self.load()['issues'][0]['code'])

    def assert_isolated_identity(self, bad):
        with sqlite3.connect(':memory:') as db:
            archive.initialize(db)
            content = '\n'.join(map(archive.canonical, [self.bundle, bad, self.bundle])).encode()
            result = archive.import_bytes(db, content, 1, 'PMS_STRUTS')
            self.assertEqual((1, 1, 1), (result['retained'], result['replayed'], result['quarantined']))
            self.assertTrue(result['reconciled'])
            self.assertEqual(1, db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])

    def test_missing_header_identity_isolated_without_rolling_back_valid_bundles(self):
        bad = copy.deepcopy(self.bundle)
        bad['sourcePk'] = 'None'
        bad['header'].pop('projectId')
        self.assert_isolated_identity(bad)

    def test_empty_and_null_like_identities_are_isolated(self):
        for identity in (None, '', ' ', 'None', ' none ', 'NULL', 'undefined', 'NaN', True, [], {}):
            with self.subTest(identity=identity):
                bad = copy.deepcopy(self.bundle)
                bad['sourcePk'] = identity
                bad['header']['projectId'] = identity
                for relation in ('members', 'products'):
                    for row in bad[relation]:
                        row['projectId'] = identity
                self.assert_isolated_identity(bad)
        bad = copy.deepcopy(self.bundle)
        bad.pop('sourcePk')
        self.assert_isolated_identity(bad)
        bad = copy.deepcopy(self.bundle)
        bad['header'].pop('projectId')
        self.assert_isolated_identity(bad)

    def test_unknown_schema_rejected_before_any_initialization_write(self):
        for name, versions in (('archive_metadata', [2]), ('ARCHIVE_METADATA', [2]),
                               ('archive_metadata', []), ('archive_metadata', [1, 2])):
            with self.subTest(name=name, versions=versions), sqlite3.connect(':memory:') as db:
                db.execute(f'CREATE TABLE {name}(schema_version INTEGER PRIMARY KEY)')
                db.executemany(f'INSERT INTO {name} VALUES (?)', [(v,) for v in versions])
                db.commit()
                before = db.serialize()
                changes = db.total_changes
                statements = []
                db.set_trace_callback(statements.append)
                with self.assertRaisesRegex(ValueError, 'UNSUPPORTED_ARCHIVE_SCHEMA'):
                    archive.initialize(db)
                self.assertEqual(changes, db.total_changes)
                self.assertEqual(before, db.serialize())
                self.assertFalse(any(sql.lstrip().upper().startswith(('CREATE', 'INSERT', 'UPDATE', 'DELETE'))
                                     for sql in statements), statements)

    def test_unknown_schema_apply_and_dryrun_leave_local_file_byte_identical(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'future.sqlite'
            with sqlite3.connect(path) as db:
                db.execute('CREATE TABLE archive_metadata(schema_version INTEGER PRIMARY KEY)')
                db.execute('INSERT INTO archive_metadata VALUES (2)')
                db.commit()
            db.close()
            before = path.read_bytes()
            for apply in (True, False):
                with self.subTest(apply=apply):
                    with self.assertRaisesRegex(ValueError, 'UNSUPPORTED_ARCHIVE_SCHEMA'):
                        archive.connect(path, apply)
                    self.assertEqual(before, path.read_bytes())

    def test_v1_reinitialize_replay_and_withdraw_remain_valid(self):
        batch = self.load()['batchId']
        archive.initialize(self.db)
        self.assertTrue(self.load()['batchReplay'])
        self.assertTrue(archive.withdraw(self.db, batch, 'v1 regression')['withdrawn'])
        archive.initialize(self.db)
        self.assertEqual([(1,)], self.db.execute('SELECT schema_version FROM archive_metadata').fetchall())
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM archive_project').fetchone()[0])
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM active_archive_project').fetchone()[0])

if __name__ == '__main__':
    unittest.main()
