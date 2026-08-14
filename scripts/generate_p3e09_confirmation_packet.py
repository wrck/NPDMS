#!/usr/bin/env python3
"""Generate the complete, current-hash-bound P3-E09 confirmation packet."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import subprocess
import sys
from pathlib import Path


REGISTER = Path("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json")
CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
JSON_OUTPUT = Path("specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.json")
MD_OUTPUT = Path("specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.md")


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def item_payload(item: dict[str, object]) -> dict[str, object]:
    return {
        "itemId": item["itemId"],
        "table": item["table"],
        "itemType": item["itemType"],
        "name": item.get("name", str(item["itemId"]).split(":")[-1]),
        "comparisonStatus": item["comparisonStatus"],
        "currentValue": item["currentValue"],
    }


def git_blob(root: Path, commit: str, path: Path) -> bytes:
    return subprocess.check_output(
        ["git", "show", f"{commit}:{path.as_posix()}"], cwd=root
    )


def build(root: Path) -> dict[str, object]:
    register_path = root / REGISTER
    contract_path = root / CONTRACT
    register = json.loads(register_path.read_text(encoding="utf-8"))
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    drift_generator = load_module("p3e09_packet_drift", root / "scripts/generate_ddl_drift_review.py")
    catalog_generator = load_module("p3e09_packet_catalog", root / "scripts/generate_ddl_model_decision_catalog.py")

    items = register["items"]
    by_id = {item["itemId"]: item for item in items}
    deferred = {item["itemId"] for item in items if item["decision"] == "DEFER"}
    confirmation = contract.get("p3e09RequirementOwnerConfirmation", {})
    confirmed = isinstance(confirmation, dict) and confirmation.get("status") == "ACCEPTED"
    if confirmed:
        source_commit = confirmation["preConfirmationSourceCommit"]
        frozen_register_bytes = git_blob(root, source_commit, REGISTER)
        frozen_packet_bytes = git_blob(root, source_commit, JSON_OUTPUT)
        if hashlib.sha256(frozen_register_bytes).hexdigest().upper() != confirmation["preConfirmationRegisterFileSha256"]:
            raise ValueError("pre-confirmation DDL register file hash drift")
        if hashlib.sha256(frozen_packet_bytes).hexdigest().upper() != confirmation["preConfirmationPacketFileSha256"]:
            raise ValueError("pre-confirmation packet file hash drift")
        frozen_register = json.loads(frozen_register_bytes)
        frozen_packet = json.loads(frozen_packet_bytes)
        if frozen_register.get("itemsSha256") != confirmation["preConfirmationItemsSha256"]:
            raise ValueError("pre-confirmation decision items hash drift")
        frozen_groups = {
            group["code"]: {item["itemId"] for item in group["items"]}
            for group in frozen_packet["groups"]
        }
        confirmation_scope = set().union(*frozen_groups.values())
    else:
        frozen_groups = None
        confirmation_scope = deferred
    q07_ids, q08_ids, _counts = drift_generator.q07_q08_item_ids(register)
    v17_tables = {
        table
        for tables in contract["v17Delta"]["objectTargetTables"].values()
        for table in tables
    }
    v17_ids = {item["itemId"] for item in items if item["table"] in v17_tables}
    remaining = confirmation_scope - q07_ids - q08_ids - v17_ids

    residual_groups: dict[str, set[str]] = {
        "Q09": set(), "Q10": set(), "Q11": set(), "Q12": set(), "Q13": set(), "Q14": set(),
    }
    for identifier in remaining:
        item = by_id[identifier]
        if item["itemType"] == "TABLE_OPTION":
            residual_groups["Q09"].add(identifier)
        elif item["itemType"] == "COLUMN":
            residual_groups["Q14"].add(identifier)
        elif item["itemType"] == "CONSTRAINT" and str(item["name"]).startswith("chk_"):
            residual_groups["Q13"].add(identifier)
        else:
            category = catalog_generator.unique_key_category(str(item["name"]))
            target = {
                "SOURCE_IDEMPOTENCY": "Q10",
                "RELATION_GRAIN": "Q11",
                "BUSINESS_IDENTITY": "Q12",
                "VERSION_SEQUENCE": "Q12",
            }.get(category)
            if target is None:
                raise ValueError(f"unclassified residual P3-E09 item: {identifier} ({category})")
            residual_groups[target].add(identifier)

    groups = [
        ("Q07", "当前哈希技术约束", q07_ids, "A", "接受257项主键、租户引用键、同域外键和稳定技术CHECK；历史违规数据进入迁移问题池。"),
        ("Q08", "当前哈希候选索引", q08_ids, "A", "接受122项为候选索引基线；不代表性能验收，后续仅以前向迁移调整。"),
        ("V1.7", "V1.7十表物理候选", v17_ids, "A", "接受十张候选表的全部表、字段、约束、索引和表选项；不扩大到已排除或后置对象。"),
        ("Q09", "非V1.7表选项", residual_groups["Q09"], "A", "统一采用InnoDB、utf8mb4、utf8mb4_0900_ai_ci；COMMENT仅描述对象语义，不作为业务规则。"),
        ("Q10", "来源幂等唯一键", residual_groups["Q10"], "A", "按租户、来源系统和来源业务键防止重复同步；来源键按不透明值精确比较。"),
        ("Q11", "关系粒度唯一键", residual_groups["Q11"], "A", "仅阻止同一关系粒度重复，不额外限制项目、订单、设备或参与方数量。"),
        ("Q12", "业务身份与版本序号唯一键", residual_groups["Q12"], "A", "业务编码、单号、SN、文档版本和产品版本在声明粒度内唯一且不复用。"),
        ("Q13", "跨字段一致性CHECK", residual_groups["Q13"], "A", "保留设备缓存一致性及公司/部门成对填写检查，不固化可扩展状态值。"),
        ("Q14", "市场目录审计字段与RMA投影", residual_groups["Q14"], "A", "保留基础平台审计/租户/来源字段；rma_marked仅作兼容查询投影，不推导业务动作或数量方向。"),
    ]
    coverage = set().union(*(set(ids) for _code, _name, ids, _recommendation, _reason in groups))
    missing = sorted(confirmation_scope - coverage)
    if missing:
        raise ValueError(f"confirmation packet does not cover all decision items: {missing}")

    if confirmed:
        current_groups = {code: set(ids) for code, _name, ids, _recommendation, _reason in groups}
        if current_groups != frozen_groups:
            raise ValueError("current confirmation groups differ from the frozen pre-confirmation packet")
        if len(coverage) != confirmation.get("confirmedUniqueItemCount"):
            raise ValueError("confirmed P3-E09 item count drift")
        for code, _name, ids, _recommendation, _reason in groups:
            canonical = json.dumps(sorted(ids), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            if confirmation["groups"][code]["itemIdsSha256"] != hashlib.sha256(canonical).hexdigest().upper():
                raise ValueError(f"confirmed P3-E09 group drift: {code}")
    return {
        "schemaVersion": 1,
        "status": "REQUIREMENT_OWNER_ACCEPTED" if confirmed else "USER_CONFIRMATION_REQUIRED",
        "purpose": "P3_E09_CURRENT_HASH_COMPLETE_CONFIRMATION_PACKET",
        "currentDdlSha256": register["currentDdlSha256"],
        "decisionRegisterItemsSha256": register["itemsSha256"],
        "decisionRegisterFileSha256": hashlib.sha256(register_path.read_bytes()).hexdigest().upper(),
        "deferredItemCount": confirmation["deferredItemCountAtConfirmation"] if confirmed else len(deferred),
        "coveredDeferredItemCount": confirmation["coveredDeferredItemCount"] if confirmed else len(coverage & deferred),
        "reconfirmedExistingDecisionItemCount": confirmation["reconfirmedExistingDecisionItemCount"] if confirmed else len(coverage - deferred),
        "confirmation": {
            "decisionRef": confirmation.get("decisionRef"),
            "confirmedAt": confirmation.get("confirmedAt"),
            "decision": confirmation.get("decision"),
            "reviewStatus": confirmation.get("reviewStatus"),
            "approvedDdlSha256": confirmation.get("approvedDdlSha256"),
            "preConfirmationSourceCommit": confirmation.get("preConfirmationSourceCommit"),
            "preConfirmationItemsSha256": confirmation.get("preConfirmationItemsSha256"),
            "preConfirmationRegisterFileSha256": confirmation.get("preConfirmationRegisterFileSha256"),
            "preConfirmationPacketFileSha256": confirmation.get("preConfirmationPacketFileSha256"),
        } if confirmed else None,
        "groups": [
            {
                "code": code,
                "name": name,
                "recommendedDecision": recommendation,
                "recommendationReason": reason,
                "itemCount": len(ids),
                "items": [item_payload(by_id[identifier]) for identifier in sorted(ids)],
            }
            for code, name, ids, recommendation, reason in groups
        ],
    }


def escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def render_markdown(packet: dict[str, object]) -> str:
    lines = [
        "# P3-E09 当前哈希完整确认清单",
        "",
        f"> 状态：`{packet['status']}`",
        f"> 当前 DDL SHA-256：`{packet['currentDdlSha256']}`",
        (
            f"> 确认时待决策项：{packet['deferredItemCount']}；本清单覆盖：{packet['coveredDeferredItemCount']}。"
            if packet["status"] == "REQUIREMENT_OWNER_ACCEPTED"
            else f"> 待确认项：{packet['deferredItemCount']}；本清单覆盖：{packet['coveredDeferredItemCount']}。"
        ),
        "",
        "## 决策摘要",
        "",
        "|编号|决策组|项数|推荐|确认效果|",
        "|---|---|---:|---|---|",
    ]
    for group in packet["groups"]:
        lines.append(
            f"|{group['code']}|{group['name']}|{group['itemCount']}|{group['recommendedDecision']}|{group['recommendationReason']}|"
        )
    lines.extend([
        "",
        "推荐组合：`Q07 A、Q08 A、V1.7 A、Q09 A、Q10 A、Q11 A、Q12 A、Q13 A、Q14 A`。",
        "",
        "该组合已形成当前哈希下的需求方决策，不代表Reviewer签署或生成`approvedDdlSha256`。"
        if packet["status"] == "REQUIREMENT_OWNER_ACCEPTED"
        else "该组合只形成当前哈希下的需求方决策，不代表Reviewer签署或生成`approvedDdlSha256`。",
    ])
    for group in packet["groups"]:
        lines.extend([
            "",
            f"## {group['code']} {group['name']}（{group['itemCount']}项）",
            "",
            f"推荐：**{group['recommendedDecision']}**。{group['recommendationReason']}",
            "",
            "|Item ID|当前定义|",
            "|---|---|",
        ])
        for item in group["items"]:
            lines.append(f"|`{item['itemId']}`|`{escape(item['currentValue'])}`|")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    packet = build(root)
    json_text = json.dumps(packet, ensure_ascii=False, indent=2) + "\n"
    md_text = render_markdown(packet)
    outputs = {root / JSON_OUTPUT: json_text, root / MD_OUTPUT: md_text}
    if args.check:
        drift = [path for path, content in outputs.items() if not path.exists() or path.read_text(encoding="utf-8") != content]
        if drift:
            print("[FAIL] P3-E09 confirmation packet drift: " + ", ".join(str(path) for path in drift))
            return 1
        print(f"[PASS] P3-E09 confirmation packet covers {packet['coveredDeferredItemCount']} DEFER items")
        return 0
    for path, content in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
        print(f"[WRITE] {path.relative_to(root).as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
