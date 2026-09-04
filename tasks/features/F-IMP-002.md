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

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`39`
- 已接收或已确认主干等价路径数：`127`
- 仍需逐路径适配记录数：`89`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `0267ef4d0d4f9d91a64fd09caaabc18c12819954`
- `0564ec7e1194e9116058734e90430d4bfcb330e8`
- `062d1e846cbdfe0d9588dbb7235e358861a4aeef`
- `085015c1710d1f8b0d326e63994b18ff265e765f`
- `08ee613b59ea88e04c51cb2dc9671c4a6be552ec`
- `0cf2ba79aea969689696aac962c9a53b9fa65ab1`
- `18c2b0ec569f10174817e3c23a22abe264a90bda`
- `1b2b6a752defe9ebf187521dd417c71e43699c0e`
- `1eec2fbc12fc97862952888b4f44b6508d2c736c`
- `2bb1dbc0b34a4ec1b758bf839da9824ed0322529`
- `35c0db90a3ad20cbdd864cd85c03e01f9469abb3`
- `54383436951d4afa0a8b884b4718406e774ee619`
- `65a6b395e692b463f070ffc784a9e9fc4461e431`
- `6793c1d96efc3e421085ecb539f307ca88b8d1e3`
- `6ce766596c819dea691478cc20479464a809633c`
- `774aeb11bd663645a6f29e32624a00073979bcc5`
- `7fa32fc50ba4886a55379185682f357e01fc4e03`
- `808151ce4662c85b8ea1c5267bc52b4d4699fd1d`
- `80a8d4221a2a7973295ca0e12b2e7283cf60ba59`
- `8370d0f19be603dc6429d7a0cb4ffff80262cab8`
- `871cfcbb5e5c532dd2b2ab8741b5c23d5d21445f`
- `935324cf381a7ef61404ad854039ec1142dc86f7`
- `9561384bda94cbc9ce0616434f301d1886977b74`
- `99bc69ff4c0af15a2fc20178fc3eab6432417339`
- `9a4763e85d10d820f957eeec90a71be3d4914fae`
- `a3099280c782be16b891ce4cbe4c13d69cda6347`
- `b63b5a0c47818845eefd567c44cd9e1c3077d2db`
- `b943461c9f59714f02260e34f78505c8f091a037`
- `c071450250a7bb65110ba2ed12ebac4d011a2253`
- `c3bde6fe599b327e9dc8bf2a3ac315ef294c8ed7`
- `c649c4245b3e13de39abdf89899d2a1195483a4d`
- `ce0447ecb867b5478b95440482d998d773e006be`
- `d5d8e978170bedd36a86de5f29696b5304bddc39`
- `d71ced4091a1830fb8718db6c4eb1badaff4c203`
- `dd374c5f8ae494d6b775809dd69a4a2c3f655603`
- `ddc928d02f19f244fbb3c167046d2f8b45bf8d36`
- `dfcc224c842a59addc0f0ddcc6373b49a664cb2a`
- `f1ecb73d7a7532789e56c3795655a805c3bec9cd`
- `fb69dbcc07c87324a63ec789df9e2bb977f29204`
