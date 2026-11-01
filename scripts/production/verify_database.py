"""Read-only initialization audit; exit 1 on integrity defects, 2 on setup pending.

Requires PyMySQL. Credentials are supplied only in NPDMS_VERIFY_DB_PASSWORD.
This checks database facts, not browser acceptance or process publication semantics.
"""
import argparse
from collections import defaultdict
import datetime
import decimal
import hashlib
import json
import os
from pathlib import Path
import re

from build_initialization import normalized_ddl, row_digest

ROOT = Path(__file__).resolve().parents[2]


def identifier(value):
    if not re.fullmatch(r"[A-Za-z0-9_]+", value):
        raise ValueError("Unexpected metadata identifier")
    return "`" + value + "`"


def mapped_columns(root):
    """Direct persisted DO fields; excludes deprecated classes and response fields.

    Inherited fields and hand-written SQL require separate review; this is not a
    Java parser or a substitute for application integration tests.
    """
    modules = list(root.glob("pms-module-*")) + [root / ("yudao-module-" + n)
                                               for n in ("system", "infra", "bpm")]
    result = []
    annotation = r"@(?:[\w]+\.)*TableField"
    for module in modules:
        for path in module.glob("src/main/java/**/*DO.java"):
            source = path.read_text(encoding="utf8")
            table = re.search(r'@TableName\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', source)
            if not table or "@Deprecated" in source:
                continue
            columns = []
            previous = source.find("public class")
            for field in re.finditer(r"private\s+(?!static\b)([\w<>?, .\[\]]+)\s+(\w+)\s*(?:=[^;]*)?;", source):
                prefix = source[previous:field.start()]
                previous = field.end()
                if re.search(annotation + r"\([^)]*exist\s*=\s*false", prefix, re.S):
                    continue
                override = re.search(annotation + r'\(\s*(?:value\s*=\s*)?"([^"]+)"', prefix)
                columns.append(override[1] if override else re.sub(r"(?<!^)([A-Z])", r"_\1", field[2]).lower())
            result.append((table[1], columns, path.relative_to(root).as_posix()))
    return result


def encode(value):
    if isinstance(value, bytes):
        return {"$bytes": value.hex()}
    if isinstance(value, decimal.Decimal):
        return {"$decimal": str(value)}
    if isinstance(value, (datetime.datetime, datetime.date, datetime.time)):
        return {"$date": str(value)}
    raise TypeError(type(value).__name__)


def administrator_initialized(users):
    """Bootstrap changes only the known administrator, using a BCrypt hash."""
    return (len(users) == 1 and users[0].get("id") == 1
            and users[0].get("tenant_id") == 1 and users[0].get("username") == "admin"
            and users[0].get("status") == 0 and users[0].get("deleted") in (0, b"\x00")
            and re.fullmatch(r"\$2[aby]\$(?:1[0-9]|2[0-9]|3[01])\$[./A-Za-z0-9]{53}",
                             users[0].get("password") or "") is not None)


def audit(connection, root=ROOT, release=None, phase="pristine"):
    errors, pending = [], []
    def rows(sql, args=None):
        with connection.cursor() as cursor:
            cursor.execute(sql, args)
            return cursor.fetchall()
    def scalar(sql, args=None):
        return next(iter(rows(sql, args)[0].values()))
    rows("SET SESSION TRANSACTION READ ONLY")
    rows("START TRANSACTION WITH CONSISTENT SNAPSHOT")
    tables = {r["TABLE_NAME"] for r in rows("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")}
    columns = defaultdict(set)
    for row in rows("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"):
        columns[row["TABLE_NAME"]].add(row["COLUMN_NAME"])
    mapped = mapped_columns(root)
    for table, expected, path in mapped:
        if table not in tables:
            errors.append({"check": "mapped_table_missing", "table": table, "source": path})
        elif set(expected) - columns[table]:
            errors.append({"check": "mapped_columns_missing", "table": table,
                           "columns": sorted(set(expected) - columns[table]), "source": path})
    sync_targets = json.loads((root / "pms-module-integration/src/main/resources/sync/generic-targets.json").read_text(encoding="utf8"))
    for target in sync_targets:
        name = target["table"]
        expected = set(target.get("columns", [])) | set(target.get("updateColumns", []))
        if name not in tables or expected - columns[name]:
            errors.append({"check": "sync_target_schema_mismatch", "table": name,
                           "columns": sorted(expected - columns[name])})
    release = release or root / "sql/releases/production-178-v530-r2"
    source_manifest = json.loads((release / "source-manifest.json").read_text(encoding="utf8"))
    expected_tables = set(source_manifest["tables"])
    for name in expected_tables - tables:
        errors.append({"check": "source_table_missing", "table": name})
    for name in tables - expected_tables - {"flyway_schema_history"}:
        errors.append({"check": "unexpected_table", "table": name})
    for name in expected_tables & tables:
        ddl = rows("SHOW CREATE TABLE " + identifier(name))[0]["Create Table"]
        if hashlib.sha256(normalized_ddl(ddl).encode()).hexdigest() != source_manifest["tables"][name]["ddlSha256"]:
            errors.append({"check": "source_structure_differs", "table": name})
    triggers = {r["TRIGGER_NAME"] for r in rows("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")}
    expected_triggers = {t["TRIGGER_NAME"]: t for t in source_manifest["triggers"]}
    if set(expected_triggers) != triggers:
        errors.append({"check": "source_trigger_set_differs"})
    for trigger in rows("SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_STATEMENT,ACTION_TIMING,ACTION_ORDER,SQL_MODE FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()"):
        expected = expected_triggers.get(trigger["TRIGGER_NAME"])
        if expected and any(str(value).replace("\r\n", "\n") != str(expected[key]).replace("\r\n", "\n") for key, value in trigger.items()):
            errors.append({"check": "source_trigger_differs", "trigger": trigger["TRIGGER_NAME"]})
    engine_tables = {n for n in expected_tables if n.startswith(("ACT_", "FLW_"))}
    for name in engine_tables - tables:
        errors.append({"check": "engine_table_missing", "table": name})
    for table, key in (("ACT_GE_PROPERTY", "schema.version"), ("ACT_GE_PROPERTY", "common.schema.version"),
                       ("ACT_GE_PROPERTY", "eventregistry.schema.version"), ("ACT_ID_PROPERTY", "schema.version")):
        if table in tables and scalar("SELECT COUNT(*) FROM " + identifier(table) + " WHERE NAME_=%s AND VALUE_='8.0.0.0'", (key,)) != 1:
            errors.append({"check": "engine_version", "table": table, "key": key})
    if "flyway_schema_history" not in tables:
        errors.append({"check": "migration_history_missing"})
        history = []
    else:
        history = rows("SELECT version,type,script,success FROM flyway_schema_history ORDER BY installed_rank")
        if not history or any(not row["success"] for row in history):
            errors.append({"check": "migration_history_failed_or_empty"})
    # Referential integrity is checked from the actual FK definitions, including
    # tenant-qualified and composite keys. NULL keys use MySQL MATCH SIMPLE.
    foreign_keys = defaultdict(list)
    for row in rows("SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"):
        foreign_keys[(row["TABLE_NAME"], row["CONSTRAINT_NAME"], row["REFERENCED_TABLE_NAME"])].append(row)
    for (table, constraint, parent), fields in foreign_keys.items():
        nonnull = " AND ".join("c." + identifier(r["COLUMN_NAME"]) + " IS NOT NULL" for r in fields)
        joined = " AND ".join("p." + identifier(r["REFERENCED_COLUMN_NAME"]) + "=c." + identifier(r["COLUMN_NAME"]) for r in fields)
        count = scalar("SELECT COUNT(*) FROM " + identifier(table) + " c WHERE " + nonnull + " AND NOT EXISTS(SELECT 1 FROM " + identifier(parent) + " p WHERE " + joined + ")")
        if count:
            errors.append({"check": "foreign_key_orphans", "table": table, "constraint": constraint, "count": count})
    # Check logical platform links that have no physical FK. Keep deleted parent
    # rows as valid historical references; do not require them to be enabled.
    logical = {
        "department_parent": "SELECT COUNT(*) FROM system_dept c LEFT JOIN system_dept p ON p.id=c.parent_id AND p.tenant_id=c.tenant_id WHERE c.parent_id<>0 AND p.id IS NULL",
        "organization_person_bindings": "SELECT COUNT(*) FROM system_dept WHERE leader_user_id IS NOT NULL OR COALESCE(phone,'')<>'' OR COALESCE(email,'')<>''",
        "menu_parent": "SELECT COUNT(*) FROM system_menu c LEFT JOIN system_menu p ON p.id=c.parent_id WHERE c.parent_id<>0 AND p.id IS NULL",
        "role_menu": "SELECT COUNT(*) FROM system_role_menu c LEFT JOIN system_role r ON r.id=c.role_id LEFT JOIN system_menu m ON m.id=c.menu_id WHERE r.id IS NULL OR m.id IS NULL",
        "user_role": "SELECT COUNT(*) FROM system_user_role c LEFT JOIN system_users u ON u.id=c.user_id AND u.tenant_id=c.tenant_id LEFT JOIN system_role r ON r.id=c.role_id AND r.tenant_id=c.tenant_id WHERE u.id IS NULL OR r.id IS NULL",
        "dictionary_type": "SELECT COUNT(*) FROM system_dict_data c LEFT JOIN system_dict_type t ON t.type=c.dict_type WHERE t.id IS NULL",
        "form_publication": "SELECT COUNT(*) FROM plt_dynamic_form_template t LEFT JOIN plt_dynamic_form_template_revision r ON r.id=t.current_published_revision_id AND r.template_id=t.id AND r.tenant_id=t.tenant_id WHERE t.current_published_revision_id IS NOT NULL AND r.id IS NULL",
        "questionnaire_publication": "SELECT COUNT(*) FROM acc_satisfaction_questionnaire_template t LEFT JOIN acc_satisfaction_questionnaire_template_revision r ON r.id=t.current_revision_id AND r.template_id=t.id AND r.tenant_id=t.tenant_id WHERE t.current_revision_id IS NOT NULL AND r.id IS NULL",
        "document_template_publication": "SELECT COUNT(*) FROM imp_eng_doc_template t LEFT JOIN imp_eng_doc_template_version r ON r.id=t.current_version_id AND r.template_id=t.id AND r.tenant_id=t.tenant_id WHERE t.current_version_id IS NOT NULL AND r.id IS NULL",
        "document_template_parent": "SELECT COUNT(*) FROM imp_eng_doc_template t LEFT JOIN imp_eng_doc_template p ON p.id=t.parent_template_id AND p.tenant_id=t.tenant_id WHERE t.parent_template_id IS NOT NULL AND p.id IS NULL",
        "business_view_form": "SELECT COUNT(*) FROM plt_business_view_revision v LEFT JOIN plt_dynamic_form_template_revision r ON r.id=v.dynamic_form_revision_id AND r.tenant_id=v.tenant_id WHERE v.dynamic_form_revision_id IS NOT NULL AND r.id IS NULL",
        "process_metadata": "SELECT COUNT(*) FROM bpm_process_definition_info i LEFT JOIN ACT_RE_PROCDEF p ON p.ID_=i.process_definition_id WHERE p.ID_ IS NULL",
        "process_deployment": "SELECT COUNT(*) FROM ACT_RE_PROCDEF p LEFT JOIN ACT_RE_DEPLOYMENT d ON d.ID_=p.DEPLOYMENT_ID_ WHERE d.ID_ IS NULL",
        "model_resource": "SELECT COUNT(*) FROM ACT_RE_MODEL m LEFT JOIN ACT_GE_BYTEARRAY b ON b.ID_=m.EDITOR_SOURCE_VALUE_ID_ WHERE m.EDITOR_SOURCE_VALUE_ID_ IS NOT NULL AND b.ID_ IS NULL",
        "model_extra_resource": "SELECT COUNT(*) FROM ACT_RE_MODEL m LEFT JOIN ACT_GE_BYTEARRAY b ON b.ID_=m.EDITOR_SOURCE_EXTRA_VALUE_ID_ WHERE m.EDITOR_SOURCE_EXTRA_VALUE_ID_ IS NOT NULL AND b.ID_ IS NULL",
        "process_resource": "SELECT COUNT(*) FROM ACT_RE_PROCDEF p WHERE NOT EXISTS(SELECT 1 FROM ACT_GE_BYTEARRAY b WHERE b.DEPLOYMENT_ID_=p.DEPLOYMENT_ID_ AND b.NAME_=p.RESOURCE_NAME_)",
        "source_plaintext_password_config": "SELECT COUNT(*) FROM infra_config WHERE config_key='system.user.init-password'",
        "unexpected_oauth_credentials": "SELECT COUNT(*) FROM system_oauth2_client WHERE client_id<>'default' OR secret<>'' OR JSON_LENGTH(authorized_grant_types)<>0",
        "extra_initial_users": "SELECT COUNT(*) FROM system_users WHERE id<>1 OR tenant_id<>1",
        "extra_initial_tenants": "SELECT COUNT(*) FROM system_tenant WHERE id<>1",
    }
    for name, sql in logical.items():
        count = scalar(sql)
        if count:
            errors.append({"check": name, "count": count})
    # Explicit runtime references only; historical sourceEvidence IDs are not
    # target bindings and must not be rewritten to make a check pass.
    view_ids = {str(r["id"]) for r in rows("SELECT id FROM plt_business_view_revision")}
    form_ids = {str(r["id"]) for r in rows("SELECT id FROM plt_dynamic_form_template_revision")}
    process_ids = {r["ID_"] for r in rows("SELECT ID_ FROM ACT_RE_PROCDEF")}
    def check_template_refs(value, revision):
        if isinstance(value, dict):
            for key, item in value.items():
                if key == "sourceEvidence":
                    continue
                valid = {"businessViewRevisionId": view_ids, "dynamicFormTemplateRevisionId": form_ids,
                         "processDefinitionId": process_ids, "approvalProcessDefinitionId": process_ids}.get(key)
                if valid is not None and item is not None and str(item) not in valid:
                    errors.append({"check": "template_reference_missing", "revision": revision, "field": key})
                check_template_refs(item, revision)
        elif isinstance(value, list):
            for item in value:
                check_template_refs(item, revision)
    for row in rows("SELECT id,execution_snapshot FROM proj_project_template_revision WHERE status='PUBLISHED' AND deleted=0"):
        if not row["execution_snapshot"]:
            errors.append({"check": "published_template_snapshot_missing", "revision": row["id"]})
        else:
            check_template_refs(json.loads(row["execution_snapshot"]), row["id"])
    allowed = set(source_manifest["seedCounts"])
    row_counts = {}
    for table in sorted(tables - {"flyway_schema_history"}):
        count = scalar("SELECT COUNT(*) FROM " + identifier(table))
        if count:
            row_counts[table] = count
            if table not in allowed:
                errors.append({"check": "unexpected_initial_data", "table": table, "count": count})
    for table, count in source_manifest["seedCounts"].items():
        if row_counts.get(table, 0) != count:
            errors.append({"check": "seed_count_differs", "table": table})
    for table, expected in source_manifest["seedDigests"].items():
        values = rows("SELECT " + ",".join(map(identifier, expected["columns"])) + " FROM " + identifier(table))
        if phase == "bootstrapped" and table == "system_users":
            # Normalize only the three fields changed by bootstrap; identity and
            # all other selected administrator fields must still match the seed.
            for value in values:
                value.update(password="!BOOTSTRAP_REQUIRED!", status=1, updater="production-bootstrap")
        encoded = json.loads(json.dumps(values, default=encode))
        if row_digest(encoded) != expected["sha256"]:
            errors.append({"check": "seed_values_differ", "table": table})
    if not administrator_initialized(rows("SELECT id,username,tenant_id,status,deleted,password FROM system_users")):
        pending.append("administrator_bootstrap")
        if phase == "bootstrapped":
            errors.append({"check": "administrator_not_initialized"})
    if scalar("SELECT COUNT(*) FROM infra_file_config WHERE master=1 AND deleted=0") != 1:
        pending.append("file_storage_configuration")
    if scalar("SELECT COUNT(*) FROM proj_project_template WHERE status='ACTIVE' AND deleted=0") != 5:
        pending.append("project_template_native_validation_and_publication")
    if "ACT_RE_PROCDEF" not in tables or scalar("SELECT COUNT(*) FROM ACT_RE_PROCDEF") == 0:
        pending.append("bpm_native_deployment_and_target_bindings")
    if scalar("SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE status_code='PUBLISHED' AND deleted=0") == 0:
        pending.append("production_inspection_rules_synthetic_rule_excluded")
    pending.extend(["production_organization_and_authorized_people", "external_connections_and_channels", "real_browser_business_acceptance"])
    connection.rollback()
    return {"integrityPassed": not errors, "productionReady": False, "errors": errors,
            "pending": pending, "mappedEntitiesChecked": len(mapped), "tables": len(tables) - ("flyway_schema_history" in tables),
            "syncTargetsChecked": len(sync_targets),
            "engineTables": len(engine_tables), "triggers": len(triggers), "foreignKeysChecked": len(foreign_keys),
            "nonemptyTableCounts": row_counts, "migrationHistory": history,
            "scope": "Fresh initialization only; direct DO fields, actual FK/logical references, seed isolation and setup prerequisites. No service or browser acceptance."}


if __name__ == "__main__":
    import pymysql
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--schema", required=True)
    parser.add_argument("--user", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--release", type=Path, default=ROOT / "sql/releases/production-178-v530-r2")
    parser.add_argument("--phase", choices=("pristine", "bootstrapped"), default="pristine")
    parser.add_argument("--ssl-ca", type=Path)
    args = parser.parse_args()
    if args.host not in ("127.0.0.1", "localhost", "::1") and not args.ssl_ca:
        parser.error("Remote verification requires --ssl-ca for certificate and hostname verification")
    tls = {"ssl_ca": str(args.ssl_ca), "ssl_verify_cert": True, "ssl_verify_identity": True} if args.ssl_ca else {}
    with pymysql.connect(host=args.host, port=args.port, database=args.schema, user=args.user,
                         password=os.environ["NPDMS_VERIFY_DB_PASSWORD"], charset="utf8mb4",
                         cursorclass=pymysql.cursors.DictCursor, **tls) as conn:
        result = audit(conn, release=args.release, phase=args.phase)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({k: result[k] for k in ("integrityPassed", "productionReady", "errors", "pending")}, ensure_ascii=True))
    raise SystemExit(1 if result["errors"] else 2 if result["pending"] else 0)
