#!/usr/bin/env python3
"""Build an isolated MySQL benchmark dataset and measure F-PROJ-002 query shapes."""

from __future__ import annotations

import argparse
import json
import math
import statistics
import subprocess
import time
from pathlib import Path


SETUP_SQL = r"""
USE npdms;
DROP TABLE IF EXISTS fproj002_perf_tree_path;
DROP TABLE IF EXISTS fproj002_perf_project;
CREATE TABLE fproj002_perf_project (
  id BIGINT PRIMARY KEY, root_id BIGINT NOT NULL, parent_id BIGINT NULL,
  business_level_code VARCHAR(32) NOT NULL, visible TINYINT NOT NULL,
  KEY idx_parent(parent_id,id), KEY idx_root_level(root_id,business_level_code,id)
);
CREATE TABLE fproj002_perf_tree_path (
  root_id BIGINT NOT NULL, ancestor_id BIGINT NOT NULL, descendant_id BIGINT NOT NULL, distance INT NOT NULL,
  PRIMARY KEY(root_id,ancestor_id,descendant_id),
  KEY idx_desc(root_id,descendant_id,ancestor_id)
);
SET SESSION cte_max_recursion_depth=210000;
INSERT INTO fproj002_perf_project(id,root_id,parent_id,business_level_code,visible)
WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<200000)
SELECT n,
  1 + FLOOR((n-1)/10000)*10000,
  CASE WHEN MOD(n-1,10000)=0 THEN NULL
       WHEN n<=2001 THEN 1
       WHEN MOD(n-1,10000)>=9969 THEN n-1
       ELSE 1 + FLOOR((n-1)/10000)*10000 + FLOOR((MOD(n-1,10000)-1)/10) END,
  CASE MOD(n,3) WHEN 0 THEN 'LEVEL_REGION' WHEN 1 THEN 'LEVEL_OFFICE' ELSE 'LEVEL_NODE' END,
  IF(MOD(n,7)=0,0,1)
FROM seq;
INSERT INTO fproj002_perf_tree_path(root_id,ancestor_id,descendant_id,distance)
SELECT 1,id,id,0 FROM fproj002_perf_project WHERE root_id=1;
INSERT INTO fproj002_perf_tree_path(root_id,ancestor_id,descendant_id,distance)
SELECT 1,1,id,1 FROM fproj002_perf_project WHERE root_id=1 AND id<>1;
INSERT IGNORE INTO fproj002_perf_tree_path(root_id,ancestor_id,descendant_id,distance)
WITH RECURSIVE chain AS (SELECT 9970 id UNION ALL SELECT id+1 FROM chain WHERE id<10000)
SELECT 1,a.id,d.id,d.id-a.id FROM chain a JOIN chain d ON d.id>=a.id;
"""

CLEANUP_SQL = "USE npdms; DROP TABLE IF EXISTS fproj002_perf_tree_path; DROP TABLE IF EXISTS fproj002_perf_project;"

QUERIES = {
    "children": "SELECT COUNT(*) FROM fproj002_perf_project WHERE parent_id=1 AND visible=1",
    "descendants": "SELECT COUNT(*) FROM fproj002_perf_tree_path t JOIN fproj002_perf_project p ON p.id=t.descendant_id WHERE t.root_id=1 AND t.ancestor_id=1 AND t.distance>0 AND p.visible=1",
    "ancestors": "SELECT COUNT(*) FROM fproj002_perf_tree_path t JOIN fproj002_perf_project p ON p.id=t.ancestor_id WHERE t.root_id=1 AND t.descendant_id=10000 AND p.visible=1",
    "locate": "SELECT COUNT(*) FROM fproj002_perf_tree_path t JOIN fproj002_perf_project p ON p.id=t.ancestor_id WHERE t.root_id=1 AND t.descendant_id=9999 AND p.visible=1",
    "business_level": "SELECT COUNT(*) FROM fproj002_perf_tree_path t JOIN fproj002_perf_project p ON p.id=t.descendant_id WHERE t.root_id=1 AND t.ancestor_id=1 AND p.business_level_code='LEVEL_OFFICE' AND p.visible=1",
}


def mysql(container: str, sql: str, capture: bool = True) -> str:
    command = ["docker", "exec", "-i", container, "sh", "-lc",
               'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD"']
    result = subprocess.run(command, input=sql, text=True, capture_output=capture)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"mysql exited with {result.returncode}")
    return result.stdout.strip() if capture else ""


def percentile(values: list[float], ratio: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, math.ceil(len(ordered) * ratio) - 1))]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", required=True)
    parser.add_argument("--runs", type=int, default=20)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    mysql(args.container, SETUP_SQL)
    results = {}
    for name, query in QUERIES.items():
        samples = []
        for _ in range(args.runs):
            started = time.perf_counter()
            mysql(args.container, f"USE npdms; {query};")
            samples.append((time.perf_counter() - started) * 1000)
        results[name] = {
            "p50Ms": round(statistics.median(samples), 2),
            "p95Ms": round(percentile(samples, 0.95), 2),
            "sqlCount": 1,
        }
    payload = {
        "dataset": {"projects": 200000, "singleTreeNodes": 10000, "directChildren": 2000,
                    "depthFixture": 30},
        "runsPerQuery": args.runs,
        "queries": results,
        "pass": all(item["p95Ms"] <= 2000 and item["sqlCount"] == 1 for item in results.values()),
    }
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    mysql(args.container, CLEANUP_SQL)
    return 0 if payload["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
