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
EXPECTED_REQUIREMENT_COUNT = 103
P3E09_STATE_ASSETS = (
    "docs/decisions/0022-core-migration-schema-and-key-policy.md",
    "specs/001-project-delivery-platform/appendices/project-order-migration-mapping.md",
    "specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md",
    "specs/001-project-delivery-platform/appendices/core-field-migration-completeness.md",
)
REQUIREMENT_HEADING = re.compile(r"^###\s+([A-Z]+-\d+)\s*$", re.M)
PHASE3_TEST = re.compile(r"^- Phase 3测试类别：(.+?)\s*$", re.M)
PHASE3_EVIDENCE = re.compile(r"^- Phase 3证据类型：(.+?)\s*$", re.M)


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


def validate_v18_revalidation(root: Path, gate: str) -> list[str]:
    """Validate the reopened V1.8 Phase 3 state without approving stale design assets."""
    errors: list[str] = []
    require_tokens(errors, "Phase 3 V1.8 gate", gate, (
        "REVALIDATION_REQUIRED", "NOT_READY_FOR_SDS_BASELINE_V1.8",
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
    if gate_path.exists():
        gate = gate_path.read_text(encoding="utf-8")
        if "REVALIDATION_REQUIRED" in gate:
            return validate_v18_revalidation(root, gate)
    design_dir = root / "docs" / "design"
    documents: dict[str, str] = {}
    for name in DESIGN_FILES:
        path = design_dir / name
        if not path.exists():
            errors.append(f"missing Phase 3 design document: {path.as_posix()}")
            continue
        text = path.read_text(encoding="utf-8")
        documents[name] = text
        for marker in ("文档状态：`BASELINE`", "适用基线：PRD V1.7", "Requirement ID：", "Owner："):
            if marker not in text:
                errors.append(f"{name} missing metadata: {marker}")

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
    if not contract_path.exists():
        errors.append("missing explicit Phase 2/3 contract map")
    else:
        contract_text = contract_path.read_text(encoding="utf-8")
        if "Phase 3验证注记状态：`BASELINE`" not in contract_text:
            errors.append("contract map missing Phase 3 BASELINE marker")
        blocks = parse_contract_blocks(contract_text)
        if len(blocks) != EXPECTED_REQUIREMENT_COUNT:
            errors.append(f"expected {EXPECTED_REQUIREMENT_COUNT} Phase 3 verification mappings, got {len(blocks)}")
        for identifier, block in blocks.items():
            tests = PHASE3_TEST.findall(block)
            evidence = PHASE3_EVIDENCE.findall(block)
            if len(tests) != 1 or not tests[0].strip():
                errors.append(f"{identifier} missing unique Phase 3 test categories")
            if len(evidence) != 1 or not evidence[0].strip():
                errors.append(f"{identifier} missing unique Phase 3 evidence types")
            if "领域测试" in block or "后续补充" in block:
                errors.append(f"{identifier} uses generic Phase 3 placeholder")

        exact = {
            "PM-05": ("部分失败", "逐项重试"),
            "PM-06": ("无环", "唯一期次"),
            "PM-11": ("5万节点", "2000直接子节点", "深度30"),
            "INT-12": ("五元组", "临时明文不落库", "原子切换", "秘密扫描零命中"),
            "NFR-01": ("50并发用户30分钟", "10000请求", "P95", "Playwright trace"),
            "NFR-02": ("AES-256", "密钥轮换", "秘密扫描"),
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
        gate = gate_path.read_text(encoding="utf-8")
        required_model_status = "MODEL_BASELINE_REVIEW_PENDING" if "MODEL_BASELINE_REVIEW_PENDING" in gate else "MODEL_BASELINE_READY"
        required_review_status = "IN_REVIEW" if required_model_status == "MODEL_BASELINE_REVIEW_PENDING" else "APPROVED"
        required_overall_status = "NOT_READY_FOR_SDS_BASELINE" if required_model_status == "MODEL_BASELINE_REVIEW_PENDING" else "READY_FOR_SDS_BASELINE"
        require_tokens(errors, "Phase 3 gate", gate, (
            required_review_status, required_overall_status, "P3-E01", "P3-E02", "P3-E03",
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
    if gate_path.is_file() and "REVALIDATION_REQUIRED" in gate_path.read_text(encoding="utf-8"):
        print("[PASS] PRD V1.8 Phase 3 revalidation gate: 100 mappings; not released as SDS baseline")
        return 0
    print(f"[PASS] SDS Phase 3 documents, NFR controls and {EXPECTED_REQUIREMENT_COUNT} verification mappings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
