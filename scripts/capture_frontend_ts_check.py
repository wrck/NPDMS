#!/usr/bin/env python3
"""Run the locked NPDMS frontend type gate and write reproducible Phase 3 evidence."""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import re
import shutil
import subprocess
from pathlib import Path


ERROR = re.compile(r"^(?P<file>.+?)\((?P<line>\d+),(?P<column>\d+)\): error (?P<code>TS\d+): (?P<message>.+)$")


def scope_for(path: str) -> str:
    normalized = path.replace("\\", "/")
    if normalized.startswith("src/components/Pms"):
        return "PMS_SHARED"
    marker = "src/views/pms/"
    if normalized.startswith(marker):
        parts = normalized[len(marker):].split("/")
        return f"PMS_{parts[0].upper()}" if parts else "PMS_OTHER"
    return "UPSTREAM_OR_NON_PMS"


def run(frontend: Path, commit: str) -> dict[str, object]:
    corepack = shutil.which("corepack.cmd") or shutil.which("corepack")
    if not corepack:
        raise RuntimeError("corepack executable not found")
    completed = subprocess.run(
        [corepack, "pnpm", "ts:check"], cwd=frontend, text=True, encoding="utf-8",
        errors="replace", stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    errors = []
    for line in completed.stdout.splitlines():
        match = ERROR.match(line.strip())
        if match:
            item = match.groupdict()
            item["line"] = int(item["line"])
            item["column"] = int(item["column"])
            item["scope"] = scope_for(item["file"])
            errors.append(item)
    return {
        "schemaVersion": 1,
        "capturedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "implementationCommit": commit,
        "frontendPath": str(frontend),
        "command": "corepack pnpm ts:check",
        "exitCode": completed.returncode,
        "errorCount": len(errors),
        "errors": errors,
    }


def render(payload: dict[str, object]) -> str:
    errors = payload["errors"]
    by_scope = collections.Counter(item["scope"] for item in errors)
    by_code = collections.Counter(item["code"] for item in errors)
    by_file = collections.Counter(item["file"] for item in errors)
    lines = [
        "# P3-E08 前端类型检查证据",
        "",
        "> 状态：`OPEN / FAIL`  ",
        f"> 实现提交：`{payload['implementationCommit']}`  ",
        f"> 命令：`{payload['command']}`  ",
        f"> 结果：exit code `{payload['exitCode']}`，错误 `{payload['errorCount']}` 项",
        "",
        "该证据只记录可复现失败，不以生产构建成功覆盖类型门禁，也不通过关闭检查、放宽TypeScript规则或批量断言消除错误。JSON为逐错误机器证据。",
        "",
        "## 1. 按范围",
        "",
        "|范围|错误数|",
        "|---|---:|",
    ]
    lines.extend(f"|{name}|{count}|" for name, count in by_scope.most_common())
    lines.extend(["", "## 2. 按错误代码", "", "|代码|错误数|", "|---|---:|"])
    lines.extend(f"|{name}|{count}|" for name, count in by_code.most_common())
    lines.extend(["", "## 3. 高错误文件", "", "|文件|错误数|", "|---|---:|"])
    lines.extend(f"|`{name}`|{count}|" for name, count in by_file.most_common(30))
    lines.extend([
        "", "## 4. 工程判定", "",
        "1. 失败可稳定复现，属于锁定实现提交的现存质量债，不是本次Phase 3文档生成引入。",
        "2. 错误横跨PMS公共组件、多个PMS业务域和非PMS上游页面，不能作为单一Feature的局部修补处理。",
        "3. P3-E08继续阻塞任何前端Feature验收、真实浏览器验收和发布；应拆为公共契约/组件、PMS领域页面、上游兼容三类修复工作包。",
        "4. 每个工作包必须保留严格检查，先以当前JSON中的错误集合建立失败基线，再逐类清零并回归`ts:check`、构建及实际页面。",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--implementation", type=Path, default=Path(r"E:\AICoding\Projects\NPDMS"))
    parser.add_argument("--json-output", type=Path, default=Path("docs/engineering/gates/phase-3/frontend-ts-check-evidence.json"))
    parser.add_argument("--md-output", type=Path, default=Path("docs/engineering/gates/phase-3/frontend-ts-check-evidence.md"))
    args = parser.parse_args()
    commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain"], cwd=args.implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
    if dirty:
        raise SystemExit("implementation repository must be clean before evidence capture")
    frontend = args.implementation / "yudao-ui" / "yudao-ui-admin-vue3"
    payload = run(frontend, commit)
    args.json_output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    args.md_output.write_text(render(payload), encoding="utf-8", newline="\n")
    print(json.dumps({"exitCode": payload["exitCode"], "errorCount": payload["errorCount"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
