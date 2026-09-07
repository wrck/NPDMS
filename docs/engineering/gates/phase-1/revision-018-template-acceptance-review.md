# 修订018模板与项目级验收的Phase 1差量复核

> 状态：`IN_REVIEW`；来源任务自审/独立复审输入，不代签Phase 1批准
> 日期：`2026-09-08`
> 基线：PRD V1.8修订018、ADR-0045及需求方已确认的项目级验收设计
> 审计起点：`3d82db0ba0d12d99a334b91d4db1c03b4aa787fd`
> 写入认领：`278ee1e987b350cc9839b95f92403b93d2ba9ae5` / `DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE`
> 唯一阶段状态：`docs/engineering/gates/phase-1/gate-status.md`

## 1. 本轮范围与判断

R2已取得候选技术GO，接收记录见`../phase-2/input/template-acceptance-r2-independent-review.md`。该结论仅关闭原候选的两项P2，不批准正式阶段、实施或Schema。本轮先使Phase 1分册准确表达已批准语义，继续保留所有正式Gate和Q边界。

| Requirement/边界 | 本轮正式SDS落位 | 自审判断 |
|---|---|---|
| PM-03、PM-11：模板、Stage/Task、WorkBinding、BusinessView | 02、02a、02b、04、06 | 节点负责组织/导航；单主绑定办理Owner实体，规则只消费结果；无第二套Template/任务/导航 |
| ACC-03：项目级主身份 | 02、02b、02c | tenant/project/acceptanceType单一根；报告版本演进，不按阶段、订单行、设备另建根 |
| ACC-03、COM-01：可选范围与保护 | 02、02a、02c、02d、05、06 | 未直接/显式前置依赖COM时不隐含要求范围；已配置依赖未知不放行；COM掌握数量/真实前驱，ACC掌握绑定/减量保护 |
| ACC-03、ACC-04：报告、历史完成和当前通过 | 02b、02c、02d、05 | 三类事实分离；新来源COMPLETED后可换版/撤销报告，历史完成不回退；零附件不造文件或通过事实 |
| PM-03、PM-11、ACC-03：受控触发 | 02a、02d、06 | 原审批事实可靠捕获与ACC命令分开；GET/readiness/视图渲染无创建副作用，未形成“先完成才能创建”的等待环 |
| ACC-03/04、CLO-01/02：权限与消费 | 05、06、07 | 独立入口重验项目及ACC权限；节点入口取权限交集；原actor及文件授权保留，不直接写PROJ阶段/项目终态 |

03系统架构仅读取复核，模块化单体/Owner公开边界不变。九份受影响分册头部更新为修订018且保持REVALIDATION_REQUIRED。其他Feature、版本切片、状态与既有Done不改；100需求/111切片不增加。已审R2正文、API、字段及算法不修改，仅登记接收结果。

## 2. 具体对齐而非新业务裁决

- 修正02中“当前总体范围必须通过”的无条件写法，限定于冻结规则要求的COM证据；显式初验前置仍按初验自己的规则重验。
- 在02/02b/02c明确项目级活动身份与各Owner事实，节点不是验收根；范围资格失效不等于保护消失。
- 05区分不可关闭的状态迁移完整性与可配置业务门禁，补齐已审的新来源报告换版/撤销及历史不回退；05的阶段范围重验同样限定为配置依赖。
- 06/07补齐可靠触发、权限交集和原执行身份，不增加审批节点、角色、生命周期或Job越权路径。

API方法、完整锁序、前驱列、归档/Outbox物理限制仍由R2候选供Phase 2回写；本轮不展开DDL，不把候选直接作为已批准物理合同。

## 3. 检查、已知限制与下一门禁

实施前运行`python -X utf8 -B scripts/validate_sds_phase1.py --technical`，得到两项FAIL：Phase 1 README缺少当前/历史入口说明；`carrier contract PRD identity is stale`。前者在本轮修正；后者来自`docs/traceability/sds-revision-016-physical-contract.json`对PRD身份的旧绑定（scripts/sds_gate_contract.py的MODEL），原受管物理合同未纳入本轮写边界，禁止只换身份使检查通过。

修改后运行相同命令仍FAIL，仅保留`carrier contract PRD identity is stale`；README问题已消除，其他检查未报错。已按PRD/ADR和R2逐项自审九份分册，并检查候选除review回执外正文/算法/字段与`3d82db0b`一致、未变更PRD/Feature/API源码/物理合同及认领外路径。独立Phase 1复审待返回；在存在未解决全量检查项和正式批准前，不将Phase 1标为APPROVED/READY。独立复审须分别判断“本次受影响逻辑边界”和“全量正式Gate”，不得混用R2技术GO。

后续依序完成正式SDS/API/Feature物理合同的Phase 2差量与适用Schema/P3-E09，再判断Q-TPLACC-001关闭和实现准入。本轮不修改Feature Ready/Done、PRD、业务代码、迁移或运行环境，不要求无关运行证据。
