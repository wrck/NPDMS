#!/usr/bin/env python3
"""Static PRD/SDS/projection regression checks; never a runtime acceptance report."""
from __future__ import annotations
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def requirement(text: str, identifier: str) -> str:
    match = re.search(r"(?m)^####\s+\d+(?:\.\d+)*\s+" + re.escape(identifier) + r"\s+.+$", text)
    if not match:
        return ""
    end = re.search(r"(?m)^#{1,4}\s+", text[match.end():])
    return text[match.start():match.end()+end.start()] if end else text[match.start():]


def inspect_prd(text: str) -> dict[str, bool]:
    r = lambda identifier: requirement(text, identifier)
    body = text.split("## 目录",1)[-1]
    exit_flow = body.split("##### 13.1.2.5",1)[-1].split("##### 13.1.2.6",1)[0]
    e2e = [line for line in text.splitlines() if line.startswith(("| E2E-17 |","| E2E-19 |","| E2E-20 |"))]
    return {
      "R01": "售前测试模板配置`S0→S4`" in r("PM-03") and "S0→S4→S6" not in r("PM-03") and "售前测试模板未配置EXE-02" in r("EXE-03"),
      "R02": "首次推进与后续换版" in r("PLN-04") and "目标准入" in r("SCH-05") and "目标不固定为S3" in r("PLN-04"),
      "R03": "活动项目选择退出意图" in exit_flow and "冻结模板允许的最后一个真实阶段达到准出条件\n  ↓\n选择项目退出类型" not in exit_flow,
      "R04": "不通过、需整改" in r("ACC-03") and "验收通过事实为否" in r("ACC-03"),
      "R05": "采集清单保存后通过任务状态流转自动进入P4" not in r("CUT-03") and "暂存不触发流程推进" in r("CUT-03"),
      "R06": len(e2e)==3 and all("PM-05" not in line and "PM-01/03/05" not in line and "、RPT-02" not in line for line in e2e) and "| E2E-22 | V2 |" in body and "| E2E-23 | V2 |" in body,
      "R07": "APPROVED_BUSINESS_SNAPSHOT" in r("INT-12") and "后者仅允许EXE-03" in r("INT-12"),
      "R08": "不按最后同步时间覆盖" in r("INT-09") and "按逻辑“或”阻断" in r("INT-09"),
      "R09": "来源签名或幂等键" not in r("PLT-02") and "必须同时校验来源身份" in r("PLT-02"),
      "R10": all("正式执行前再次输入临时密码" in r(i) and "两个独立CollectionTask" in r(i) for i in ("INS-02","INS-04")),
      "R11": "实际计划路径" in r("PLN-01") and "倒推S1~S6" not in r("PLN-01") and "不得生成不存在阶段" in r("PLN-01"),
      "R12": "新总体验收版本" in r("ACC-03") and "旧范围报告和结论" in r("PM-06") and "同一项目、适用报告类型仍只有一个当前报告版本" in r("ACC-03"),
      "R13": "CUT-03的P3采集项入口" in r("INT-12") and "清单版本、采集项和设备" in r("INT-12"),
      "R14": "经审批更长" not in r("NFR-02") and "30秒为当前V2硬上限" in r("NFR-02"),
      "R15": "最终归档命令由" in r("INS-07") and "一线工程师归档权限" not in r("INS-07"),
      "R16": "| V1核心/V2治理 |" not in body and "SUB平台审批仅向OA发待办链接" in body,
    }


def main() -> int:
    prd = ROOT / "docs/baseline/prd-v1.8.md"
    text = prd.read_text(encoding="utf-8-sig")
    outcomes = inspect_prd(text)
    outcomes["MIRROR"] = prd.read_bytes() == (ROOT / "需求/PRD-项目实施交付管理平台.md").read_bytes()
    spec = importlib.util.spec_from_file_location("trace_validation",ROOT/"scripts/generate_requirement_traceability.py")
    trace = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(trace)
    identity = trace.baseline_identity(prd)
    coverage = json.loads((ROOT/"docs/traceability/requirement-version-coverage.json").read_text())
    outcomes["PROVENANCE"] = coverage["baselineIdentity"]["gitBlob"] == identity["gitBlob"] and coverage["baselineIdentity"]["changeId"] == identity["changeId"]
    slices = coverage["slices"]
    outcomes["COUNTS"] = len(slices)==111 and len({item["requirementId"] for item in slices})==100
    outcomes["NO_STALE_COMPLETE"] = all(item["implementationStatus"] == "REVALIDATION_REQUIRED" for item in slices if any(mapping.get("revalidationRequired") for mapping in item["features"]))
    for phase in (1,2,3):
        gate = (ROOT/f"docs/engineering/gates/phase-{phase}/gate-status.md").read_text()
        from sds_gate_contract import validate_gate
        outcomes[f"GATE_{phase}"] = not validate_gate(ROOT, phase, technical=True)
    sds = (ROOT/"docs/design/20-test-design.md").read_text(encoding="utf-8-sig")
    outcomes["TEST_DESIGN"] = all(f"TC-PRD016-R{i:02d}" in sds for i in range(1,17)) and "NOT_RUN" in sds
    com = json.loads((ROOT/"specs/features/F-COM-001-project-qualification-contract.json").read_text())
    outcomes["COM_CLOSED_STAGE"] = "NO_TRACKING_CLOSED" in com["qualification"]["lifecycle"] and "requires S6" not in com["qualification"]["lifecycleStageInvariant"]
    stage = json.loads((ROOT/"specs/features/F-PROJ-008-physical-contract.json").read_text())
    outcomes["GRAPH_CONTRACT"] = "expectedGraphVersion" in stage["interfaces"]["AdvanceStage"]["requiredInputs"] and stage["status"] == "REVALIDATION_REQUIRED"
    for key, passed in outcomes.items():
        print(f"[{'PASS' if passed else 'FAIL'}] STATIC {key}")
    print("Static documentation/projection validation only; runtime, database, browser and independent approval NOT_RUN.")
    return 0 if all(outcomes.values()) else 1

if __name__ == "__main__":
    raise SystemExit(main())
