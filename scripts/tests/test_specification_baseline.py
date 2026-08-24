from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import specification_baseline

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


class SpecificationBaselineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = Path(__file__).resolve().parents[2]

    def test_project_feature_inputs_are_allowlisted(self) -> None:
        allowlist = load_allowlist(self.repo / "docs/specification-baseline/allowlist.json")
        paths = {item.path for item in allowlist}
        required = {
            "docs/baseline/prd-v1.8.md",
            "docs/baseline/prd-v1.8-amendment-001-no-manual-project-draft.md",
            "docs/coding/database-query-interface.md",
            "docs/decisions/0029-stage-task-work-binding-workbench.md",
            "docs/decisions/0030-project-task-execution-contract-and-cutover-checklist-carriers.md",
            "docs/decisions/0032-manual-project-creation-cross-context-atomicity.md",
            "specs/features/README.md",
            "specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md",
            "specs/features/F-PROJ-002-physical-contract.json",
            "specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md",
        }
        self.assertTrue(required <= paths)


class SpecificationBaselinePathTest(unittest.TestCase):
    def test_allowlist_contains_exactly_92_files(self) -> None:
        allowlist = Path(__file__).resolve().parents[2] / "docs/specification-baseline/allowlist.json"

        entries = load_allowlist(allowlist)

        self.assertEqual(92, len(entries))
        self.assertEqual(92, len({entry.path for entry in entries}))

    def test_accepts_feature_spec_paths(self) -> None:
        feature_spec_paths = (
            "specs/features/README.md",
            "specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md",
            "specs/features/F-PROJ-002-physical-contract.json",
            "specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md",
        )

        for path in feature_spec_paths:
            with self.subTest(path=path):
                validate_relative_path(path, "FEATURE_SPEC")

    def test_accepts_coding_baseline_paths(self) -> None:
        validate_relative_path(
            "docs/coding/database-query-interface.md",
            "ENGINEERING",
        )

    def test_accepts_only_current_phase_gate_entry_paths(self) -> None:
        current_gate_paths = (
            "docs/engineering/gates/phase-1/README.md",
            "docs/engineering/gates/phase-1/gate-status.md",
            "docs/engineering/gates/phase-2/README.md",
            "docs/engineering/gates/phase-2/gate-status.md",
            "docs/engineering/gates/phase-3/README.md",
            "docs/engineering/gates/phase-3/gate-status.md",
        )

        for path in current_gate_paths:
            with self.subTest(path=path):
                validate_relative_path(path, "ENGINEERING")

    def test_current_phase_1_gate_is_approved_and_ready_for_phase_2_v18(self) -> None:
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
        self.assertIn("结论：`READY_FOR_PHASE_2_V1.8`", gate_status)
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
            "docs/engineering/gates/phase-2/independent-review.md",
            "docs/engineering/gates/phase-2/self-review.md",
            "docs/engineering/gates/phase-3/evidence-packet-templates/README.md",
            "docs/engineering/gates/phase-3/independent-review.md",
            "docs/engineering/gates/phase-3/self-review.md",
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
        self._git("add", "docs/specification-baseline")
        self._git("commit", "-q", "-m", "snapshot contract")
        return manifest_path

    def _assert_no_transaction_artifacts(self) -> None:
        paths = [*(self.repo / entry.path for entry in self.entries)]
        paths.append(self.repo / "docs/specification-baseline/manifest.json")
        for path in paths:
            self.assertEqual([], list(path.parent.glob(f".{path.name}.*")))
        self.assertFalse((self.repo / "docs/specification-baseline/.apply.lock").exists())

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

    def test_apply_rolls_back_when_second_write_fails(self) -> None:
        original_contents = (b"old prd\n", b"old sds\n")
        for entry, content in zip(self.entries, original_contents, strict=True):
            target = self.repo / entry.path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self._git("add", entry.path)
        self._git("commit", "-q", "-m", "managed snapshot")

        original_publish = specification_baseline._publish_staged_file
        write_count = 0

        def fail_second_write(path: Path, staged: Path, expected: object) -> None:
            nonlocal write_count
            write_count += 1
            if write_count == 2:
                raise OSError("second write failed")
            original_publish(path, staged, expected)

        with patch("specification_baseline._publish_staged_file", side_effect=fail_second_write):
            with self.assertRaisesRegex(OSError, "second write failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(original_contents[0], (self.repo / self.entries[0].path).read_bytes())
        self.assertEqual(original_contents[1], (self.repo / self.entries[1].path).read_bytes())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())

    def test_apply_restores_nonfirst_target_when_link_raises_os_error(self) -> None:
        original_contents = (b"old prd\n", b"old sds\n")
        for entry, content in zip(self.entries, original_contents, strict=True):
            target = self.repo / entry.path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self._git("add", entry.path)
        self._git("commit", "-q", "-m", "managed snapshot")
        second_target = self.repo / self.entries[1].path
        original_link = specification_baseline.os.link

        def fail_second_target_link(source: object, destination: object) -> None:
            if Path(destination) == second_target:
                raise OSError("second target link failed")
            original_link(source, destination)

        with patch("specification_baseline.os.link", side_effect=fail_second_target_link):
            with self.assertRaisesRegex(BaselineError, "second target link failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(original_contents[0], (self.repo / self.entries[0].path).read_bytes())
        self.assertEqual(original_contents[1], second_target.read_bytes())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())
        self._assert_no_transaction_artifacts()

    def test_apply_restores_manifest_when_link_raises_os_error(self) -> None:
        manifest_path = self._write_contract_files()
        original_manifest = manifest_path.read_bytes()
        original_link = specification_baseline.os.link

        def fail_manifest_link(source: object, destination: object) -> None:
            if Path(destination) == manifest_path:
                raise OSError("manifest link failed")
            original_link(source, destination)

        with patch("specification_baseline.os.link", side_effect=fail_manifest_link):
            with self.assertRaisesRegex(BaselineError, "manifest link failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(original_manifest, manifest_path.read_bytes())
        self.assertFalse((self.repo / self.entries[0].path).exists())
        self.assertFalse((self.repo / self.entries[1].path).exists())
        self._assert_no_transaction_artifacts()

    def test_apply_restores_batch_when_nonfirst_backup_cleanup_fails(self) -> None:
        original_contents = (b"old prd\n", b"old sds\n")
        for entry, content in zip(self.entries, original_contents, strict=True):
            target = self.repo / entry.path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self._git("add", entry.path)
        self._git("commit", "-q", "-m", "managed snapshot")
        second_target = self.repo / self.entries[1].path
        original_unlink = Path.unlink
        cleanup_failed = False

        def fail_second_backup_cleanup(path: Path, *args: object, **kwargs: object) -> None:
            nonlocal cleanup_failed
            if (
                not cleanup_failed
                and path.parent == second_target.parent
                and path.name.startswith(f".{second_target.name}.")
                and path.name.endswith(".backup")
            ):
                cleanup_failed = True
                raise OSError("second backup cleanup failed")
            original_unlink(path, *args, **kwargs)

        with patch.object(Path, "unlink", autospec=True, side_effect=fail_second_backup_cleanup):
            with self.assertRaisesRegex(BaselineError, "second backup cleanup failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertTrue(cleanup_failed)
        self.assertEqual(original_contents[0], (self.repo / self.entries[0].path).read_bytes())
        self.assertEqual(original_contents[1], second_target.read_bytes())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())
        self._assert_no_transaction_artifacts()

    def test_apply_restores_manifest_when_backup_cleanup_fails(self) -> None:
        manifest_path = self._write_contract_files()
        original_manifest = manifest_path.read_bytes()
        original_unlink = Path.unlink
        cleanup_failed = False

        def fail_manifest_backup_cleanup(path: Path, *args: object, **kwargs: object) -> None:
            nonlocal cleanup_failed
            if (
                not cleanup_failed
                and path.parent == manifest_path.parent
                and path.name.startswith(f".{manifest_path.name}.")
                and path.name.endswith(".backup")
            ):
                cleanup_failed = True
                raise OSError("manifest backup cleanup failed")
            original_unlink(path, *args, **kwargs)

        with patch.object(Path, "unlink", autospec=True, side_effect=fail_manifest_backup_cleanup):
            with self.assertRaisesRegex(BaselineError, "manifest backup cleanup failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertTrue(cleanup_failed)
        self.assertEqual(original_manifest, manifest_path.read_bytes())
        self.assertFalse((self.repo / self.entries[0].path).exists())
        self.assertFalse((self.repo / self.entries[1].path).exists())
        self._assert_no_transaction_artifacts()

    def test_apply_aggregates_cleanup_and_transient_recovery_failures(self) -> None:
        target = self.repo / self.entries[0].path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"old prd\n")
        self._git("add", self.entries[0].path)
        self._git("commit", "-q", "-m", "managed snapshot")
        original_unlink = Path.unlink
        original_replace = specification_baseline.os.replace
        cleanup_failed = False
        recovery_failed = False

        def fail_backup_cleanup(path: Path, *args: object, **kwargs: object) -> None:
            nonlocal cleanup_failed
            if not cleanup_failed and path.name.endswith(".backup"):
                cleanup_failed = True
                raise OSError("backup cleanup failed")
            original_unlink(path, *args, **kwargs)

        def fail_first_recovery(source: object, destination: object) -> None:
            nonlocal recovery_failed
            if not recovery_failed and Path(source).name.endswith(".backup") and Path(destination) == target:
                recovery_failed = True
                raise OSError("recovery failed")
            original_replace(source, destination)

        with (
            patch.object(Path, "unlink", autospec=True, side_effect=fail_backup_cleanup),
            patch("specification_baseline.os.replace", side_effect=fail_first_recovery),
        ):
            with self.assertRaisesRegex(
                BaselineError,
                "backup cleanup failed.*recovery failed",
            ):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertTrue(cleanup_failed)
        self.assertTrue(recovery_failed)
        self.assertEqual(b"old prd\n", target.read_bytes())
        self.assertFalse((self.repo / self.entries[1].path).exists())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())
        self._assert_no_transaction_artifacts()

    def test_apply_reports_rollback_failure(self) -> None:
        original_contents = (b"old prd\n", b"old sds\n")
        for entry, content in zip(self.entries, original_contents, strict=True):
            target = self.repo / entry.path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self._git("add", entry.path)
        self._git("commit", "-q", "-m", "managed snapshot")
        original_publish = specification_baseline._publish_staged_file
        publish_count = 0

        def fail_publish_and_rollback(path: Path, staged: Path, expected: object) -> None:
            nonlocal publish_count
            publish_count += 1
            if publish_count == 2:
                raise OSError("publish failed")
            if publish_count == 3:
                raise OSError("rollback failed")
            original_publish(path, staged, expected)

        with patch(
            "specification_baseline._publish_staged_file",
            side_effect=fail_publish_and_rollback,
        ):
            with self.assertRaisesRegex(BaselineError, "snapshot apply failed and rollback failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(3, publish_count)

    def test_apply_lock_covers_plan_publish_and_rollback(self) -> None:
        original_contents = (b"old prd\n", b"old sds\n")
        for entry, content in zip(self.entries, original_contents, strict=True):
            target = self.repo / entry.path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            self._git("add", entry.path)
        self._git("commit", "-q", "-m", "managed snapshot")
        lock_path = self.repo / "docs/specification-baseline/.apply.lock"
        original_plan_snapshot = specification_baseline.plan_snapshot
        original_publish = specification_baseline._publish_staged_file
        observed_lock_states: list[bool] = []
        publish_count = 0

        def observe_plan(*args: object, **kwargs: object) -> object:
            observed_lock_states.append(lock_path.is_file())
            return original_plan_snapshot(*args, **kwargs)

        def observe_publish(path: Path, staged: Path, expected: object) -> None:
            nonlocal publish_count
            observed_lock_states.append(lock_path.is_file())
            publish_count += 1
            if publish_count == 2:
                raise OSError("publish failed")
            original_publish(path, staged, expected)

        with patch("specification_baseline.plan_snapshot", side_effect=observe_plan), patch(
            "specification_baseline._publish_staged_file", side_effect=observe_publish
        ):
            with self.assertRaisesRegex(OSError, "publish failed"):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual([True, True, True, True], observed_lock_states)
        self.assertFalse(lock_path.exists())

    def test_apply_refuses_second_repository_lock_holder(self) -> None:
        lock_path = self.repo / "docs/specification-baseline/.apply.lock"

        with specification_baseline._repository_apply_lock(self.repo):
            with self.assertRaisesRegex(BaselineError, "already in progress"):
                apply_snapshot(self.repo, self.manifest, self.blobs)
            self.assertTrue(lock_path.is_file())

        self.assertFalse(lock_path.exists())
        self.assertFalse((self.repo / self.entries[0].path).exists())
        self.assertFalse((self.repo / self.entries[1].path).exists())

    def test_apply_refuses_target_drift_after_preflight(self) -> None:
        target = self.repo / self.entries[0].path
        target.parent.mkdir(parents=True)
        target.write_bytes(b"committed\n")
        self._git("add", self.entries[0].path)
        self._git("commit", "-q", "-m", "managed snapshot")
        original_plan_snapshot = specification_baseline.plan_snapshot

        def mutate_target_after_preflight(*args: object, **kwargs: object) -> object:
            changes = original_plan_snapshot(*args, **kwargs)
            target.write_bytes(b"local edit after preflight\n")
            return changes

        with patch(
            "specification_baseline.plan_snapshot",
            side_effect=mutate_target_after_preflight,
        ):
            with self.assertRaises(BaselineError):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(b"local edit after preflight\n", target.read_bytes())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())

    def test_apply_refuses_drift_immediately_before_replacement(self) -> None:
        target = self.repo / self.entries[0].path
        target.parent.mkdir(parents=True)
        target.write_bytes(b"committed\n")
        self._git("add", self.entries[0].path)
        self._git("commit", "-q", "-m", "managed snapshot")
        original_replace = specification_baseline.os.replace
        injected = False

        def mutate_before_replacement(source: object, destination: object) -> None:
            nonlocal injected
            if not injected and Path(source) == target:
                injected = True
                target.write_bytes(b"local edit before replacement\n")
            original_replace(source, destination)

        with patch("specification_baseline.os.replace", side_effect=mutate_before_replacement):
            with self.assertRaises(BaselineError):
                apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertTrue(injected)
        self.assertEqual(b"local edit before replacement\n", target.read_bytes())
        self.assertFalse((self.repo / self.entries[1].path).exists())
        self.assertFalse((self.repo / "docs/specification-baseline/manifest.json").exists())

    def test_apply_refuses_dirty_manifest(self) -> None:
        manifest_path = self.repo / "docs/specification-baseline/manifest.json"
        manifest_path.parent.mkdir(parents=True)
        manifest_path.write_bytes(b"committed manifest\n")
        self._git("add", "docs/specification-baseline/manifest.json")
        self._git("commit", "-q", "-m", "managed manifest")
        manifest_path.write_bytes(b"local manifest edit\n")

        with self.assertRaises(BaselineError):
            apply_snapshot(self.repo, self.manifest, self.blobs)

        self.assertEqual(b"local manifest edit\n", manifest_path.read_bytes())
        self.assertFalse((self.repo / self.entries[0].path).exists())
        self.assertFalse((self.repo / self.entries[1].path).exists())

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
