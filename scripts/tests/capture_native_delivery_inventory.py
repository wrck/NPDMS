#!/usr/bin/env python3
"""Capture current code entry/write evidence. Counts are source markers, never production acceptance."""
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs/generated/native-delivery-20261006/source-inventory.json"


def source(path, text, offset):
    return {"path": str(path.relative_to(ROOT)), "line": text.count("\n", 0, offset) + 1}


def main():
    policies, writes, raw_uploads, old_commands = [], [], [], []
    native_modules = ("engineering", "acceptance", "cutover")
    for path in sorted(ROOT.glob("pms-module-*/src/main/java/**/*.java")):
        text = path.read_text()
        policy = re.search(r"implements\s+[^\{;]*\bFileBusinessObjectPolicyProvider\b", text)
        if policy:
            policies.append({**source(path, text, policy.start()), "class": path.stem,
                             "dispatchExpressions": re.findall(r'(?:ownerContext|objectType)\(\)\s*\{[^{}]*?return\s+([^;]+);', text)})
        if not any(path.relative_to(ROOT).parts[0] == f"pms-module-{module}" for module in native_modules):
            continue
        for match in re.finditer(r"(?:\.\s*register(?:NativeSourceFile|NativeUploadedFile|BusinessResultMaterial|FileMaterial)\s*\(|::registerNativeSourceFile|(?:generatedFiles|grantFiles)\.create\s*\()", text):
            public_methods = list(re.finditer(r"public\s+(?:[\w<>.?]+\s+)+([\w]+)\s*\([^;{}]*\)\s*\{", text[:match.start()]))
            writes.append({**source(path, text, match.start()), "class": path.stem,
                           "nearestPublicMethod": public_methods[-1].group(1) if public_methods else None,
                           "marker": match.group(0).strip()})
        for match in re.finditer(r"\b(savefile|confirmresult)\b", text, re.I):
            old_commands.append({**source(path, text, match.start()), "symbol": match.group(0)})
    views = ROOT / "yudao-ui/yudao-ui-admin-vue3/src/views/pms"
    for directory in ("engineering", "acceptance", "delivery-business", "cutover"):
        for path in sorted((views / directory).glob("**/*.vue")):
            text = path.read_text()
            for match in re.finditer(r"<UploadFile\b[^>]*", text):
                raw_uploads.append({**source(path, text, match.start()), "element": match.group(0)})
    result = {
        "head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "scope": "All direct PMS file policy implementations; native engineering/acceptance/cutover registration callsites; scoped Vue raw UploadFile candidates. NOT a route-assembly or production pass count.",
        "historicalCounts": {"entryGroups": 32, "writePaths": 29, "reusableAsCurrentAcceptance": False,
                             "reason": "Committed handoff/reference plans lack a matching row-level 32/29 inventory; current survey savefile/confirmresult symbols are absent. New command chains and legacy raw URLs are listed separately."},
        "counts": {"directFilePolicyImplementations": len(policies), "nativeRegistrationCallsites": len(writes),
                   "rawUploadFileCallsites": len(raw_uploads), "rawUploadFileComponents": len({item['path'] for item in raw_uploads}),
                   "oldSurveyCommandSymbols": len(old_commands)},
        "filePolicies": policies, "nativeMaterialRegistrationCallsites": writes,
        "rawUploadFileCandidates": raw_uploads, "oldSurveyCommandSymbols": old_commands
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
