#!/usr/bin/env python3
"""Generate owner-fillable Phase 3 evidence packets from the accepted ADR."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from validate_phase3_evidence_submission import REQUIRED_FACTS


DECISION_REF = "docs/decisions/0004-phase3-production-assurance-directions.md"
PACKET_DEFINITIONS = {
    "P3-E01": ("A", "复用企业现有网关/LB、证书和网络区", ["TECHNICAL_ARCHITECTURE_OWNER", "OPERATIONS_OWNER"]),
    "P3-E02": ("A", "使用企业托管MySQL/Redis HA服务", ["DBA_OWNER", "OPERATIONS_OWNER"]),
    "P3-E03": ("A", "业务Owner先批准RPO/RTO，再由DBA/运维设计并演练", ["BUSINESS_OWNER", "DBA_OWNER", "OPERATIONS_OWNER"]),
    "P3-E04": ("A", "使用企业KMS/Secrets Manager", ["SECURITY_OWNER", "OPERATIONS_OWNER"]),
    "P3-E05": ("A", "OpenTelemetry统一采集并接入企业现有后端", ["OBSERVABILITY_OWNER", "SECURITY_OWNER", "COMPLIANCE_OWNER"]),
    "P3-E06": ("A", "建设独立近生产性能环境", ["TEST_OWNER", "OPERATIONS_OWNER", "DATA_OWNER"]),
    "P3-E07": ("B", "建立平台级接口配置注册表，Feature引用不可变版本", ["INTEGRATION_ARCHITECTURE_OWNER", "EXTERNAL_SYSTEM_OWNER"]),
    "P3-E09": ("A", "逐表、列、索引和约束生成差异并逐项裁决", ["DATA_ARCHITECTURE_OWNER", "BUSINESS_OWNER", "MIGRATION_OWNER"]),
}


def build_packets() -> dict[str, dict[str, object]]:
    packets: dict[str, dict[str, object]] = {}
    for identifier, (decision, direction, owner_roles) in PACKET_DEFINITIONS.items():
        facts = {field: None for field in sorted(REQUIRED_FACTS[identifier])}
        facts.update(
            {
                "directionDecision": decision,
                "directionStatus": "ACCEPTED",
                "chosenDirection": direction,
                "evidenceOwnerRoles": owner_roles,
            }
        )
        if identifier == "P3-E09":
            facts.update(
                {
                    "currentDdlSha256": "3CDDE2E206EE4AE401ECC398EA01A6F44FFAD37AC2644478DCD42202330D58BC",
                    "driftDecisionRegister": "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json",
                }
            )
        evidence_refs = [DECISION_REF]
        if identifier == "P3-E09":
            evidence_refs.extend([
                "specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json",
                "specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json",
                "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json",
            ])
        packets[identifier] = {
            "schemaVersion": 1,
            "id": identifier,
            "status": "DRAFT",
            "decisionOwner": "REQUIREMENT_OWNER",
            "evidenceOwnerRoles": owner_roles,
            "reviewOwner": None,
            "environmentOrReleaseId": None,
            "capturedAt": None,
            "confirmedFacts": facts,
            "evidenceRefs": evidence_refs,
            "verificationResult": None,
            "unresolvedItems": ["由evidenceOwnerRoles对应Owner填写全部空值并附受控证据引用"],
            "revalidationTriggers": [],
        }
    return packets


def render(payload: object) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def expected_files(output_dir: Path) -> dict[Path, str]:
    packets = build_packets()
    files = {output_dir / f"{identifier.lower()}-submission.json": render(packet) for identifier, packet in packets.items()}
    manifest = {
        "schemaVersion": 1,
        "decisionBaseline": DECISION_REF,
        "status": "OWNER_INPUT_PENDING",
        "packets": [
            {
                "id": identifier,
                "path": f"docs/engineering/gates/phase-3/evidence-packet-templates/{identifier.lower()}-submission.json",
                "evidenceOwnerRoles": PACKET_DEFINITIONS[identifier][2],
            }
            for identifier in PACKET_DEFINITIONS
        ],
    }
    files[output_dir / "manifest.json"] = render(manifest)
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("docs/engineering/gates/phase-3/evidence-packet-templates"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    files = expected_files(args.output_dir)
    if args.check:
        drift = [str(path) for path, content in files.items() if not path.exists() or path.read_text(encoding="utf-8") != content]
        if drift:
            print(f"[FAIL] Phase 3 evidence packet drift: {drift}")
            return 1
        print(f"[PASS] {len(PACKET_DEFINITIONS)} Phase 3 evidence packets and manifest")
        return 0
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for path, content in files.items():
        path.write_text(content, encoding="utf-8")
    print(f"[WRITE] {len(PACKET_DEFINITIONS)} Phase 3 evidence packets and manifest")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
