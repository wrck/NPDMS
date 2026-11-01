"""Prepare the reviewed 178 production baseline and later forward migrations.

Only writes local files; never connects to a database or invokes Flyway.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / ".run/baseline-migrations"
RELEASE = "sql/releases/production-178-v530-r2"
BASELINE = RELEASE + "/migrations/B530__production_178_initialization.sql"


def collect(root):
    files = {Path(BASELINE).name: (root / BASELINE).read_bytes()}
    source_manifest = json.loads((root / RELEASE / "source-manifest.json").read_text(encoding="utf8"))
    if hashlib.sha256(files[Path(BASELINE).name]).hexdigest() != source_manifest["migrationSha256"]:
        raise ValueError("Frozen 178 baseline hash mismatch")
    sources = {Path(BASELINE).name: BASELINE}
    versions = {}
    for path in sorted((root / "sql/migrations").glob("V*.sql")):
        match = re.fullmatch(r"V(\d+(?:[._]\d+)*)__.+\.sql", path.name)
        if not match:
            raise ValueError(f"Invalid migration name: {path.name}")
        version = tuple(int(v) for v in re.split(r"[._]", match[1]))
        while len(version) > 1 and version[-1] == 0:
            version = version[:-1]
        if version in versions:
            raise ValueError(f"Duplicate version: {versions[version]} / {path.name}")
        versions[version] = path.name
        if version <= (530,):
            continue
        files[path.name] = path.read_bytes()
        sources[path.name] = path.relative_to(root).as_posix()
    excluded_tables = set(source_manifest["excludedTables"]) - {"flyway_schema_history"}
    # Fail before generating an entrypoint if later migrations reintroduce an
    # explicitly retired object. DROP/reference text alone is not creation.
    for name, data in files.items():
        sql = data.decode("utf-8-sig")
        for obj in excluded_tables:
            if re.search(r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?"
                         + re.escape(obj) + r"`?(?=\s|\()", sql, re.I):
                raise ValueError(f"Retired object recreated in {name}: {obj}")
    manifest = {
        "lineage": "178 final structure/configuration B530 plus forward migrations; new empty databases only",
        "files": [{"path": sources[name], "sha256": hashlib.sha256(data).hexdigest()}
                  for name, data in files.items()],
        "sourceManifest": RELEASE + "/source-manifest.json",
        "excludedTables": source_manifest["excludedTables"],
        "excludedData": source_manifest["excludedData"],
        "historicalChain": "Unchanged; V1-V530 are covered by the frozen source state and not replayed",
    }
    files["manifest.json"] = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    return files


def materialize(files, output, check=False):
    existing = {p.name for p in output.iterdir()} if output.exists() else set()
    extra = existing - set(files)
    if extra:
        raise ValueError(f"Unexpected output files; inspect without automatic deletion: {sorted(extra)}")
    if check:
        if existing != set(files) or any((output / name).read_bytes() != data for name, data in files.items()):
            raise ValueError("Baseline projection is missing, modified or stale; regenerate it")
    else:
        output.mkdir(parents=True, exist_ok=True)
        for name, data in files.items():
            (output / name).write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    files = collect(ROOT)
    materialize(files, OUTPUT, args.check)
    print(f"178 production lineage: {len(files) - 1} SQL files; source snapshot frozen; historical files unchanged")


if __name__ == "__main__":
    main()
