---
name: create-software-spec-docs
description: Use when converting raw or existing software requirements into standardized SRS, business-domain SDS, and TAS documents, especially when Markdown and DOCX must strictly match approved templates, preserve traceability, or repair template drift.
---

# Create Software Specification Documents

## Overview

Produce a requirements, business solution, and test/acceptance document set whose structure is derived from approved templates and whose content remains traceable to authoritative sources. Treat template headings, repeated-unit fields, and table headers as a contract.

**REQUIRED SUB-SKILL:** Use `documents` whenever creating or editing DOCX files.

## Workflow

1. Identify authoritative inputs: raw requirements, confirmed decisions, terminology rules, version scope, and the requested output directory. Do not use unrelated project history as a requirement source.
2. Read the selected Markdown templates completely from `assets/templates/`. Read [document-boundaries.md](references/document-boundaries.md) before deciding whether content belongs in SRS, SDS, or TAS.
3. Establish a source ledger before writing:
   - confirmed source statements;
   - derived content marked `【建议】`;
   - unresolved content marked `【待确认】`;
   - terminology mappings, including names restricted to technical-selection sections.
4. Write Markdown in this order:
   - SRS: business intent, scope, behavior, rules, data, permissions, NFRs, acceptance criteria;
   - SDS: business-domain capabilities, objects, invariants, collaboration, configuration, and implementation mapping;
   - TAS: strategy, environment, data, gates, executable test cases, UAT, report, and traceability.
5. Keep the exact fixed heading order. For every repeated unit, copy every template child heading and table schema:
   - each `FR-*` has all 12 SRS child sections;
   - each `BD-*` has all 15 SDS child sections;
   - each `TC-*` has metadata, test steps, and execution record.
6. Preserve non-applicable template structures. Fill the original table with `不适用` and explain the trimming decision; do not silently delete required headings or substitute prose for a template table.
7. Maintain `目标／来源 → FR/NFR → AC → BD/DS → TC/UAT` traceability. Do not create a design or test commitment without a source requirement.
8. Generate DOCX from the matching Word asset with `scripts/build_docx_from_template.py`. Preserve the template cover, 5x2 cover table, TOC field, styles, header/footer, and package parts.
9. Run both validation gates. Fix every failure before delivery:

```powershell
python scripts/validate_documents.py --kind srs --input <srs.md>
python scripts/verify_docx.py --markdown <srs.md> --docx <srs.docx> --template assets/templates/01-software-requirements-specification-template.docx
```

Repeat for SDS and TAS. When a product name may appear only in technical selection, add:

```powershell
--restricted-term Yudao --allow-section 2.5
```

10. Render every DOCX when Word or LibreOffice is available and inspect pagination, table overflow, and TOC behavior. If no renderer exists, report that visual verification was not performed; structural validation is not visual acceptance.

## Quick Reference

| Need | Required action |
| --- | --- |
| One current release | Describe the current scope; do not force V1/V2/V3 |
| Unsupported inference | Mark `【建议】` or remove it |
| Unresolved rule | Mark `【待确认】` and exclude it from deterministic acceptance |
| Small module | Simplify content depth, not template structure |
| No external integration | Keep the template table and fill an `不适用` row |
| Word output | Build from the corresponding DOCX asset, never from a custom cover |
| Validation failure | Fix the document; do not weaken the validator to accept drift |

## Common Mistakes

- Combining several template chapters because the module is small.
- Treating implementation architecture or API paths as requirements.
- Writing technical component design instead of a business-domain SDS.
- Putting test steps in SRS acceptance criteria.
- Omitting execution-record tables because testing has not started.
- Claiming Word layout passed when only OOXML structure was checked.

## Assets and Tools

- `assets/templates/`: authoritative SRS, SDS, and TAS Markdown/DOCX templates.
- `scripts/validate_documents.py`: Markdown structure and terminology gate.
- `scripts/build_docx_from_template.py`: template-preserving DOCX generator.
- `scripts/verify_docx.py`: Markdown/DOCX parity and package-integrity gate.
