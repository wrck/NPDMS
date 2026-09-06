#!/usr/bin/env python3
"""One-shot, fail-closed documentation alignment; no application or migration writes."""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

BASE = '641538d67d2a6b6705b1b9c04f245c18adaaffe8'
OLD_BLOB = '37c709bf49ce3813042b650eea44050537cec4e0'
REV = 'CHG-PRD-2026-09-06-016'
PRD = Path('docs/baseline/prd-v1.8.md')
MIRROR = Path('需求/PRD-项目实施交付管理平台.md')
DOMAINS = Path('specs/001-project-delivery-platform/domains')
REPORT = Path('docs/engineering/gates/phase-1/prd-revision-016-alignment.md')
CHANGES: dict[str, str] = {}
OPERATIONS: list[dict[str, object]] = []


def read(path: str | Path) -> str:
    p = Path(path)
    return CHANGES[p.as_posix()] if p.as_posix() in CHANGES else p.read_text(encoding='utf-8-sig')


def put(path: str | Path, text: str) -> None:
    CHANGES[Path(path).as_posix()] = text.rstrip() + '\n'


def replace(text: str, old: str, new: str, *, minimum: int = 1, label: str = '') -> str:
    count = text.count(old)
    if count < minimum:
        raise RuntimeError(f'Unmatched source ({label}): {old[:140]!r}; found {count}, need {minimum}')
    if count:
        OPERATIONS.append({'label': label, 'old': old, 'occurrences': count})
    return text.replace(old, new)


def req_bounds(text: str, req: str) -> tuple[int, int]:
    match = re.search(r'(?m)^####\s+\d+(?:\.\d+)*\s+' + re.escape(req) + r'\s+.+$', text)
    if not match:
        raise RuntimeError(f'Requirement heading not found: {req}')
    nxt = re.search(r'(?m)^#{1,4}\s+', text[match.end():])
    return match.start(), match.end() + nxt.start() if nxt else len(text)


def edit_req(text: str, req: str, pairs=(), rule: str = '', acceptance: str = '') -> str:
    a, b = req_bounds(text, req)
    block = text[a:b]
    for old, new in pairs:
        block = replace(block, old, new, label=req)
    if rule:
        block = replace(block, '**用户故事：**', rule.strip() + '\n\n**用户故事：**', label=req + ':rule')
    if acceptance:
        block = replace(block, '**涉及数据字段：**', acceptance.strip() + '\n\n**涉及数据字段：**', label=req + ':acceptance')
    return text[:a] + block + text[b:]


def change_prd() -> str:
    text = read(PRD)
    raw = PRD.read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == OLD_BLOB
    table_end = text.index('\n---\n', text.index('## 修订记录'))
    row = ('| V1.8修订016 | 2026-09-06 | 需求方修订授权/文档整改 | `' + REV +
           '`：依据本轮全面审查后的“让PRD正文、追溯投影与受影响设计共同闭合”修订指令，统一售前模板、阶段图推进、三类退出、验收结论和范围覆盖、CUT暂存/提交、V1/V2验收隔离、实施命令来源、HR与目录字段Owner、回调来源验证、巡检两任务认证、计划路径、P3采集入口、命令超时及归档权限；同步正式SDS、来源副本和生成器。保留100项Requirement、111个切片、优先级及13领域Owner；不宣称独立设计复审、实现或发布通过。 |\n')
    text = text[:table_end].rstrip() + '\n' + row + text[table_end:]

    # R01: one actual presales graph; no fabricated installation stage/task.
    text = edit_req(text, 'PM-03', [('售前测试模板配置`S0→S4→S6`转移关系', '售前测试模板配置`S0→S4`转移关系')],
        rule='**售前测试边界（修订016）：** 售前模板仅实例化S0/S4，S4仅包含EXE-03/04及其真实交付要求；不实例化EXE-01/02、S5或S6。满足冻结完成规则后从S4使用CLO-01/02正常闭环，保留`closed_from_stage=S4`。该裁剪不取消设备、项目、凭证、命令和文件授权。',
        acceptance='- **WHEN** 创建售前测试项目并完成EXE-03/04的适用要求\n- **THEN** 系统只存在S0/S4阶段，不要求安装完成事实、终验或S6，按CLO-01/02从S4闭环而不补造任务。')
    text = edit_req(text, 'EXE-03', [
        ('只有EXE-02安装完成且属于当前项目配置范围的设备，才能发起在线采集或建立手动Log记录', '只有属于当前项目配置范围且满足冻结模板适用前置的设备，才能发起在线采集或建立手动Log记录；模板要求EXE-02时必须已安装完成，售前测试模板未配置EXE-02时不要求也不补造安装完成事实'),
        ('（EXE-02硬件安装已完成）', '（已满足冻结模板的适用前置；售前测试不要求EXE-02）'),
        ('前置依赖：EXE-02硬件安装记录（配置调试准入）', '前置依赖：项目冻结模板的配置调试准入；仅配置EXE-02的模板要求安装完成')],
        rule='**命令与手工来源（修订016）：** V1已批准手工命令/脚本由IMP在EXE-03业务上下文内校验设备适用性、操作权限及明确批准依据，冻结命令内容、哈希、来源业务记录/版本、批准依据和操作者，按INT-12的`APPROVED_BUSINESS_SNAPSHOT`来源下发；这不是任意命令入口，也不要求启用SCH-03。直接选择手动上传是独立合法来源；只有由在线失败转人工的操作必须关联原失败任务，不要求为纯手动来源伪造失败任务。',
        acceptance='- **WHEN** 售前测试设备在授权配置范围内且模板只配置EXE-03/04\n- **THEN** 可建立有效手工Log或使用已批准业务命令快照采集；不得因缺少EXE-02或SCH-03而阻断。\n- **WHEN** 命令缺少IMP校验的批准依据、设备权限或不可变来源快照\n- **THEN** 拒绝下发，不能以手工输入或售前模板为由绕过授权。')

    # R02: owner fact changes are not hard-coded stage writes.
    text = edit_req(text, 'PLN-04', [
        ('审批通过后项目进入S3方案阶段', '首次计划批准后由PROJ按冻结阶段图校验是否推进（通常进入S3）'),
        ('项目才可进入S3', '仅在项目仍处于该计划任务对应的当前阶段且全部完成/准出条件成立时，才请求PROJ解析并校验后置阶段'),
        ('项目仍停留S2', '项目保持审批处理前的真实当前阶段'),
        ('项目阶段推进至S3方案阶段', 'PROJ重新校验当前完成规则、准出及唯一后置阶段准入后原子推进；目标不固定为S3'),
        ('S3实施方案编审（审批通过后进入方案阶段）', '模板实际配置的后置阶段（通常S3；由PROJ统一推进）')],
        rule='**首次推进与后续换版（修订016）：** SOL只形成计划批准及换版事实，不写Project.current_stage。后续工期/计划调整审批仅替换计划基线并重算受影响完成规则；项目已进入后续阶段时不自动退回S2/S3，也不再次执行首次推进。正式阶段回退必须走另行受控的项目规则。钉钉仅作为平台审批页面入口和通知通道，其送达回调不得携带平台审批终态。',
        acceptance='- **WHEN** 不含S3的模板完成适用计划审批，或S4中的项目批准调整计划\n- **THEN** 前者只由PROJ解析真实后置目标；后者只换计划版本并重算门禁，均不得创建S3或隐式回退当前阶段。')
    text = edit_req(text, 'SCH-05', [
        ('方案变更为"已通过"并进入S4', '方案变更为"已通过"并请求PROJ按冻结阶段图校验推进'),
        ('进入"可下载"并触发S4准入', '进入"可下载"并提供批准事实，由PROJ校验冻结图的后置阶段准入（通常S4）')],
        rule='**阶段Owner（修订016）：** 最终审批通过只使方案版本成为批准事实；PROJ必须重验当前阶段CompletionRule/准出、唯一解析StageTransitionDefinition并校验目标准入，不能由SCH、BPM回调或较新方案换版直接写入固定S4。项目已在后续阶段时仅重算影响，不隐式回退。')
    text = edit_req(text, 'EXE-06', rule='**推进与完成失效（修订016）：** IMP按当前范围形成割接覆盖和S4完成判定事实；只有PROJ可沿冻结StageTransitionDefinition原子推进。正文中“准入S5”仅描述包含S5的标准模板；目标未配置S5时不生成S5，不把固定S5当作成功结果。已进入后续阶段后上游覆盖失效，只失效当前门禁并阻止依赖它的下一动作，不覆盖历史快照、不隐式移动current_stage；正式回退另行受控。',
        acceptance='- **WHEN** S4当前范围全部得到有效成功覆盖且模板后置不含S5\n- **THEN** 只向PROJ提供完成事实，由其解析实际后置或允许的闭环入口，不创建或进入S5。')
    # Correct all duplicate data-flow summaries without changing historical revisions.
    head, body = text.split('## 目录', 1)
    body = body.replace('审批通过后项目阶段自动推进至S3', '审批通过后由PROJ校验并推进到模板唯一后置阶段（通常S3）')
    body = body.replace('审批通过后项目进入S3实施方案编审', '审批通过后由PROJ校验模板唯一后置阶段（通常S3）')
    text = head + '## 目录' + body

    # R03: qualification of exceptional exits is independent of normal delivery.
    a = text.index('##### 13.1.2.5 ')
    b = text.index('##### 13.1.2.6 ', a)
    block = text[a:b]
    block = replace(block, '冻结模板允许的最后一个真实阶段达到准出条件\n  ↓\n选择项目退出类型', '活动项目选择退出意图并校验主体权限\n  ↓\n选择项目退出类型', label='R03')
    block = replace(block, '  ├─ NORMAL 正常交付闭环\n', '  ├─ NORMAL 正常交付闭环\n  │   → 最后真实阶段满足冻结模板准出条件\n', label='R03')
    block = replace(block, '最后真实阶段完成 → 选择退出类型', '活动项目选择退出类型 → 按类型校验资格（仅NORMAL要求最后真实阶段完成）', label='R03')
    text = text[:a] + block + text[b:]
    text = edit_req(text, 'CLO-01', acceptance='- **WHEN** 尚未完成正常交付的代理商自服活动项目申请NO_TRACKING，且权限、原因、依据、后代及在途处置满足对应规则\n- **THEN** 允许生成该类型通过快照，不先要求最后阶段正常准出；EXCEPTION继续独立使用PM-10。')

    # R04/R12: report evidence and acceptance outcome are different facts.
    text = edit_req(text, 'ACC-03', [
        ('附件上传成功且字段完整后才形成有效版本', '附件上传成功且字段完整后才形成有效证据版本；只有明确的“通过”结论且覆盖当前适用验收范围，才形成验收通过事实'),
        ('终验时间、结论、验收人和附件完整后形成当前有效终验版本', '终验时间、结论、验收人和附件完整后形成当前有效报告证据；仅结论明确通过且当前范围完整覆盖时形成终验通过事实'),
        ('非直签项目提交字段和附件完整的终验', '非直签项目提交字段和附件完整、结论明确通过且覆盖当前范围的终验')],
        rule='**验收结论与范围覆盖（修订016）：** “有效终验”作为S5/CLO门禁用语时，必须同时满足报告/文件有效、结论明确通过、签约适用顺序及当前验收范围完整覆盖。不通过、需整改、结论未知或仅字段完整的报告均可保存为证据，但不通过门禁；当前版本撤销、换版或范围变化均须重算。\n\n同一项目、适用报告类型仍只有一个当前报告版本。报告保存`projectScopeVersion`、精确范围明细/数量及来源报告/文件版本。V2新增范围B后，原A的报告保留原范围下通过事实，但不能单独覆盖A+B。提交新总体验收版本时可明确引用A的既有通过证据和B的补充证据，并由验收人对当前适用总范围给出明确通过结论；只上传B报告不得自动推导总体通过。草稿和上传失败不切换当前报告指针，新版本不得覆盖旧正文或旧结论；本规则不新建第二个当前报告，也不引入条件通过或满意度例外放行。',
        acceptance='- **WHEN** 报告字段和附件完整但验收结论为不通过、需整改或未知\n- **THEN** 保存证据及结论，验收通过事实为否，S5和NORMAL闭环门禁不满足。\n- **WHEN** 当前范围从A追加为A+B，而当前报告仅覆盖A或仅上传B的补充报告\n- **THEN** 原A证据保持不可变，总范围门禁显示缺口；只有明确引用A/B证据并对当前范围通过的新总体验收版本才能解除缺口。')
    text = edit_req(text, 'PM-06', rule='**补充验收承载（修订016）：** 需要补充验收时按ACC-03形成当前范围的总体验收新版本，保留旧范围报告和结论；不得建立第二个“当前终验”或把旧报告范围从A改成A+B。追加不减少既有适用验收义务，明确不适用的新范围须保留冻结规则与本次审批依据。范围切换与依赖它的当前门禁失效必须原子完成；旧历史快照不改写。')
    text = edit_req(text, 'ACC-04', [
        ('来源记录未批准/未确认、来源版本失效、文件哈希校验失败或用户无下载权限', '来源记录未达到对应业务要求的批准/确认结论、来源版本失效或文件哈希校验失败'),
        ('对应交付件保持未归档/失效或不可下载状态，不计入CLO-01齐套结果', '对应交付件保持未归档/失效状态，不计入CLO-01齐套结果')], rule='**有效性与访问分离（修订016）：** 报告证据可归档不等于验收通过。ACC-04向门禁提供来源结论、范围及版本，失败报告不得满足“通过终验”要求；当前用户无下载权限只影响访问，不改变其他合法主体可验证的客观归档/齐套事实。')

    # R05.
    text = replace(text, '采集清单保存后通过任务状态流转自动进入P4', '采集清单提交并通过必填、权限和版本校验后通过任务状态流转进入P4', minimum=2, label='R05')
    # R06: split version-specific E2E references, preserve all reference scenarios.
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.startswith('| E2E-17 |'):
            lines[i] = line.replace('；转销关系可追溯', '').replace('PM-01/03/05', 'PM-01/03').replace('PM-11、', 'PM-11（V1）、')
        elif line.startswith('| E2E-19 |') or line.startswith('| E2E-20 |'):
            lines[i] = line.replace('、RPT-02', '').replace('不计入正常交付完成率', '形成可供V2统计区分的类型化终态事实；不执行RPT-02')
    text = '\n'.join(lines) + '\n'
    text = replace(text, '\n所有P0需求至少提供单项验收', '\n| E2E-22 | V2 | 回归E2E-17→PM-05转销 | 借测与正式项目身份分离、逐项引用/派生、失败重试，不回写借测历史 | PM-05@V2、PM-01@V1、EXE-03@V1、EXE-04@V1 |\n| E2E-23 | V2 | 回归E2E-19/20→RPT-02分类统计 | NORMAL/NO_TRACKING/EXCEPTION分项、真实closed_from_stage、同口径下钻和导出；不虚构S5/S6 | RPT-02@V2、CLO-01@V1、CLO-02@V1、PM-10@V1 |\n\n所有P0需求至少提供单项验收', label='R06')

    # R07/R13: EXE-approved snapshots are a scoped exception, P3 is a real V1 entry.
    text = edit_req(text, 'INT-12', [
        ('EXE-03/04实施采集入口和CUT-06割接任务入口', 'EXE-03/04实施采集入口、CUT-03的P3采集项入口和CUT-06割接任务入口'),
        ('EXE-03/04和CUT-06入口', 'EXE-03/04、CUT-03/P3和CUT-06入口'),
        ('从EXE-03/04、CUT-06或独立中心', '从EXE-03/04、CUT-03/P3、CUT-06或独立中心'),
        ('采集任务只能引用已发布模板及其确定版本', '采集任务使用已发布模板及其确定版本，或仅在EXE-03业务入口使用IMP已校验批准依据的不可变业务命令快照'),
        ('任务必须关联业务入口/用途、项目、设备、已发布命令模板及版本和认证方式', '任务必须关联业务入口/用途、项目、设备、命令来源快照和认证方式；命令来源为已发布模板及版本，或仅限EXE-03的已批准业务快照')],
        rule='**受控命令来源和P3入口（修订016）：** `commandSourceType`只区分`PUBLISHED_TEMPLATE`与`APPROVED_BUSINESS_SNAPSHOT`。后者仅允许EXE-03，必须由IMP提供来源记录/版本、批准依据、内容哈希、设备范围及当前权限校验结果，DAC重新校验并冻结；不能由客户端自称已批准，独立中心、CUT和INS仍须使用各自已发布模板。SCH-03未启用不得阻止前述V1路径。CUT-03入口必须同时绑定CutoverTask、P3清单版本、采集项和设备；结果回填原采集项，仍由CUT确认，不自动判定风险通过。',
        acceptance='- **WHEN** EXE-03提供经IMP校验的已批准业务命令快照且各项权限有效\n- **THEN** V1允许按该确定内容创建任务，不依赖SCH-03；独立中心使用同类手工输入则拒绝。\n- **WHEN** CUT-03在P3某设备采集项创建任务并接收回调\n- **THEN** 保存割接任务、清单版本、采集项、设备和结果的精确绑定，技术成功不替代CUT业务判定。')
    # R08: source arrival order cannot decide identity ownership.
    text = edit_req(text, 'INT-05', [
        ('HR是人员工号、姓名、组织、岗位和在离职状态Owner', 'HR是人员工号、姓名、任职部门引用、岗位和在离职状态Owner；任职部门引用须映射SYSTEM稳定部门身份，HR不覆盖LDAP/AD目录组织结构'),
        ('HR同步必要的人员、组织与在离职主数据', 'HR同步必要的人员、任职部门引用与在离职主数据')],
        rule='**身份来源裁决（修订016）：** HR人员事实与LDAP/AD账号/认证及目录组织结构分开保存来源ID和版本，不比较两个系统的版本号或最后到达时间。HR离职或LDAP/AD禁用任一成立即拒绝新请求并撤销会话/未用任务授权；恢复须有对应Owner的有效更正并通过身份映射校验，另一来源的普通启用同步不能消除未解除的禁用依据。同步中断只展示最近成功快照，不允许人工补录冒充HR权威事实。')
    text = edit_req(text, 'INT-09', [
        ('两来源冲突时以最近成功同步版本为准并转人工核对', '两来源冲突时保留各自原值和版本，按字段Owner裁决并转人工核对，不按最后同步时间覆盖'),
        ('LDAP/AD是企业账号、姓名、工号、邮箱、组织、岗位和启停状态的身份Owner', 'LDAP/AD是企业账号不可变标识、登录名、目录邮箱、认证、目录组织结构和账号启停状态的Owner；姓名、工号、岗位、任职部门引用和在离职状态以HR为Owner，目录中的同名属性仅作匹配辅助')],
        rule='**字段级写入与停用优先（修订016）：** SYSTEM承载稳定部门与人员映射，LDAP/AD同步目录层级/部门身份，HR同步人员任职关系，两者不得互写对方事实。身份冲突不授予新权限；HR离职与目录禁用按逻辑“或”阻断，不以迟到的启用事件复活账号。恢复必须消除全部有效阻断依据，再重新计算平台角色和项目授权，不恢复已撤销任务授权。',
        acceptance='- **WHEN** HR离职和LDAP/AD启用事件按任意顺序到达\n- **THEN** 用户持续被拒绝且现有会话失效，来源事实分别保留；只有对应Owner更正且全部禁用依据解除后才可重新认证。')
    # R09.
    text = edit_req(text, 'PLT-02', [('外部回调文件需校验来源签名或幂等键', '外部回调文件必须同时校验来源身份、契约要求的签名、业务对象/任务授权及幂等键；幂等键不得替代来源认证或验签')],
        acceptance='- **WHEN** 外部文件回调具有幂等键但身份、签名或业务对象范围不合法\n- **THEN** 拒绝创建可引用文件版本和业务引用，记录拒绝原因；不能因请求未重复而放行。')
    # R10/R14/R15.
    for req in ('INS-02', 'INS-04'):
        text = edit_req(text, req,
            rule='**预检与执行认证隔离（修订016）：** INS-04预检与INS-02正式执行是两个独立CollectionTask。预检通过只证明设备、端点和认证上下文在有效期内通过预检，不授予正式执行的凭证使用权。临时模式在预检结束后清除密码，正式执行前再次输入临时密码并签发新的任务授权；不得缓存、跨任务复用或自动保存为凭证。已保存凭证模式在正式执行前重新校验当前授权及凭证版本；端点、认证或预检有效性变化须重新预检。',
            acceptance='- **WHEN** 临时认证完成预检后尚未再次输入密码，或凭证授权在预检后已撤销\n- **THEN** 不创建正式执行任务；取得新的合法输入/授权且预检仍适用后才执行，两个任务的授权和失败记录分别保留。')
    text = edit_req(text, 'NFR-02', [('管理员可按已发布模板配置更短或经审批更长的阈值', '管理员只能按已发布模板配置1至30秒的阈值，30秒为当前V2硬上限，不提供超限审批例外')],
        acceptance='- **WHEN** 巡检规则超时配置为0、负数或超过30秒（包括携带审批说明）\n- **THEN** 拒绝发布或执行该配置，不能以批准说明突破当前PRD上限。')
    text = edit_req(text, 'INS-07', [('权限体系（一线工程师归档权限、责任人处理权限）', '权限体系（一线工程师申请归档、服务经理执行归档、责任人处理权限）')],
        rule='**归档动作权限（修订016）：** 一线工程师可申请归档并在授权范围确认问题闭环；最终归档命令由有该项目管理范围的服务经理执行。问题责任人的处理权、闭环确认权和归档执行权不互相推导。',
        acceptance='- **WHEN** 一线工程师提交归档申请而未取得服务经理归档执行权限\n- **THEN** 可保留申请但不得执行最终归档；授权服务经理重验报告、标注、跟踪项和文件条件后归档。')
    # R11.
    text = edit_req(text, 'PLN-01', [
        ('自动推算项目各阶段（S1工前准备～S6项目闭环）的最迟完成时间', '自动推算冻结模板实际计划路径中各参与阶段的最迟完成时间'),
        ('S1～S6阶段标准工期占比由适用项目模板版本提供', '实际计划路径中参与阶段的标准工期占比由适用项目模板版本提供'),
        ('倒推S1~S6各阶段最迟完成时间', '倒推冻结模板实际计划路径中参与阶段的最迟完成时间')],
        rule='**计划路径与裁剪（修订016）：** 计划保存所用StageTransitionDefinition版本、条件输入、唯一解析的计划路径和参与阶段占比；完整工程模板通常覆盖S1～S6，裁剪模板只计算真实配置且参与本计划的阶段，占比须合计100%，不得生成不存在阶段的日期、完成或超期事实。冻结条件无法唯一确定路径时只能保留待处理草稿并说明分支缺口，不能静默选择。后续实际分支或范围变化须形成计划差异和新审批版本；计划不能反写阶段关系或强制阶段推进。',
        acceptance='- **WHEN** 模板包含S2但不包含S5/S6，且实际计划路径唯一、参与阶段占比合计100%\n- **THEN** 只为真实参与阶段推算日期并保存路径版本，不生成S5/S6计划项；路径不唯一或占比错误时不生成生效计划。')
    # R16: system summaries must not overwrite operation-specific Owners.
    text = replace(text, '| V1核心/V2治理 |', '| V1客户同步；V2仅各已编号业务消费，治理工作台为V3 |', label='R16-CRM')
    text = replace(text, '| 5 | HR | 组织与人员主数据 | 入向 | V1 | 授权管理员临时维护并等待来源恢复校准 |', '| 5 | HR | 人员、人事状态和任职部门引用；不覆盖目录组织结构 | 入向 | V1 | 最近成功快照并标记同步异常；不伪造HR权威值 |', label='R16-HR')
    text = replace(text, '| 6 | OA | 领料、外采、授权等代办链接 | 出向 | V2 | 平台内审批继续运行，待办改用站内消息 |', '| 6 | OA | OA拥有领料/外采及AUT-01再次申请审批；SUB平台审批仅向OA发待办链接 | 调用及结果回填 | V2 | OA业务保持待处理或按正式人工依据补录；SUB平台审批继续、通知站内兜底 |', label='R16-OA')
    text = replace(text, '| 13 | LDAP/AD | 统一身份、组织和单点登录 | 双向 | V1 | 按安全策略使用受控本地账号兜底 |', '| 13 | LDAP/AD | 目录账号、认证和目录组织结构；人事属性按HR Owner | 入向/认证 | V1 | 普通用户不降级本地密码，仅受控启用预置默认禁用的应急管理员 |', label='R16-LDAP')
    text = text.replace('NORMAL_CLOSED或NO_TRACKING_CLOSED或NO_TRACKING_CLOSED', 'NORMAL_CLOSED或NO_TRACKING_CLOSED')
    return text

BASELINE_HELPERS = r'''
def baseline_identity(prd: Path) -> dict[str, str]:
    """Derive provenance from the actual PRD input, never from a release constant."""
    raw = prd.read_bytes()
    text = raw.decode("utf-8-sig")
    revisions = re.findall(r"(?m)^\|\s*V1\.8修订(\d+)\s*\|[^\n]*?`(CHG-PRD-\d{4}-\d{2}-\d{2}-\d+)`", text)
    if not revisions:
        raise SystemExit("PRD revision identity is missing")
    number, change_id = max(revisions, key=lambda item: int(item[0]))
    digest = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    return {"revision": number.zfill(3), "changeId": change_id, "gitBlob": digest, "path": prd.as_posix()}


def feature_revalidation_slices(text: str, valid_keys: set[str]) -> set[str]:
    """A Spec-owned review marker suspends current coverage, not historical Task facts."""
    markers = re.findall(r"(?m)^>\s*PRD差量重验证：`([^`]+)`\s*$", text)
    if len(markers) > 1:
        raise SystemExit("duplicate Feature PRD revalidation marker")
    pending = {key.strip() for marker in markers for key in re.split(r"[；;]", marker) if key.strip()}
    if pending - valid_keys:
        raise SystemExit("unknown revalidation slices: " + ", ".join(sorted(pending - valid_keys)))
    return pending


def engineering_gate_states(prd: Path) -> dict[str, dict[str, str]]:
    candidates = [prd.resolve().parent, *prd.resolve().parents]
    root = next((p for p in candidates if (p / "docs/engineering/gates").is_dir()), None)
    result: dict[str, dict[str, str]] = {}
    for phase in ("phase-1", "phase-2", "phase-3"):
        source = Path("docs/engineering/gates") / phase / "gate-status.md"
        value = (root / source).read_text(encoding="utf-8-sig") if root and (root / source).is_file() else ""
        fields = {}
        for label in ("审查状态", "当前结论", "机器门禁", "适用修订"):
            match = re.search(r"(?m)^>\s*" + label + r"：`([^`]+)`", value)
            fields[label] = match.group(1) if match else "UNKNOWN"
        result[phase] = {"source": source.as_posix(), **fields}
    return result

'''


def update_trace_generator() -> None:
    path = Path('scripts/generate_requirement_traceability.py')
    text = read(path)
    text = replace(text, 'import argparse\n', 'import argparse\nimport hashlib\n', label='generator imports')
    text = replace(text, '\ndef read(path: Path) -> str:', '\n' + BASELINE_HELPERS + '\ndef read(path: Path) -> str:', label='generator provenance')
    text = replace(text, '        coverage_match = FEATURE_COVERAGE_LINE.search(read(path))',
                   '        spec_text = read(path)\n        pending = feature_revalidation_slices(spec_text, valid_slice_keys)\n        coverage_match = FEATURE_COVERAGE_LINE.search(spec_text)', label='generator review input')
    text = replace(text, '                "task_status_raw": task["raw_status"] if task else "",',
                   '                "task_status_raw": task["raw_status"] if task else "",\n                "revalidation_required": slice_key in pending,', label='generator review mapping')
    text = replace(text, '        features.append(\n',
                   '        mapped_keys = {item["slice_key"] for item in mappings}\n        if pending - mapped_keys:\n            raise SystemExit(f"revalidation marker must reference this Feature coverage: {feature_id}")\n        features.append(\n', label='generator map validation')
    text = replace(text, '                "featurePath": path.as_posix(),',
                   '                "featurePath": path.as_posix(),\n                "revalidationSlices": sorted(pending),', label='generator Feature metadata')
    text = replace(text, 'def derived_status(mappings: list[dict[str, str]]) -> str:\n',
                   'def derived_status(mappings: list[dict[str, str]]) -> str:\n    if any(item.get("revalidation_required") for item in mappings):\n        return "REVALIDATION_REQUIRED"\n', label='generator no stale complete')
    text = replace(text, '    requirements = extract_requirements(prd_text)',
                   '    identity = baseline_identity(prd)\n    gates = engineering_gate_states(prd)\n    requirements = extract_requirements(prd_text)', label='generator dynamic baseline')
    text = replace(text, '                        "taskStatus": item["task_status"],',
                   '                        "taskStatus": item["task_status"],\n                        "revalidationRequired": item.get("revalidation_required", False),', label='generator JSON review')
    text = replace(text, '        "baseline": "PRD V1.8 / CHG-PRD-2026-08-30-008",',
                   '        "baseline": "PRD V1.8 / " + identity["changeId"],\n        "baselineIdentity": identity,\n        "engineeringGates": gates,', label='generator baseline literal')
    text = replace(text, '            "prd": "需求/PRD-项目实施交付管理平台.md",',
                   '            "prd": identity["path"],', label='generator primary path')
    text = replace(text, '            "Feature Spec FULL + authoritative completed task => IMPLEMENTATION_COMPLETE",',
                   '            "Feature Spec pending PRD revalidation => REVALIDATION_REQUIRED; historical Task status is preserved",\n            "Validated Feature Spec FULL + authoritative completed task => IMPLEMENTATION_COMPLETE",', label='generator rule legend')
    text = replace(text,
        '        "> 源基线：`需求/PRD-项目实施交付管理平台.md` V1.8修订008；领域决策：`docs/design/phase-1-domain-ownership.md`；结构化同源投影：`docs/traceability/requirement-version-coverage.json`。",',
        '        f"> 源基线：`{identity[\'path\']}` V1.8修订{identity[\'revision\']}；CHG：`{identity[\'changeId\']}`；Git Blob：`{identity[\'gitBlob\']}`；结构化同源投影：`docs/traceability/requirement-version-coverage.json`。",', label='generator Markdown provenance')
    text = replace(text,
        '        "- 当前状态：PRD V1.8修订008重新基线化；既有Feature实施结论只关闭其机器可读声明覆盖的切片或子闭环，不因Feature完成自动关闭整个Requirement",',
        '        f"- 当前状态：PRD V1.8修订{identity[\'revision\']}同源生成；历史Task完成事实不自动代表当前修订覆盖；有Spec差量标记时派生REVALIDATION_REQUIRED",', label='generator stale state')
    text = replace(text,
        '        "- 当前规格阻断：无；VS-001～VS-011均已裁决关闭，配置基础前置原则适用，但正文明确V2、V3或延后的内容保持原版本",',
        '        "- 当前工程门禁：" + "；".join(f"{name}={value[\'当前结论\']}（{value[\'审查状态\']}）" for name, value in gates.items()),\n        f"- Feature差量重验证切片：{sum(1 for row in coverage_slices if row[\'implementationStatus\'] == \'REVALIDATION_REQUIRED\')}；该值只从Feature Spec标记派生，不是第二套Task状态",', label='generator actual Gate')
    text = replace(text, '        "| `NOT_STARTED` |', '        "| `REVALIDATION_REQUIRED` | Feature Spec声明当前PRD差量待验证；历史Task状态保留，但不能投影为当前切片已完成 |",\n        "| `NOT_STARTED` |', label='generator table legend')
    # Current Phase 1 design summary, not a second implementation-status source.
    point = '\ndef domain_owners(requirements:'
    updates = '''\n# Current target semantics; physical implementation remains subject to Feature/Gate review.\nEXACT_PHASE1_DESIGN["PM-03"] = ("项目治理", "ProjectTemplateVersion / StageTransitionDefinition / StageWorkBinding / TaskWorkBinding", "冻结图当前准出→唯一后置→目标准入→原子推进", "ProjectTreeScope + Owner权限", "Project阶段编排服务", "Stage/Task/Transition/Deliverable/Binding/CompletionRule版本", "裁剪/分支/目标准入/并发/无虚构阶段")\nEXACT_PHASE1_DESIGN["PM-06"] = ("项目治理", "ContractScopeAppendRequest / ProjectScopeVersion", "同一ACTIVE项目范围追加；不创建期次群组", "Project + COM范围权限", "项目范围追加命令与COM公开接口", "范围版本/差异/逐阶段影响/任务/验收引用", "数量/版本/原子回滚/补充验收")\nEXACT_PHASE1_DESIGN["CLO-01"] = ("验收与项目闭环", "ClosureGateSnapshot", "NORMAL与NO_TRACKING分型校验", "ProjectStageScope", "Closure校验服务", "closureType/closedFromStage/范围/规则/事实版本", "失败终验/不适用/快照失效")\nEXACT_PHASE1_DESIGN["CLO-02"] = ("验收与项目闭环", "ProjectClosure", "CLO-02唯一产生NORMAL_CLOSED/NO_TRACKING_CLOSED", "BPM候选与项目范围", "Closure批准命令", "不可变闭环与实际BPM定义引用", "终态唯一Writer/同事务/重校验")\nEXACT_PHASE1_DESIGN["ACC-03"] = ("验收与项目闭环", "AcceptanceReportRevision", "报告证据有效不等于验收通过", "ProjectStageScope", "验收报告提交与范围校验", "当前报告/结论/精确范围/来源文件版本", "失败报告/范围A+B/直签顺序/无伪造通过")\n'''
    text = replace(text, point, '\n' + updates + point, label='generator target design summaries')
    put(path, text)

STAGE_DESIGN = '''
### 阶段图、范围与终态

ProjectTemplateVersion以已发布StageDefinition/TaskDefinition、StageTransitionDefinition、阶段/任务交付件要求及Stage/Task WorkBinding形成不可变快照。每个执行节点只有一个主绑定，原生分别为STAGE_NATIVE/TASK_NATIVE，组合视图仍由各Owner鉴权。发布校验唯一开始、可达收口、无环、无悬空和分支可唯一判定；只实例化图中真实阶段，不使用虚假的NOT_APPLICABLE阶段。

PROJ拥有唯一阶段推进命令：锁定并重验Project/当前阶段/模板图版本/范围水位，计算当前CompletionRule及准出，唯一解析出向转移，再校验目标准入；全部通过后原子关闭当前节点、激活实际目标并写current_stage、版本、不可变快照、审计及Outbox。目标来自冻结图，不来自S编号加一、SOL/CUT回调或客户端。无目标但当前为允许收口节点时使用CLO入口，不生成新阶段；零/多目标或目标准入失败不推进。进入验收的COM精确范围与ACC绑定须加入同一受控推进事务，COM入口不能成为第二个阶段Writer。

售前模板只有S0/S4，S4只要求EXE-03/04；没有EXE-02、S5和S6实例。其他模板仅在启用EXE-02时要求安装完成。所有场景继续校验设备、项目、命令和文件范围。后续计划/方案换版只改变Owner基线和当前门禁，不隐式移动current_stage；上游条件失效保留过去快照，并阻止依赖该条件的新动作。

终态固定ACTIVE/NORMAL_CLOSED/NO_TRACKING_CLOSED/EXCEPTION_CLOSED。CLO-02是前两类闭环的唯一业务入口，由其同一业务事务调用PROJ受控终态Writer；PM-10仅产生EXCEPTION_CLOSED。终态同时冻结closure_type、closed_from_stage及闭环依据，current_stage保留最后真实阶段。普通事件消费者、BPM回调和ACC-06不能另写终态。NO_TRACKING先检查代理商自服资格、显式意图、依据和在途/后代处置，不先要求正常交付准出；NORMAL才检查模板实际交付条件。PM-10重开只适用于EXCEPTION_CLOSED。
'''
SCOPE_DESIGN = '''
### 同项目范围追加与验收覆盖

PM-06由PROJ编排ContractScopeAppendRequest，引用COM拥有的订单行分配和项目范围版本；ProjectScopeVersion是该COM项目范围水位的稳定引用，不在PROJ维护第二套数量或版本真值。请求冻结projectId、baseScopeVersion、订单行/数量/公司、原因、逐阶段影响和审批实例。同一ACTIVE项目批准追加后，由COM原子检查全项目累计分配量并推进一次范围版本，PROJ生成新增任务、交付件及绑定，ACC绑定当前适用验收范围；任一步失败整个追加动作回滚。闭环项目不得直接追加，不创建期次项目、群组或第二个项目编码。

AcceptanceReportRevision保存reportType、reportVersion、结论、验收人/时间、文件版本、精确scopeVersion及范围明细。reportEvidenceValid只表示文件及字段有效；acceptancePassed必须同时满足明确通过、适用顺序和当前范围完整覆盖。前者不能替代后者。每项目/适用报告类型仍仅一个当前报告指针，草稿不切换当前指针。

A追加B时，A的旧通过报告原范围不变，但不足以满足当前A+B门禁。新总体验收版本须明确引用A已有证据和B补充证据，并对当前适用总范围获得明确通过结论；单独B附件不能推导总体通过。不通过、需整改、未知结论可以归档为证据，不产生通过事实。不新增条件通过、满意度例外或第二个当前终验。

ACC-04对来源证据做索引，来源当前版本/哈希/结论/范围由Owner决定；文件访问权限与客观齐套判定分离。CLO快照保存closureType、closedFromStage、模板图/规则/范围版本和所有适用来源版本，批准提交点必须重新锁定校验；变化导致旧快照失效并重提，不修改旧审批或报告事实。
'''
IDENTITY_DESIGN = '''
### 人事与目录字段Owner

HR拥有人员稳定ID、工号、姓名、任职部门引用、岗位和在离职状态；LDAP/AD拥有目录账号不可变ID、登录名、目录邮箱、认证、账号启停及目录组织结构。SYSTEM提供统一人员映射、稳定部门和公司—部门范围；HR任职引用须映射到同一稳定部门，不能覆盖目录结构。各来源分别保存原值、来源版本和同步水位，禁止跨来源比较版本或最后同步时间裁决。

访问允许条件同时要求人事状态合法、目录账号启用、映射唯一及当前平台授权有效。HR离职或目录禁用任一有效时拒绝新请求并撤销会话/未使用任务授权；另一来源的启用事件不解除阻断。恢复须由相应Owner更正全部有效阻断依据并重新认证/鉴权，不能恢复已撤销任务授权。来源不可用仅保留最近成功快照；人工处理只能修复映射和允许的扩展事实，不能冒充来源人事/目录权威值。
'''
COLLECTION_DESIGN = '''
### 采集入口、命令来源与两任务授权

V1入口为独立中心、EXE-03/04、CUT-03/P3和CUT-06；V2追加INS-02/04。P3任务携带CutoverTask、checklistVersion、itemId、deviceId和选定结果版本，DAC只回填技术状态及证据引用，CUT按当前业务规则判定。暂存P3草稿不推进；提交经必填、授权、评估/清单版本校验后才进入P4。

commandSourceType为PUBLISHED_TEMPLATE或APPROVED_BUSINESS_SNAPSHOT。第二类仅允许EXE-03：IMP先按来源业务记录/版本、批准依据、命令内容哈希、设备和主体权限返回受信快照，DAC再次鉴权并冻结；客户端自报approved不得替代Owner证据。独立中心、CUT和INS仍只选各自已发布模板，不因EXE例外开放任意命令；SCH-03在V2才作为可选脚本治理来源。

INS-04预检与INS-02执行是两个CollectionTask，各自的授权只绑定自身taskId。预检结束使其临时秘密及执行授权失效，正式执行前必须重新输入临时密码；已保存凭证也须重新检查当前用户/设备/协议/模板/有效期并签发新任务授权。预检Fact绑定设备、端点、认证引用/用户名、有效期及对应版本；变化或过期须重新预检。禁止以后台缓存、会话草稿或自动保存凭证跨任务复用临时密码。

巡检规则单命令timeoutSeconds默认30，允许1..30；超过30即拒绝，不存在批准更长的例外。超时标记当前命令失败，后续是否继续由冻结规则决定。INS-07一线工程师可申请归档及按权限确认问题闭环，最终归档由授权服务经理执行；处理权、确认权和归档权分别验证。
'''
FILE_DESIGN = '''
### 外部文件准入与验收判定分离

外部回调必须通过来源身份认证、契约验签、任务/对象/租户范围、大小/类型/哈希及幂等校验。幂等键只是去重事实，不是认证凭据；未经认证或签名不合法，即使幂等键存在且首次出现，也不能创建FileVersion或业务引用。保持修订004扫描默认关闭、真实SKIPPED及开启时失败关闭，不把SKIPPED解释为安全通过。

文件技术可用、报告证据有效、客户验收通过和项目范围覆盖分别判定。失败报告可以作为不可变证据保存；ACC/SOL/CUT等Owner的明确通过及精确范围事实不能由“上传完成/文件可下载/有哈希”替代。当前用户无下载权限不改变客观齐套或验收结果。
'''
PLAN_DESIGN = '''
### 计划路径与领域事实事件

ConstructionPlanRevision引用冻结阶段图版本、条件输入、唯一可判定的计划路径、参与阶段及占比。仅实际配置并参与本计划的阶段占比合计100%；未配置S5/S6不生成对应日期或超期对象。路径无法唯一确定时保留待处理草稿，不默选分支。日期/范围/实际分支调整生成新计划差异并经PLN-04审批，不改冻结图。

计划批准、方案最终批准、割接覆盖变化只发布Owner事实及版本，PROJ根据当前真实阶段判断是否调用统一推进；后期基线换版不重放首次阶段推进。Cutover成功事实带任务、归档版本、精确冻结范围及范围有效性，IMP按当前需割接范围聚合有效并集，不用任务个数代替覆盖。已进入后续阶段后失效只影响当前可执行门禁，不改过去快照或隐式回退。

RPT-02@V2必须保留全状态、真实阶段、超期、三类终态、根/节点粒度、时间口径、图表下钻及权限一致的导出。正常交付闭环率只取NORMAL_CLOSED分子；业务闭环率取NORMAL_CLOSED+NO_TRACKING_CLOSED；EXCEPTION_CLOSED单列。V1只形成上述可消费事实，不以RPT-02实现作为V1验收前置。
'''
PHYSICAL_DESIGN = '''
### 物理契约及迁移的差量边界

阶段图必须能按templateVersion精确恢复有向转移、条件、优先级、默认分支，以及阶段/任务的主WorkBinding、CompletionRule、PermissionPolicy和交付件版本；推进锁定并重验graphVersion、projectVersion、scopeVersion及Owner事实版本。仅保存固定阶段序号、只校验EXIT Gate的旧物理合同不足以证明修订012/016覆盖。

闭环记录新增/补齐的必要语义为closureType、closedFromStage、gateSnapshotRef及实际BPM定义身份；不再存在“NORMAL_CLOSED只允许S6”的约束。ACC报告与CLO快照须能精确引用范围水位和原始证据版本；COM范围水位是唯一数量版本权威。当前报告指针唯一约束维度为tenant/project/reportType，历史报告与文件不可覆盖。

PM-06不使用MultiPhaseProjectGroup/MultiPhaseProjectMember/CrossPhaseContentReference或其群组API作为目标模型。既有这些表、源字段或已执行迁移仅保留历史与兼容证据，不删除、不改名替代，也不把旧数据自动转换为范围追加。前向物理方案必须逐表区分复用、新增、兼容读取、历史隔离，并在受影响Feature物理合同、迁移设计与数据库验证中关闭；本次文档修改不生成或执行Flyway，不伪造已批准DDL或真实数据库验证结果。
'''

DESIGN_PARTS = {'01-requirement-traceability.md': '生成追溯只读取当前PRD修订/Blob、Feature Spec覆盖与差量标记、原始Feature Task实施事实及当前Gate。文档静态一致不替代Implementation Done；受影响Spec的旧FULL映射在差量重验证关闭前必须显示REVALIDATION_REQUIRED，不能用Task历史Done恢复当前覆盖。', '02e-version-scope-matrix.md': '100项Requirement主切片仍为V1 53/V2 47，111个正式切片仍为V1 53/V2 58。PM-05转销和RPT-02报表只在V2验收，V1售前闭环与NO_TRACKING验证不得依赖二者。EXE-03批准业务命令快照为V1，SCH-03脚本治理为V2；CUT-03/P3是既有V1入口。E2E-17/19/20为V1事实验证，E2E-22/23为对应V2增强，不增加Requirement切片。', '09-database-design.md': PHYSICAL_DESIGN, '20-test-design.md': ''}


def update_designs() -> None:
    for filename, addition in DESIGN_PARTS.items():
        path = Path('docs/design') / filename
        text = read(path)
        text = re.sub(r'(?m)^> 文档状态：.*$', '> 文档状态：`REVALIDATION_REQUIRED`（修订016差量已回写；正式复审以当前Gate为准）', text, count=1)
        text = re.sub(r'(?m)^> 适用基线：.*$', '> 适用基线：PRD V1.8修订016（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留', text, count=1)
        text = text.replace('V1.8独立复审GO，当前分册已纳入正式基线', '既有独立复审GO仅属原批准范围，当前差量须按Gate重验证')
        # Retire the obsolete PM-06 target, preserving its text as explicit history.
        retired = []
        new_lines = []
        for line in text.splitlines():
            if (any(token in line for token in ('MultiPhaseProjectGroup', 'MultiPhaseProjectMember', 'CrossPhaseContentReference', 'PM-06 是多期关系聚合', 'PM-06唯一期次/冲突群组'))
                    and not line.startswith('>')):
                retired.append(line)
                continue
            new_lines.append(line)
        text = '\n'.join(new_lines) + '\n'
        text = text.replace('`NORMAL_CLOSED`只允许S6', '三类闭环终态保留最后真实阶段，不限制为S6')
        text = text.replace('ACTIVE、NORMAL_CLOSED、EXCEPTION_CLOSED', 'ACTIVE、NORMAL_CLOSED、NO_TRACKING_CLOSED、EXCEPTION_CLOSED')
        text = text.replace('计划批准并进入实施方案', '计划批准；PROJ按冻结图重验并决定推进，后续换版不隐式改阶段')
        text = text.replace('方案批准，可进入部署', '方案批准；PROJ按冻结图重验并决定推进')
        text = text.replace('正常闭环仅由CLO-02产生NORMAL_CLOSED', '业务闭环仅由CLO-02按类型产生NORMAL_CLOSED/NO_TRACKING_CLOSED')
        text = text.replace('PM-10“回退”保持`lifecycle_status=ACTIVE`，将`current_stage`回到S0并按规则置为待指派', 'PM-10“回退”保持`lifecycle_status=ACTIVE`，按允许重新指派的受控规则处理阶段并置为待指派，不隐式固定回到S0')
        text = text.replace('CLO-02审批全部通过后才写入`NORMAL_CLOSED`', 'CLO-02审批全部通过后才按closure_type写入`NORMAL_CLOSED`或`NO_TRACKING_CLOSED`')
        text = text.replace('`NORMAL_CLOSED`不得通过PM-10直接重开', '`NORMAL_CLOSED`和`NO_TRACKING_CLOSED`不得通过PM-10直接重开')
        text = text.replace('临时用户名密码可以用于单次连接但不落库', '临时用户名可留审计，临时密码仅用于单任务且不落库')
        if filename == '08-data-model.md':
            anchor = '\n项目树查询规则：'
            rows = '\n| ContractScopeAppendRequest | 聚合根 | PM-06同一ACTIVE项目范围追加申请、批准差异及逐阶段影响 | 不创建期次群组；COM拥有分配及唯一范围水位，PROJ只编排并引用 |\n| ProjectScopeVersionReference | 跨域引用 | COM项目范围版本与精确范围快照 | 不产生第二套数量/版本真值；历史验收不自动覆盖新增范围 |\n| AcceptanceScopeEvidence | 不可变引用 | 报告版本、文件版本与精确验收范围 | 旧范围通过证据保留，新总范围须明确通过且完整覆盖 |\n'
            text = replace(text, anchor, rows + anchor, label='SDS08 PM06 objects')
        if filename == '05-state-machine.md':
            text = re.sub(r'(?m)^\| ProjectClosure \|.*$', '| ProjectClosure | 草稿、待审核、材料审核、已完成、驳回整改；闭环类型独立 | NORMAL/NO_TRACKING分别校验，CLO-02按类型原子形成终态；不造S5/S6，失败保持ACTIVE | ClosureSubmitted、ProjectClosureCompleted（携带closureType/closedFromStage） |', text)
        if filename == '06-workflow-design.md':
            text = text.replace('| 项目正常闭环 |', '| 项目业务闭环（NORMAL/NO_TRACKING） |')
            text = text.replace('`lifecycle_status`置为`NORMAL_CLOSED`', '`lifecycle_status`按闭环类型置为`NORMAL_CLOSED`或`NO_TRACKING_CLOSED`并保留真实阶段')
        if filename == '20-test-design.md':
            addition = test_design_text()
            text = text.replace('及11个补充V2切片专项断言已完成测试设计复核并纳入SDS基线', '及11个补充V2切片的原复核作为历史保留；修订016新增断言的执行证据尚待对应Feature提交')
        if retired:
            text += '\n### 被替代的PM-06关系设计（仅保留历史，不是当前实现输入）\n\n'
            text += '\n'.join('> ' + line for line in retired) + '\n'
        text += '\n## 修订016差量契约\n\n' + addition.strip() + '\n\n对应PRD审查项、派生覆盖和验证结果见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。本文不能替代Feature物理合同重验证、独立复审或运行测试。\n'
        put(path, text)
    # Summary must not contradict the live Gate statuses.
    path = Path('docs/design/00-system-detailed-design.md')
    text = read(path)
    text = text.replace('> 文档状态：`BASELINE`', '> 文档状态：`REVALIDATION_REQUIRED`')
    text = text.replace('> 适用基线：PRD V1.8修订007', '> 适用基线：PRD V1.8修订016')
    text = re.sub(r'(?m)^(\| SDS Phase [123] \|).*$', lambda m: m.group(1) + ' `REVALIDATION_REQUIRED` | `BLOCKED_BY_PRD_DELTA` | `docs/engineering/gates/phase-' + re.search(r'[123]', m.group(1)).group() + '/gate-status.md` |', text)
    text = text.replace('V1.8修订007已按100项正式Requirement和111个目标版本切片完成三阶段差量复核并纳入SDS基线，可作为下游Feature Ready评估输入', '修订007的100项Requirement、111个切片复核结论仅为历史；修订016已同步受影响分册，当前放行只读取Phase 1/2/3的gate-status，不沿用旧结论作为新Feature Ready依据')
    for filename in DESIGN_PARTS:
        text = re.sub(r'(?m)^(\| `' + re.escape(filename) + r'` \|[^\n]*\|) `BASELINE` \|$', r'\1 `REVALIDATION_REQUIRED` |', text)
    text += '\n## 修订016文档与工程证据边界\n\n已回写PRD及受影响SDS，并由同源生成器更新领域规格、Requirement切片覆盖和Phase 2映射。静态文档校验通过只证明本轮断言与投影一致，不证明独立设计审查、数据库、浏览器、真实集成或生产Gate通过。既有物理合同与Feature Ready按影响标记重验证，未影响Task历史Done不改写。见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。\n'
    put(path, text)

CASES = [
('R01', 'PM-03@V1、EXE-03@V1、PM-11@V1', '售前S0/S4且没有EXE-02、S5、S6', '授权采集/联调可执行并从S4闭环，不补造任务', '05-state-machine.md'),
('R02', 'PM-03@V1、PLN-04@V1、SCH-05@V1、EXE-06@V1', '裁剪模板首次批准；S4期间计划换版', '仅PROJ按冻结图准出/唯一后置/准入推进；后期换版不退回', '06-workflow-design.md'),
('R03', 'CLO-01@V1、CLO-02@V1、PM-10@V1', '未完成交付的代理商自服或取消项目', '按退出类型校验；NO_TRACKING/EXCEPTION不先要求正常准出', '05-state-machine.md'),
('R04', 'ACC-03@V1、ACC-04@V1、CLO-01@V1', '元数据及附件完整、结论不通过/整改/未知', '证据保留，不满足验收通过及NORMAL门禁', '08-data-model.md'),
('R05', 'CUT-03@V1', 'P3缺必填时保存、提交；补齐后提交', '保存留P3、缺项提交拒绝；只有有效提交进入P4', '06-workflow-design.md'),
('R06', 'PM-05@V2、RPT-02@V2、PM-03@V1', 'V1未启用PM-05、RPT-02、SCH-03', 'V1事实链可独立验收；E2E-22/23才执行转销与报表', '02e-version-scope-matrix.md'),
('R07', 'EXE-03@V1、INT-12@V1', 'IMP批准业务快照；独立中心同类手工输入', '前者经Owner/权限重验可执行，后者拒绝；不依赖SCH-03', '12-integration-design.md'),
('R08', 'INT-05@V1、INT-09@V1', 'HR离职与目录启用按不同顺序到达', '独立来源版本保留，离职/禁用任一有效均拒绝并撤销会话', '02c-data-ownership-matrix.md'),
('R09', 'PLT-02@V1、INT-12@V1', '有幂等键但来源/签名/对象授权无效', '不创建可用文件版本或业务引用；留下拒绝审计', '13-file-design.md'),
('R10', 'INS-02@V2、INS-04@V2、INT-12@V2', '预检后未再次输入临时密码或授权已撤销', '不执行正式任务；新输入/授权与适用预检同时成立才执行', '14-security-design.md'),
('R11', 'PLN-01@V1、PLN-03@V1', '模板无S5/S6；计划路径多义/占比无效', '只计算实际参与阶段，多义/错误不形成生效计划', '08-data-model.md'),
('R12', 'PM-06@V2、COM-01@V1、ACC-03@V1', '范围A追加B，旧报告仅覆盖A或仅上传B', '原A事实不变，总范围需新总体明确通过版本', '09-database-design.md'),
('R13', 'CUT-03@V1、INT-12@V1', 'P3采集项下发/成功/失败/重复回调', '任务/清单/采集项/设备/结果版本精确绑定，不替代业务通过', '10-api-design.md'),
('R14', 'INS-09@V2、NFR-02@V2', '超时1、30、31及0秒，31秒附审批说明', '1..30可配置，其他拒绝；超时后续命令按冻结规则', '14-security-design.md'),
('R15', 'INS-07@V2', '工程师申请并尝试最终归档，授权服务经理归档', '申请/处理/确认/归档权限分离，服务经理重验所有门禁', '07-authorization-design.md'),
('R16', 'INT-03@V1、INT-05@V2、AUT-01@V2', 'OA材料审批与SUB平台审批；CRM治理工作台', '分操作Owner/兜底；未定义治理工作台不进入V2', '12-integration-design.md'),
]


def test_design_text() -> str:
    rows = ['### 修订016目标版本与负向验收矩阵', '', '| 用例 | Requirement切片 | 前置/操作 | 必须断言 | 执行状态 |', '|---|---|---|---|---|']
    rows += [f'| TC-PRD016-{key} | {requirements} | {given} | {expected} | NOT_RUN |' for key, requirements, given, expected, _ in CASES]
    rows += ['', '上述用例是测试设计，不是运行证据。每项实现验收还必须提供持久化前后值、Owner API结果、幂等/并发/拒绝审计，以及涉及界面的真实浏览器证据；不能用本轮文档静态断言替代。V1只执行V1子集，V2回归相关V1并执行自身新增用例。', '', '所有涉及范围/审批/授权的用例须注入并发变更：旧scopeVersion、旧图、旧报告、旧授权或重复回调不产生二次范围占用、虚假阶段或第二个终态。']
    return '\n'.join(rows)


def update_phase2_generator() -> None:
    import ast
    path = Path('scripts/generate_phase2_contract_map.py')
    text = read(path)
    overrides = {
      'PM-03': {'data': 'ProjectTemplateVersion、StageTransitionDefinition、Stage/TaskWorkBinding、ProjectStageSnapshot', 'workflow': '冻结图发布校验；当前CompletionRule/准出→唯一后置→目标准入→原子推进；售前仅S0/S4，无虚构阶段', 'tables': '既有PROJ模板/阶段/快照承载；图与Stage绑定物理差量见09分册及受影响Feature，未重验证不得声称NO_PHYSICAL_DELTA'},
      'PM-06': {'data': 'ContractScopeAppendRequest、COM ProjectScopeVersion引用、范围差异与逐阶段影响', 'tables': 'PROJ追加申请及COM唯一范围水位/分配；物理复用/新增/兼容处置见09分册，旧多期群组不作为当前输入', 'apis': '同一projectId范围追加应用命令；COM范围写入、PROJ任务/绑定及ACC范围绑定公开契约', 'events': '范围版本和受影响业务事实事件；不产生ProjectPhaseGroupChanged', 'workflow': 'ACTIVE项目审批后同事务追加范围/任务/交付件；重算旧事实覆盖，闭环项目拒绝', 'authorization': '项目、合同/订单范围与审批权限；不授予其他项目权限'},
      'PM-10': {'workflow': '责任回退保持ACTIVE；仅EXCEPTION_CLOSED可重开；CLO-02按类型唯一产生NORMAL_CLOSED/NO_TRACKING_CLOSED，保留真实阶段'},
      'PM-11': {'data': 'ProjectStage/ProjectTask、Stage/TaskWorkBinding、CompletionRule、任务树及依赖', 'workflow': 'V1阶段/任务工作台及冻结绑定运行；V2甘特与受控依赖；非原生完成须重验Owner事实，图只由PROJ推进'},
      'PLN-01': {'workflow': '冻结图唯一计划路径及实际参与阶段占比100%；无S5/S6不生成日期，路径多义不生效；计算草稿经PLN-04批准'},
      'PLN-04': {'workflow': '首次批准提供Owner事实，由PROJ重验图推进；后期换版不隐式退回/推进，钉钉仅通知入口'},
      'SCH-05': {'workflow': '实际BPM全部强制节点通过形成批准版本；只提供Owner事实，PROJ按冻结图校验推进'},
      'EXE-03': {'workflow': '按模板适用前置；售前不要求安装；V1手动Log或IMP批准业务命令快照，经DAC授权执行，不依赖SCH-03'},
      'EXE-06': {'workflow': '当前需割接范围被有效成功结果并集及合法不适用依据完整覆盖后形成完成事实；PROJ按图推进，失效重算而不篡改历史'},
      'ACC-03': {'data': 'AcceptanceReportRevision、验收结论及精确范围/来源证据引用', 'workflow': '字段/附件完整只形成证据；明确通过且顺序合法、当前范围完整覆盖才满足门禁；同类型一个当前报告，A+B须新总体明确通过'},
      'ACC-04': {'workflow': '索引有效来源/版本/哈希/结论与范围；失败验收证据可归档但不满足通过门禁；下载权限不改变客观齐套'},
      'CLO-01': {'workflow': 'NORMAL/NO_TRACKING先分型，冻结真实阶段/模板/范围/来源；NO_TRACKING不要求正常准出；通过快照变化即失效'},
      'CLO-02': {'workflow': '实际BPM全部通过且最新快照持续有效后，CLO-02原子形成NORMAL_CLOSED或NO_TRACKING_CLOSED；保留current_stage与closed_from_stage，不造S6'},
      'CUT-03': {'workflow': 'P3暂存不推进；有效提交才P4；设备采集绑定清单版本/采集项/设备，技术成功不替代CUT结果'},
      'INT-05': {'workflow': 'HR人员/人事/任职引用与目录账号结构Owner分离；V1通知；V2 OA材料/外采审批结果与SUB待办链接区别处理'},
      'INT-09': {'workflow': '目录认证/账号/组织结构Owner与HR属性分离；离职或禁用任一有效拒绝，跨来源不按时间覆盖；应急账号受控'},
      'INT-12': {'workflow': 'V1独立/EXE03-04/CUT03-P3/CUT06入口；EXE03批准业务快照为限定命令来源；V2预检与执行独立任务各自授权，临时密码重新输入'},
      'PLT-02': {'workflow': '文件身份/版本/权限；外部来源认证+契约验签+对象授权+幂等均须成立；扫描默认关闭真实SKIPPED'},
      'INS-02': {'workflow': '在线先独立预检再新任务授权；临时密码重新输入；离线新尝试保留失败；结果进入报告'},
      'INS-04': {'workflow': '独立预检任务及有效期；通过不授予正式任务权限，端点/认证变化重检'},
      'INS-07': {'authorization': '工程师申请归档及授权问题确认；服务经理执行最终归档；三类权限不互相推导'},
      'INS-09': {'workflow': '同一巡检规则版本化发布；单命令1..30秒硬上限，超限无审批例外'},
      'NFR-02': {'workflow': 'V1凭证与临时秘密边界；V2预检/正式执行各自授权，命令1..30秒，超时后续按冻结规则'},
    }
    tree = ast.parse(text)
    assignment = next(node for node in tree.body if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == 'GROUPS')
    replacements = []
    src_lines = text.splitlines(keepends=True)
    for entry in assignment.value.elts:
        ids_node, call = entry.elts
        ids = [item.value for item in ids_node.elts]
        if not set(ids) & set(overrides):
            continue
        rebuilt = []
        for req in ids:
            changes = overrides.get(req, {})
            args = [repr(changes[name]) if name in changes else ast.get_source_segment(text, arg) for name, arg in zip(('data', 'tables', 'apis'), call.args)]
            keywords = {kw.arg: ast.get_source_segment(text, kw.value) for kw in call.keywords}
            for name, value in changes.items():
                if name not in ('data', 'tables', 'apis'):
                    keywords[name] = repr(value)
            rebuilt.append('    ((' + repr(req) + ',), contract(' + ', '.join(args + [f'{name}={value}' for name, value in keywords.items()]) + ')),\n')
        replacements.append((entry.lineno - 1, entry.end_lineno, ''.join(rebuilt)))
    for a, b, new in sorted(replacements, reverse=True):
        src_lines[a:b] = [new]
    text = ''.join(src_lines)
    text = replace(text, '    requirements, slices = load_requirement_model(prd)', '''    identity_spec = importlib.util.spec_from_file_location("phase2_baseline_identity", Path(__file__).with_name("generate_requirement_traceability.py"))
    identity_module = importlib.util.module_from_spec(identity_spec)
    identity_spec.loader.exec_module(identity_module)
    identity = identity_module.baseline_identity(prd)
    gates = identity_module.engineering_gate_states(prd)
    requirements, slices = load_requirement_model(prd)''', label='phase2 identity')
    text = replace(text, '        "> 文档状态：`BASELINE`",', '        f"> 文档状态：`{gates[\'phase-2\'][\'审查状态\']}`",', label='phase2 actual status')
    text = replace(text, '        "> 适用基线：PRD V1.8 修订008（`docs/baseline/prd-v1.8.md`）",', '        f"> 适用基线：PRD V1.8修订{identity[\'revision\']}（`{identity[\'path\']}`；Git Blob `{identity[\'gitBlob\']}`）",', label='phase2 current revision')
    text = replace(text, '        "> Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`（仅表示SDS设计可进入Phase 3，不批准DDL、Feature或Release）",', '        f"> Phase 3验证注记状态：`{gates[\'phase-3\'][\'当前结论\']}`；只投影当前Gate，不批准DDL、Feature或Release。",', label='phase2 false ready')
    put(path, text)


def update_features() -> list[dict[str, object]]:
    impacted_keys = {'PM-01@V1','PM-02@V1','PM-03@V1','PM-08@V1','PM-10@V1','PM-11@V1','COM-01@V1','PLT-02@V1'}
    records = []
    for path in sorted(Path('specs/features').glob('F-*.md')):
        text = read(path)
        match = re.search(r'(?m)^>\s*Requirement切片覆盖：`([^`]+)`', text)
        if not match:
            continue
        keys = {item.strip().split('=')[0] for item in re.split('[；;]', match.group(1))}
        pending = sorted(keys & impacted_keys)
        if not pending:
            continue
        fid = re.match(r'F-[A-Z]+-\d+', path.name).group()
        task = Path('tasks/features') / (fid + '.md')
        task_text = task.read_text(encoding='utf-8-sig') if task.exists() else ''
        # Read the authoritative Task before changing design qualification, but never alter it.
        task_status = re.search(r'(?m)^>\s*Feature实施状态：.*$', task_text)
        historical_ready = re.search(r'(?m)^>\s*Feature Ready：.*$', text)
        if historical_ready:
            line = historical_ready.group()
            text = text.replace(line, line.replace('> Feature Ready：', '> 上次Feature Ready（历史）：') + '\n> Feature Ready：`REVALIDATION_REQUIRED`（当前修订设计/实现影响尚需复核）', 1)
        text = re.sub(r'(?m)^> 文档状态：.*$', '> 文档状态：`REVALIDATION_REQUIRED`', text, count=1)
        marker = '\n> PRD差量重验证：`' + '；'.join(pending) + '`\n> 差量依据：`' + REV + '`；旧Task事实保留，不直接投影当前完成\n'
        text = text.replace(match.group(), match.group() + marker, 1)
        text = re.sub(r'(?m)^> Technical Plan：', '> 上次Technical Plan（历史，不授权当前差量实施）：', text)
        if fid == 'F-PROJ-008':
            for old, new in [
                ('从S0～S3推进到相邻S1～S4', '沿冻结StageTransitionDefinition在本切片范围内唯一解析目标并推进（包含售前S0→S4）'),
                ('S0→S1、S1→S2、S2→S3、S3→S4四条通用相邻推进', 'S0～S4范围内模板实际配置的正向推进，包含售前S0→S4；标准四条链路仅为预置模板示例'),
                ('S4→S5；继续使用F-COM-001 `enter-acceptance-stage`及ACC范围绑定事务', 'S4→S5不属于本Feature已完成范围；对应入口必须委托PROJ统一编排并同事务完成COM/ACC范围绑定，不得另写阶段'),
                ('目标阶段由服务端从冻结顺序推导', '目标阶段由服务端从冻结图按条件、优先级和默认分支唯一解析并校验目标准入'),
                ('不得跳级、指定非相邻目标或代理S4→S5', '不得指定客户端目标或绕过冻结转移；S0→S4是模板合法边而非通用跳过；本Feature不代理S4→S5'),
                ('相邻推进', '冻结图推进'),
                ('相邻目标', '冻结图解析目标'),
                ('提交相邻阶段推进', '提交冻结图推进'),
                ('S0～S3每个阶段至少一个EXIT Gate', '本切片实际配置的每个可推进阶段至少一个EXIT Gate'),
                ('同一Stage全部EXIT Gate通过才允许推进', '同一Stage全部EXIT Gate、当前CompletionRule及解析目标ENTRY Gate均通过才允许推进'),
                ('`NO_PHYSICAL_DELTA`：', '`PHYSICAL_REVALIDATION_REQUIRED`：下列是原合同的复用候选而非当前已放行结论；图/目标准入/Stage绑定物理差量须重验证：'),
            ]:
                text = text.replace(old, new)
            text += '\n### 修订016范围内的阶段命令\n\n' + STAGE_DESIGN
        text += '\n## 修订016覆盖资格\n\n当前受影响切片：' + '、'.join(pending) + '。当前PRD与正式SDS已修正业务语义，旧Feature Ready/Technical Plan及物理合同不能证明新语义已经实现。必须按本Feature范围复核图/状态/权限/范围/文件契约及相关运行证据后，由权威Task记录当前实施结果，并在Spec中解除本标记；不得仅因文档生成通过或历史Task为Done而解除。关联但未声明覆盖的Requirement不产生完成状态。详见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`与SDS20的TC-PRD016用例。\n'
        put(path, text)
        records.append({'featureId':fid,'featurePath':path.as_posix(),'pendingSlices':pending,'taskPath':task.as_posix() if task.exists() else None,'historicalTaskStateLine':task_status.group() if task_status else '见权威Task正文；本次不修改','taskSha256':hashlib.sha256(task.read_bytes()).hexdigest() if task.exists() else None})
    index = read('specs/features/README.md')
    for record in records:
        fid = record['featureId']
        for line in index.splitlines():
            if line.startswith('| [' + fid + ']'):
                cols = line.split('|')
                # Six columns: do not change the last (Task-owned implementation state).
                if len(cols) != 8:
                    raise RuntimeError('Feature index column shape changed: ' + fid)
                cols[4] = ' REVALIDATION_REQUIRED '
                cols[5] = ' REVALIDATION_REQUIRED（修订016；见Spec） '
                index = index.replace(line, '|'.join(cols), 1)
    index += '\n修订016覆盖资格：`PRD差量重验证`只保存在受影响Feature Spec，列出其机器可读覆盖中待验证的切片。生成器据此暂停当前Requirement完成投影，原Task实施事实及本索引最后一列保持不变；没有映射的新增义务仍为NOT_STARTED。\n'
    put('specs/features/README.md', index)
    # Two inspected physical contracts expose concrete contradictions, not just stale headers.
    p = Path('specs/features/F-COM-001-project-qualification-contract.json')
    data = json.loads(read(p))
    data['status'] = 'REVALIDATION_REQUIRED'
    data['baselineChange'] = REV
    data['qualification']['lifecycle'] = ['ACTIVE','NORMAL_CLOSED','NO_TRACKING_CLOSED','EXCEPTION_CLOSED']
    data['qualification']['lifecycleStageInvariant'] = 'Every project references its last real instantiated template stage; closed lifecycles do not require S6 and preserve closed_from_stage'
    data['qualification']['stage'] = 'Return and revalidate the real template stage exactly; acceptance-bound scope and all three closed lifecycles remain protected against ordinary reductions/releases'
    data['forbidden'] = [item for item in data['forbidden'] if item != 'NORMAL_CLOSED outside S6'] + ['fabricated S5/S6 for closure', 'NO_TRACKING bypasses scope protection']
    data['reviewBoundary'] = 'Target contract corrected; provider implementation and database/runtime evidence are not revalidated by this documentation change'
    put(p, json.dumps(data,ensure_ascii=False,indent=2))
    p = Path('specs/features/F-PROJ-008-physical-contract.json')
    data = json.loads(read(p))
    data['status'] = 'REVALIDATION_REQUIRED'
    data['baselineChange'] = REV
    data['contractType'] = 'PHYSICAL_IMPACT_REVIEW_REQUIRED'
    data['phaseGateImpact'] = 'REVISION_016_TARGET_SEMANTICS_ALIGNED_PREVIOUS_READY_IS_HISTORICAL_RUNTIME_NOT_REVALIDATED'
    data['v1Scope']['included'][0] = 'FROZEN_GRAPH_ADVANCE_WITHIN_S0_TO_S4_INCLUDING_PRESALES_S0_TO_S4'
    runtime = data['objects']['ProjectStageGateRuntime']
    runtime['allowedAdvances'] = ['TEMPLATE_FROZEN_TRANSITIONS_WITHIN_FEATURE_SCOPE']
    runtime['referenceTemplateExamples'] = ['S0->S1','S1->S2','S2->S3','S3->S4','S0->S4']
    runtime['requiredGateShape'] = 'ACTUALLY_INSTANTIATED_SOURCE_COMPLETION_AND_EXIT_GATES_THEN_UNIQUE_TRANSITION_THEN_TARGET_ENTRY_GATES'
    runtime['lockOrder'] = ['PROJECT_AND_GRAPH_VERSION','CURRENT_AND_RESOLVED_TARGET_STAGE','EXIT_AND_ENTRY_GATES','GATE_REFERENCE','OWNER_FACT']
    runtime['concurrency'] = 'PROJECT_TREE_GRAPH_SCOPE_STAGE_BINDING_RULE_AND_OWNER_FACT_REVALIDATION'
    data['interfaces']['StageAdvanceReadiness']['response'] += ['graphVersion','transitionId','targetEntryGateResults']
    data['interfaces']['AdvanceStage']['requiredInputs'] += ['expectedGraphVersion']
    data['successTransaction'].insert(3,'TARGET_ENTRY_GATES_VALIDATED')
    data['reviewBoundary'] = 'Existing table reuse is a candidate; graph, entry-gate and binding persistence must pass the current Phase 2 physical review before new Implementation'
    put(p, json.dumps(data,ensure_ascii=False,indent=2))
    return records

# Each detailed rule has one SDS home. Other volumes link the relevant contract.
DESIGN_PARTS.update({
 '02-domain-model.md': 'Project、ProjectStage/ProjectTask、模板阶段转移和绑定由PROJ拥有；闭环申请和判定由ACC/CLO拥有，CLO-02通过受控PROJ Writer形成分型终态。COM独占项目范围分配及版本，PROJ的PM-06只编排同项目追加，不拥有第二份数量真值。核心状态见05，精确范围/验收对象见08，身份来源见02c，采集见12。',
 '02a-context-map.md': '新增/澄清的方向是SOL/IMP/CUT提供有版本事实→PROJ统一阶段编排；CLO-02批准事务→PROJ分型终态Writer；COM范围版本→PROJ任务/绑定及ACC验收覆盖；HR人事/任职引用与LDAP目录身份→SYSTEM映射。不得让事件消费者、COM入口、BPM回调成为第二个项目阶段或终态Writer。契约字段见02d，来源Owner见02c。',
 '02b-aggregate-boundary-decisions.md': 'PM-06原多期关系聚合被同一projectId下的范围追加编排替代；COM范围分配与版本仍为独立Owner，PROJ不复制数量。ACC报告证据有效性、验收通过和当前范围覆盖分别建模（08）。预检和正式巡检属于两个独立CollectionTask，各自授权；P3清单答案由CUT而不是DAC拥有（12）。',
 '02d-cross-context-contracts.md': '''
| 契约 | 必要输入/输出 | 成功与禁止边界 |
|---|---|---|
| PROJ阶段推进 | Project/tree/graph/scope版本、当前CompletionRule、出向转移及源/目标Gate、Owner事实版本 | 顺序及原子性见05；目标服务端唯一解析，不按S编号加一 |
| COM范围追加→PROJ/ACC | projectId、baseScopeVersion、新范围版本、精确明细及批准变化 | 数量/水位由COM拥有；任务/绑定/当前门禁同事务更新；模型见08 |
| ACC验收报告Fact | reportVersion、reportEvidenceValid、acceptancePassed、scopeVersion、精确覆盖和文件版本 | 字段/文件完整不等于通过；旧A不能自动覆盖A+B，见08 |
| CLO终态命令及事件 | closureType、closedFromStage、最新Gate快照、实际BPM定义、Project版本 | CLO-02/PM-10唯一业务入口；事件只通知已提交事实，见05/11 |
| IMP→DAC命令来源 | EXE-03批准业务快照的来源/版本/哈希/批准依据/设备/主体范围 | DAC重验；独立中心/CUT/INS仍需发布模板；见12 |
| CUT P3→DAC→CUT | taskId、checklistVersion、itemId、deviceId、CollectionTask/resultVersion | 技术回调只生成证据，CUT决定业务通过；见12 |
| 外部文件回调 | 来源认证、契约签名、任务/对象授权、大小/类型/哈希、幂等 | 所有校验同时成立，不能用幂等键代验签，见13 |
''',
 '02c-data-ownership-matrix.md': IDENTITY_DESIGN + '\nCOM独占订单行数量、项目分配和项目范围版本；PROJ仅编排PM-06并引用该版本。ACC独占报告证据、验收结论及范围覆盖事实；项目阶段由PROJ统一推进，闭环业务入口仅CLO-02/PM-10（见05、08）。',
 '04-module-design.md': 'PROJ统一配置及执行StageTransitionDefinition和Stage/Task WorkBinding（05/08）；SOL计划审批只产出版本事实（06）；IMP按当前范围聚合割接覆盖；ACC区分报告证据有效、验收通过与齐套（08/13）；DAC包含P3采集入口与EXE-03批准业务快照、巡检两个任务授权（12）；SYSTEM分别承接HR和目录Owner（02c）。PM-06不提供项目群组模块。RPT-02@V2仍提供全部状态/阶段/超期/三类退出/比例/下钻/导出，不能缩成终态摘要。',
 '05-state-machine.md': STAGE_DESIGN + '\nACC报告证据与验收通过分离、范围变化及CLO快照失效见08。CUT-03暂存保持P3，只有有效提交进入P4；巡检实际归档状态由服务经理受控命令推进，详见06/07/12。',
 '06-workflow-design.md': PLAN_DESIGN + '\nCLO流程在选择退出类型后分别校验：NORMAL检查冻结模板准出，NO_TRACKING检查代理商自服资格、依据和在途处置，EXCEPTION使用PM-10；三条入口不能共用正常交付前置（05）。P3暂存仅草稿，校验提交后才P4。在线巡检先预检任务、再新输入/授权创建正式任务，最终归档仅服务经理执行（12）。',
 '07-authorization-design.md': '身份字段与停用冲突以02c为准；任何来源的有效离职/禁用均阻断，另一来源晚到的启用不覆盖。EXE-03限定业务快照授权、独立中心模板限制及两个巡检任务分别授权见12；不能依赖客户端approved。INS-07工程师申请归档，授权服务经理执行最终归档；跟踪项处理/确认不授予归档权。NO_TRACKING、异常关闭分别先校验对应角色和业务资格，不先要求NORMAL阶段准出。外部文件回调不能以幂等键替代认证，详见13。',
 '08-data-model.md': SCOPE_DESIGN + '\nProjectStage及ProjectTask分别保存主WorkBinding、PermissionPolicy、CompletionRule、交付件要求和版本；StageTransitionDefinition是前后置关系的唯一源。图发布/推进不变量见05；两个CollectionTask及命令来源字段见12。',
 '10-api-design.md': '''
| 既有命令/契约 | 修订016最小数据与处理 |
|---|---|
| `POST /api/v1/pms/projects/{id}/actions/advance-stage` | If-Match/Idempotency-Key及expectedCurrentStage/expectedTreeVersion/expectedGraphVersion；目标由冻结图唯一解析；响应含transitionId/graphVersion/源准出和目标准入结果。实际锁序及事务见05。 |
| 阶段readiness | 返回实际目标或可恢复缺口，不产生授权或完成事实；无S5/S6不得虚构节点。 |
| COM验收阶段入口 | 委托PROJ唯一推进服务，同事务完成COM范围锁定和ACC精确绑定；不直接写current_stage。 |
| PM-06范围追加 | projectId、baseScopeVersion、合同/订单行/数量、影响及审批引用；服务端重验后才切范围、补任务/绑定；新旧范围模型见08。 |
| ACC报告/验收Fact | 当前报告版本、结论、reportEvidenceValid、acceptancePassed、精确scopeVersion/范围及来源文件；不接受客户端直接指定通过。 |
| CLO-01/02 | closureType、真实closedFromStage、Gate快照及来源/项目版本；提交时重新鉴权和重验快照，不能补造阶段。 |
| CollectionTask创建 | PUBLISHED_TEMPLATE引用或仅EXE-03 APPROVED_BUSINESS_SNAPSHOT引用；P3精确上下文；预检及正式执行分别签发授权（12）。 |
| 文件回调 | 来源身份、签名、对象/任务权限与幂等均独立校验，失败不产生可引用文件（13）。 |

本节只更新契约，不声明Provider、公开Java接口、OpenAPI或数据库已实现；受影响物理合同及Feature Ready必须重验证。已有路由继续受原功能权限和领域Owner约束，不增加通用绕过入口。
''',
 '11-event-design.md': '计划/方案批准事件只载Owner业务版本，不直接推进固定S3/S4；PROJ按05校验是否推进。Cutover成功事件携带taskId、归档版本、精确范围及有效性，IMP按当前需割接范围聚合，不以一个成功任务代替全范围。范围/报告失效事件只使当前依赖门禁失效，历史快照不覆盖。项目闭环事件必须携带closureType、closedFromStage、项目版本与闭环/Gate快照引用，只通知已经由CLO-02/PM-10提交的事实；消费者不得再次写终态。事件键和聚合版本共同防重放/乱序，重试不绕过当前授权。',
 '12-integration-design.md': COLLECTION_DESIGN + '\nHR与目录字段及恢复策略以02c为准。OA材料/外采及AUT-01再次申请由OA拥有外部审批结果；SUB审批在平台内，OA只收待办链接，两者不能共享“平台内审批继续”的兜底。CRM治理工作台为V3，不混入未定义V2范围。外部回调文件准入复用13。',
 '13-file-design.md': FILE_DESIGN,
 '14-security-design.md': '新增负向安全边界：HR离职/目录禁用按02c分别留源并共同阻断；合法幂等键不能放行伪造文件回调（13）；EXE-03限定批准业务快照不能扩展为独立中心任意命令（12）；预检与正式巡检各有独立任务授权，临时秘密再次输入而不复用。对以上允许与拒绝路径扫描浏览器/存储/队列/网关/应用/采集日志/回调/导出，秘密命中数必须0。巡检配置1..30秒，无超限审批例外。文档静态校验不代替这些实际安全测试。',
 '15-cache-and-concurrency.md': '按05/08在同一项目命令内稳定锁定并重验project/tree/graph/scope、阶段/报告/闭环及Owner事实版本。范围追加、报告换版与CLO批准竞争时，只接受当前版本，不产生部分占用、部分任务、第二终态或过期通过快照。readiness缓存不授权写入；权限收缩与来源离职/禁用使相关缓存失效。临时密码不能因跨预检/执行任务的便利而写缓存（12）。',
 '16-exception-and-idempotency.md': '授权/签名失败、当前Owner事实缺失、版本冲突和分支不唯一均失败关闭（05/12/13）。幂等重放不替代来源认证及当前操作授权；P3暂存幂等只保存草稿，不能推进。在线失败与人工结果分别留存；INS预检失败不能授权正式任务，临时密码不得用于异步补偿。范围追加失败按08整体回滚，旧通过事实只保留原范围，不伪造新范围完成。',
})

VALIDATOR = r'''#!/usr/bin/env python3
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
        outcomes[f"GATE_{phase}"] = identity["gitBlob"] in gate and "PRD_V1.8_REVISION_016" in gate and "REVALIDATION_REQUIRED" in gate
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
'''

TESTS = r'''from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
def load(name, path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
trace=load("trace016",ROOT/"scripts/generate_requirement_traceability.py")
validator=load("validator016",ROOT/"scripts/validate_prd_revision_016_alignment.py")

class BaselineQualificationTests(unittest.TestCase):
    def test_historical_full_is_not_current_complete_when_pending(self):
        mappings=[{"coverage":"FULL","task_status":"COMPLETE","revalidation_required":True}]
        self.assertEqual("REVALIDATION_REQUIRED",trace.derived_status(mappings))
        self.assertEqual("COMPLETE",mappings[0]["task_status"])
    def test_unaffected_done_is_preserved(self):
        self.assertEqual("IMPLEMENTATION_COMPLETE",trace.derived_status([{"coverage":"FULL","task_status":"COMPLETE"}]))
    def test_partial_done_does_not_close_requirement(self):
        self.assertEqual("IMPLEMENTATION_PARTIAL",trace.derived_status([{"coverage":"PARTIAL","task_status":"COMPLETE"}]))
    def test_no_task_does_not_close_requirement(self):
        self.assertEqual("NOT_STARTED",trace.derived_status([{"coverage":"FULL","task_status":"NO_TASK"}]))
    def test_revision_and_hash_are_derived_not_constant(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"prd.md"
            raw=b"| V1.8\xe4\xbf\xae\xe8\xae\xa2017 | 2026-09-07 | owner | `CHG-PRD-2026-09-07-017` |\n"
            path.write_bytes(raw)
            identity=trace.baseline_identity(path)
            self.assertEqual("017",identity["revision"])
            self.assertEqual(hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest(),identity["gitBlob"])
    def test_unknown_revalidation_key_fails_closed(self):
        with self.assertRaises(SystemExit):
            trace.feature_revalidation_slices("> PRD差量重验证：`BOGUS-01@V1`",{"PM-03@V1"})
    def test_valid_revalidation_keys_are_precise(self):
        self.assertEqual({"PM-03@V1"},trace.feature_revalidation_slices("> PRD差量重验证：`PM-03@V1`",{"PM-03@V1","PM-01@V1"}))
    def test_missing_gate_is_unknown_not_approved(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertTrue(all(item["当前结论"]=="UNKNOWN" for item in trace.engineering_gate_states(Path(temp)/"prd.md").values()))
    def test_prd_all_sixteen_static_regressions(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        self.assertEqual([], [key for key, value in validator.inspect_prd(text).items() if not value])
    def test_reintroduced_save_advance_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        bad=text.replace("暂存不触发流程推进", "采集清单保存后通过任务状态流转自动进入P4",1)
        self.assertFalse(validator.inspect_prd(bad)["R05"])
    def test_reintroduced_signature_or_key_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        bad=text.replace("必须同时校验来源身份", "需校验来源签名或幂等键",1)
        self.assertFalse(validator.inspect_prd(bad)["R09"])
    def test_reintroduced_sixth_stage_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        bad=text.replace("售前测试模板配置`S0→S4`", "售前测试模板配置`S0→S4→S6`",1)
        self.assertFalse(validator.inspect_prd(bad)["R01"])

if __name__ == "__main__":
    unittest.main()
'''


def flush() -> None:
    for name, text in CHANGES.items():
        path = Path(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8', newline='\n')


def run_check(args: list[str], *, critical: bool = True) -> dict[str, object]:
    print('\n>>> ' + ' '.join(args), flush=True)
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env, timeout=150)
    print(result.stdout, flush=True)
    row = {'command': ' '.join(args), 'exitCode': result.returncode, 'result': 'PASS' if result.returncode == 0 else 'FAIL', 'output': result.stdout[-8000:]}
    if critical and result.returncode:
        raise RuntimeError('Required static check failed: ' + row['command'])
    return row


def update_gates(blob: str, checked: bool = False) -> None:
    for phase in (1,2,3):
        path = Path(f'docs/engineering/gates/phase-{phase}/gate-status.md')
        text = read(path)
        text = text.replace(OLD_BLOB, blob)
        if '> PRD Blob：' not in text:
            text = text.replace('> 当前依据：', f'> PRD Blob：`{blob}`<br>\n> 当前依据：', 1)
        text = text.replace('014—015', '014—016').replace('008—015', '008—016').replace('修订015', '修订016')
        text = text.replace('PRD_V1.8_REVISION_015', 'PRD_V1.8_REVISION_016')
        state = 'REVISION_016_STATIC_ALIGNMENT_PASS_FULL_GATE_PENDING' if checked else 'NOT_RUN_FOR_REVISION_016'
        text = re.sub(r'(?m)^> 机器门禁：`[^`]+`', '> 机器门禁：`'+state+'`', text)
        note = '\n## 修订016联动回写证据\n\nPRD正文、来源副本、受影响SDS及生成器已同步；追溯仅从当前PRD、Feature Spec和Task派生。当前独立设计复审及受影响物理/实现证据仍未完成，不将静态文档校验当作阶段批准。证据、具体通过/失败检查和TC-PRD016断言见`../phase-1/prd-revision-016-alignment.md`。旧Task Done不改写，受影响切片不得据旧FULL映射宣称当前完整覆盖。\n'
        if '## 修订016联动回写证据' not in text:
            text += note
        put(path, text)
    path = Path('docs/engineering/00-engineering-chain.md')
    text = read(path).replace(OLD_BLOB, blob).replace('014—015', '014—016').replace('008—015', '008—016')
    if '## 修订016当前覆盖资格' not in text:
        text += '\n## 修订016当前覆盖资格\n\n生成器从PRD修订记录和实际内容推导修订/Blob，并读取当前Phase Gate。受影响Feature Spec的`PRD差量重验证`仅限定其已声明覆盖的切片；未关闭前派生`REVALIDATION_REQUIRED`，不改写Task历史实施状态，不额外建立Capability或Task生命周期。清除标记须以对应当前修订的设计及实施证据为依据，文档生成成功本身不解除。\n'
    put(path, text)


def generators() -> list[dict[str, object]]:
    commands = [
      [sys.executable,'scripts/generate_prd_domain_requirements.py','--prd',str(PRD),'--output',str(DOMAINS)],
      [sys.executable,'scripts/generate_requirement_traceability.py','--prd',str(PRD),'--domains',str(DOMAINS),'--output','docs/traceability/requirement-matrix.md'],
      [sys.executable,'scripts/generate_phase2_contract_map.py','--prd',str(PRD)],
    ]
    return [run_check(command) for command in commands]


def verify() -> None:
    run_check([sys.executable,'scripts/validate_prd_revision_016_alignment.py'])
    run_check([sys.executable,'scripts/generate_requirement_traceability.py','--prd',str(PRD),'--domains',str(DOMAINS),'--output','docs/traceability/requirement-matrix.md','--check'])
    run_check([sys.executable,'scripts/generate_phase2_contract_map.py','--prd',str(PRD),'--check'])
    run_check(['git','diff','--check'])


def write_report(blob: str, feature_records: list[dict[str, object]], checks: list[dict[str, object]]) -> None:
    lines = [
      '# PRD V1.8修订016联动回写与验证记录', '',
      '> 状态：`DOCUMENT_STATIC_ALIGNMENT_VERIFIED`；独立设计复审、物理契约复核及运行验收仍由当前Gate控制。',
      f'> PRD变更：`{REV}`；输入PRD Blob：`{OLD_BLOB}`；输出PRD Blob：`{blob}`。',
      f'> 输入提交：`{os.environ.get("GITHUB_SHA", subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip())}`；业务基准：`{BASE}`。',
      '> 授权范围：本轮需求方明确要求PRD正文、追溯投影与受影响设计共同修订；未请求也未执行master合并、应用代码改写或部署。', '',
      '## 1. 审查项与回写落点', '',
      '| 项 | Requirement切片 | 修正/验证语义 | SDS落点 | 文档状态 | 运行验收 |', '|---|---|---|---|---|---|',
    ]
    for key, reqs, given, expected, sds in CASES:
        lines.append(f'| {key} | {reqs} | {given} → {expected} | `docs/design/{sds}`；`20-test-design.md#TC-PRD016-{key}` | STATIC_ALIGNED | NOT_RUN |')
    lines += ['', '## 2. 自动生成链', '',
       '`docs/baseline/prd-v1.8.md`与`需求/PRD-项目实施交付管理平台.md`按字节一致；13领域需求、Requirement覆盖Markdown/JSON及Phase 2契约均重新生成。修订号和Git Blob从PRD实际输入推导，不再硬编码008；当前工程状态从gate-status读取，不再固定“当前规格阻断：无”。',
       '', '100项Requirement、主版本V1 53/V2 47、111个正式切片（V1 53/V2 58）保持不变。新增E2E-22/23只隔离转销/报表的V2验收，不新增Requirement或扩大版本承诺。',
       '', '## 3. 受影响Feature与历史事实保护', '', '| Feature | 待重验证切片 | 权威Task | 处理 |', '|---|---|---|---|']
    for record in feature_records:
        lines.append(f'| {record["featureId"]} | {"、".join(record["pendingSlices"])} | `{record["taskPath"]}` | Spec当前Ready重验证，原Task字节保持不变 |')
    lines += ['', '上述标记不撤销历史Implementation Done，也不把文档修订当作新实现完成。没有对应Feature的ACC/CLO/INS等义务继续显示实际未开始/未覆盖状态，不能用一个旧Feature的FULL覆盖新语义。',
      '', '## 4. 实际执行的检查', '', '| 命令 | 退出码 | 结果 |', '|---|---|---|']
    for check in checks:
        lines.append(f'| `{str(check["command"]).replace("|", " / ")}` | {check["exitCode"]} | {check["result"]} |')
    for check in checks:
        if check['result'] != 'PASS':
            lines += ['', '### 未通过检查：`' + str(check['command']) + '`', '', '```text', str(check['output']).replace('```','~~~'), '```']
    lines += ['', '## 5. 不代表已经关闭的工程门禁', '',
      '本轮没有执行Java/前端构建、真实MySQL、浏览器E2E、真实外部联调、生产安全扫描或部署。Phase 1/2/3维持REVALIDATION_REQUIRED/BLOCKED_BY_PRD_DELTA，等待各自要求的独立语义复审和证据；静态检查通过不等于SDS整体验收或上线批准。',
      '', 'F-PROJ-008图/目标准入及F-COM-001非S6闭环资格的目标合同已修正，但Provider、持久化和旧测试仍须按新合同验证；不声称NO_PHYSICAL_DELTA或沿用旧PASS。未触及已执行DDL、原Task完成事实、DU认领或master。',
      '', '## 6. 变更文件', '']
    changed = subprocess.check_output(['git','diff','--name-only'], text=True).splitlines()
    untracked = subprocess.check_output(['git','ls-files','--others','--exclude-standard'],text=True).splitlines()
    for path in sorted(set(changed + untracked + [REPORT.as_posix()])):
        lines.append('- `' + path + '`')
    put(REPORT, '\n'.join(lines))


def check_write_scope() -> None:
    tracked = subprocess.check_output(['git','diff','--name-only','-z']).decode().split('\0')
    untracked = subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z']).decode().split('\0')
    allowed_exact = {PRD.as_posix(), MIRROR.as_posix(), REPORT.as_posix(),
      'scripts/close_prd_revision_016.py','scripts/generate_requirement_traceability.py',
      'scripts/generate_phase2_contract_map.py','scripts/validate_prd_revision_016_alignment.py',
      'scripts/tests/test_prd_revision_016_alignment.py','docs/design/00-system-detailed-design.md',
      'docs/engineering/00-engineering-chain.md','docs/traceability/requirement-matrix.md',
      'docs/traceability/requirement-version-coverage.json','docs/traceability/phase2-contract-map.md',
      'specs/features/README.md','specs/features/F-COM-001-project-qualification-contract.json',
      'specs/features/F-PROJ-008-physical-contract.json'}
    allowed_exact.update('docs/design/'+name for name in DESIGN_PARTS)
    allowed_exact.update(f'docs/engineering/gates/phase-{i}/gate-status.md' for i in (1,2,3))
    allowed_exact.update(path.as_posix() for path in Path('specs/features').glob('F-*.md') if 'PRD差量重验证' in path.read_text(encoding='utf-8-sig'))
    allowed_exact.update(path.as_posix() for path in DOMAINS.glob('*需求规格.md'))
    illegal = sorted({path for path in tracked + untracked if path and path not in allowed_exact})
    if illegal:
        raise RuntimeError('Write scope violation: ' + ', '.join(illegal))
    print('[PASS] only allowlisted documentation, generators and documentation tests changed')


def apply() -> None:
    sys.dont_write_bytecode = True
    os.environ['PYTHONDONTWRITEBYTECODE'] = '1'
    before_tasks = {path.as_posix(): path.read_bytes() for path in Path('tasks').rglob('*') if path.is_file()}
    text = change_prd()
    put(PRD, text)
    put(MIRROR, text)
    update_designs()
    feature_records = update_features()
    update_trace_generator()
    update_phase2_generator()
    put('scripts/validate_prd_revision_016_alignment.py', VALIDATOR)
    put('scripts/tests/test_prd_revision_016_alignment.py', TESTS)
    raw = CHANGES[PRD.as_posix()].encode()
    blob = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    update_gates(blob)
    flush()
    # Parse all changed Python before executing any repository generator.
    import ast
    for name in CHANGES:
        if name.endswith('.py'):
            ast.parse(Path(name).read_text(),filename=name)
    generators()
    checks = []
    checks.append(run_check([sys.executable,'-m','unittest','discover','-s','scripts/tests','-p','test_prd_revision_016_alignment.py','-v']))
    checks.append(run_check([sys.executable,'scripts/validate_prd_revision_016_alignment.py']))
    checks.append(run_check([sys.executable,'scripts/validate_prd_domain_generation.py','--prd',str(PRD),'--domains',str(DOMAINS)],critical=False))
    # Exercise existing generator tests where present, without changing old assertions.
    for pattern in ('test_generate_requirement_traceability.py','test_generate_phase2_contract_map.py'):
        if (Path('scripts/tests') / pattern).is_file():
            checks.append(run_check([sys.executable,'-m','unittest','discover','-s','scripts/tests','-p',pattern,'-v'],critical=False))
    # Existing validators are recorded honestly; a failing full Gate is not silently promoted.
    for name in ('validate_prd_baseline.py','validate_prd_semantics.py','validate_sds_phase1.py','validate_sds_phase2.py','validate_sds_phase3.py'):
        path = Path('scripts') / name
        if not path.is_file():
            continue
        help_result = subprocess.run([sys.executable,str(path),'--help'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
        command = [sys.executable,str(path)]
        for flag,value in (('--prd',str(PRD)),('--snapshot',str(PRD)),('--domains',str(DOMAINS)),('--traceability','docs/traceability/requirement-matrix.md'),('--sds','docs/design')):
            if flag in help_result.stdout:
                command += [flag,value]
        checks.append(run_check(command,critical=False))
    for name, raw_task in before_tasks.items():
        if Path(name).read_bytes() != raw_task:
            raise RuntimeError('Authoritative Task changed unexpectedly: ' + name)
    update_gates(blob, checked=True)
    flush()
    generators()  # Gate fields are inputs to the same deterministic projections.
    checks.append(run_check([sys.executable,'scripts/generate_requirement_traceability.py','--prd',str(PRD),'--domains',str(DOMAINS),'--output','docs/traceability/requirement-matrix.md','--check']))
    checks.append(run_check([sys.executable,'scripts/generate_phase2_contract_map.py','--prd',str(PRD),'--check']))
    checks.append(run_check(['git','diff','--check']))
    write_report(blob, feature_records, checks)
    # Flush only the report: do not overwrite freshly regenerated projection files.
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    REPORT.write_text(CHANGES[REPORT.as_posix()],encoding='utf-8',newline='\n')
    verify()
    check_write_scope()
    print('[PASS] revision016 documentation alignment complete; independent/runtime gates remain pending')


def main() -> int:
    parser=argparse.ArgumentParser()
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--apply',action='store_true')
    group.add_argument('--verify',action='store_true')
    group.add_argument('--check-write-scope',action='store_true')
    args=parser.parse_args()
    if args.apply:
        apply()
    elif args.verify:
        verify()
    else:
        check_write_scope()
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
