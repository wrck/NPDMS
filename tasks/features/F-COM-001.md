# F-COM-001 合同订单关联与交付范围分配

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / REQUIREMENT_CONVERGENCE_APPROVED`
> Technical Plan Gate：`PASS / USER_APPROVED`
> Implementation Done Gate：`PENDING_RUNTIME_REVALIDATION_AND_INDEPENDENT_REVIEW`
> 当前阻断：`合同关联与范围分配局部实库/浏览器验证已通过；其余AC的完整运行复核与Implementation Done独立裁决未完成`
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

> 检查点：基线=PRD修订010/014；当前Gate=Implementation Done复核；已通过=master既有增量及下述局部实库/浏览器链；剩余=其余AC运行复核与独立裁决。

## 2026-09-07 合同关联与范围分配运行修复

- 沿用已在master认领的`DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR`，Requirement为COM-01；保留replay可复用来源，不恢复旧表、旧状态或重复迁移。
- 实际断点：已有4台范围调整为6台时，预览按新增分配判断自身冲突，按钮始终禁用。调整预览现携带当前范围ID/版本，排除自身占用，并保持项目/订单归属、版本和ACC减量守卫。
- 合同权限拒绝原本以未处理异常返回业务码500，现使用403；真实无权关联请求仍被拒绝，未放宽权限。
- 隔离环境`npdms-pr7-6644`，租户1；宿主后端58286、前端18086，原58086处于Windows保留端口段。测试数据前缀`FCOM-UI-1788777648045`，项目`992203060003`。
- 真实浏览器完成ERP合同/订单展示、项目关联、分配4台、调整6台、释放和刷新历史；分配版本1/2、项目范围水位1/2/3，两条RELEASED历史均保留，页面异常为0。
- 后端25项、前端7项聚焦测试及TypeScript检查通过；后端制品构建通过。未修改SQL或数据隔离机制，本轮不宣称全部Feature Done。
