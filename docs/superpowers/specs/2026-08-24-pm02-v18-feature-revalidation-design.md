# PM-02 V1.8 Feature 重审与 Phase 推进设计

> 文档状态：`IN_REVIEW / NON_BASELINE`
> 适用基线：PRD V1.8、工程链 V1.8、SDS V1.8
> Requirement：`PM-02`，以及为关闭 PM-02 验收所需的 `PM-04`、`COM-01`、`CLO-01`、`CLO-02`关联契约
> 目标 Feature：建议登记为 `F-PROJ-002 项目拆分、项目树与进度汇总`
> 历史输入：NPDMS `features/F-PM02-*` 与 V60/V61 及其代码只作 V1.7 存量审计证据，不构成 V1.8 完成结论

## 1. 目标与边界

本设计用于按规格仓库工程链顺序完成 PM-02 的 V1.8 差量重验证：先修复 Phase 1 业务结构，再修复 Phase 2 实现契约，再完成 Phase 3 运行与测试保障，最后形成正式 Feature Spec。任一前序 Phase 未通过时不得提前编写 Technical Plan 或修改 NPDMS 业务代码。

本轮不修改 PRD 业务语义，不执行历史数据迁移、数据切换、UAT、部署或发布，不把 NPDMS 的现有树页面、服务、迁移和测试认定为已实现。

## 2. 已确认的 V1.8 差量

V1.7 的 F-PM02 Spec 和 Technical Plan 存在以下不能继承的范围收缩：

1. 将订单行、数量、办事处、序列号组合拆分和方案预览列为 Out of Scope，但 PM-02 V1.8 明确要求支持自由组合、预览及父范围守卫。
2. 将权重与汇总口径审批版本化列为 Out of Scope，但 PM-02 V1.8 要求审批、新版本、生效时间、批准人和历史快照不追溯重算。
3. 将 PM-04 多级权限整体后置，但 PM-02 V1.8 的每类查询、拆分和跨节点展示都要求先执行 `ProjectTreeScope`。
4. 只创建子项目或直接移动邻接字段，没有承接“失败时拆分申请保持草稿且不生成项目”的业务对象。
5. 声称项目树无稳定事件消费方，但 SDS V1.8 已定义 `ProjectTreeChanged`，消费者包括 Authorization、AST 和 ANA。
6. 只维护 `parent_id/root_id/tree_path/tree_depth`，没有落实 `ProjectAncestorProjection`、完整投影版本和 `proj_project_tree_change`追加历史。
7. 将父项目闭环守卫整体后置，但 PM-02 V1.8 要求全部后代闭环才允许父项目进入 CLO-02。
8. 仍引用“维护阶段”，与 V1.8 的 S0～S6、`ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED`状态模型冲突。

## 3. Phase 1：总体与业务结构

### 3.1 领域模型

正式 Phase 1 应明确以下业务结构：

- `Project` 仍是项目身份、直接父项目、生命周期和冻结模板的聚合根；项目编码与树位置解耦，移动不得改码。
- `ProjectSplitApplication` 承载拆分草稿、组合维度、范围项、方案预览、校验结果和最终应用结果。父项目不存在、跨租户、形成循环或范围超限时保持草稿，不创建子项目或父子关系。
- `ProjectHierarchy` 是 PROJ 拥有的当前父子真值；除根节点外同一时点只有一个直接父项目，不限制业务深度且必须无环。
- `ProjectAncestorProjection` 是按完整 `treeVersion`发布的可重建投影，不能代替直接父子真值；半完成版本不可对外查询或授权。
- `ProjectProgressAggregationPolicyRevision` 承载直接子项目权重、汇总口径、审批、批准版本和生效区间。历史进度快照引用当时的策略版本，不因新版本生效而重算。
- `ProjectProgressSnapshot` 保存来源事实版本、策略版本、计算结果和“待计算”原因；缺少有效子项目进度时不得静默使用 0 或旧结果。

### 3.2 Owner 与跨 Context 边界

- PROJ 拥有拆分申请、项目身份、项目树真值、祖先投影、汇总策略和进度快照。
- COM 拥有合同、订单、订单行和 `DeliveryScope`；PROJ 通过公开应用契约校验并消费可分配范围，不访问 COM Repository 或业务表。
- AST 拥有设备和序列号当前事实；PROJ 只通过公开应用契约校验拆分所引用的设备范围。
- 基础平台组织架构拥有公司、办事处部门及部门编码；PROJ 保存稳定引用和发生时快照，不把站点绑定到公司或办事处。
- ACC/CLO 拥有闭环申请和审批；闭环前通过 PROJ 公开契约取得全部后代闭环守卫结果，不直接读取项目树表。

### 3.3 状态与工作流

- 拆分申请至少区分草稿、已校验和已应用事实；校验失败回到或保持草稿，不形成项目中间状态。
- 用户确认拆分后，一个业务命令原子创建全部子项目、冻结各自模板/流程版本、实例化阶段/任务/里程碑/交付件/门禁、分配拆分范围并切换完整树版本。任一步失败整体回滚。
- 汇总策略调整必须提交配置的审批流程，批准后形成新版本和生效区间；不在 SDS 固化具体审批角色或节点。
- 父项目闭环守卫检查全部后代；任一后代仍在执行、暂停或关闭审批中，或任一汇总结果处于待计算，均拒绝进入 CLO-02 并返回具体未满足项。

### 3.4 权限

- 工程管理部和获授权服务经理可创建拆分草稿、预览并确认拆分；只有具备目标父项目范围和拆分权限的主体可应用方案。
- 项目经理只能维护本人负责项目节点的进度与交付事实，不能改变父子关系或生效权重。
- 同根树其他节点默认只开放名称、状态、阶段、里程碑进度、交付件目录和齐套状态；任务明细、人员、设备凭据、审批详情、敏感商务数据和交付件正文继续要求独立授权。
- 所有直接下级、全部后代、完整上级链、业务层级、拆分预览和汇总查询均由服务端先计算 `ProjectTreeScope`，前端参数不能扩大范围。

### 3.5 Phase 1 正式资产与 Gate

按顺序复验并在需要时修订：

1. `docs/design/01-requirement-traceability.md`
2. `docs/design/02-domain-model.md`
3. `docs/design/02a-context-map.md`
4. `docs/design/02b-aggregate-boundary-decisions.md`
5. `docs/design/02c-data-ownership-matrix.md`
6. `docs/design/02d-cross-context-contracts.md`
7. `docs/design/04-module-design.md`
8. `docs/design/05-state-machine.md`
9. `docs/design/06-workflow-design.md`
10. `docs/design/07-authorization-design.md`

Phase 1 Gate 只有在上述业务结构、Owner、状态、工作流和权限一致，且不再保留 V1.7 缩减语义时才可给出 PM-02 差量 `GO`。结论写入现有 `docs/engineering/gates/phase-1/gate-status.md`，不得另建平行 Gate 文件。

## 4. Phase 2：实现契约

Phase 2 在 Phase 1 `GO` 后依次冻结以下契约。

### 4.1 数据与数据库

- `proj_project` 保存直接父项目、根项目、不可人工写入的结构深度、业务层级标签及当前完整树版本引用。
- `proj_project_split_application` 保存草稿身份、父项目、模板版本、状态、版本和校验摘要。
- `proj_project_split_scope_item` 保存订单行、数量、办事处部门编码、序列号等组合范围及发生时快照；同一父范围不得被重复有效分配。
- `proj_project_tree_path` 保存完整祖先投影的 ancestor、descendant、distance 和 treeVersion；只允许完整版本成为 active。
- `proj_project_tree_change` 追加保存变更批次、移动/拆分前后父节点、原因、操作者、命令版本和结果，不覆盖历史。
- `proj_project_progress_policy_revision` 与明细表保存权重、口径、审批引用、批准人、版本和生效区间。
- `proj_project_progress_snapshot` 保存策略版本、来源事实版本、计算结果、待计算原因和生成时间。

所有表名、字段、主外键、唯一约束、空值策略、索引及映射必须进入 08/08a/09 和机器契约；不得直接修改已执行的 V60/V61，应使用新的前向迁移修正。

### 4.2 API 与命令

- 拆分草稿：创建、读取、更新组合范围、生成预览、重新校验、确认应用。
- 项目树：直接下级、全部后代、完整上级链、指定业务层级和节点定位，默认按需加载。
- 树变更：带 `Idempotency-Key`、`If-Match`或等价 expectedVersion 的拆分应用和子树移动命令。
- 汇总策略：创建修订、提交审批、批准结果应用、版本查询和当前策略查询。
- 进度：按策略版本计算并返回直接子项目明细、归一化权重、汇总结果、事实水位和待计算原因。
- 闭环守卫：由 PROJ 返回全部后代闭环与进度可用性结果，ACC/CLO 只消费公开契约。

接口必须返回服务端裁剪后的允许操作与数据，不接受客户端传入结构深度、树路径、计算结果、审批结果或扩大后的项目范围。

### 4.3 一致性、事件与并发

- 拆分应用在一个本地事务中完成范围再校验、全部子项目创建、F-PROJ-001模板实例化、范围分配、树真值、变更历史、完整投影版本、审计、幂等成功和 Outbox；失败不得留下部分子项目。
- 子树移动按稳定项目 ID 顺序加锁，校验目标父节点不是自身或后代，并以唯一 changeBatchId/treeVersion 切换完整投影。
- 发布 `ProjectTreeChanged`；幂等键为 changeBatchId + treeVersion，Authorization、AST 和 ANA 只能消费完整版本。
- 汇总策略批准和生效使用乐观锁与有效区间唯一性；重复审批回调不得重复生效。
- 进度快照按项目、策略版本和来源事实水位幂等，乱序事实不能覆盖更新版本。

### 4.4 Phase 2 正式资产与 Gate

按顺序复验并在需要时修订 08、08a、09、10、11、12、15、16 分册及对应 traceability contracts。Phase 2 Gate 必须证明数据、API、权限、事件、并发、幂等、回滚和跨 Context 契约可实现后，才能给出 PM-02 差量 `GO`。

## 5. Phase 3：运行与验证保障

Phase 3 在 Phase 2 `GO` 后完成：

- 性能数据集取实际迁移项目量两倍与 20 万项目的较大值，覆盖单项目树 1 万节点、直接子项目 2000 个和测试深度 30。
- 权限过滤后的直接下级、全部后代、完整上级链、指定业务层级和节点定位查询均满足页面响应时间不超过 2 秒（P95）。深度 30 只是测试规模，不限制更深合法结构。
- 覆盖拆分组合、范围超限、同一范围重复分配、跨租户、循环移动、并发移动、幂等重放、投影半版本不可见、权重审批版本、待计算、历史快照不重算和全部后代闭环守卫。
- 覆盖服务端权限负向测试，确认未授权响应不泄露其他节点业务明细且不产生业务副作用。
- UI 使用真实浏览器覆盖桌面、窄桌面、平板和手机视口；优先复用 Yudao 页面组件，其次使用 Element Plus 布局、样式和组件，自定义部分遵循 Element Plus 结构与主题变量，避免过多内联样式和页面级横向溢出。

按顺序复验 14、17、19、20 分册及 Phase 3 Gate。Phase 3 `GO` 只表示可进入 Feature Ready，不代表 SIT、UAT、部署或发布通过。

## 6. 正式 Feature Spec 目标

三阶段均 `GO` 后，正式 Feature Spec 应覆盖：

- Requirement、业务价值、Scope、Out of Scope 和依赖；
- 拆分草稿、组合范围、方案预览、原子应用；
- 任意深度项目树、完整投影版本和五类查询；
- `ProjectTreeScope`及同根树有限可见性；
- 版本化权重/汇总策略、进度快照和待计算语义；
- 全部后代闭环守卫；
- API、数据变化、事件、幂等、并发、审计、性能和真实浏览器验收；
- V1.7 现有实现逐项分类为复用、改造、退役或缺失，任何已有代码均不得直接勾选验收项。

Feature Ready 后，NPDMS 必须锁定包含该 Feature Spec 的规格提交并重新同步，再生成全新的 V1.8 Technical Plan。旧 `features/F-PM02-technical-plan.md` 不得作为完成判断或任务执行计划。

## 7. 推进顺序与停止条件

```text
PM-02 Phase 1差量修订与复审
-> Phase 1 Gate GO
-> PM-02 Phase 2差量修订与复审
-> Phase 2 Gate GO
-> PM-02 Phase 3差量修订与复审
-> Phase 3 Gate GO
-> 正式F-PROJ-002 Feature Spec
-> Feature Ready
-> 规格提交与NPDMS受管快照同步
-> 全新V1.8 Technical Plan
-> Implementation
```

发现会改变 PRD 语义的问题时标记 `BLOCKED_BY_SPEC`并回到 PRD/CHG；只影响后续 Phase 的问题登记后继续当前独立工作。任何 Phase 未 `GO`、正式 Feature Spec 未同步或 Feature Ready 未满足时，均不得开始 PM-02 业务代码改造。
