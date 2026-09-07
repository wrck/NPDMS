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
