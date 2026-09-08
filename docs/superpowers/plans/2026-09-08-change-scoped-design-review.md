# 变更级实质审查迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 取消Phase 1/2对日常变更的全量准入阻断，保留相关设计、契约及测试的实质审查，不伪造历史批准。

**Architecture:** 不建立新审批服务、状态文件或自动影响分析框架。日常变更在现有Task/DU/提交中声明Requirement、影响的Owner/接口/表/消费者、验证与结论；Phase 1/2只作为设计分类及可选全量审计。既有Feature检查、数据库约束及CI回归保留。

**Tech Stack:** Python标准库/unittest、现有Markdown工程规则；不改Java、前端、数据库或CI工作流。

**Spec:** 需求方2026-09-08确认“取消阶段全量阻断、保留变更级实质审查”；正式落点为`docs/engineering/00-engineering-chain.md`的当前变更级审查规则。

## Global Constraints

- 继续使用已明确指定的master；先行认领为`c41656718c949959afe4f894990d58edd4e14b6e`。
- 不改PRD业务语义、原验收R2合同、物理合同、已执行DDL、业务代码或CI运行门禁。
- 原GO/NO-GO/FAIL及来源身份原样留存；停用阻断不是把旧FAIL改为PASS。
- 权威仍为PRD→工程链→SDS→Feature→Plan/Task→Code/Test；缺失或冲突的实际契约仍阻断依赖实现。
- Feature Ready、Implementation状态及DU写入认领各自只有原有权威；本迁移不新建状态轴。

## Task 1：规则与审计入口一致迁移

**Files:** 修改AGENTS.md、工程链、docs/README.md、SDS总册、gates总入口、Phase 1/2 README/gate-status、open-questions；修改scripts/validate_sds_phase1.py、validate_sds_phase2.py、sds_gate_contract.py、validate_prd_revision_016_alignment.py；新增scripts/tests/test_sds_change_review.py。

**Interfaces:** Phase命令默认只说明已退出阶段准入，输出NOT_RUN而非PASS；`--audit`显式运行全量内容审计，`--technical`兼容为显式审计选择，不是绕过业务检查。Python的validate函数保留内容校验，不再要求阶段批准。Phase 1不读取Phase 2物理合同；Phase 3不递归要求已退役的Phase 1/2批准。

- [x] **Step 1: 同步权威规则。** 取消阶段顺序/全文PRD身份/多文档状态同步作为日常准入；只在实际受影响的权威文档中修订事实。历史文件加非阻断说明而非改写结果。Q-TPLACC-001只保留真实API/Schema缺口，不自动关闭。
- [x] **Step 2: 写失败用例并运行。** 默认入口不得调用validate、不得打印PASS；显式审计必须运行并对真实问题返回1：

```python
with patch.object(module, 'validate', side_effect=AssertionError('full audit ran')):
    assert module.main([]) == 0
with patch.object(module, 'validate', return_value=['tenant key missing']):
    assert module.main(['--audit']) == 1
```

运行`python -X utf8 -B -m unittest scripts.tests.test_sds_change_review -v`；预期当前入口不接受新接口/仍运行全量校验而失败。

- [x] **Step 3: 实现最小入口。** 两个main接受可选argv，新增显式audit选择；无audit/technical时仅说明本次未运行审计并返回0；显式审计输出AUDIT-FAIL/AUDIT-PASS，不出现阶段获批/允许进入下一阶段。

```python
if not (args.audit or args.technical):
    print('[NOT_RUN] Full phase audit is opt-in; use change-scoped review.')
    return 0
errors = validate(args.root.resolve(), technical=True)
```

- [x] **Step 4: 拆除耦合。** Phase 1删除Phase 2必需文件及调用、阶段状态/固定README文字/批准校验；Phase 2按PRD存在选择内容路径，删除Phase状态/摘要计数同步条件。`validate_gate`不再把Phase 1/2当作准入，也不从Phase 3递归调用；Phase 3自身校验保留。普通物理检查/Schema执行证据不因整份PRD文字身份失效；显式全量溯源审计仍能报告旧身份，DDL与合同实际内容绑定保留。
- [x] **Step 5: 复跑新测试。** 上述unittest必须通过；默认命令不得读取全库，`--audit`的失败不能被转成成功。

## Task 2：保留实质负测并收口

**Files:** 修改scripts/tests/test_validate_sds_phase1.py、test_validate_sds_phase2.py、test_sds_revision_016_gate_contract.py；如有必要修改test_prd_revision_016_alignment.py。结果只记本计划/当前DU，不新增证据包。

**Interfaces:** 租户非空、Owner、唯一性、跨Owner外键、不可变快照、实际DDL漂移、Schema证据失败等原检查保持拒绝；旧阶段措辞/批准断言改为“不作为准入”，不是删掉实质拒绝测试。

- [x] **Step 1: 更新旧阶段治理断言。** 替换只要求Phase状态/README措辞/整份PRD身份的断言；保留每项真实契约负测及失败返回。历史审计函数若保留旧来源身份检查，必须显式选择，不能成为默认隐式依赖。
- [x] **Step 2: 聚焦回归。** 运行`python -X utf8 -B -m unittest scripts.tests.test_sds_change_review scripts.tests.test_validate_sds_phase1 scripts.tests.test_validate_sds_phase2 scripts.tests.test_sds_revision_016_gate_contract scripts.tests.test_prd_revision_016_alignment -v`。针对发生的实际失败检查源码/fixtures，不改业务或物理资产使测试通过。
- [x] **Step 3: 真实入口核验。** 默认两条Phase命令输出NOT_RUN；显式全量审计按现存内容返回结果，历史物理合同陈旧不得描述为已实质修复。检查现有CI文件、PRD/验收合同/物理资产未变。
- [x] **Step 4: 自审与本地提交。** 检查默认路径无全量遍历、无虚假PASS，受影响变更仍需实质审阅和测试；检查git diff、DU排他范围及原PR1行尾差异未暂存。按明确路径提交，不推送、不标Feature Done。

## 验证记录

- 新入口8项先失败后通过；五组聚焦工具测试169项通过（21.292s）。未修改实质拒绝测试使错误通过，旧阶段治理断言改为非准入语义。
- 默认两条Phase命令exit 0/NOT_RUN；显式Phase 1内容审计exit 0/AUDIT-PASS；显式Phase 2全量审计exit 1，保留10项历史来源/旧契约文案诊断，不构成当前变更全量阻断，也未声称已修复。
- 旧FCOM/FACC断言的9项现存诊断已用认领前代码复现；相关单元负测现在先验证正向legacy fixture，再逐项移除字段，避免原先文案已缺失导致负测假阳性。
- 额外Phase 3测试21/22通过，1个实时仓库审计测试仍失败。当前6项诊断均可在修改前复现（原有7项含全文身份），新增为0；其输入/脚本及实际专项限制未作范围外修复。
- 一次性计划用于本次治理代码迁移，不要求以后的普通修改新建计划/Phase文件。未执行业务应用或Schema运行验收，未推送。具体范围和结果见本DU。
