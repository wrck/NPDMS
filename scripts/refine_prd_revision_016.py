#!/usr/bin/env python3
"""One-shot documentation refinement; no application, Task or migration writes."""
from pathlib import Path
import ast
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys

os.environ['PYTHONDONTWRITEBYTECODE'] = '1'
ROOT = Path.cwd()
PRD = Path('docs/baseline/prd-v1.8.md')
REPORT = Path('docs/engineering/gates/phase-1/prd-revision-016-alignment.md')
OLD_BLOB = 'ebd8f115566e0eacb20294f1b261a37f8ff81d12'
BASE = '641538d67d2a6b6705b1b9c04f245c18adaaffe8'

def read(p): return Path(p).read_text(encoding='utf-8-sig')
def write(p, text): Path(p).write_text(text.rstrip()+'\n', encoding='utf-8')
def replace(text, old, new):
    assert old in text, 'Missing expected source: '+old[:100]
    return text.replace(old,new)
def blob(raw): return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec)
    sys.modules[name]=module
    spec.loader.exec_module(module)
    return module

def patch_prd():
    assert blob(PRD.read_bytes()) == OLD_BLOB
    text=read(PRD)
    pairs=[
      ('模板包含S5的项目缺少当前有效终验','模板包含S5的项目当前有效终验缺失'),
      ('平台仍可按冻结规则完成审批，保留该阶段且不得补造S6实例','平台保存真实current_stage和closed_from_stage，按冻结规则完成审批且不生成S6实例'),
      ('可建立有效手工Log或使用已批准业务命令快照采集；不得因缺少EXE-02或SCH-03而阻断。','平台创建有效手工Log记录或使用已批准业务命令快照创建采集任务，保存设备范围、来源和操作人；不要求补造EXE-02或启用SCH-03。'),
      ('拒绝下发，不能以手工输入或售前模板为由绕过授权。','平台阻止下发，不创建外部采集任务，记录缺失的批准依据或权限条件；不能以手工输入或售前模板为由绕过授权。'),
      ('用户持续被拒绝且现有会话失效，来源事实分别保留；只有对应Owner更正且全部禁用依据解除后才可重新认证。','平台拒绝该用户请求并记录拒绝原因，撤销现有会话和未使用任务授权，分别保存两来源版本；只有对应Owner更正且全部禁用依据解除后才可重新认证。'),
      ('系统只存在S0/S4阶段，不要求安装完成事实、终验或S6，按CLO-01/02从S4闭环而不补造任务。','平台仅生成S0/S4阶段实例，保存EXE-03/04真实完成证据和closed_from_stage=S4；不要求安装完成事实、终验或S6，按CLO-01/02闭环且不生成虚假任务。'),
      ('PM-10不得直接关闭；平台应引导使用CLO-01/02的不予跟踪闭环路径','PM-10保持项目ACTIVE并返回CLO-01/02不予跟踪闭环入口与资格说明，记录本次拒绝，不直接写入终态'),
      ('仅`EXCEPTION_CLOSED`可以恢复为ACTIVE；`NORMAL_CLOSED`和`NO_TRACKING_CLOSED`均被拒绝','仅`EXCEPTION_CLOSED`可以更新为ACTIVE并记录重开依据；`NORMAL_CLOSED`和`NO_TRACKING_CLOSED`保持原状态，重开请求被拒绝且留痕'),
    ]
    for old,new in pairs: text=replace(text,old,new)
    write(PRD,text)
    Path('需求/PRD-项目实施交付管理平台.md').write_bytes(PRD.read_bytes())
    return blob(PRD.read_bytes())

def patch_generators():
    p=Path('scripts/generate_requirement_traceability.py'); text=read(p)
    old='    return {"revision": number.zfill(3), "changeId": change_id, "gitBlob": digest, "path": prd.as_posix()}'
    new='''    repository_root = Path(__file__).resolve().parents[1]
    try:
        source_path = prd.resolve().relative_to(repository_root).as_posix()
    except ValueError:
        source_path = prd.resolve().as_posix()
    return {"revision": number.zfill(3), "changeId": change_id, "gitBlob": digest, "path": source_path}'''
    text=replace(text,old,new)
    text=replace(text,'CROSS_CONTEXT_REQUIREMENT_IDS = {','CROSS_CONTEXT_REQUIREMENT_IDS = {\n    "PM-06", "PM-10", "PLT-02", "ACC-03",')
    write(p,text)
    p=Path('scripts/generate_phase2_contract_map.py'); text=read(p)
    pairs=[
      ('既有PROJ模板/阶段/快照承载；图与Stage绑定物理差量见09分册及受影响Feature，未重验证不得声称NO_PHYSICAL_DELTA','既有proj_project_template_revision、proj_project_template_task_definition、proj_project_stage_snapshot；图与Stage绑定的复用或新增物理差量见09分册及受影响Feature，未重验证不得声称NO_PHYSICAL_DELTA'),
      ('V2甘特与受控依赖；','V2只增加甘特展示和受控依赖新增、更新、删除，不建立第二套任务事实；'),
      ('P3暂存不推进；有效提交才P4；设备采集绑定清单版本/采集项/设备，技术成功不替代CUT结果','V1 P3暂存不推进；有效提交才P4；设备采集绑定清单版本/采集项/设备，技术成功不替代CUT结果；V2增加授权清单导出及受控流程跳转配置优化，不绕过必填、版本和权限门禁'),
      ('V2 OA材料/外采审批结果与SUB待办链接区别处理','V2创建OA领料/外采流程引用并接收外部结果，SUB平台审批仅推送待办链接；两类业务Owner及失败补偿分开处理'),
      ('V2预检与执行独立任务各自授权，临时密码重新输入','V2在线巡检复用同一凭证、任务和采集执行引擎；预检与执行独立任务各自授权，临时密码重新输入'),
      ('V2预检/正式执行各自授权，命令1..30秒，超时后续按冻结规则','V2预检/正式执行各自授权，命令1..30秒；超时终止当前命令，后续命令是否继续由任务冻结的已发布规则决定并留痕'),
    ]
    for old,new in pairs: text=replace(text,old,new)
    write(p,text)

def patch_contract_table():
    p=Path('docs/design/02d-cross-context-contracts.md'); text=read(p)
    rows=[
      ('ProjectStageAdvanceCommand','PM-03','Project','Project','Project/tree/graph/scope版本；当前完成及准出→唯一冻结转移→目标准入→原子推进。目标由服务端解析，不按S编号加一。'),
      ('ProjectScopeAppendApplied','PM-06、COM-01、ACC-03','Commerce / Project orchestration','Project / Acceptance & Closure','COM拥有数量和范围水位；同一projectId的新精确范围、任务、绑定及门禁同事务生效。旧A验收不自动覆盖A+B。'),
      ('AcceptanceReportQualificationFact','ACC-03、CLO-01','Acceptance & Closure','Project / Closure','reportVersion、reportEvidenceValid、acceptancePassed、scopeVersion、精确范围和文件版本分别保存；字段完整不等于验收通过。'),
      ('ProjectTypedClosureCommand','CLO-01、CLO-02、PM-10','Acceptance & Closure / Project governance','Project','closureType、closedFromStage、最新Gate和BPM实际定义及项目版本；CLO-02/PM-10为唯一业务入口，事件不代替终态命令。'),
      ('ApprovedImplementationCommandSnapshot','EXE-03、INT-12','Implementation Execution','Device Access & Collection','仅EXE-03受信批准记录/版本/哈希/设备/主体范围；DAC重验。独立中心、CUT、INS仍需要已发布命令模板。'),
      ('CutoverChecklistCollectionBinding','CUT-03、INT-12','Cutover','Device Access & Collection / Cutover','taskId、checklistVersion、itemId、deviceId、CollectionTask及resultVersion精确绑定；技术回调只提供证据，CUT判定业务通过。'),
      ('AuthenticatedFileCallback','PLT-02、INT-12','Authenticated integration caller','Platform file service','来源身份、契约验签、任务/对象权限、大小/类型/哈希和幂等同时校验；幂等键不能替代认证。'),
    ]
    old=re.search(r'(?ms)^\| 契约 \| 必要输入/输出 \| 成功与禁止边界 \|\n.*?(?=\n\n|\Z)',text)
    assert old, 'Expected supplementary contract table missing'
    text=text[:old.start()]+'修订016契约已合并至本文件上方唯一契约表，分别列明Requirement、Producer、Consumer和语义；详细字段与事务见05、08、12、13分册。'+text[old.end():]
    anchor=text.index('\n契约只传稳定标识')
    end=text.rfind('\n|',0,anchor)
    end=text.index('\n',end+1)
    text=text[:end]+''.join('\n| '+' | '.join(row)+' |' for row in rows)+text[end:]
    write(p,text)

def patch_tests():
    p=Path('scripts/tests/test_generate_requirement_traceability.py'); text=read(p)
    text=replace(text,'import json\n','import json\nimport hashlib\nimport re\n')
    start=text.index('    def test_current_prd_rebaseline_status_is_generator_owned')
    end=text.index('    def test_customer_and_asset_feature_contracts',start)
    body='''    def test_current_prd_rebaseline_status_is_generator_owned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            result = self.run_generator(output, check=False)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            content = output.read_text(encoding="utf-8")
            data = json.loads(output.with_name("requirement-version-coverage.json").read_text())
            raw = PRD.read_bytes()
            revisions = re.findall(r"(?m)^\\|\\s*V1\\.8修订(\\d+)\\s*\\|[^\\n]*?`(CHG-PRD-\\d{4}-\\d{2}-\\d{2}-\\d+)`", raw.decode("utf-8-sig"))
            revision, change = max(revisions, key=lambda item: int(item[0]))
            expected_blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\\0" + raw).hexdigest()
            self.assertEqual(revision.zfill(3), data["baselineIdentity"]["revision"])
            self.assertEqual(change, data["baselineIdentity"]["changeId"])
            self.assertEqual(expected_blob, data["baselineIdentity"]["gitBlob"])
            self.assertEqual("docs/baseline/prd-v1.8.md", data["baselineIdentity"]["path"])
            self.assertIn(change, content)
            self.assertIn(expected_blob, content)
            self.assertNotIn("当前规格阻断：无", content)
            for phase, values in data["engineeringGates"].items():
                source = (REPOSITORY_ROOT / values["source"]).read_text()
                match = re.search(r"(?m)^>\\s*当前结论：`([^`]+)`", source)
                self.assertIsNotNone(match, phase)
                self.assertEqual(match.group(1), values["当前结论"])

'''
    text=text[:start]+body+text[end:]
    start=text.index('    def test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks')
    end=text.index('    def test_coverage_json_contains_all_111_unique_slices',start)
    body='''    def test_requirement_slice_statuses_are_derived_from_feature_coverage_and_tasks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            result = self.run_generator(output, check=False)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            data = json.loads(output.with_name("requirement-version-coverage.json").read_text())
            rows = {row["sliceKey"]: row for row in data["slices"]}
            for key in ("PM-01@V1", "PM-03@V1", "PM-08@V1", "PM-11@V1", "PM-02@V1", "PM-10@V1", "PLT-02@V1", "COM-01@V1"):
                self.assertTrue(any(feature["revalidationRequired"] for feature in rows[key]["features"]), key)
                self.assertEqual("REVALIDATION_REQUIRED", rows[key]["implementationStatus"], key)
            for key in ("PM-04@V1", "PM-07@V1", "PRE-04@V1", "SOL-01@V2", "CUS-03@V1"):
                self.assertEqual("IMPLEMENTATION_PARTIAL", rows[key]["implementationStatus"], key)
            for key in ("PRE-01@V1", "PRE-02@V1"):
                self.assertEqual("IMPLEMENTATION_COMPLETE", rows[key]["implementationStatus"], key)
            self.assertEqual("NOT_STARTED", rows["PM-08@V2"]["implementationStatus"])
            self.assertTrue(any(feature["taskStatus"] == "COMPLETE" for feature in rows["PM-02@V1"]["features"]))

    def test_absolute_and_relative_prd_paths_produce_same_projection(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "requirement-matrix.md"
            result = self.run_generator(output, check=False)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            absolute_md = output.read_bytes()
            absolute_json = output.with_name("requirement-version-coverage.json").read_bytes()
            command = [sys.executable, str(SCRIPT), "--prd", "docs/baseline/prd-v1.8.md", "--domains", str(DOMAINS), "--output", str(output), "--coverage-output", str(output.with_name("requirement-version-coverage.json")), "--feature-index", str(FEATURE_INDEX)]
            result = subprocess.run(command, cwd=REPOSITORY_ROOT, text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(absolute_md, output.read_bytes())
            self.assertEqual(absolute_json, output.with_name("requirement-version-coverage.json").read_bytes())

'''
    text=text[:start]+body+text[end:]
    ast.parse(text)
    write(p,text)

def run(args, required=True):
    result=subprocess.run(args,text=True,capture_output=True,env=os.environ)
    output=result.stdout+result.stderr
    print('>>> '+' '.join(args),flush=True)
    print(output[-10000:],flush=True)
    row={'command':' '.join(args),'exitCode':result.returncode,'output':output,'required':required}
    if required and result.returncode: raise RuntimeError('Required check failed: '+row['command'])
    return row

def verify_write_scope():
    paths=subprocess.check_output(['git','diff','--name-only','-z']).decode().split('\0')
    paths+=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z']).decode().split('\0')
    allowed_scripts={'scripts/refine_prd_revision_016.py','scripts/close_prd_revision_016.py','scripts/generate_requirement_traceability.py','scripts/generate_phase2_contract_map.py','scripts/tests/test_generate_requirement_traceability.py'}
    for path in filter(None,paths):
        assert path.startswith(('docs/','specs/','scripts/prd_revision_016_transport/')) or path in allowed_scripts or path=='需求/PRD-项目实施交付管理平台.md', 'Forbidden write: '+path
    assert not subprocess.check_output(['git','diff',BASE,'--name-only','--','tasks','apps','backend','frontend']), 'Authoritative task or application files changed'

def main():
    new_blob=patch_prd()
    patch_generators(); patch_contract_table(); patch_tests()
    for path in [Path('docs/engineering/00-engineering-chain.md'), *Path('docs/engineering/gates').glob('phase-*/gate-status.md')]:
        write(path,read(path).replace(OLD_BLOB,new_blob))
    # Keep initial failed checks as historical evidence rather than overwrite them.
    text=read(REPORT)
    text=text.replace('## 4. 实际执行的检查','## 4. 首轮实际检查（历史；当前结果见末节）')
    text=text.replace('> 输入提交：', '> 当前复核PRD Blob：`'+new_blob+'`；SHA-256：`'+hashlib.sha256(PRD.read_bytes()).hexdigest()+'`。\n> 输入提交：',1)
    write(REPORT,text)
    commands=[
      [sys.executable,'scripts/generate_prd_domain_requirements.py','--prd',str(PRD),'--output','specs/001-project-delivery-platform/domains'],
      [sys.executable,'scripts/generate_requirement_traceability.py','--prd',str(PRD),'--domains','specs/001-project-delivery-platform/domains','--output','docs/traceability/requirement-matrix.md'],
      [sys.executable,'scripts/generate_phase2_contract_map.py','--prd',str(PRD)],
    ]
    for command in commands: run(command)
    checks=[]
    for command in [
      [sys.executable,'-m','unittest','discover','-s','scripts/tests','-p','test_generate_requirement_traceability.py','-v'],
      [sys.executable,'-m','unittest','discover','-s','scripts/tests','-p','test_prd_revision_016_alignment.py','-v'],
      [sys.executable,'scripts/validate_prd_revision_016_alignment.py'],
      [sys.executable,'scripts/validate_prd_semantics.py','--prd',str(PRD)],
      [sys.executable,'scripts/validate_prd_domain_generation.py','--prd',str(PRD),'--domains','specs/001-project-delivery-platform/domains'],
      [sys.executable,'scripts/generate_requirement_traceability.py','--prd',str(PRD),'--domains','specs/001-project-delivery-platform/domains','--output','docs/traceability/requirement-matrix.md','--check'],
      [sys.executable,'scripts/generate_phase2_contract_map.py','--prd',str(PRD),'--check'],
    ]: checks.append(run(command))
    checks.append(run([sys.executable,'scripts/validate_prd_baseline.py','--prd',str(PRD),'--report',str(REPORT),'--expected-version','V1.8','--expected-status','正式基线'],False))
    for phase in (1,2,3): checks.append(run([sys.executable,f'scripts/validate_sds_phase{phase}.py'],False))
    # Machine lint is not permission to invent independent reviews or runtime evidence.
    lines=['','## 5. 最终文档复核（修订016）','',
      '本节是当前结果；第4节保留首轮失败证据。修正验收条款的可观察输出、跨域契约表的唯一Producer/Consumer结构、生成器绝对/相对路径一致性及原有V2业务义务。未把Gate改为APPROVED/GO，也未执行业务代码、数据库、浏览器、真实集成或部署。',
      '', '| 命令 | 退出码 | 当前结果 |','|---|---|---|']
    for row in checks: lines.append('| `'+row['command']+'` | '+str(row['exitCode'])+' | '+('PASS' if row['exitCode']==0 else 'NOT_PASSED')+' |')
    for row in checks:
        if row['exitCode']:
            lines += ['', '### 当前未通过：`'+row['command']+'`','', '```text',row['output'][-14000:].strip(),'```']
    lines += ['', '### 关闭边界', '',
      '16项PRD整改及对应SDS规则、13领域派生与追溯来源一致性已按本节检查复核。Phase 1/2/3仍为REVALIDATION_REQUIRED / BLOCKED_BY_PRD_DELTA；旧校验器中要求修订007批准的断言不构成本轮放行证据。剩余物理契约、迁移、独立设计复审及运行验收须按当前Gate逐项提交证据。',
      '8个受影响Feature保留Spec差量标记；原Feature Task及Delivery Unit的历史字节保持不变。不把旧FULL+Done投影为新基线COMPLETE。',
      '一次性修订脚本及9个传输片段在本轮成功提交中删除；保留正式生成器、26项静态检查及回归测试。临时远端执行工作流由本轮结束前移除。']
    write(REPORT,read(REPORT)+'\n'+'\n'.join(lines))
    run(['git','diff','--check'])
    verify_write_scope()
    for path in Path('scripts/prd_revision_016_transport').glob('part-*.txt'): path.unlink()
    Path('scripts/close_prd_revision_016.py').unlink()
    Path(__file__).unlink()
    verify_write_scope()
    print('FINAL_PRD_BLOB='+new_blob,flush=True)
    print('DOCUMENT_REFINEMENT_COMPLETE_RUNTIME_NOT_RUN',flush=True)

if __name__=='__main__': main()
