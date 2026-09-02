# F-CUT-007 割接首页授权KPI

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@f6141e21`
> Technical Plan Gate：`PASS / GO@fad19d81`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-01@V2=FULL`
> Feature Spec：`specs/features/F-CUT-007-cutover-dashboard-kpis.md`
> 机器合同：`specs/features/F-CUT-007-api-contract.json`、`specs/features/F-CUT-007-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-007-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-007-cutover-dashboard-kpis.md`

## 当前最小工作单元

- 形成CUT-01@V2授权KPI完整纵向Feature，不重复实现COM-01或拆出跨模块Provider碎片。
- CUT自有范围固定为只读实时聚合、REST、四张KPI卡片和真实动作守卫复用。
- 跨模块预留接口按正常闭环在CUT测试/受控验收中模拟；生产Fake、fallback和空成功Provider禁止注册。
- Feature Ready、唯一Technical Plan及Task 1～4均已通过；当前进入Task 5受控正向MySQL与统一页面闭环。

## 候选边界

- `todoCount`只统计当前用户真实拥有至少一个P2～P6写动作的`NEW_PLATFORM`非归档任务，并按`taskId`去重。
- P5待办复用现有`myTodos`资格并叠加`ACTION_VIEW`可见范围；其他阶段复用现有服务端动作守卫。
- `archived/approving/rejectedPendingModification`按锁定状态谓词独立计数，允许与待办重叠。
- 不新增表、事件、缓存、权限或迁移，不修改旧割接页面/接口。

## 最近Gate

- `3c7e9192`首轮Feature Ready复审为NO-GO，唯一阻断是跨Owner动作守卫失败被错误压扁为CUT；本候选已按真实物理Owner补齐封闭ErrorData和依赖传播。
- `2d337775`单点复审确认Owner传播已关闭，仅指出`CommonResult<null>`无法承载ErrorData；当前已统一为403/500/503均返回非空`CommonResult<ErrorData>`。
- 错误Envelope单点机器合同整改已在`b65af8e4`通过独立复审：`PASS / GO`。
- Feature Ready最终状态关闭已在`f6141e21`通过独立复审：`READY / GO`。
- 唯一Technical Plan验证编排整改已在`fad19d81`通过独立复审：`PASS / GO`。
- Task 1共享P2～P6动作语义与批量事实合同已在`b9a3ab32`通过独立复审：`PASS / GO`。
- Task 2批量候选查询与KPI服务已在`d80fd05c`通过独立复审：`PASS / GO`。
- Task 3 KPI REST契约与受控正向MockMvc已在`f5fd52f8`通过独立复审：`PASS / GO`。
- Task 4统一工作台KPI卡片已在`da1ea93e`通过独立复审：`PASS / GO`。
- Task 5候选已完成独立MySQL 8.4全量迁移、真实MyBatis聚合与生产页面受控正向接线：MySQL 1/1、CUT后端聚焦28项（27通过、1项按`skipITs=true`跳过）、前端8文件53/53及`pnpm ts:check`通过；当前等待独立复审。
- 最近Gate：`Task 5 Controlled Positive MySQL / Unified Page Acceptance Gate = REVIEW_REQUIRED`。
- 当前状态：`BASELINE / READY / IN_PROGRESS`。按正向闭环顺序实施CUT查询、REST和工作台；跨模块预留接口只在测试与受控验收中使用确定性替身。

## 状态边界

- F-CUT-002～006状态保持不变。
- Task 10生产Owner与真实浏览器阻断保持不变。
- COM-01由其他分支/Owner推进，本Feature不得重复实现或修改。
