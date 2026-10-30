"""Materialize the reviewed, 179-only Flyway recovery manifest without overwrites."""

import argparse
import hashlib
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "sql/releases/test-v498/recovery-179/manifest.json"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path, help="new, nonexistent output directory")
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    revision = manifest["source_commit"]
    contents = []
    names = set()
    for entry in manifest["files"]:
        name = entry["filename"]
        if Path(name).name != name or name in names:
            raise SystemExit("Invalid or duplicate migration filename: " + name)
        names.add(name)
        if entry["origin"] == "git":
            data = subprocess.run(
                ["git", "show", revision + ":" + entry["source"]],
                cwd=ROOT, check=True, capture_output=True,
            ).stdout
        elif entry["origin"] == "workspace":
            path = (ROOT / entry["source"]).resolve()
            if not path.is_relative_to(MANIFEST.parent.resolve()):
                raise SystemExit("Recovery file is outside the reviewed manifest directory")
            # Git checkouts may use CRLF; Flyway ignores line endings when hashing SQL.
            data = path.read_bytes().replace(b"\r\n", b"\n")
        else:
            raise SystemExit("Unknown migration origin")
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise SystemExit("Migration checksum differs from manifest: " + name)
        contents.append((name, data))

    if len(contents) != 25 or any(name.startswith("V506__") for name in names):
        raise SystemExit("Unexpected recovery migration set")
    if not any(name.startswith("V506_1__") for name in names):
        raise SystemExit("Missing V506.1 reconciliation migration")
    for version in range(1, 7):
        if not any(name.startswith(f"V516_{version}__") for name in names):
            raise SystemExit(f"Missing V516.{version} base-sync migration")
    try:
        args.output.mkdir(parents=True, exist_ok=False)
    except FileExistsError:
        raise SystemExit("Output already exists; no migration files were overwritten") from None
    for name, data in contents:
        with (args.output / name).open("xb") as out:
            out.write(data)
    print(f"Prepared {len(contents)} verified migrations in {args.output}")


if __name__ == "__main__":
    main()
