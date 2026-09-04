# F-CUT-007 割接首页授权KPI

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@f6141e21`
> Technical Plan Gate：`PASS / GO@fad19d81`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-01@V2=FULL`
> Feature Spec：`specs/features/F-CUT-007-cutover-dashboard-kpis.md`
> 机器合同：`specs/features/F-CUT-007-api-contract.json`、`specs/features/F-CUT-007-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-007-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-007-cutover-dashboard-kpis.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；本Feature无新增Flyway迁移`
> master复验：`CUT共享后端242项（跳过MySQL 27）与前端68项零失败；生产动作事实、真实MySQL与真实浏览器未闭合，Implementation Done Gate保持NOT_READY`

## 当前最小工作单元

- 形成CUT-01@V2授权KPI完整纵向Feature，不重复实现COM-01或拆出跨模块Provider碎片。
- CUT自有范围固定为只读实时聚合、REST、四张KPI卡片和真实动作守卫复用。
- 跨模块预留接口按正常闭环在CUT测试/受控验收中模拟；生产Fake、fallback和空成功Provider禁止注册。
- Feature Ready、唯一Technical Plan及Task 1～5均已通过；CUT自有闭环已在受控跨模块事实下形成，生产依赖接通仍阻断Implementation Done。

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
- Task 5在`7f1f44ed`完成独立MySQL 8.4全量迁移、真实MyBatis聚合与生产页面受控正向接线：MySQL 1/1、CUT后端聚焦28项（27通过、1项按`skipITs=true`跳过）、前端8文件53/53及`pnpm ts:check`通过；独立复审结论为`PASS / GO`。
- 最近Gate：生产Owner适配、唯一QueryService/Controller Bean与真实浏览器运行验收，继续`BLOCKED_BY_DEPENDENCY`。
- 当前状态：`BASELINE / READY / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`。跨模块预留接口仅在测试与受控验收中使用确定性替身，不构成生产事实。

## 状态边界

- F-CUT-002～006状态保持不变。
- Task 10生产Owner与真实浏览器阻断保持不变。
- COM-01由其他分支/Owner推进，本Feature不得重复实现或修改。

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`8`
- 已接收或已确认主干等价路径数：`35`
- 仍需逐路径适配记录数：`4`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `22ce22c6e913ae2e03b90e5bab95fb693eb7d2d1`
- `7d6a2886c06abd42a1deeb87a0ef6ccd34e33efd`
- `7f1f44ed77caadd16bb5241ac55209e8e225d51a`
- `a96b0b6fdcd941a132757a3ac41053ba8f50a3c7`
- `b9a3ab3259bd841b3a13bb75909e8c4e37ced51a`
- `d80fd05c4261efa734290b69968a76e087fc7562`
- `da1ea93e2cfa4d73bd7513d0c9ed3221058a3f09`
- `f5fd52f8e3f52377740f3398285915868162123b`
