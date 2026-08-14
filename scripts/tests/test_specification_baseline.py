from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from specification_baseline import (
    BaselineError,
    BaselineEntry,
    apply_snapshot,
    build_manifest,
    load_allowlist,
    managed_worktree_changes,
    plan_snapshot,
    read_git_blob,
    resolve_full_commit,
    validate_snapshot,
    validate_relative_path,
)


class SpecificationBaselinePathTest(unittest.TestCase):
    def test_allowlist_contains_exactly_111_files(self) -> None:
        allowlist = Path(__file__).resolve().parents[2] / "docs/specification-baseline/allowlist.json"

        entries = load_allowlist(allowlist)

        self.assertEqual(111, len(entries))
        self.assertEqual(111, len({entry.path for entry in entries}))

    def test_current_phase_1_gate_is_approved_and_ready_for_phase_2(self) -> None:
        repository = Path(__file__).resolve().parents[2]
        allowlist = load_allowlist(repository / "docs/specification-baseline/allowlist.json")
        managed_paths = {entry.path for entry in allowlist}
        self.assertTrue(
            {
                "docs/engineering/gates/phase-1/README.md",
                "docs/engineering/gates/phase-1/gate-status.md",
            }.issubset(managed_paths)
        )

        readme = (repository / "docs/engineering/gates/phase-1/README.md").read_text(
            encoding="utf-8"
        )
        gate_status = (repository / "docs/engineering/gates/phase-1/gate-status.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("审查状态：`APPROVED`", gate_status)
        self.assertIn("结论：`READY_FOR_PHASE_2`", gate_status)
        self.assertNotIn("当前仍为 `NOT_READY_FOR_PHASE_2`", readme)

    def test_rejects_path_traversal(self) -> None:
        invalid_paths = (
            "../docs/baseline/prd-v1.7.md",
            "docs/../需求/input.md",
            "/docs/baseline/prd-v1.7.md",
            "C:/docs/baseline/prd-v1.7.md",
            "docs\\baseline\\prd-v1.7.md",
        )

        for path in invalid_paths:
            with self.subTest(path=path), self.assertRaises(BaselineError):
                validate_relative_path(path, "BASELINE")

    def test_rejects_forbidden_process_material(self) -> None:
        invalid_paths = (
            "docs/engineering/gates/phase-3/gate-status.md",
            "docs/superpowers/plans/internal.md",
            "docs/design/archive/old.md",
            "docs/design/input/external.md",
            "需求/割接分析.md",
        )

        for path in invalid_paths:
            with self.subTest(path=path), self.assertRaises(BaselineError):
                validate_relative_path(path, "SDS")

    def test_rejects_duplicate_or_unsorted_allowlist(self) -> None:
        invalid_files = (
            [
                {"path": "docs/design/02-domain-model.md", "category": "SDS"},
                {"path": "docs/design/01-requirement-traceability.md", "category": "SDS"},
            ],
            [
                {"path": "docs/design/01-requirement-traceability.md", "category": "SDS"},
                {"path": "docs/design/01-requirement-traceability.md", "category": "SDS"},
            ],
        )

        for files in invalid_files:
            with self.subTest(files=files), tempfile.TemporaryDirectory() as temp_dir:
                allowlist = Path(temp_dir) / "allowlist.json"
                allowlist.write_text(
                    json.dumps({"schemaVersion": 1, "files": files}),
                    encoding="utf-8",
                )
                with self.assertRaises(BaselineError):
                    load_allowlist(allowlist)


class SpecificationBaselineGitTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp_dir.name)
        self._git("init", "-q")
        self._git("config", "user.name", "Baseline Test")
        self._git("config", "user.email", "baseline@example.invalid")
        source = self.repo / "docs" / "baseline" / "prd-v1.7.md"
        source.parent.mkdir(parents=True)
        source.write_bytes(b"baseline\n")
        self._git("add", "docs/baseline/prd-v1.7.md")
        self._git("commit", "-q", "-m", "baseline")
        self.commit = self._git("rev-parse", "HEAD").stdout.strip()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *args],
            cwd=self.repo,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )

    def test_rejects_short_revision(self) -> None:
        with self.assertRaises(BaselineError):
            resolve_full_commit(self.repo, self.commit[:12])

    def test_reads_blob_from_commit_not_worktree(self) -> None:
        source = self.repo / "docs" / "baseline" / "prd-v1.7.md"
        source.write_bytes(b"uncommitted\n")

        content = read_git_blob(
            self.repo,
            resolve_full_commit(self.repo, self.commit),
            "docs/baseline/prd-v1.7.md",
        )

        self.assertEqual(b"baseline\n", content)

    def test_reports_managed_source_worktree_change(self) -> None:
        source = self.repo / "docs" / "baseline" / "prd-v1.7.md"
        source.write_bytes(b"uncommitted\n")
        entries = (BaselineEntry("docs/baseline/prd-v1.7.md", "BASELINE"),)

        changes = managed_worktree_changes(self.repo, entries)

        self.assertEqual(1, len(changes))
        self.assertIn("docs/baseline/prd-v1.7.md", changes[0])


class SpecificationBaselineSnapshotTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.repo = self.root / "target"
        self.repo.mkdir()
        self._git("init", "-q")
        self._git("config", "user.name", "Baseline Test")
        self._git("config", "user.email", "baseline@example.invalid")
        (self.repo / ".gitkeep").write_bytes(b"")
        self._git("add", ".gitkeep")
        self._git("commit", "-q", "-m", "target")
        self.entries = (
            BaselineEntry("docs/baseline/prd-v1.7.md", "BASELINE"),
            BaselineEntry("docs/design/01-requirement-traceability.md", "SDS"),
        )
        self.blobs = {
            self.entries[0].path: b"prd\n",
            self.entries[1].path: b"sds\n",
        }
        self.manifest = build_manifest("a" * 40, self.entries, self.blobs)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *args],
            cwd=self.repo,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )

    def _write_contract_files(self) -> Path:
        contract_dir = self.repo / "docs" / "specification-baseline"
        contract_dir.mkdir(parents=True, exist_ok=True)
        allowlist = {
            "schemaVersion": 1,
            "files": [
                {"path": entry.path, "category": entry.category}
                for entry in self.entries
            ],
        }
        (contract_dir / "allowlist.json").write_text(
            json.dumps(allowlist, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        manifest_path = contract_dir / "manifest.json"
        manifest_path.write_text(
            json.dumps(self.manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        return manifest_path

    def test_check_mode_never_writes(self) -> None:
        changes = plan_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(["ADD", "ADD"], [change.action for change in changes])
        self.assertFalse((self.repo / self.entries[0].path).exists())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())

    def test_apply_creates_replaces_and_keeps_exact_files(self) -> None:
        replace_path = self.repo / self.entries[0].path
        replace_path.parent.mkdir(parents=True)
        replace_path.write_bytes(b"old\n")
        self._git("add", self.entries[0].path)
        self._git("commit", "-q", "-m", "old snapshot")
        keep_path = self.repo / self.entries[1].path
        keep_path.parent.mkdir(parents=True)
        keep_path.write_bytes(self.blobs[self.entries[1].path])

        changes = plan_snapshot(self.repo, self.manifest, self.blobs)
        apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(["REPLACE", "KEEP"], [change.action for change in changes])
        self.assertEqual(b"prd\n", replace_path.read_bytes())
        self.assertEqual(b"sds\n", keep_path.read_bytes())
        self.assertTrue((self.repo / "docs/specification-baseline/manifest.json").is_file())

    def test_apply_refuses_dirty_managed_destination(self) -> None:
        target = self.repo / self.entries[0].path
        target.parent.mkdir(parents=True)
        target.write_bytes(b"committed\n")
        self._git("add", self.entries[0].path)
        self._git("commit", "-q", "-m", "managed")
        target.write_bytes(b"local edit\n")

        changes = plan_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual("CONFLICT", changes[0].action)
        with self.assertRaises(BaselineError):
            apply_snapshot(self.repo, self.manifest, self.blobs)
        self.assertEqual(b"local edit\n", target.read_bytes())

    def test_second_apply_is_idempotent(self) -> None:
        apply_snapshot(self.repo, self.manifest, self.blobs)

        changes = plan_snapshot(self.repo, self.manifest, self.blobs)
        apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(["KEEP", "KEEP"], [change.action for change in changes])

    def test_validator_rejects_missing_file(self) -> None:
        self._write_contract_files()
        apply_snapshot(self.repo, self.manifest, self.blobs)
        manifest_path = self.repo / "docs/specification-baseline/manifest.json"
        (self.repo / self.entries[0].path).unlink()

        errors = validate_snapshot(self.repo, manifest_path)

        self.assertTrue(any("missing snapshot file" in error for error in errors))

    def test_validator_rejects_one_byte_drift(self) -> None:
        self._write_contract_files()
        apply_snapshot(self.repo, self.manifest, self.blobs)
        manifest_path = self.repo / "docs/specification-baseline/manifest.json"
        (self.repo / self.entries[0].path).write_bytes(b"prd!\n")

        errors = validate_snapshot(self.repo, manifest_path)

        self.assertTrue(any("sha256 mismatch" in error for error in errors))

    def test_validator_rejects_unregistered_manifest_entry(self) -> None:
        manifest_path = self._write_contract_files()
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
        payload["files"].append(
            {
                "path": "docs/design/02-domain-model.md",
                "category": "SDS",
                "sha256": "0" * 64,
            }
        )
        manifest_path.write_text(json.dumps(payload), encoding="utf-8")

        errors = validate_snapshot(self.repo, manifest_path)

        self.assertTrue(any("manifest paths differ from allowlist" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
