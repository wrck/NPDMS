# F-IMP-002 到货签收与里程碑事实

> Feature实施状态：`IN_PROGRESS`
> 实施子状态：`TASKS_1_TO_11_COMPLETE / TASK12_PENDING`
> 总体工程阶段：`IMPLEMENTATION_PARTIAL / PRODUCTION_ASSEMBLY_PENDING`
> Feature Ready Gate：`READY / GO@4b5a2ac9`
> Technical Plan Gate：`PASS / GO@e0184ac4`
> Implementation Done Gate：`NOT_READY`
> Requirement：`EXE-01@V1=FULL`
> Feature Spec：`specs/features/F-IMP-002-arrival-acceptance.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`
> 来源实现：`codex/f-cut-001-matrices@eda54bd0c911641c0d977288ee63b3a1df81e69d`
> master集成：`DU-20260903-S0-S6-REQUIREMENT-SELECTIVE-INTEGRATION`

## 已实际实现并选择性进入master

- Task 1：`ArrivalAcceptanceFactApi`、查询/锁定重验DTO和结构化范围水位；
- Task 2：五张IMP Owner表及前向Schema；
- Task 3：DO、Mapper/XML、分页/详情、明细/差异/证据修订与稳定锁序；
- Task 4：DRAFT、PARTIALLY_ACCEPTED、DIFFERENCE_PENDING、ACCEPTED、CONFIRMED状态机与项目事实计算；
- Task 5：草稿创建、提交、项目经理确认、更正、补签、豁免、失效、线性后继链和事实版本；
- Task 6：`ImplementationEvidencePublished`、ACC回执模型、Outbox投递与重试逻辑；
- Task 7：消费端查询与锁定重验候选；
- Task 8：严格REST VO、错误分类及Controller候选；
- Task 9：字典、菜单、五项权限和三个保持PAUSED的Job；
- Task 10：新到货签收前端工作台、API和组件交互测试；
- Task 11：来源分支已记录MySQL 8.4指定套件40/40、较宽到货相关非IT回归168项零失败；
- AST `DeviceScopeFactApi`生产Owner支撑和平台Outbox事件白名单已同步接入。

## master迁移编号映射

来源迁移因master已推进至V192，选择性接收时按内容不变原则重排：

| 来源 | master |
|---|---|
| V133 | V193 |
| V134 | V194 |
| V135 | V195 |
| V136 | V196 |
| V137 | V197 |
| V138 | V198 |
| V139 | V199 |
| V140 | V200 |
| V141 | V201 |
| V142 | V202 |

迁移合同测试已同步改为读取V193～V202；旧V133～V142未进入master，避免低版本迁移在V192之后被Flyway忽略。

## 尚未完成：Task 12

- 使用master当前COM、AST和ACC生产事实完成最终Adapter复核；
- 唯一生产ApplicationService、FactApi与Controller Bean装配；
- ACC生产消费者闭环和三个Job激活裁决；
- 当前master全量依赖回归、真实MySQL复验、真实Chromium业务闭环；
- 独立Implementation Done裁决和Requirement投影收口。

## 安全边界

- Controller候选继续不注册`@RestController/@Component`，不得在Task 12前形成半装配生产入口；
- Job继续保持`PAUSED`；
- 不接收来源分支中的CUT/COM重复实现、生成追溯投影、旧迁移编号或生产Fake/fallback；
- Feature主状态保持`IN_PROGRESS`；Task 1～11完成表示代码已实施并进入master，不等于EXE-01完整Feature Done。

## 三分支按提交时间代码事实重放（2026-09-04）

> 状态以提交源码、测试、迁移、前端与构建文件为事实依据；Feature未关闭的Gate继续保留。

- 原实施状态记录：`> Feature实施状态：IN_PROGRESS`
- 当前实施状态：代码已接收；未完成Feature保持 `IN_PROGRESS`。
- 已接收代码路径：`5`
- 已处理来源提交：`8`
- 来源分支：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。
- 接收原则：按提交时间逐提交重放；任何单文件或单hunk冲突均不阻断其他模块代码。
- 完整逐提交、逐文件记录：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java`
- `sql/migrations/V133__fimp002_arrival_acceptance.sql`
- `sql/migrations/V140__fimp002_task5b_successor_fact_impact.sql`
- `sql/migrations/V141__fimp002_successor_batch_identity.sql`
- `sql/migrations/V142__fimp002_arrival_acceptance_seed.sql`
