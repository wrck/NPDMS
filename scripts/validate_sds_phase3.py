#!/usr/bin/env python3
"""Validate Phase 3 runtime, release and verification assurance artifacts."""

from __future__ import annotations

import argparse
import importlib.util
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
P3E09_STATE_ASSETS = (
    "docs/decisions/0022-core-migration-schema-and-key-policy.md",
    "specs/001-project-delivery-platform/appendices/project-order-migration-mapping.md",
    "specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md",
    "specs/001-project-delivery-platform/appendices/core-field-migration-completeness.md",
)
REQUIREMENT_HEADING = re.compile(r"^###\s+([A-Z]+-\d+)\s*$", re.M)
PRD_REQUIREMENT_ROW = re.compile(r"^\|\s*需求编号\s*\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*$", re.M)
PRD_TARGET_VERSION = re.compile(r"^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", re.M)
PHASE3_TEST = re.compile(r"^- Phase 3测试类别：(.+?)\s*$", re.M)
PHASE3_PRD_ACCEPTANCE = re.compile(r"^- Phase 3 PRD验收基线：(.+?)\s*$", re.M)
PHASE3_AUTHORIZATION_ASSERTION = re.compile(r"^- Phase 3授权拒绝断言：(.+?)\s*$", re.M)
PHASE3_GUARD_ASSERTION = re.compile(r"^- Phase 3业务守卫断言：(.+?)\s*$", re.M)
PHASE3_SIDE_EFFECT_ASSERTION = re.compile(r"^- Phase 3副作用断言：(.+?)\s*$", re.M)
PHASE3_EVIDENCE = re.compile(r"^- Phase 3证据类型：(.+?)\s*$", re.M)
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
    if not conclusion or conclusion.group(1) != "NOT_READY_FOR_SDS_BASELINE_V1.8":
        errors.append("Phase 3 V1.8 gate conclusion must be NOT_READY_FOR_SDS_BASELINE_V1.8")
    require_tokens(errors, "Phase 3 V1.8 gate", gate, (
        "IN_REVIEW", "NOT_READY_FOR_SDS_BASELINE_V1.8",
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
            "NOT_READY_FOR_SDS_BASELINE_V1.8" if revalidation else "READY_FOR_SDS_BASELINE_V1.8"
        )
        if gate_conclusion != expected_conclusion:
            errors.append(
                f"Phase 3 gate conclusion mismatch; expected={expected_conclusion} actual={gate_conclusion}"
            )
        if revalidation:
            errors.extend(validate_v18_in_review(root, gate))
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
            if any(token in block for token in ("领域测试", "后续补充", "占位测试", "占位证据", "验证业务活动正确")):
                errors.append(f"{identifier} uses generic Phase 3 placeholder")

        exact = {
            "PM-05": ("部分失败", "逐项重试"),
            "PM-06": ("无环", "唯一期次"),
            "PM-11": ("5万节点", "2000直接子节点", "深度30"),
            "CUS-02": (
                "CustomerRelationshipSnapshot", "/customers/{id}/service-level-revisions",
                "结束原等级区间并生成新版本", "等级与策略快照", "历史业务快照不回写",
            ),
            "CUT-07": (
                "CutoverPlan", "/cutover-config/checklist-items",
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
        print("[PASS] PRD V1.8 Phase 3 in-review gate: 100 mappings; not released as SDS baseline")
        return 0
    print(f"[PASS] SDS Phase 3 documents, NFR controls and {EXPECTED_REQUIREMENT_COUNT} verification mappings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
