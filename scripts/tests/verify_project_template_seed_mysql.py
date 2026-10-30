#!/usr/bin/env python3
"""Opt-in MySQL verification in a disposable schema on an explicitly named container.

Copies table STRUCTURE only; never reads or updates application business rows.
Checks replay, identity collisions, tenant/creator scoping and immutable history.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_project_template_seed_pack as pack
import prepare_fresh_project_template_migrations as fresh


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True)
    args = parser.parse_args()
    info = json.loads(subprocess.check_output(["docker", "inspect", args.container], text=True))[0]
    env = dict(v.split("=", 1) for v in info["Config"]["Env"] if "=" in v)
    source = env["MYSQL_DATABASE"]
    if not re.fullmatch(r"[A-Za-z0-9_]+", source):
        raise ValueError("Unexpected source database identifier")
    schema = "npdms_tpl_seed_unit_" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "_" + str(os.getpid())
    command = ["docker", "exec", "-i", args.container, "sh", "-c",
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names']

    def sql(value, *, failure=False, select_schema=True):
        query = (f"USE `{schema}`;\n" if select_schema else "") + value
        result = subprocess.run(command, input=query, text=True, encoding="utf8", capture_output=True)
        if failure:
            if not result.returncode:
                raise AssertionError("Expected migration to reject unsafe input")
            return result.stderr
        if result.returncode:
            raise AssertionError(result.stderr[-1500:])
        return result.stdout.strip()

    created = False
    try:
        sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4;", select_schema=False)
        created = True
        for table in ("proj_project_template", "proj_project_template_revision", "plt_business_view_revision"):
            sql(f"CREATE TABLE `{table}` LIKE `{source}`.`{table}`;")
        sql("""INSERT INTO proj_project_template
            (id,tenant_id,code,name,status,creator,updater) VALUES
            (910001,1,'TPL-DIRECT-ENG-STD','old example','ACTIVE','seed','seed'),
            (910002,1,'TPL-DIRECT-ENG-SUP','user-owned','ACTIVE','user','user'),
            (910003,2,'TPL-DIRECT-ENG-AGENT','other tenant','ACTIVE','seed','seed');
            INSERT INTO proj_project_template_revision
            (id,tenant_id,template_id,revision_no,status,definition_snapshot,creator,updater)
            VALUES (911001,1,910001,1,'PUBLISHED',JSON_OBJECT('immutable','history'),'seed','seed');
            CREATE TABLE historical_project (id BIGINT PRIMARY KEY, snapshot JSON);
            INSERT INTO historical_project VALUES (1,JSON_OBJECT('revisionId',911001,'stage','S4'));
            """)
        before_history = sql("SELECT definition_snapshot FROM proj_project_template_revision WHERE id=911001; SELECT snapshot FROM historical_project;")
        migration = pack.OUTPUT.read_text(encoding="utf8")
        sql(migration)
        roots = sql("SELECT id,status,version FROM proj_project_template WHERE id IN (910001,910002,910003) ORDER BY id;")
        assert roots == "910001\tRETIRED\t1\n910002\tACTIVE\t0\n910003\tACTIVE\t0", roots
        counts = sql("SELECT COUNT(*) FROM proj_project_template; SELECT COUNT(*) FROM proj_project_template_revision; SELECT COUNT(*) FROM plt_business_view_revision;")
        assert counts == "10\n8\n4", counts
        sql(migration)
        assert roots == sql("SELECT id,status,version FROM proj_project_template WHERE id IN (910001,910002,910003) ORDER BY id;")
        assert before_history == sql("SELECT definition_snapshot FROM proj_project_template_revision WHERE id=911001; SELECT snapshot FROM historical_project;")
        sql("UPDATE proj_project_template SET name='collision' WHERE id=993009001593; UPDATE proj_project_template SET status='ACTIVE' WHERE id=910001;")
        error = sql(migration, failure=True)
        assert "Project template seed collision" in error, error[-500:]
        assert "ACTIVE" == sql("SELECT status FROM proj_project_template WHERE id=910001;")
        assert counts == sql("SELECT COUNT(*) FROM proj_project_template; SELECT COUNT(*) FROM proj_project_template_revision; SELECT COUNT(*) FROM plt_business_view_revision;")
        error = sql(fresh.GUARD, failure=True)
        assert "Fresh initialization requires an empty database" in error, error[-500:]
        print("PASS: MySQL replay, tenant/creator boundaries, immutable history, collision rollback, nonempty fresh guard")
    finally:
        if created:
            # Exact schema name created by this process; never an app database.
            assert schema.startswith("npdms_tpl_seed_unit_") and schema != source
            sql(f"DROP DATABASE `{schema}`;", select_schema=False)


if __name__ == "__main__":
    main()
