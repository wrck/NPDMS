#!/usr/bin/env python3
"""Validate the local specification snapshot without reading the source repo."""

from __future__ import annotations

import sys
from pathlib import Path

from specification_baseline import validate_snapshot


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    manifest = repository / "docs/specification-baseline/manifest.json"
    errors = validate_snapshot(repository, manifest)
    for error in errors:
        print(f"FAIL {error}")
    if errors:
        print(f"SUMMARY FAIL errors={len(errors)}")
        return 1
    print("SUMMARY PASS snapshot matches manifest and allowlist")
    return 0


if __name__ == "__main__":
    sys.exit(main())
