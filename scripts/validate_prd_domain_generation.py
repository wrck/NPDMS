from __future__ import annotations

import argparse
import re
from pathlib import Path


DOMAIN_FILES = {
    "PLT": "PLT-平台公共能力需求规格.md",
    "CUS": "CUS-客户与服务关系需求规格.md",
    "PROJ": "PROJ-项目治理需求规格.md",
    "COM": "COM-合同订单履约需求规格.md",
    "SOL": "SOL-交付准备与方案需求规格.md",
    "IMP": "IMP-现场实施需求规格.md",
    "CUT": "CUT-变更切换与稳定治理需求规格.md",
    "ACC": "ACC-验收与项目闭环需求规格.md",
    "AST": "AST-资产管理需求规格.md",
    "RES": "RES-资源与外包需求规格.md",
    "SRV": "SRV-服务运营需求规格.md",
    "KNO": "KNO-技术知识治理需求规格.md",
    "ANA": "ANA-经营分析需求规格.md",
}
DETAIL_RE = re.compile(r"^## ([A-Z]+-\d+) .+$", re.MULTILINE)
FORMAL_ROW_RE = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|", re.MULTILINE)


def _table_ids(prd: str, start_marker: str, end_marker: str) -> set[str]:
    lines = prd.splitlines()
    start = next(i for i, line in enumerate(lines) if start_marker in line)
    end = next(i for i, line in enumerate(lines[start + 1 :], start + 1) if end_marker in line)
    return {
        match.group(1)
        for line in lines[start:end]
        if (match := re.match(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|", line))
    }


def _domain_details(text: str) -> list[str]:
    return DETAIL_RE.findall(text)


def main() -> int:
    parser = argparse.ArgumentParser(description="校验按PRD格式生成的13领域需求")
    parser.add_argument("--prd", type=Path, required=True)
    parser.add_argument("--domains", type=Path, required=True)
    args = parser.parse_args()
    prd = args.prd.read_text(encoding="utf-8")
    formal = _table_ids(prd, "### A.1 V1/V2正式需求索引", "### A.2 正式需求统计")
    v3 = _table_ids(prd, "#### A.3.1 已编号演进项", "#### A.3.2 跨需求演进方向")
    out_scope = _table_ids(prd, "### A.4 OUT_OF_SCOPE索引", "##")

    errors: list[str] = []
    expected_new_formal = {
        "PLT-01", "PLT-02", "PROJ-12", "ANA-01", "COM-01", "COM-02",
        "SOL-01", "IMP-01", "IMP-02", "RES-01", "ACC-05", "ACC-06",
        "SRV-01", "AST-01", "AST-02",
    }
    if len(formal) != 104:
        errors.append(f"PRD formal count={len(formal)}, expected=104")
    if not expected_new_formal.issubset(formal):
        errors.append(f"missing confirmed formal requirements: {sorted(expected_new_formal - formal)}")
    generated_domain_codes = {path.name.split("-", 1)[0] for path in args.domains.glob("*") if path.name.endswith("需求规格.md")}
    if set(DOMAIN_FILES) != generated_domain_codes:
        errors.append("domain file set is not exactly the 13-domain set")

    all_details: list[str] = []
    texts: dict[str, str] = {}
    for code, filename in DOMAIN_FILES.items():
        path = args.domains / filename
        if not path.exists():
            errors.append(f"missing {filename}")
            continue
        text = path.read_text(encoding="utf-8")
        texts[code] = text
        details = _domain_details(text)
        all_details.extend(details)
        marker_groups = (
            ("**业务场景与需求描述：**",),
            ("**核心业务规则：**",),
            ("**用户故事：**",),
            ("**业务验收标准：**", "**验收标准：**"),
            ("**涉及数据字段：**", "**涉及数据：**"),
            ("**权限与数据范围：**",),
            ("**异常、降级及留痕要求：**",),
            ("**依赖关系：**",),
        )
        for marker_group in marker_groups:
            for identifier in details:
                start = text.find(f"## {identifier} ")
                next_heading = text.find("\n## ", start + 1)
                block = text[start : next_heading if next_heading >= 0 else len(text)]
                if not any(marker in block for marker in marker_group):
                    errors.append(f"{code}/{identifier} missing {' or '.join(marker_group)}")
        if "legacy" in text.lower() or "该功能不单独定义" in text or "【待确认】" in text:
            errors.append(f"{code} contains legacy/template placeholder")

    if set(all_details) != formal:
        errors.append(f"formal detail mismatch: missing={sorted(formal - set(all_details))}, extra={sorted(set(all_details) - formal)}")
    duplicates = sorted(identifier for identifier in set(all_details) if all_details.count(identifier) > 1)
    if duplicates:
        errors.append(f"duplicate formal details: {duplicates}")

    substantive_requirements = {
        "PLT-01": ("幂等", "状态机", "待办"),
        "PLT-02": ("文件版本", "审批引用", "逻辑删除"),
        "PROJ-12": ("主组合", "循环关系", "统计规则"),
        "ANA-01": ("统计时点", "下钻", "数据完整性"),
        "COM-01": ("订单行", "已分配数量", "来源编号"),
        "COM-02": ("回写", "对账", "CRM"),
        "SOL-01": ("模板版本", "条件字段", "提交"),
        "IMP-01": ("不合格", "整改", "阶段门禁"),
        "IMP-02": ("高风险", "阻断", "安全员"),
        "RES-01": ("资质", "有效期", "新分派"),
        "ACC-05": ("转持续服务", "遗留问题", "关闭证据"),
        "ACC-06": ("关闭门禁", "交接单", "待关闭"),
        "SRV-01": ("停产", "停止服务", "权威来源"),
        "AST-01": ("RMA", "替换关系", "不删除"),
        "AST-02": ("维保客观状态", "计算时点", "不依赖独立维保"),
    }
    combined_domains = "\n".join(texts.values())
    for identifier, phrases in substantive_requirements.items():
        start = combined_domains.find(f"## {identifier} ")
        end = combined_domains.find("\n## ", start + 1) if start >= 0 else -1
        block = combined_domains[start : end if end >= 0 else len(combined_domains)]
        missing_phrases = [phrase for phrase in phrases if phrase not in block]
        if missing_phrases:
            errors.append(f"{identifier} missing substantive phrases: {missing_phrases}")

    expected_owner = {
        "PROJ": {"INT-01"},
        "AST": {"INT-02", "INT-06"},
        "CUS": {"INT-03"},
        "KNO": {"INT-04"},
        "RES": {"INT-07"},
        "PLT": {"INT-05", "INT-09", "INT-10", "INT-12"},
    }
    for code, identifiers in expected_owner.items():
        if any(f"## {identifier} " not in texts.get(code, "") for identifier in identifiers):
            errors.append(f"integration owner split mismatch in {code}")

    v3_seen: set[str] = set()
    out_seen: set[str] = set()
    for text in texts.values():
        v3_seen.update(match.group(1) for match in re.finditer(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)(?:（跨需求方向）)?\s*\|", text, re.MULTILINE))
        out_seen.update(FORMAL_ROW_RE.findall(text[text.find("## 5. OUT_OF_SCOPE追溯") :]))
    if not v3.issubset(v3_seen):
        errors.append(f"missing V3 trace: {sorted(v3 - v3_seen)}")
    if not out_scope.issubset(out_seen):
        errors.append(f"missing OUT_OF_SCOPE trace: {sorted(out_scope - out_seen)}")
    expected_v3 = {f"KNO-V3-{i:02d}" for i in range(1, 9)} | {f"ANA-V3-{i:02d}" for i in range(1, 6)} | {"SRV-V3-01"}
    if not expected_v3.issubset(v3):
        errors.append(f"missing confirmed V3 rows: {sorted(expected_v3 - v3)}")
    if "FR-SRV-014" not in out_scope:
        errors.append("FR-SRV-014 must remain OUT_OF_SCOPE")
    if any(identifier in formal for identifier in ("FR-SRV-014", "FR-SRV-019", "FR-SRV-020", "FR-SRV-021", "FR-SRV-022", "FR-SRV-023")):
        errors.append("excluded service requirements leaked into formal index")
    for code, marker in (("ACC", "CLO-05→ACC-02（跨需求方向）"), ("RES", "SUB-03（跨需求方向）")):
        if marker not in texts.get(code, ""):
            errors.append(f"missing cross-demand V3 direction in {code}: {marker}")
    for required in ("初始化可扩展状态定义+受控状态机", "项目经理完成整改后生成新任务和新问卷版本重新收集", "V1/V2关闭后只读"):
        if not any(required in text for text in texts.values()):
            errors.append(f"missing confirmed rule: {required}")

    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(f"[PASS] 13 domain files; formal={len(all_details)}; v3={len(v3)}; out_of_scope={len(out_scope)}")
    print("[PASS] PRD format sections, unique ownership, integration split and confirmed rules")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
