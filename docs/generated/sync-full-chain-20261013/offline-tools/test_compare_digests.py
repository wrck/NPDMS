import gzip
import json
import tempfile
import unittest
from pathlib import Path
from compare_digests import delta, compare


class DigestTests(unittest.TestCase):
    def snapshots(self, start, end):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        paths = [Path(temp.name) / 'start.gz', Path(temp.name) / 'end.gz']
        for path, rows in zip(paths, [start, end]):
            with gzip.open(path, 'wt') as f:
                for row in rows:
                    f.write(json.dumps(row) + '\n')
        return paths

    def test_exact_cumulative_subtraction_and_scope(self):
        row = dict(schema='db_new', digest='a', count=10, timer_ps='9007199254740993',
                   normalized_sql='SELECT * FROM db_new.tab')
        a, b = self.snapshots([row], [dict(row, count=13, timer_ps='9007199254741000')])
        rows, warnings = delta(a, b, 'db_new', 'src')
        result = rows[('target', 'SELECT * FROM <target>.tab')]
        self.assertEqual(result['count'], 3)
        self.assertEqual(result['timer_ps'], 7)
        self.assertEqual(warnings, {'negative_deltas': [], 'missing_end_digests': []})

    def test_counter_reset_is_reported_not_hidden(self):
        row = dict(schema='src', digest='a', count=10)
        a, b = self.snapshots([row], [dict(row, count=2)])
        rows, warnings = delta(a, b, 'target', 'src')
        self.assertFalse(rows)
        self.assertEqual(len(warnings['negative_deltas']), 1)

    def test_added_removed_and_zero_baseline(self):
        a, b = self.snapshots([dict(schema='src', digest='gone', count=1)],
                              [dict(schema='src', digest='new', count=4, timer_ps='2000000000000',
                                    normalized_sql='SELECT * FROM reuse_reusev154m_phase')])
        rows, warnings = delta(a, b, 'target', 'src')
        result = compare({}, rows)[0]
        self.assertIsNone(result['count_ratio'])
        self.assertEqual(result['candidate_seconds'], 2)
        self.assertEqual(result['scope'], 'source')
        self.assertEqual(len(warnings['missing_end_digests']), 1)


if __name__ == '__main__':
    unittest.main()
