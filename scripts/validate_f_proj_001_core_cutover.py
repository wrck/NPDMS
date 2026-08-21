#!/usr/bin/env python3
"""Validate the reuse-first F-PROJ-001 core write-model cutover."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


MIGRATION = Path("sql/migrations/V63__f_proj_001_core_project_write_model.sql")
PROJECT_SOURCE_ROOT = Path("pms-module-project/src/main/java")

REQUIRED_CORE_TABLES = (
    "proj_project_template_task_definition",
    "proj_project_task_execution_contract",
    "proj_project_task_completion_evaluation",
    "cut_cutover_checklist",
    "cut_cutover_checklist_item",
    "cut_cutover_checklist_item_result",
)

EXPECTED_CREATE_CONTROLLER = "ProjectMasterController.java"
EXPECTED_CREATE_SERVICE = "ProjectManualCreationServiceImpl.java"

DESTRUCTIVE_OR_BACKFILL_PATTERN = re.compile(
    r"(?im)^\s*(?:DROP\s+(?:TABLE|COLUMN)|RENAME\s+TABLE|TRUNCATE\s+TABLE|"
    r"INSERT\s+INTO|UPDATE\s+|DELETE\s+FROM)"
)
LEGACY_PROJECT_WRITE_PATTERN = re.compile(
    r"(?is)\b(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM|REPLACE\s+INTO)\s+"
    r"`?pms_project[a-z0-9_]*`?\b"
)
DEPRECATED_TYPE_PATTERN = re.compile(
    r"@Deprecated(?:\s*\([^)]*\))?\s*(?:public\s+)?(?:abstract\s+|final\s+)?"
    r"(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)",
    re.DOTALL,
)
TABLE_NAME_TYPE_PATTERN = re.compile(
    r'@TableName\s*\(\s*["\'](?P<table>pms_project[a-z0-9_]*)["\']\s*\)'
    r".*?\b(?:class|record)\s+(?P<type>[A-Za-z_$][A-Za-z0-9_$]*)",
    re.DOTALL,
)
MAPPER_TYPE_PATTERN = re.compile(
    r"\b(?:interface|class)\s+(?P<mapper>[A-Za-z_$][A-Za-z0-9_$]*)\s+"
    r"extends\s+BaseMapperX?\s*<\s*(?P<entity>[A-Za-z_$][A-Za-z0-9_$]*)\s*>",
    re.DOTALL,
)
ORM_WRITE_METHOD_PATTERN = r"(?:insert|update|delete|replace)[A-Za-z0-9_$]*"

# These write consumers predate F-PROJ-001. The cutover validator freezes the
# audited set so a newly introduced consumer cannot silently revive the legacy
# write model while the remaining consumers are retired by later features.
LEGACY_ORM_WRITE_ALLOWLIST: frozenset[tuple[str, str]] = frozenset(
    {
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "batchchange/TeamBatchChangeServiceImpl.java",
            "ProjectTeamMemberMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "phase/ProjectPhaseServiceImpl.java",
            "ProjectPhaseMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "planchange/PlanChangeServiceImpl.java",
            "ProjectPhaseMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "portfolio/ProjectPortfolioServiceImpl.java",
            "ProjectPortfolioMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "portfolio/ProjectPortfolioServiceImpl.java",
            "ProjectPortfolioMemberMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "portfolio/ProjectPortfolioServiceImpl.java",
            "ProjectPortfolioRuleMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "projectgovernance/ProjectGovernanceServiceImpl.java",
            "ProjectGovernanceActionMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "projectgovernance/ProjectGovernanceServiceImpl.java",
            "ProjectMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "projecttask/ProjectTaskServiceImpl.java",
            "ProjectTaskMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "projectteam/ProjectTeamServiceImpl.java",
            "ProjectTeamMemberMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "projecttree/ProjectTreeServiceImpl.java",
            "ProjectMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "risk/ProjectRiskServiceImpl.java",
            "ProjectRiskMapper",
        ),
        (
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/"
            "schedulebackward/ScheduleBackwardServiceImpl.java",
            "ProjectPhaseMapper",
        ),
    }
)


def _java_sources(repository: Path) -> list[Path]:
    root = repository / PROJECT_SOURCE_ROOT
    return sorted(root.rglob("*.java")) if root.is_dir() else []


def _relative(path: Path, repository: Path) -> str:
    return path.relative_to(repository).as_posix()


def _has_table_ddl(sql: str, table: str) -> bool:
    return re.search(
        rf"(?i)\b(?:CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?|ALTER\s+TABLE)\s+`?{re.escape(table)}`?\b",
        sql,
    ) is not None


def _active_create_paths(repository: Path, sources: list[Path]) -> tuple[list[Path], list[Path]]:
    controllers: list[Path] = []
    services: list[Path] = []
    create_method = re.compile(r"\bcreateProject\s*\(")
    for path in sources:
        text = path.read_text(encoding="utf-8")
        if not create_method.search(text) or "@Deprecated" in text:
            continue
        if "@RestController" in text and "@PostMapping" in text:
            controllers.append(path)
        if "@Service" in text:
            services.append(path)
    return controllers, services


def _deprecated_references(repository: Path, sources: list[Path]) -> list[str]:
    deprecated_by_path: dict[Path, set[str]] = {}
    for path in sources:
        matches = set(DEPRECATED_TYPE_PATTERN.findall(path.read_text(encoding="utf-8")))
        if matches:
            deprecated_by_path[path] = matches

    errors: list[str] = []
    for path in sources:
        if path in deprecated_by_path:
            continue
        text = path.read_text(encoding="utf-8")
        for deprecated_path, type_names in deprecated_by_path.items():
            for type_name in sorted(type_names):
                if re.search(rf"\b{re.escape(type_name)}\b", text):
                    errors.append(
                        f"{_relative(path, repository)} references deprecated type {type_name} "
                        f"from {_relative(deprecated_path, repository)}"
                    )
    return errors


def _legacy_orm_write_paths(repository: Path, sources: list[Path]) -> list[str]:
    legacy_entities: set[str] = set()
    source_text: dict[Path, str] = {}
    for path in sources:
        text = path.read_text(encoding="utf-8")
        source_text[path] = text
        for match in TABLE_NAME_TYPE_PATTERN.finditer(text):
            legacy_entities.add(match.group("type"))

    legacy_mappers: set[str] = set()
    for text in source_text.values():
        for match in MAPPER_TYPE_PATTERN.finditer(text):
            if match.group("entity") in legacy_entities:
                legacy_mappers.add(match.group("mapper"))

    errors: list[str] = []
    for path, text in source_text.items():
        relative = _relative(path, repository)
        for mapper in sorted(legacy_mappers):
            variables = re.findall(
                rf"\b{re.escape(mapper)}\s+([A-Za-z_$][A-Za-z0-9_$]*)\b", text
            )
            if (relative, mapper) not in LEGACY_ORM_WRITE_ALLOWLIST and any(
                re.search(rf"\b{re.escape(variable)}\s*\.\s*{ORM_WRITE_METHOD_PATTERN}\s*\(", text)
                for variable in variables
            ):
                errors.append(
                    f"{relative} contains an ORM write to legacy pms_project table via {mapper}"
                )
    return errors


def validate_repository(repository: Path) -> list[str]:
    repository = repository.resolve()
    errors: list[str] = []
    migration_path = repository / MIGRATION
    migration_sql = ""
    if not migration_path.is_file():
        errors.append(f"missing core migration: {MIGRATION.as_posix()}")
    else:
        migration_sql = migration_path.read_text(encoding="utf-8")
        for table in REQUIRED_CORE_TABLES:
            if not _has_table_ddl(migration_sql, table):
                errors.append(f"core migration does not establish formal table {table}")
        destructive = DESTRUCTIVE_OR_BACKFILL_PATTERN.search(migration_sql)
        if destructive:
            statement = destructive.group(0).strip().splitlines()[0]
            errors.append(f"core migration contains destructive DDL or data backfill: {statement}")
        if LEGACY_PROJECT_WRITE_PATTERN.search(migration_sql):
            errors.append("core migration contains a legacy pms_project write")

    sources = _java_sources(repository)
    for path in sources:
        text = path.read_text(encoding="utf-8")
        if LEGACY_PROJECT_WRITE_PATTERN.search(text):
            errors.append(f"{_relative(path, repository)} contains a legacy pms_project write")
    errors.extend(_legacy_orm_write_paths(repository, sources))

    controllers, services = _active_create_paths(repository, sources)
    if len(controllers) != 1 or controllers[0].name != EXPECTED_CREATE_CONTROLLER:
        paths = ", ".join(_relative(path, repository) for path in controllers) or "none"
        errors.append(
            f"expected exactly one active create controller ({EXPECTED_CREATE_CONTROLLER}); found {paths}"
        )
    if len(services) != 1 or services[0].name != EXPECTED_CREATE_SERVICE:
        paths = ", ".join(_relative(path, repository) for path in services) or "none"
        errors.append(
            f"expected exactly one active create service ({EXPECTED_CREATE_SERVICE}); found {paths}"
        )

    errors.extend(_deprecated_references(repository, sources))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = validate_repository(args.repository)
    if errors:
        for error in errors:
            print(f"ERROR {error}")
        print(f"SUMMARY FAIL {len(errors)} core-cutover contract error(s)")
        return 1
    print("SUMMARY PASS F-PROJ-001 core cutover contract is satisfied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
