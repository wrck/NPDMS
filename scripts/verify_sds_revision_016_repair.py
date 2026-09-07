#!/usr/bin/env python3
"""Reproduce design-content checks; preserve formal approval failures separately.

This never signs a gate, creates a deployment or runs a database migration.
MySQL evidence was produced independently by the labelled isolated-container test.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    tests=['test_validate_sds_phase*.py','test_validate_domain_entity_migration_alignment.py',
           'test_sds_revision_016_gate_contract.py','test_generate_requirement_traceability.py',
           'test_prd_revision_016_alignment.py','test_prd_workbench_contract.py']
    commands=[['-m','unittest','discover','-s','scripts/tests','-p',pattern,'-v'] for pattern in tests]
    commands += [[f'scripts/validate_sds_phase{p}.py','--technical'] for p in (1,2,3)]
    commands += [
        ['scripts/validate_domain_entity_migration_alignment.py','--implementation','.'],
        ['scripts/validate_prd_semantics.py','--prd','docs/baseline/prd-v1.8.md'],
        ['scripts/validate_prd_baseline.py','--prd','docs/baseline/prd-v1.8.md','--report','docs/engineering/gates/phase-1/prd-revision-016-alignment.md','--expected-version','V1.8','--expected-status','正式基线'],
        ['scripts/validate_prd_revision_016_alignment.py'],
        ['scripts/validate_prd_domain_generation.py','--prd','docs/baseline/prd-v1.8.md','--domains','specs/001-project-delivery-platform/domains'],
        ['scripts/generate_requirement_traceability.py','--prd','docs/baseline/prd-v1.8.md','--domains','specs/001-project-delivery-platform/domains','--output','docs/traceability/requirement-matrix.md','--check'],
        ['scripts/generate_phase2_contract_map.py','--prd','docs/baseline/prd-v1.8.md','--check'],
        ['scripts/generate_sds_revision_016_schema.py','--check'],
    ]
    rows=[]
    env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
    for command in commands:
        result=subprocess.run([sys.executable,*command],cwd=ROOT,env=env,text=True,capture_output=True,timeout=120)
        output=result.stdout+result.stderr
        match=re.search(r'Ran (\d+) tests?',output)
        row={'command':'python '+ ' '.join(command),'exitCode':result.returncode,'passed':result.returncode==0,'testCount':int(match.group(1)) if match else None,'output':output}
        rows.append(row)
        print(f"[{ 'PASS' if row['passed'] else 'FAIL' }] {row['command']}",flush=True)
        if not row['passed']:print(output,flush=True)
    # The gate's real status determines whether a formal failure is expected.
    # Never turn an unexpected content failure into success under an approval label.
    formal=[]
    for phase in (1,2,3):
        path=ROOT/f'docs/engineering/gates/phase-{phase}/gate-status.md'
        approved=re.search(r'^> 审查状态：`APPROVED`',path.read_text(),re.M) is not None
        result=subprocess.run([sys.executable,f'scripts/validate_sds_phase{phase}.py'],cwd=ROOT,env=env,text=True,capture_output=True,timeout=120)
        output=result.stdout+result.stderr
        failures=[line for line in output.splitlines() if line.startswith('[FAIL]')]
        refused_only_for_approval=result.returncode==1 and bool(failures) and all('APPROVAL_REQUIRED' in line or 'current independent review evidence is required' in line for line in failures)
        expected=result.returncode==0 if approved else refused_only_for_approval
        formal.append({'phase':phase,'exitCode':result.returncode,'actualResult':'GO' if result.returncode==0 else 'BLOCKED','expectedSafetyBoundaryVerified':expected,'output':output})
        print(f"FORMAL phase-{phase}: {formal[-1]['actualResult']}; expected boundary={expected}",flush=True)
    diff=subprocess.run(['git','diff','--check'],cwd=ROOT,text=True,capture_output=True)
    passed=all(r['passed'] for r in rows) and all(r['expectedSafetyBoundaryVerified'] for r in formal) and diff.returncode==0
    report={'scope':'TECHNICAL_DESIGN_VALIDATION_NOT_INDEPENDENT_APPROVAL',
        'status':'PASS' if passed else 'FAIL','pythonVersion':sys.version.split()[0],
        'inputCommit':os.environ.get('INPUT_SHA') or subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'actionsRunId':os.environ.get('GITHUB_RUN_ID'),
        'prdSha256':hashlib.sha256((ROOT/'docs/baseline/prd-v1.8.md').read_bytes()).hexdigest(),
        'testCount':sum(r['testCount'] or 0 for r in rows),'checks':rows,'formalGates':formal,'diffCheck':diff.returncode}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(f"SUMMARY: {report['status']}; {report['testCount']} unit/mutation tests; formal gates kept separate")
    return 0 if passed else 1
if __name__=='__main__':raise SystemExit(main())
