# F-COM-001 合同订单关联与交付范围分配

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / REQUIREMENT_CONVERGENCE_APPROVED`
> Technical Plan Gate：`PASS / USER_APPROVED`
> Implementation Done Gate：`PENDING_RUNTIME_REVALIDATION_AND_INDEPENDENT_REVIEW`
> 当前阻断：`当前环境缺少隔离MySQL凭据，实库迁移/应用测试与真实浏览器验收未执行；Implementation Done独立裁决未完成`
> Requirement ID：`COM-01@V1`；协作`PM-03`、`PM-10`、`ACC-03`
> Feature Spec：`specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md`
> Technical Plan：`docs/superpowers/plans/2026-09-02-f-com-001-requirement-convergence.md`
> Delivery Unit：`tasks/delivery-units/DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE.md`

## 当前检查点

需求方已确认COM-A与COM-B承载不同需求，按Requirement整体合并。master已形成可构建增量：统一规格以项目办事处发生时快照作为COM唯一地点事实，COM-B的AST站点/位置迁入IMP/AST；PLT迁移证据Owner已随CUT旧数据核对依赖由`master代码回执c9066332`独立落位，COM仍只消费公开API。历史分支Gate与Done只作来源证据，不能转记master完成状态。

## 实施边界

- 以COM-A合同/订单副本、公司范围、DeliveryScope、ACC绑定、REST/UI闭环为代码基础。
- 吸收COM-B的批次event/前驱CAS、人工候选/对账、订单合同来源身份、项目范围水位和`getAssignedScope`。
- `CommerceAuthorityIngestApi`成为新批次主入口；`CommerceAuthorityWriteApi`仅在替代路径可用后标记废弃并保留兼容适配，新能力不得继续依赖旧接口。
- COM范围和DTO不得持有`siteId/siteLocationId/locationText/locationResolutionStatus`；设备序列号仍只通过AST公开校验契约验证。
- 不接收ACC-001/002、CUT、IMP业务实现，不实现ERP网络连接器或历史生产迁移。
- 来源分支V124～V127重新编号为master V160～V163；不得修改已执行V70/V72或与master/其他分支冲突的版本。

## Task 1：权威规格与工程链

- [x] PRD修订010、COM领域规格和唯一Feature Spec完成。
- [x] COM-A/COM-B旧实现复用、迁移和废弃边界完成。
- [x] master排他DU和实施计划完成。
- [x] PRD/SDS/Feature/追溯机器校验全部通过并提交上游基线。

## Task 2：COM-A纵向闭环适配

- [x] 选择性迁入COM/PROJ/ACC窄契约、Provider、业务实现、REST、UI和测试。
- [x] 将来源V124～V127重编号为V160～V163并更新全部引用。
- [x] 在当前master依赖上完成聚焦测试和模块构建，形成可构建增量提交。

## Task 3：COM-B非重复需求能力

- [x] 实现`CommerceAuthorityIngestApi`批次CAS并迁移受控导入调用。
- [x] 实现人工候选、不可变依据与ERP Owner对账。
- [x] 为订单—合同关系补齐稳定来源身份。
- [x] 实现项目范围版本水位和`DeliveryScopeApi.getAssignedScope`，统一全部写路径的递增与锁序。
- [x] 对已替代旧接口/旧模型加废弃标记，确认没有新调用继续建立在旧功能上。

## Task 4：拆分与整体验证

- [x] 确认PLT迁移证据、IMP/AST实施地点不进入COM代码、表、DTO或完成状态。
- [x] 完成迁移静态校验、权限负向、并发/幂等、前端测试及生产构建。
- [ ] 在具备隔离凭据的环境完成MySQL迁移和应用集成测试。
- [ ] 完成真实浏览器正向闭环及关键负向验收。
- [x] 更新Requirement矩阵与DU阶段回执。
- [ ] 完成Implementation Done独立裁决。

> 检查点：基线=PRD修订010；当前Gate=Implementation Done复核；已通过=master可构建增量与静态/单元/前端验证；阻塞=MySQL实库、真实浏览器、独立裁决；下一步=补齐运行证据后申请裁决。

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`35`
- 已接收或已确认主干等价路径数：`164`
- 仍需逐路径适配记录数：`87`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `05af6f3685ce5db5a13df0ec2cc3428e789d700c`
- `06593a42796076cb67392dcd71e4ab5ad29de665`
- `18237796431cbf779e6aabcef5563024cdd700fa`
- `319a616e0a135aadab8dcf675e5a81ffabe1c333`
- `32092b115a32262070d62b60bb3d429da3e496c6`
- `3412e38397776d471c6ea3867def2001609d5b46`
- `3b9e680a0ede81dd20c1075d8c0ad7982afc5073`
- `43d63dcd50b65d62ce6ec3a35de55ee7a9e22bc1`
- `4996c75465fd57599054a305e62cddea8fb75102`
- `563daac11db0ce09027c62b602e56e9544fdd4f6`
- `5abbc82ba866c4f3dafc3d5b186c0afdce1e9d0d`
- `5e56728152f642302bfee63e641465ab29b4af36`
- `63b6efc0a149bf713da6fbd9717ab6648f4cea79`
- `65639c9c913cf506430fb74fec88ba59be0c2501`
- `6bd13e416a4c914e08851cf40f2a161daa0b9f6a`
- `76a3dfc7721e427dbf0e80e89dd26aab6c87701c`
- `7c8b11fec472fd430d5a465af551f5431655fa8e`
- `7d578e3749e8a1262e1589d1d3342c98872d91aa`
- `835a1a57f17b483068cbd841b4861d153ef24147`
- `86ea27de4cf58d2b984c6c77cb7bab59c6729fd6`
- `8c2feeff72fe452cbb81bde305826002066c7aaf`
- `9fd37981611e926ff8bce2c39c13685e14f28499`
- `a8418dbb6800fe892ddb1a51b9380d149574b4a5`
- `aabf19d7009779dbd9e07c1581935390f4d56bc7`
- `ac8a6c9a39ed6abd35b3885fbaddd601ba26868c`
- `ae1968c63af614700bd586915e37c74ef1b0152b`
- `b6c0176c9ad0f4c130ab4ece83e42d7595dd3c52`
- `c541126b644ff28d72ad8735534a6b63f859c729`
- `c57ee7b5f5226f5dc902d817c034ff1a8f6618c3`
- `cc03787ec9c761358756da6320728928b47eaa39`
- `d8a275619ab20b2fa49e39f4bbb24be4ddc57a82`
- `dbfc8e5571852350d98e75da1bf0b3692df2b00d`
- `dd0a26eed23af025ef705d989d6f28d96cbd6ba4`
- `f25e0ebfd38c78e80937de4100a6564b35533da5`
- `f76525efcb720df2c7de12a17c0741e6d73d98c7`

## 代码事实时间序重放检查点（2026-09-04）

> 依据三个来源分支的实际提交代码逐项记录；代码接收不自动构成 Implementation Done。

- 来源分支：`codex/f-acc-001-sds`, `codex/f-cut-001-matrices`
- 代码事实记录：`500` 个提交-路径组合
- 重放顺序：全局提交时间、来源稳定顺序、分支拓扑顺序。
- 接收范围：全部模块；冲突只保留到具体文件或 hunk，不形成整提交、整模块或整分支拒绝。
- 详细清单：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv` 与稳定化报告。
