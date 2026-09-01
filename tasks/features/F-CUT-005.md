# F-CUT-005 P5分级审批

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO @ 2e3fdba3`
> Technical Plan Gate：`PASS / GO @ 912d0cdb`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-05@V1=FULL`
> Feature Spec：`specs/features/F-CUT-005-p5-graded-approval.md`
> 机器合同：`specs/features/F-CUT-005-api-contract.json`、`specs/features/F-CUT-005-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`、`specs/features/F-CUT-005-candidate-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-005-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-005-p5-graded-approval.md`

## 当前最小工作单元

- Feature Ready已在锁定提交`2e3fdba3`独立复审GO；唯一Technical Plan已在`912d0cdb`独立复审GO，Task 1～7均已独立GO，当前进入Task 8。
- PROJ/SYSTEM生产候选Provider缺失不阻断受控替身规格与后续内核实现，但阻断生产完整装配、真实浏览器和Implementation Done。

## Gate清单

- [x] API/Physical/Candidate/Legacy Machine Contract Gate。
- [x] Feature Ready最终裁决：`GO @ 2e3fdba3`。
- [x] 唯一Technical Plan独立复审：`PASS / GO @ 912d0cdb`。
- [x] Task 1 CUT领域合同、消费端口与快照Codec：`PASS / GO @ e6dac9fe`。
- [x] Task 2 五表Schema、DO、Mapper与锁查询：`PASS / GO @ 367438e6`。
- [x] Task 3 审批启动、来源冻结与公开FactApi Provider候选：`PASS / GO @ df406b0c`。
- [x] Task 4 通过/驳回状态机与任务P4/P6原子迁移：`PASS / GO @ 6dae8751`。
- [x] Task 5 详情、本人待办、改派队列与管理员改派：`PASS / GO @ 0142e01e`。
- [x] Task 6 站内通知提交后投递与暂停Job：`PASS / GO @ e5aff408`。
- [x] Task 7 六路由REST、严格请求Codec与错误合同：`PASS / GO @ eefb40ec`。

## 最近检查点

- `5e3ce44c`方向成立但Feature Ready未通过；不得进入Technical Plan或实现。
- `2efad8ce`已关闭动作判别、候选交集和通知边界；快照可空性与管理员改派入口仍需定点复审。
- `2e3fdba3`关闭剩余两项并获Feature Ready正式GO；最近Gate为唯一Technical Plan独立复审。
- `1a45569f` Technical Plan首轮NO-GO仅指出生产Owner产出路径缺失与测试顺序冲突；当前候选只整改这两项。
- `912d0cdb`关闭Owner合同/SYSTEM阻断表达与实施顺序残留，Technical Plan正式GO；最近实施单元为Task 1。
- Task 1只新增CUT领域规则、三类消费端口、Owner异常、精确快照Codec与`src/test`受控正向事实；无生产Bean、DDL、COM或Yudao修改。
- `e6dac9fe`关闭冻结快照跨子事实身份一致性与COLLECTION可选引用正数约束，Task 1正式GO；最近Gate为Task 2 Schema/迁移与隔离MySQL正向验证。
- Task 2候选使用串行V153创建五张CUT Owner表，补齐DO、场景化Query、Mapper XML、节点CAS与通知领取；静态合同5/5及隔离MySQL 8.4全量迁移和正向约束验证通过，等待独立Gate。
- `367438e6`统一Yudao审计字段物理类型与本人待办稳定排序，Task 2正式GO；最近Gate为Task 3启动/冻结/FactApi正向实现审查。
- `df406b0c`关闭通知/暂停物理约束、业务摘要、路由快照及公开事务异常边界，Task 3正式GO；最近Gate为Task 4通过/驳回及P4/P6原子推进。
- `6dae8751`关闭Task 4锁序、冻结Owner事实重验、当前候选交集、根挂起及业务错误分类，独立复审正式GO；最近Gate为Task 5查询、allowedActions与改派正向实现。
- `0142e01e`关闭Task 5完整候选交集、hold恢复、通知版本键及待办投影身份一致性，独立复审正式GO；最近Gate为Task 6站内通知提交后投递与暂停Job。
- `e5aff408`以独立`REQUIRES_NEW` Provider Bean隔离SYSTEM通知事务，证明Provider失败回滚不影响CUT外层持久化`PENDING_RETRY`，独立复审正式GO；最近Gate为Task 7六路由REST与严格请求合同。
- `0f98d527`关闭审批成功后Owner刷新与结构化错误合同问题；`eefb40ec`进一步冻结实际决定节点并使同键同载荷重放直接复用平台结果，Task 7独立复审正式GO；最近Gate为Task 8实施前边界核验。

## 物理Owner支撑Task

- `T-FCUT005-PROJ-01`：PROJ拥有`ProjectCutoverServiceManagerFactApi`公开事实、锁定实现和合入顺序；当前仅预留合同，Provider未实施。
- `T-FCUT005-SYSTEM-01`：SYSTEM角色/成员/用户状态保持权威来源；当前`PermissionApi/RoleApi/AdminUserApi`不足以形成版本化锁定事实，Task自起点`BLOCKED_BY_DEPENDENCY`。只有SYSTEM物理Owner获明确授权并交付公开合同/Provider Gate后才恢复，禁止PMS平台自造版本、修改Yudao或直读其表。
- 两项Provider缺失不阻断Feature Ready后的CUT内核及`src/test`正向闭环，持续阻断完整生产装配、真实浏览器和Implementation Done。

## 依赖边界

- F-CUT-002/003/004为业务来源；当前允许在F-CUT-005单元/集成中使用已锁定合同的受控事实。
- PROJ当前服务经理与SYSTEM二线/研发候选均作为本Feature的物理Owner支撑Task预留正式端口，不建立纯Provider Feature；不得跨模块读表、修改Yudao或注册生产Fake/fallback。
- V2提前时间与外部通知、CUT-06闭环和`Q-FCUT004-001`均排除。
