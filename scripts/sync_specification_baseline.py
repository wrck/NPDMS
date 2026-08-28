#!/usr/bin/env python3
"""Check or apply a commit-locked specification baseline snapshot."""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

from specification_baseline import (
    BaselineError,
    apply_snapshot,
    build_manifest,
    load_allowlist,
    managed_worktree_changes,
    plan_snapshot,
    read_git_blob,
    resolve_full_commit,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-repo", required=True, type=Path)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--allowlist", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    destination_repo = Path(__file__).resolve().parents[1]
    allowlist_path = args.allowlist
    if not allowlist_path.is_absolute():
        allowlist_path = destination_repo / allowlist_path

    try:
        entries = load_allowlist(allowlist_path)
        commit = resolve_full_commit(args.source_repo, args.revision)
        source_changes = managed_worktree_changes(args.source_repo, entries)
        if source_changes:
            raise BaselineError(
                "allowlisted source files have uncommitted changes:\n"
                + "\n".join(source_changes)
            )
        blobs = {
            entry.path: read_git_blob(args.source_repo, commit, entry.path)
            for entry in entries
        }
        manifest = build_manifest(commit, entries, blobs)
        changes = plan_snapshot(destination_repo, manifest, blobs)
        counts = Counter(change.action for change in changes)
        for change in changes:
            print(f"{change.action}\t{change.path}")
        print(
            "SUMMARY "
            + " ".join(
                f"{action}={counts[action]}"
                for action in ("ADD", "REPLACE", "KEEP", "CONFLICT")
            )
            + f" TOTAL={len(changes)}"
        )
        if counts["CONFLICT"]:
            return 1
        if args.apply:
            apply_snapshot(destination_repo, manifest, blobs)
            print("APPLIED docs/specification-baseline/manifest.json")
        return 0
    except BaselineError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
