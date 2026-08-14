#!/usr/bin/env python3
"""Shared primitives for the commit-locked specification snapshot."""

from __future__ import annotations

import json
import hashlib
import os
import re
import subprocess
import tempfile
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Mapping, Sequence


ALLOWED_CATEGORIES = frozenset(
    {
        "BASELINE",
        "ENGINEERING",
        "SDS",
        "DECISION",
        "TRACEABILITY",
        "DOMAIN_SPEC",
        "MODEL_APPENDIX",
        "MODEL_EVIDENCE",
    }
)
FORBIDDEN_PREFIXES = (
    "docs/engineering/gates/",
    "docs/superpowers/",
    "需求/",
)
CURRENT_GATE_PATHS = frozenset(
    {
        "docs/engineering/gates/phase-1/README.md",
        "docs/engineering/gates/phase-1/gate-status.md",
    }
)
MODEL_EVIDENCE_PATHS = frozenset(
    {
        "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json",
        "specs/001-project-delivery-platform/evidence/migration/target-field-catalog.jsonl",
    }
)


class BaselineError(ValueError):
    """Raised when a baseline contract or repository fact is invalid."""


@dataclass(frozen=True)
class BaselineEntry:
    path: str
    category: str


@dataclass(frozen=True)
class SnapshotChange:
    path: str
    action: str


@dataclass(frozen=True)
class _TargetState:
    kind: str
    content: bytes | None


def _run_git(repo: Path, *args: str) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            ["git", *args],
            cwd=repo,
            check=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        detail = ""
        if isinstance(exc, subprocess.CalledProcessError):
            detail = exc.stderr.decode("utf-8", errors="replace").strip()
        raise BaselineError(detail or f"git command failed: {' '.join(args)}") from exc


def _category_accepts(path: str, category: str) -> bool:
    if category == "BASELINE":
        return path.startswith("docs/baseline/")
    if category == "ENGINEERING":
        return path in {
            "docs/README.md",
            "docs/engineering/00-engineering-chain.md",
            *CURRENT_GATE_PATHS,
        }
    if category == "SDS":
        return path.startswith("docs/design/")
    if category == "DECISION":
        return path.startswith("docs/decisions/")
    if category == "TRACEABILITY":
        return path.startswith("docs/traceability/")
    if category == "DOMAIN_SPEC":
        return path == "specs/001-project-delivery-platform/00-master-spec.md" or path.startswith(
            "specs/001-project-delivery-platform/domains/"
        )
    if category == "MODEL_APPENDIX":
        return path.startswith("specs/001-project-delivery-platform/appendices/")
    if category == "MODEL_EVIDENCE":
        return path in MODEL_EVIDENCE_PATHS
    return False


def validate_relative_path(path: str, category: str) -> None:
    if category not in ALLOWED_CATEGORIES:
        raise BaselineError(f"unknown baseline category: {category}")
    if not path or "\\" in path or re.match(r"^[A-Za-z]:", path) or path.startswith("/"):
        raise BaselineError(f"baseline path must be a POSIX relative path: {path}")

    parts = PurePosixPath(path).parts
    if any(part in {"", ".", ".."} for part in parts):
        raise BaselineError(f"baseline path traversal is forbidden: {path}")
    if (
        (path.startswith(FORBIDDEN_PREFIXES) and path not in CURRENT_GATE_PATHS)
        or "archive" in parts
        or "input" in parts
    ):
        raise BaselineError(f"process or external input material is forbidden: {path}")
    if not _category_accepts(path, category):
        raise BaselineError(f"path does not belong to category {category}: {path}")


def load_allowlist(path: Path) -> tuple[BaselineEntry, ...]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise BaselineError(f"cannot read allowlist: {path}") from exc

    if not isinstance(payload, dict) or set(payload) != {"schemaVersion", "files"}:
        raise BaselineError("allowlist must contain only schemaVersion and files")
    if payload["schemaVersion"] != 1 or not isinstance(payload["files"], list):
        raise BaselineError("allowlist schemaVersion must be 1 and files must be a list")

    entries: list[BaselineEntry] = []
    for raw in payload["files"]:
        if not isinstance(raw, dict) or set(raw) != {"path", "category"}:
            raise BaselineError("each allowlist entry must contain only path and category")
        if not isinstance(raw["path"], str) or not isinstance(raw["category"], str):
            raise BaselineError("allowlist path and category must be strings")
        validate_relative_path(raw["path"], raw["category"])
        entries.append(BaselineEntry(raw["path"], raw["category"]))

    paths = [entry.path for entry in entries]
    if paths != sorted(paths):
        raise BaselineError("allowlist paths must be sorted")
    if len(paths) != len(set(paths)):
        raise BaselineError("allowlist paths must be unique")
    return tuple(entries)


def resolve_full_commit(source_repo: Path, revision: str) -> str:
    if not re.fullmatch(r"[0-9A-Fa-f]{40}", revision):
        raise BaselineError("source revision must be a full 40-character commit id")
    commit_type = _run_git(source_repo, "cat-file", "-t", revision).stdout.decode("ascii").strip()
    if commit_type != "commit":
        raise BaselineError(f"source revision is not a commit: {revision}")
    resolved = _run_git(source_repo, "rev-parse", revision).stdout.decode("ascii").strip().lower()
    if resolved != revision.lower():
        raise BaselineError(f"source revision does not resolve exactly: {revision}")
    return resolved


def read_git_blob(source_repo: Path, commit: str, path: str) -> bytes:
    return _run_git(source_repo, "show", f"{commit}:{path}").stdout


def managed_worktree_changes(
    source_repo: Path,
    entries: Sequence[BaselineEntry],
) -> tuple[str, ...]:
    if not entries:
        return ()
    result = _run_git(
        source_repo,
        "status",
        "--porcelain",
        "--untracked-files=no",
        "--",
        *(entry.path for entry in entries),
    )
    return tuple(
        line.decode("utf-8", errors="replace")
        for line in result.stdout.splitlines()
        if line.strip()
    )


def build_manifest(
    commit: str,
    entries: Sequence[BaselineEntry],
    blobs: Mapping[str, bytes],
) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise BaselineError("manifest commit must be a lowercase full commit id")
    expected_paths = [entry.path for entry in entries]
    if expected_paths != sorted(expected_paths) or len(expected_paths) != len(set(expected_paths)):
        raise BaselineError("manifest entries must be sorted and unique")
    if set(blobs) != set(expected_paths):
        raise BaselineError("blob paths must exactly match baseline entries")

    return {
        "schemaVersion": 1,
        "source": {
            "repositoryId": "project-delivery-platform-spec",
            "commit": commit,
        },
        "files": [
            {
                "path": entry.path,
                "category": entry.category,
                "sha256": hashlib.sha256(blobs[entry.path]).hexdigest(),
            }
            for entry in entries
        ],
    }


def _manifest_entries(manifest: Mapping[str, object]) -> tuple[BaselineEntry, ...]:
    files = manifest.get("files")
    if not isinstance(files, list):
        raise BaselineError("manifest files must be a list")
    entries: list[BaselineEntry] = []
    for raw in files:
        if not isinstance(raw, dict) or set(raw) != {"path", "category", "sha256"}:
            raise BaselineError("manifest file entry must be an object")
        path = raw.get("path")
        category = raw.get("category")
        if not isinstance(path, str) or not isinstance(category, str):
            raise BaselineError("manifest path and category must be strings")
        validate_relative_path(path, category)
        entries.append(BaselineEntry(path, category))
    paths = [entry.path for entry in entries]
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        raise BaselineError("manifest paths must be sorted and unique")
    return tuple(entries)


def _path_is_dirty(repo: Path, path: str) -> bool:
    result = _run_git(repo, "status", "--porcelain", "--untracked-files=all", "--", path)
    return bool(result.stdout.strip())


def plan_snapshot(
    destination_repo: Path,
    manifest: Mapping[str, object],
    blobs: Mapping[str, bytes],
) -> tuple[SnapshotChange, ...]:
    entries = _manifest_entries(manifest)
    if set(blobs) != {entry.path for entry in entries}:
        raise BaselineError("snapshot blobs must exactly match manifest paths")

    changes: list[SnapshotChange] = []
    for entry in entries:
        target = destination_repo / entry.path
        expected = blobs[entry.path]
        if target.is_file() and target.read_bytes() == expected:
            action = "KEEP"
        elif target.exists() and _path_is_dirty(destination_repo, entry.path):
            action = "CONFLICT"
        elif target.exists():
            action = "REPLACE"
        else:
            action = "ADD"
        changes.append(SnapshotChange(entry.path, action))
    return tuple(changes)


def _stage_file(path: Path, content: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temp_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        try:
            os.unlink(temp_name)
        except FileNotFoundError:
            pass
        raise
    return Path(temp_name)


def _target_state(path: Path) -> _TargetState:
    if path.is_file():
        return _TargetState("FILE", path.read_bytes())
    if path.exists():
        return _TargetState("OTHER", None)
    return _TargetState("MISSING", None)


def _ensure_target_states(expected_states: Mapping[Path, _TargetState]) -> None:
    for path, expected in expected_states.items():
        if _target_state(path) != expected:
            raise BaselineError(f"managed destination changed after preflight: {path}")


def _new_backup_path(path: Path) -> Path:
    descriptor, backup_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".backup", dir=path.parent)
    os.close(descriptor)
    return Path(backup_name)


def _restore_moved_target(path: Path, backup: Path) -> None:
    try:
        os.link(backup, path)
    except FileExistsError:
        backup.unlink(missing_ok=True)
    except OSError:
        if path.exists():
            raise
        os.replace(backup, path)
    else:
        backup.unlink(missing_ok=True)


def _restore_after_backup_cleanup_failure(
    path: Path,
    backup: Path,
    cleanup_error: OSError,
) -> None:
    recovery_errors: list[OSError] = []
    for _attempt in range(2):
        try:
            os.replace(backup, path)
            break
        except OSError as recovery_error:
            recovery_errors.append(recovery_error)
    else:
        detail = "; ".join(str(error) for error in recovery_errors)
        raise BaselineError(
            f"failed to clean snapshot backup {backup}: {cleanup_error}; "
            f"failed to restore snapshot target {path}: {detail}"
        ) from cleanup_error

    if recovery_errors:
        raise BaselineError(
            f"failed to clean snapshot backup {backup}: {cleanup_error}; "
            f"initial recovery failed for {path}: {recovery_errors[0]}; target restored"
        ) from cleanup_error
    raise BaselineError(
        f"failed to clean snapshot backup {backup}: {cleanup_error}; target restored"
    ) from cleanup_error


def _publish_staged_file(path: Path, staged: Path, expected: _TargetState) -> None:
    if expected.kind == "MISSING":
        try:
            os.link(staged, path)
        except OSError as exc:
            raise BaselineError(f"failed to publish snapshot target {path}: {exc}") from exc
    elif expected.kind == "FILE":
        backup = _new_backup_path(path)
        applied = _TargetState("FILE", staged.read_bytes())
        try:
            os.replace(path, backup)
        except FileNotFoundError as exc:
            backup.unlink(missing_ok=True)
            raise BaselineError(f"managed destination changed during apply: {path}") from exc
        if _target_state(backup) != expected:
            _restore_moved_target(path, backup)
            raise BaselineError(f"managed destination changed during apply: {path}")
        try:
            os.link(staged, path)
        except OSError as exc:
            _restore_moved_target(path, backup)
            raise BaselineError(f"failed to publish snapshot target {path}: {exc}") from exc
        if _target_state(backup) != expected:
            _publish_staged_file(path, backup, applied)
            raise BaselineError(f"managed destination changed during apply: {path}")
        try:
            backup.unlink(missing_ok=True)
        except OSError as cleanup_error:
            # The outer transaction cannot journal this target until this helper
            # returns, so restore it here if cleanup fails after publication.
            _restore_after_backup_cleanup_failure(path, backup, cleanup_error)
    else:
        raise BaselineError(f"cannot replace non-file managed destination: {path}")
    staged.unlink(missing_ok=True)


def _remove_file_if_unchanged(path: Path, expected: _TargetState) -> None:
    backup = _new_backup_path(path)
    try:
        os.replace(path, backup)
    except FileNotFoundError as exc:
        backup.unlink(missing_ok=True)
        raise BaselineError(f"managed destination changed during apply: {path}") from exc
    if _target_state(backup) != expected:
        _restore_moved_target(path, backup)
        raise BaselineError(f"managed destination changed during apply: {path}")
    backup.unlink(missing_ok=True)


def _restore_target_state(path: Path, original: _TargetState, applied: bytes) -> None:
    if original.kind == "FILE":
        assert original.content is not None
        staged = _stage_file(path, original.content)
        try:
            _publish_staged_file(path, staged, _TargetState("FILE", applied))
        finally:
            staged.unlink(missing_ok=True)
    elif original.kind == "MISSING":
        _remove_file_if_unchanged(path, _TargetState("FILE", applied))
    else:
        raise BaselineError(f"cannot restore non-file managed destination: {path}")


@contextmanager
def _repository_apply_lock(destination_repo: Path):
    """Serialize cooperative snapshot applies for the entire local transaction."""
    lock_path = destination_repo / "docs" / "specification-baseline" / ".apply.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(lock_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError as exc:
        raise BaselineError(f"snapshot apply already in progress: {lock_path}") from exc
    try:
        with os.fdopen(descriptor, "w", encoding="ascii") as handle:
            handle.write(f"pid={os.getpid()}\n")
        yield
    finally:
        lock_path.unlink(missing_ok=True)


def apply_snapshot(
    destination_repo: Path,
    manifest: Mapping[str, object],
    blobs: Mapping[str, bytes],
) -> None:
    """Apply under a cooperative repository lock; non-cooperating writers are out of scope."""
    with _repository_apply_lock(destination_repo):
        _apply_snapshot(destination_repo, manifest, blobs)


def _apply_snapshot(
    destination_repo: Path,
    manifest: Mapping[str, object],
    blobs: Mapping[str, bytes],
) -> None:
    entries = _manifest_entries(manifest)
    manifest_path = destination_repo / "docs" / "specification-baseline" / "manifest.json"
    initial_states = {
        destination_repo / entry.path: _target_state(destination_repo / entry.path)
        for entry in entries
    }
    initial_states[manifest_path] = _target_state(manifest_path)
    changes = plan_snapshot(destination_repo, manifest, blobs)
    conflicts = [change.path for change in changes if change.action == "CONFLICT"]
    manifest_relative_path = "docs/specification-baseline/manifest.json"
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if (
        manifest_path.exists()
        and _path_is_dirty(destination_repo, manifest_relative_path)
        and _target_state(manifest_path) != _TargetState("FILE", manifest_bytes)
    ):
        conflicts.append(manifest_relative_path)
    if conflicts:
        raise BaselineError(f"managed destination has local changes: {', '.join(conflicts)}")

    writes = [
        (destination_repo / change.path, blobs[change.path])
        for change in changes
        if change.action != "KEEP"
    ]
    writes.append((manifest_path, manifest_bytes))
    staged_writes: list[tuple[Path, bytes, Path]] = []
    written: list[tuple[Path, bytes]] = []
    try:
        for path, content in writes:
            staged_writes.append((path, content, _stage_file(path, content)))
        _ensure_target_states(initial_states)
        for path, content, staged in staged_writes:
            _publish_staged_file(path, staged, initial_states[path])
            written.append((path, content))
        expected_final_states = dict(initial_states)
        expected_final_states.update(
            (path, _TargetState("FILE", content)) for path, content in written
        )
        _ensure_target_states(expected_final_states)
    except BaseException as exc:
        rollback_errors: list[BaseException] = []
        for path, content in reversed(written):
            try:
                _restore_target_state(path, initial_states[path], content)
            except BaseException as rollback_error:
                rollback_errors.append(rollback_error)
        if rollback_errors:
            detail = "; ".join(str(error) for error in rollback_errors)
            raise BaselineError(f"snapshot apply failed and rollback failed: {detail}") from exc
        raise
    finally:
        for _, _, staged in staged_writes:
            staged.unlink(missing_ok=True)


def validate_snapshot(destination_repo: Path, manifest_path: Path) -> list[str]:
    errors: list[str] = []
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        return [f"cannot read manifest: {exc}"]

    if not isinstance(manifest, dict) or set(manifest) != {"schemaVersion", "source", "files"}:
        return ["manifest must contain only schemaVersion, source, and files"]
    if manifest.get("schemaVersion") != 1:
        errors.append("manifest schemaVersion must be 1")
    source = manifest.get("source")
    if not isinstance(source, dict) or set(source) != {"repositoryId", "commit"}:
        errors.append("manifest source must contain repositoryId and commit")
    else:
        if source.get("repositoryId") != "project-delivery-platform-spec":
            errors.append("manifest repositoryId is invalid")
        if not isinstance(source.get("commit"), str) or not re.fullmatch(
            r"[0-9a-f]{40}", source["commit"]
        ):
            errors.append("manifest commit must be a lowercase full commit id")

    try:
        entries = _manifest_entries(manifest)
    except BaselineError as exc:
        return [*errors, str(exc)]

    allowlist_path = manifest_path.with_name("allowlist.json")
    try:
        allowed_entries = load_allowlist(allowlist_path)
    except BaselineError as exc:
        return [*errors, str(exc)]
    if entries != allowed_entries:
        errors.append("manifest paths differ from allowlist")

    raw_files = manifest["files"]
    assert isinstance(raw_files, list)
    for entry, raw in zip(entries, raw_files, strict=True):
        sha256 = raw.get("sha256")
        if not isinstance(sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", sha256):
            errors.append(f"invalid sha256: {entry.path}")
            continue
        target = destination_repo / entry.path
        if not target.is_file():
            errors.append(f"missing snapshot file: {entry.path}")
            continue
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual != sha256:
            errors.append(f"sha256 mismatch: {entry.path}")
    return errors
