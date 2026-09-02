# F-CUT-007 割接首页授权KPI

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_CREATED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-01@V2=FULL`
> Feature Spec：`specs/features/F-CUT-007-cutover-dashboard-kpis.md`
> 机器合同：`specs/features/F-CUT-007-api-contract.json`、`specs/features/F-CUT-007-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-007-legacy-reuse-audit.md`
> 唯一Technical Plan：`未生成；Feature Ready通过前禁止生成`

## 当前最小工作单元

- 形成CUT-01@V2授权KPI完整纵向Feature，不重复实现COM-01或拆出跨模块Provider碎片。
- CUT自有范围固定为只读实时聚合、REST、四张KPI卡片和真实动作守卫复用。
- 跨模块预留接口按正常闭环在CUT测试/受控验收中模拟；生产Fake、fallback和空成功Provider禁止注册。
- 当前仅形成规格候选并申请Feature Ready复审，不进入Technical Plan、代码、迁移或测试实现。

## 候选边界

- `todoCount`只统计当前用户真实拥有至少一个P2～P6写动作的`NEW_PLATFORM`非归档任务，并按`taskId`去重。
- P5待办复用现有`myTodos`资格并叠加`ACTION_VIEW`可见范围；其他阶段复用现有服务端动作守卫。
- `archived/approving/rejectedPendingModification`按锁定状态谓词独立计数，允许与待办重叠。
- 不新增表、事件、缓存、权限或迁移，不修改旧割接页面/接口。

## 最近Gate

- `3c7e9192`首轮Feature Ready复审为NO-GO，唯一阻断是跨Owner动作守卫失败被错误压扁为CUT；本候选已按真实物理Owner补齐封闭ErrorData和依赖传播。
- `2d337775`单点复审确认Owner传播已关闭，仅指出`CommonResult<null>`无法承载ErrorData；当前已统一为403/500/503均返回非空`CommonResult<ErrorData>`。
- 最近Gate：`F-CUT-007跨Owner失败传播API/Physical Machine Contract最小整改复审`。
- 当前状态：`DRAFT / NOT_READY / REVIEW_REQUIRED / NOT_STARTED`。
- 通过前不得生成Technical Plan；通过后才允许按正向闭环顺序实施CUT查询、REST和工作台，测试使用受控跨模块替身。

## 状态边界

- F-CUT-002～006状态保持不变。
- Task 10生产Owner与真实浏览器阻断保持不变。
- COM-01由其他分支/Owner推进，本Feature不得重复实现或修改。
