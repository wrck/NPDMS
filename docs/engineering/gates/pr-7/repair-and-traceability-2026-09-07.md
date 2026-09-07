# PR #7 当前版本修复与追溯边界

> 状态：`IN_PROGRESS / NOT_A_GO_DECISION`
> 审计起点：`757ec7c8eb4df9b18658191176ae5e029f0fbcc5`
> 实施认领：`DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR`
> 权威顺序：PRD、工程链、SDS、Feature Spec、Feature Task；本文件只记录修复与验证证据。

## 已进入候选的确定性修复

| 审查项 | Requirement / Feature | 代码与验证边界 |
|---|---|---|
| R01 COM同名测试重复 | COM-01 / F-COM-001 | 保留更强的负向用例，同时断言平台幂等与业务候选均未写入；不删除负向测试 |
| R02 CUT重复Mockito桩 | CUT-03 / F-CUT-003 | 只去掉第二次相同rematch桩注册，不改变生产服务或业务断言 |
| R03 就绪事实非法成员 | EXE-06 / F-IMP-001支撑合同 | sourceObjectIds必须为非空正整数；devices/sourceFacts/watermarkEntries拒绝null成员；Owner错误与调用方错误保持稳定分类；新增7项JUnit回归，不实现生产Provider |
| R08 真实Outbox夹具 | INT-12 / F-INT-012平台支撑 | CollectionTaskMySqlTest导入真实PlatformTransactionalOutboxWriter；不以空mock替换事务保障 |
| R09 前端runner | PM-01及既有位置/安装契约 | 五个node:test套件由Node单独执行；Vitest只排除这五个并保留默认排除集；旧工勘页面保持已批准的历史只读，不恢复旧写入口 |
| R10 后继迁移引用 | COM-01 / F-COM-001 | 旧测试读取实际V162/V163，不重建已排除V209/V210，不改写SQL |
| R13 Python重复辅助函数 | COM-01 / F-COM-001 | 恢复master同一组规范辅助函数；全部17项活动测试类AST不变。含重复块及未被调用的来源辅助函数的原文件完整保存在archive，不据此减少活动测试用例 |

核心修复提交为`bb2442a3`。其Java25全Reactor `-fae -DskipITs=true clean verify`在run `34071313048`的backend job已成功；该命令不代表skipITs=false的MySQL集成测试或真实浏览器验收。其真实MySQL已通过PLT采集幂等、IMP到货与ACC套件，但暴露CUT旧夹具及COM字段映射问题；扩大回归尚未全绿。

## R05 / R06：纠正任务与路径投影

原Task尾部由重放器把来源路径统称为“已接收”，同时把AST的REVALIDATION_REQUIRED和CUT的IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES批量改成IN_PROGRESS。这既丢失状态细节，又把已排除/改号路径误报为当前实现。

16份Feature Task与Feature索引恢复至master@25b9d8cd的权威正文，不改变已有Task内容、未完成范围或Done Gate。移出的重放尾部仍可由审计起点及不可变原始CSV还原；不删除原始证据。Requirement生成器重新执行后，Markdown矩阵和JSON覆盖均与同一master权威输入一致，不手工修改覆盖值。

新增`scripts/generate_pr7_reception_projection.py`输出来源SHA、来源路径、当前物理路径和处置类别：

- PRESENT_NOT_ACCEPTANCE_PROOF：文件存在，不是行为等价或验收证明；
- EXCLUDED_BY_OWNER_SELECTION：在已记录Owner选择提交中删除；
- EXCLUDED_EXTERNAL_SPEC_SNAPSHOT：不接收第二规格仓快照；
- SUCCESSOR_CANDIDATE_REQUIRES_REVIEW：同名后继候选，不自动宣称迁移语义等价；
- ABSENT_REQUIRES_REVIEW：仍需逐项裁决。

CUS服务等级支撑的10条来源记录、8个路径按正式合同归到消费者F-CUT-002，Owner仍为CUS，CUS-02@V2仅为支撑引用，不产生Requirement完成覆盖。该精确路径规则置于分支兜底之前。新增4项投影回归防止重新把不存在路径或支撑合同标成已完成。

复现命令：

```bash
python3 scripts/generate_pr7_reception_projection.py \
  --output /tmp/pr7-source-path-disposition.csv \
  --summary /tmp/pr7-source-path-summary.json
python3 -m unittest discover -s scripts/tests -p test_pr7_reception_projection.py -v
```

投影不保存Feature状态，不构成第二状态源。原始JSON/CSV字节保持不变。677条原始pending经通用最终树选择后登记归零，仍不是677条语义验收；R07的逐项行为证明不能由本脚本代替。

## 归档而非绕过测试

`archive/test_specification_baseline.py.txt`保留被重放带入、依赖已废弃第二规格仓模块的原测试。当前AGENTS/工程链明确唯一仓库权威且不维护外部规格快照，因此不为了导入成功重新创建该子系统。活动测试改为检查本仓库正式输入、禁止恢复外部快照与Requirement派生边界；正式追溯生成器及其原测试仍执行。

归档旧架构套件不等于它通过验收。扩大Python治理套件中其余SDS、迁移摘要、旧PRD修订引用等失败仍需独立处理，不通过skip或改写PRD掩盖。

## 保持开放的边界

R04租户0运行基线与正整数公开合同冲突仍须Owner裁决，不静默切换租户。R11历史DU缺字段/索引问题不得补造旧认领或审批。R12既有环境执行过V161的Flyway历史校验和受控例外证据未取得，空库SQL/V160升级不能替代。R07完整语义重放、生产Provider、真实浏览器和人工Owner Review均未因此关闭；PR不得据本报告转为GO或合并。
