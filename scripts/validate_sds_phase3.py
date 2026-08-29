#!/usr/bin/env python3
"""Validate Phase 3 runtime, release and verification assurance artifacts."""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
from pathlib import Path


DESIGN_FILES = (
    "14-security-design.md",
    "17-audit-and-observability.md",
    "18-deployment-design.md",
    "19-performance-design.md",
    "20-test-design.md",
)
EXPECTED_REQUIREMENT_COUNT = 100
REVISION_007_DELTA_SLICE_TOKENS = {
    "PM-08@V2": ("唯一匹配自动生效", "无匹配或多匹配转人工", "冻结规则版本"),
    "PM-11@V2": ("甘特与依赖CRUD共用同一任务事实", "数据库约束记录"),
    "ACC-01@V2": ("送达不等于客户确认", "失败回退V1链接/扫码"),
    "ACC-02@V2": ("自动触达不重复V1问卷事实", "问卷实例与结果对象前后对照"),
    "CUT-01@V2": ("KPI按授权范围聚合且只读", "任务状态前后对照"),
    "CUT-03@V2": ("导出与流程跳转按授权及已发布配置执行", "清单事实前后对照"),
    "CUT-05@V2": ("A/B级专项提前时间按边界判断", "提醒失败不改变审批"),
    "INT-02@V2": ("ITR故障入向幂等", "出向失败不回滚本地归档", "技术公告仍归INT-04"),
    "INT-05@V2": ("OA领料/外采流程引用", "OA/钉钉完成不改写平台审批与业务状态"),
    "INT-12@V2": ("复用统一设备连接、凭证、任务和采集契约", "不得形成第二套凭证或采集引擎"),
    "NFR-02@V2": ("当前命令超时终止并失败", "冻结的已发布规则决定并留痕"),
}
P3E09_STATE_ASSETS = (
    "docs/decisions/0022-core-migration-schema-and-key-policy.md",
    "specs/001-project-delivery-platform/appendices/project-order-migration-mapping.md",
    "specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md",
    "specs/001-project-delivery-platform/appendices/core-field-migration-completeness.md",
)
REQUIREMENT_HEADING = re.compile(r"^###\s+([A-Z]+-\d+)\s*$", re.M)
VERSION_SLICE_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+@V[12])\s*\|", re.M)
PRD_REQUIREMENT_ROW = re.compile(r"^\|\s*需求编号\s*\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*$", re.M)
PRD_TARGET_VERSION = re.compile(r"^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", re.M)
PHASE3_TEST = re.compile(r"^- Phase 3测试类别：(.+?)\s*$", re.M)
PHASE3_PRD_ACCEPTANCE = re.compile(r"^- Phase 3 PRD验收基线：(.+?)\s*$", re.M)
PHASE3_AUTHORIZATION_ASSERTION = re.compile(r"^- Phase 3授权拒绝断言：(.+?)\s*$", re.M)
PHASE3_GUARD_ASSERTION = re.compile(r"^- Phase 3业务守卫断言：(.+?)\s*$", re.M)
PHASE3_SIDE_EFFECT_ASSERTION = re.compile(r"^- Phase 3副作用断言：(.+?)\s*$", re.M)
PHASE3_EVIDENCE = re.compile(r"^- Phase 3证据类型：(.+?)\s*$", re.M)
CONTRACT_DATA_OBJECTS = re.compile(r"^- 数据对象：(.+?)\s*$", re.M)
CONTRACT_DATA_TABLES = re.compile(r"^- 数据表：(.+?)\s*$", re.M)
SIDE_EFFECT_OBJECTS = re.compile(r"数据对象“([^”]+)”及数据表“([^”]+)”")
SIDE_EFFECT_EVENT = re.compile(r"事件边界为“([^”]+)”")
SIDE_EFFECT_FILE = re.compile(r"文件边界为“([^”]+)”")
SIDE_EFFECT_INTEGRATION = re.compile(r"外部集成为“([^”]+)”")
GATE_REVIEW_STATE = re.compile(r"^>\s*审查状态：`([^`]+)`", re.M)
GATE_CONCLUSION = re.compile(r"^>\s*结论：`([^`]+)`", re.M)
ACCEPTANCE_HEADING = re.compile(r"^\*\*(?:业务)?验收标准：\*\*\s*$", re.M)
POST_ACCEPTANCE_HEADING = re.compile(
    r"^\*\*(?:涉及数据字段|权限与数据范围|异常、降级及留痕要求|依赖关系)：\*\*",
    re.M,
)
STALE_V18_DESIGN_TEXT = (
    "SDS Phase 1/2 REVALIDATION_REQUIRED",
    "V1.8差量复审尚未完成",
    "docs\\baseline\\prd-v1.7.md",
    "未形成可复核证据前本分册不能基线化",
)
CROSS_CONTEXT_TABLE_REFERENCES = {
    "PM-08": {"ast_area_department_mapping"},
    "PM-02": {"proj_project_tree_change"},
    "PM-04": {"proj_project_tree_change"},
    "PRE-03": {"ast_asset_sync_item"},
    "EXE-06": {"proj_project_stage_snapshot"},
    "CUT-03": {"cut_cutover_configuration_revision"},
    "CUT-05": {"plt_todo"},
    "CUT-08": {"ast_asset_sync_item"},
    "INT-01": {"ast_asset_sync_batch", "ast_asset_sync_item"},
    "INT-02": {"cut_task", "cut_cutover_closure"},
    "INT-03": {"ast_asset_sync_batch", "ast_asset_sync_item"},
    "INT-05": {"plt_sync_batch", "plt_external_key_mapping"},
    "INT-07": {"plt_integration_reconciliation"},
    "INT-09": {"ast_asset_sync_item"},
    "INT-10": {"ast_asset_sync_item"},
    "INT-12": {"plt_collection_result_consumption"},
    "NFR-02": {"plt_collection_result_consumption"},
    "NFR-03": {"ast_asset_sync_item"},
}


def require_tokens(errors: list[str], label: str, text: str, tokens: tuple[str, ...]) -> None:
    for token in tokens:
        if token not in text:
            errors.append(f"{label} missing required token: {token}")


def parse_contract_blocks(text: str) -> dict[str, str]:
    matches = list(REQUIREMENT_HEADING.finditer(text))
    return {
        match.group(1): text[match.start(): matches[index + 1].start() if index + 1 < len(matches) else len(text)]
        for index, match in enumerate(matches)
    }


def split_contract_values(value: str) -> list[str]:
    return [item.strip() for item in value.split("、") if item.strip()]


def load_object_table_contract(root: Path, errors: list[str]) -> dict[str, dict[str, object]]:
    path = root / "docs" / "traceability" / "domain-object-table-map.json"
    if not path.exists():
        errors.append("missing domain object-table contract for Phase 3 reverse validation")
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        errors.append(f"invalid domain object-table contract: {exc}")
        return {}
    objects = payload.get("objects")
    if not isinstance(objects, dict):
        errors.append("domain object-table contract must contain an objects mapping")
        return {}
    if not objects:
        errors.append("domain object-table contract objects mapping must not be empty")
    return objects


def prd_formal_requirement_ids(text: str) -> list[str]:
    """Extract the V1/V2 requirement IDs from the authoritative PRD blocks."""
    matches = list(PRD_REQUIREMENT_ROW.finditer(text))
    identifiers: list[str] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        version = PRD_TARGET_VERSION.search(text[match.end():end])
        if version and version.group(1) in {"V1", "V2"}:
            identifiers.append(match.group(1))
    return identifiers


def prd_version_slice_keys(root: Path) -> list[str]:
    prd_text = (root / "docs" / "baseline" / "prd-v1.8.md").read_text(encoding="utf-8-sig")
    matches = list(PRD_REQUIREMENT_ROW.finditer(prd_text))
    result: list[str] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(prd_text)
        version = PRD_TARGET_VERSION.search(prd_text[match.end():end])
        if version and version.group(1) in {"V1", "V2"}:
            result.append(f"{match.group(1)}@{version.group(1)}")
    result.extend(
        match.group(1)
        for match in re.finditer(
            r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+@V[12])\s*\|\s*[A-Z]+(?:-[A-Z0-9]+)?-\d+\s*\|\s*V[12]\s*\|",
            prd_text,
            re.M,
        )
    )
    return result


def normalize_acceptance(text: str) -> str:
    parts: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("- "):
            line = line[2:].strip()
        line = re.sub(r"\*\*", "", line)
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            parts.append(line)
    return "；".join(parts)


def prd_formal_requirement_acceptance(text: str) -> dict[str, str]:
    matches = list(PRD_REQUIREMENT_ROW.finditer(text))
    result: dict[str, str] = {}
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        block = text[match.end():end]
        version = PRD_TARGET_VERSION.search(block)
        if not version or version.group(1) not in {"V1", "V2"}:
            continue
        acceptance_heading = ACCEPTANCE_HEADING.search(block)
        if not acceptance_heading:
            continue
        tail = block[acceptance_heading.end():]
        post_heading = POST_ACCEPTANCE_HEADING.search(tail)
        result[match.group(1)] = normalize_acceptance(
            tail[:post_heading.start() if post_heading else len(tail)]
        )
    return result


def validate_v18_in_review(root: Path, gate: str) -> list[str]:
    """Validate the V1.8 Phase 3 candidate without approving it."""
    errors: list[str] = []
    state = GATE_REVIEW_STATE.search(gate)
    conclusion = GATE_CONCLUSION.search(gate)
    if not state or state.group(1) != "IN_REVIEW":
        errors.append("Phase 3 V1.8 gate review state must be IN_REVIEW")
    if not conclusion or conclusion.group(1) != "NOT_READY_FOR_SDS_BASELINE_REVISION_007":
        errors.append("Phase 3 V1.8 gate conclusion must be NOT_READY_FOR_SDS_BASELINE_REVISION_007")
    require_tokens(errors, "Phase 3 V1.8 gate", gate, (
        "IN_REVIEW", "NOT_READY_FOR_SDS_BASELINE_REVISION_007", "111个目标版本切片",
        "P3-E09", "AI-MIG-000", "Q08候选索引", "| Phase 1/2前置 | PASS |",
    ))
    contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
    if not contract_path.exists():
        errors.append("missing V1.8 Phase 2/3 contract map")
        return errors
    contract_text = contract_path.read_text(encoding="utf-8")
    if "Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`" not in contract_text:
        errors.append("V1.8 contract map missing Phase 3-ready input marker")
    blocks = parse_contract_blocks(contract_text)
    if len(blocks) != 100:
        errors.append(f"expected 100 V1.8 Phase 3 verification mappings, got {len(blocks)}")
    if {"ACC-05", "COM-02", "IMP-02"} & set(blocks):
        errors.append("removed/deferred V1.8 requirements leaked into Phase 3 mappings")
    return errors


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    gate_path = root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
    gate = ""
    revalidation = False
    if gate_path.exists():
        gate = gate_path.read_text(encoding="utf-8")
        state = GATE_REVIEW_STATE.search(gate)
        conclusion = GATE_CONCLUSION.search(gate)
        review_state = state.group(1) if state else None
        gate_conclusion = conclusion.group(1) if conclusion else None
        if review_state not in {"IN_REVIEW", "APPROVED"}:
            errors.append(f"Phase 3 gate has invalid review state: {review_state}")
        revalidation = review_state == "IN_REVIEW"
        expected_conclusion = (
            "NOT_READY_FOR_SDS_BASELINE_REVISION_007" if revalidation else "READY_FOR_SDS_BASELINE_V1.8"
        )
        if gate_conclusion != expected_conclusion:
            errors.append(
                f"Phase 3 gate conclusion mismatch; expected={expected_conclusion} actual={gate_conclusion}"
            )
        if revalidation:
            errors.extend(validate_v18_in_review(root, gate))
        else:
            require_tokens(errors, "Phase 3 approved gate", gate, (
                "APPROVED", "READY_FOR_SDS_BASELINE_V1.8", "修订007", "111个目标版本切片",
            ))
    design_dir = root / "docs" / "design"
    documents: dict[str, str] = {}
    expected_document_status = "文档状态：`IN_REVIEW`" if revalidation else "文档状态：`BASELINE`"
    for name in DESIGN_FILES:
        path = design_dir / name
        if not path.exists():
            errors.append(f"missing Phase 3 design document: {path.as_posix()}")
            continue
        text = path.read_text(encoding="utf-8")
        documents[name] = text
        for marker in (expected_document_status, "适用基线：PRD V1.8", "Requirement ID：", "Owner："):
            if marker not in text:
                errors.append(f"{name} missing metadata: {marker}")
        for stale_text in STALE_V18_DESIGN_TEXT:
            if stale_text in text:
                errors.append(f"{name} contains stale V1.8 design state: {stale_text}")

    security = documents.get("14-security-design.md", "")
    require_tokens(errors, "security design", security, (
        "AES-256", "密钥材料与业务数据分离", "五元组", "临时输入", "不落库",
        "秘密扫描", "fail closed", "50MB", "SSRF", "服务端",
    ))

    observability = documents.get("17-audit-and-observability.md", "")
    require_tokens(errors, "observability design", observability, (
        "operationId", "correlationId", "traceId", "Outbox", "P95", "≤0.5%",
        "≥99%", "≤60秒", "runbook", "高风险",
    ))

    deployment = documents.get("18-deployment-design.md", "")
    require_tokens(errors, "deployment design", deployment, (
        "JDK 25", "pnpm 9.15.5", "Expand -> Backfill -> Verify -> Switch -> Contract",
        "--frozen-lockfile", "前向迁移", "上一JAR", "制品hash", "releaseId",
        "不得修改已执行迁移", "恢复", "AI-MIG-000", "不定义迁移批准哈希",
    ))

    performance = documents.get("19-performance-design.md", "")
    require_tokens(errors, "performance design", performance, (
        "P95≤2秒", "≤0.5%", "50个并发登录用户", "持续30分钟", "≥10000",
        "50MB", "20万", "200万", "1万", "5万", "2000", "深度30",
        "≤30秒", "≥99%", "≤60秒", "dataSetVersion",
    ))

    test_design = documents.get("20-test-design.md", "")
    require_tokens(errors, "test design", test_design, (
        "正常", "异常", "权限拒绝", "幂等", "并发", "Chrome", "Edge", "Firefox",
        "1920×1080", "1440×900", "1366×768", "1024×768", "Playwright trace",
        "秘密扫描0命中", "≥10000", "≤0.5%", "P95≤2秒", "≥99%", "≤60秒",
    ))
    for slice_key, tokens in REVISION_007_DELTA_SLICE_TOKENS.items():
        row = re.search(rf"^\|\s*{re.escape(slice_key)}\s*\|(.+)$", test_design, re.M)
        if not row:
            errors.append(f"test design missing revision 007 delta slice: {slice_key}")
            continue
        for token in tokens:
            if token not in row.group(1):
                errors.append(f"{slice_key} test design missing delta assertion/evidence token: {token}")

    contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
    prd_path = root / "docs" / "baseline" / "prd-v1.8.md"
    prd_identifiers: list[str] = []
    prd_acceptance: dict[str, str] = {}
    if not prd_path.exists():
        errors.append("missing PRD V1.8 baseline for Phase 3 traceability")
    else:
        prd_identifiers = prd_formal_requirement_ids(prd_path.read_text(encoding="utf-8"))
        prd_acceptance = prd_formal_requirement_acceptance(prd_path.read_text(encoding="utf-8"))
        if len(prd_identifiers) != EXPECTED_REQUIREMENT_COUNT or len(set(prd_identifiers)) != EXPECTED_REQUIREMENT_COUNT:
            errors.append(
                "PRD V1.8 expected 100 unique formal V1/V2 requirements for Phase 3 traceability; "
                f"got rows={len(prd_identifiers)} unique={len(set(prd_identifiers))}"
            )
        if set(prd_acceptance) != set(prd_identifiers):
            errors.append(
                "PRD V1.8 Phase 3 acceptance extraction must cover every formal requirement; "
                f"missing={sorted(set(prd_identifiers) - set(prd_acceptance))}"
            )
    if not contract_path.exists():
        errors.append("missing explicit Phase 2/3 contract map")
    else:
        contract_text = contract_path.read_text(encoding="utf-8")
        expected_slices = prd_version_slice_keys(root)
        actual_slices = VERSION_SLICE_ROW.findall(contract_text)
        if (
            len(actual_slices) != 111
            or len(set(actual_slices)) != 111
            or set(actual_slices) != set(expected_slices)
        ):
            errors.append("Phase 3 input must contain all 111 PRD-derived version slices exactly once")
        object_table_contract = load_object_table_contract(root, errors)
        all_contract_tables = {
            table
            for object_contract in object_table_contract.values()
            if isinstance(object_contract, dict)
            for table in (object_contract.get("targetTables") or [])
        }
        database_design_path = root / "docs" / "design" / "09-database-design.md"
        if database_design_path.exists():
            all_contract_tables.update(
                re.findall(
                    r"`((?:proj|sol|imp|acc|cut|srv|cus|ast|com|res|ana|plt)_[a-z0-9_]+)`",
                    database_design_path.read_text(encoding="utf-8"),
                )
            )
        expected_contract_marker = "Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`"
        if expected_contract_marker not in contract_text:
            errors.append(f"contract map missing marker: {expected_contract_marker}")
        blocks = parse_contract_blocks(contract_text)
        if len(blocks) != EXPECTED_REQUIREMENT_COUNT:
            errors.append(f"expected {EXPECTED_REQUIREMENT_COUNT} Phase 3 verification mappings, got {len(blocks)}")
        if prd_identifiers and set(blocks) != set(prd_identifiers):
            errors.append(
                "Phase 3 verification mapping IDs must exactly match PRD V1.8 formal V1/V2 requirements; "
                f"missing={sorted(set(prd_identifiers) - set(blocks))} "
                f"extra={sorted(set(blocks) - set(prd_identifiers))}"
            )
        for identifier, block in blocks.items():
            tests = PHASE3_TEST.findall(block)
            prd_acceptance_values = PHASE3_PRD_ACCEPTANCE.findall(block)
            authorization_assertions = PHASE3_AUTHORIZATION_ASSERTION.findall(block)
            guard_assertions = PHASE3_GUARD_ASSERTION.findall(block)
            side_effect_assertions = PHASE3_SIDE_EFFECT_ASSERTION.findall(block)
            formal_object_values = CONTRACT_DATA_OBJECTS.findall(block)
            formal_table_values = CONTRACT_DATA_TABLES.findall(block)
            evidence = PHASE3_EVIDENCE.findall(block)
            if len(tests) != 1 or not tests[0].strip() or not all(token in tests[0] for token in (
                "业务规则/聚合单元测试", "API契约与输入边界测试", "服务端授权拒绝测试",
                "状态/异常恢复测试", "幂等与并发冲突测试", "数据库约束与迁移测试",
            )):
                errors.append(f"{identifier} missing unique Phase 3 test categories")
            if len(evidence) != 1 or not evidence[0].strip() or not all(token in evidence[0] for token in (
                "自动化测试报告（用例ID、业务对象ID、断言与结果）", "数据库迁移/约束验证记录",
            )):
                errors.append(f"{identifier} missing unique Phase 3 evidence types")
            expected_acceptance = prd_acceptance.get(identifier)
            if len(prd_acceptance_values) != 1 or prd_acceptance_values[0].strip() != expected_acceptance:
                errors.append(f"{identifier} Phase 3 PRD acceptance must exactly match the authoritative PRD block")
            if len(authorization_assertions) != 1 or not all(
                token in authorization_assertions[0] for token in ("越权按", "拒绝", "不产生业务副作用")
            ):
                errors.append(f"{identifier} missing structured Phase 3 authorization rejection assertion")
            if len(guard_assertions) != 1 or not all(
                token in guard_assertions[0] for token in ("按“", "对应业务守卫拒绝", "原有效业务事实保持不变")
            ):
                errors.append(f"{identifier} missing structured Phase 3 business guard assertion")
            if len(side_effect_assertions) != 1 or not all(
                token in side_effect_assertions[0]
                for token in ("成功仅按契约写入/引用数据对象", "事件边界", "授权拒绝", "不得新增有效业务版本")
            ):
                errors.append(f"{identifier} missing structured Phase 3 side-effect assertion")
            else:
                declaration = SIDE_EFFECT_OBJECTS.search(side_effect_assertions[0])
                if not declaration:
                    errors.append(f"{identifier} missing parseable object-table declaration")
                elif len(formal_object_values) != 1 or len(formal_table_values) != 1:
                    errors.append(f"{identifier} must declare exactly one formal data-object/table pair")
                else:
                    declared_objects = split_contract_values(formal_object_values[0])
                    declared_tables = split_contract_values(formal_table_values[0])
                    side_effect_objects = split_contract_values(declaration.group(1))
                    side_effect_tables = split_contract_values(declaration.group(2))
                    if set(declared_objects) != set(side_effect_objects):
                        errors.append(
                            f"{identifier} formal data objects differ from side-effect objects: "
                            f"formal={declared_objects} sideEffect={side_effect_objects}"
                        )
                    if set(declared_tables) != set(side_effect_tables):
                        errors.append(
                            f"{identifier} formal data tables differ from side-effect tables: "
                            f"formal={declared_tables} sideEffect={side_effect_tables}"
                        )
                    allows_feature_forward_description = False
                    allowed_tables = set(CROSS_CONTEXT_TABLE_REFERENCES.get(identifier, set()))
                    for object_name in declared_objects:
                        object_contract = object_table_contract.get(object_name)
                        if not isinstance(object_contract, dict):
                            errors.append(f"{identifier} declares unknown domain object: {object_name}")
                            continue
                        requirement_ids = set(object_contract.get("requirementIds") or [])
                        if identifier not in requirement_ids:
                            errors.append(
                                f"{identifier} cannot declare domain object {object_name}; "
                                f"object requirements={sorted(requirement_ids)}"
                            )
                        allowed_tables.update(object_contract.get("targetTables") or [])
                        allows_feature_forward_description = allows_feature_forward_description or (
                            object_contract.get("targetTablePolicy") == "FEATURE_FORWARD_MIGRATION"
                        )
                    for table_name in declared_tables:
                        if table_name in allowed_tables:
                            continue
                        if allows_feature_forward_description and table_name.startswith("FEATURE_FORWARD_MIGRATION("):
                            continue
                        errors.append(f"{identifier} declares unknown target table for its objects: {table_name}")
                    for cross_context_table in CROSS_CONTEXT_TABLE_REFERENCES.get(identifier, set()):
                        if cross_context_table not in all_contract_tables:
                            errors.append(
                                f"{identifier} cross-context table reference is absent from formal design: "
                                f"{cross_context_table}"
                            )

            if len(side_effect_assertions) == 1:
                side_effect = side_effect_assertions[0]
                surfaces = (
                    ("event", SIDE_EFFECT_EVENT, "事件Outbox/Inbox", "事件消息ID、Outbox/Inbox"),
                    ("file", SIDE_EFFECT_FILE, "文件上传/下载/版本/恶意内容与权限回源测试", "文件哈希、版本、扫描、引用与权限拒绝记录"),
                    ("integration", SIDE_EFFECT_INTEGRATION, "外部集成映射、超时/重试/对账/降级测试", "脱敏请求响应、幂等键、重试/对账与降级记录"),
                )
                for surface_name, pattern, test_token, evidence_token in surfaces:
                    match = pattern.search(side_effect)
                    if not match:
                        errors.append(f"{identifier} missing {surface_name} boundary declaration")
                        continue
                    if not match.group(1).startswith("N/A"):
                        if len(tests) != 1 or test_token not in tests[0]:
                            errors.append(f"{identifier} missing {surface_name} specialty test")
                        if len(evidence) != 1 or evidence_token not in evidence[0]:
                            errors.append(f"{identifier} missing {surface_name} specialty evidence")
            if any(token in block for token in ("领域测试", "后续补充", "占位测试", "占位证据", "验证业务活动正确")):
                errors.append(f"{identifier} uses generic Phase 3 placeholder")

        exact = {
            "PM-05": ("部分失败", "逐项重试"),
            "PM-06": ("无环", "唯一期次"),
            "PM-11": ("5万节点", "2000直接子节点", "深度30"),
            "CUS-02": (
                "CustomerServiceLevelRevision", "cus_customer_service_level_revision", "/customers/{id}/service-level-revisions",
                "结束原等级区间并生成新版本", "等级与策略快照", "历史业务快照不回写",
            ),
            "CUT-07": (
                "CutoverConfigurationRevision", "cut_cutover_configuration_revision", "/cutover-config/checklist-items",
                "草稿→已发布→已停用", "稳定编码", "动态维度", "已生成实例继续按消费版本解释",
            ),
            "INT-12": ("五元组", "临时明文不落库", "原子切换", "秘密扫描零命中"),
            "NFR-01": (
                "50并发用户30分钟", "10000请求", "P95", "错误率不高于0.5%",
                "50MB", "20万项目", "200万任务", "Chrome/Edge/Firefox", "Playwright trace",
            ),
            "NFR-02": (
                "AES-256", "任务级短期取密", "临时输入", "密码记录数为0",
                "撤销", "明文命中数为0", "密钥轮换", "秘密扫描",
            ),
            "NFR-03": ("99%", "60秒"),
            "PLT-02": ("50MB", "恶意内容", "权限"),
        }
        for identifier, tokens in exact.items():
            block = blocks.get(identifier, "")
            require_tokens(errors, f"{identifier} Phase 3 mapping", block, tokens)

    gate_path = root / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
    if not gate_path.exists():
        errors.append("missing Phase 3 gate status")
    else:
        required_model_status = "MODEL_BASELINE_REVIEW_PENDING" if "MODEL_BASELINE_REVIEW_PENDING" in gate else "MODEL_BASELINE_READY"
        require_tokens(errors, "Phase 3 gate", gate, (
            "P3-E01", "P3-E02", "P3-E03",
            "P3-E04", "P3-E05", "P3-E06", "P3-E08", "P3-E09", "AI-MIG-000", "DOWNSTREAM-GATED",
            required_model_status,
        ))
        if "MODEL_BASELINE_REVIEW_PENDING" in gate:
            for baseline_claim in (
                "P3-E09模型基线已发布",
                "P3-E09已发布当前SDS模型基线",
                "P3-E09模型基线可供SDS和后续Feature使用",
            ):
                if baseline_claim in gate:
                    errors.append(f"Phase 3 gate contains premature P3-E09 baseline claim: {baseline_claim}")
        pending = required_model_status == "MODEL_BASELINE_REVIEW_PENDING"
        for relative_path in P3E09_STATE_ASSETS:
            asset_path = root / relative_path
            if not asset_path.exists():
                errors.append(f"missing P3-E09 state asset: {relative_path}")
                continue
            asset = asset_path.read_text(encoding="utf-8")
            if pending:
                if "当前新候选待fresh review" not in asset:
                    errors.append(f"P3-E09 pending state missing fresh-review notice: {relative_path}")
                if "模型基线已发布" in asset:
                    errors.append(f"P3-E09 pending state contains published baseline claim: {relative_path}")
            else:
                if "正式独立复审已GO、模型基线已发布" not in asset:
                    errors.append(f"P3-E09 ready state missing formal GO publication: {relative_path}")
                if "当前新候选待fresh review" in asset:
                    errors.append(f"P3-E09 ready state retains pending review notice: {relative_path}")
    register_validator_path = Path(__file__).with_name("validate_phase3_evidence_register.py")
    register_path = root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json"
    if not register_validator_path.exists() or not register_path.exists():
        errors.append("missing Phase 3 evidence register or validator")
    else:
        spec = importlib.util.spec_from_file_location("phase3_evidence_validator", register_validator_path)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        errors.extend(f"evidence register: {error}" for error in module.validate(register_path))

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    gate_path = args.root.resolve() / "docs" / "engineering" / "gates" / "phase-3" / "gate-status.md"
    if gate_path.is_file() and (
        (match := GATE_REVIEW_STATE.search(gate_path.read_text(encoding="utf-8")))
        and match.group(1) == "IN_REVIEW"
    ):
        print("[PASS] PRD V1.8 revision 007 Phase 3 in-review gate: 100 mappings, 111 version slices; not released as SDS baseline")
        return 0
    print(
        f"[PASS] PRD V1.8 revision 007 Phase 3 ready: {EXPECTED_REQUIREMENT_COUNT} mappings, "
        "111 version slices; released as SDS baseline"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
