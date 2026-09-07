"""PR7 R05/R06: source inventory cannot masquerade as accepted implementation."""
import csv
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from generate_pr7_reception_projection import build_rows, classify, MARKER, RAW_PATH
from validate_pr7_integration_reception import derive_feature


class Pr7ReceptionProjectionTest(unittest.TestCase):
    def test_service_level_is_owned_by_cus_and_supports_cut002_only(self):
        policy = json.loads((ROOT / 'docs/traceability/code-fact-chronological-resolution-policy-2026-09-04.json').read_text())
        rows = list(csv.DictReader(io.StringIO((ROOT / RAW_PATH).read_text())))
        support = [r for r in rows if r['path'].startswith('pms-module-customer/') and '/api/servicelevel/' in r['path']]
        self.assertTrue(support)
        for row in support:
            with self.subTest(path=row['path']):
                self.assertEqual('F-CUT-002', derive_feature(row['feature'], row['sourceBranches'], row['path'], row['subject'], policy['featureRules'])[0])
        projected = [row for row in build_rows() if row['owner'] == 'CUS']
        self.assertEqual(len({r['path'] for r in support}), len(projected))
        self.assertTrue(all(row['coverageClaim'] == 'NONE_SUPPORTING_CONTRACT_ONLY' for row in projected))

    def test_missing_paths_and_renumbers_never_claim_acceptance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'sql/migrations').mkdir(parents=True)
            successor = root / 'sql/migrations/V193__arrival.sql'
            successor.write_text('-- current schema')
            self.assertEqual(('ABSENT_REQUIRES_REVIEW', ''), classify(root, 'missing.java', set()))
            self.assertEqual(('EXCLUDED_BY_OWNER_SELECTION', ''), classify(root, 'retired.java', {'retired.java'}))
            self.assertEqual(('SUCCESSOR_CANDIDATE_REQUIRES_REVIEW', 'sql/migrations/V193__arrival.sql'),
                             classify(root, 'sql/migrations/V133__arrival.sql', set()))
            self.assertEqual(('PRESENT_NOT_ACCEPTANCE_PROOF', 'sql/migrations/V193__arrival.sql'),
                             classify(root, 'sql/migrations/V193__arrival.sql', set()))

    def test_task_status_sources_do_not_contain_generated_receipt_suffixes(self):
        for path in (ROOT / 'tasks/features').glob('F-*.md'):
            with self.subTest(path=path.name):
                self.assertNotIn(MARKER, path.read_text())

    def test_projection_retains_provenance_and_missing_rows(self):
        rows = build_rows()
        self.assertTrue(any(row['pathStatus'] == 'ABSENT_REQUIRES_REVIEW' for row in rows))
        self.assertTrue(any(row['pathStatus'] == 'EXCLUDED_BY_OWNER_SELECTION' for row in rows))
        for row in rows:
            with self.subTest(path=row['sourcePath']):
                self.assertTrue(row['sourceCommits'])
                self.assertNotEqual('COMPLETE', row['coverageClaim'])
                if row['pathStatus'] == 'PRESENT_NOT_ACCEPTANCE_PROOF':
                    self.assertTrue((ROOT / row['currentPath']).is_file())
                else:
                    self.assertFalse((ROOT / row['sourcePath']).is_file())
