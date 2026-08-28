#!/usr/bin/env python3
"""Execute the core migration DDL in an isolated MySQL container and record evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
DEFAULT_OUTPUT = Path("specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json")


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, check=check, text=True, encoding="utf-8", stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def parse_metrics(raw: str) -> dict[str, object]:
    lines = [line.strip().split("\t") for line in raw.splitlines() if line.strip()]
    if len(lines) < 3:
        raise ValueError(f"unexpected MySQL verification output: {raw!r}")
    constraint_counts = {row[0]: int(row[1]) for row in lines[2:]}
    return {
        "mysqlVersion": lines[0][0],
        "tableCount": int(lines[1][0]),
        "columnCount": int(lines[1][1]),
        "constraintCountByType": constraint_counts,
        "constraintCount": sum(constraint_counts.values()),
    }


def expected_table_count(ddl: str) -> int:
    return len(re.findall(r"(?im)^\s*CREATE\s+TABLE\s+\w+\s*\(", ddl))


def validate(ddl: Path, image: str, timeout_seconds: int) -> dict[str, object]:
    ddl = ddl.resolve()
    ddl_bytes = ddl.read_bytes()
    ddl_sha = hashlib.sha256(ddl_bytes).hexdigest().upper()
    expected_tables = expected_table_count(ddl_bytes.decode("utf-8"))
    container = f"codex-npdms-p3e09-{os.getpid()}"
    schema = "npdms_p3e09_check"
    image_id = run("docker", "image", "inspect", image, "--format", "{{.Id}}").stdout.strip()
    mount = f"type=bind,source={ddl},target=/docker-entrypoint-initdb.d/001.sql,readonly"
    try:
        run(
            "docker", "run", "-d", "--name", container,
            "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes",
            "-e", f"MYSQL_DATABASE={schema}",
            "--mount", mount,
            image,
        )
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            state = run("docker", "inspect", container, "--format", "{{.State.Status}}", check=False).stdout.strip()
            if state == "exited":
                logs = run("docker", "logs", container, check=False)
                raise RuntimeError(f"MySQL DDL initialization failed:\n{logs.stdout}\n{logs.stderr}")
            ping = run("docker", "exec", container, "mysqladmin", "ping", "--silent", check=False)
            if ping.returncode == 0:
                count = run(
                    "docker", "exec", container, "mysql", "-N", "-e",
                    f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{schema}';",
                    check=False,
                )
                if count.returncode == 0 and count.stdout.strip() == str(expected_tables):
                    break
            time.sleep(1)
        else:
            raise TimeoutError(f"MySQL container did not become ready within {timeout_seconds}s")

        query = (
            "SELECT VERSION();"
            "SELECT COUNT(*),"
            "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='npdms_p3e09_check') "
            "FROM information_schema.tables WHERE table_schema='npdms_p3e09_check';"
            "SELECT constraint_type,COUNT(*) FROM information_schema.table_constraints "
            "WHERE constraint_schema='npdms_p3e09_check' GROUP BY constraint_type ORDER BY constraint_type;"
        )
        result = run("docker", "exec", container, "mysql", "-N", "-e", query)
        metrics = parse_metrics(result.stdout)
        try:
            ddl_path = ddl.relative_to(Path.cwd().resolve()).as_posix()
        except ValueError:
            ddl_path = ddl.as_posix()
        return {
            "schemaVersion": 1,
            "status": "PASS",
            "purpose": "P3_E09_ISOLATED_MYSQL_DDL_EXECUTION",
            "capturedAt": datetime.now(timezone.utc).isoformat(),
            "ddlPath": ddl_path,
            "ddlSha256": ddl_sha,
            "containerImage": image,
            "containerImageId": image_id,
            "temporarySchema": schema,
            "expectedTableCount": expected_tables,
            **metrics,
        }
    finally:
        run("docker", "rm", "-f", container, check=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ddl", type=Path, default=DEFAULT_DDL)
    parser.add_argument("--image", default="mysql:8.4")
    parser.add_argument("--timeout-seconds", type=int, default=60)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    evidence = validate(args.ddl, args.image, args.timeout_seconds)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(
        f"[PASS] MySQL {evidence['mysqlVersion']} isolated DDL execution; "
        f"tables={evidence['tableCount']} columns={evidence['columnCount']} constraints={evidence['constraintCount']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
