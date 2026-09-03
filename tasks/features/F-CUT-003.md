# F-CUT-003 P3动态采集清单、直接填写与人工降级闭环

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@ea986d61`
> Technical Plan Gate：`PASS / GO@ac740458`
> Implementation Done Gate：`BLOCKED_BY_DEPENDENCY`
> Requirement：`CUT-03@V1=FULL`
> Feature Spec：`specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-003-p3-dynamic-checklist.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源V147/V148 -> master V178/V179`
> master复验：`CUT共享后端242项（跳过MySQL 27）与前端68项零失败；真实MySQL、生产Owner装配和真实浏览器未复验，状态保持IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`
> 前置Feature：`F-CUT-001`、`F-CUT-002`
> 外部硬依赖：F-AST-002公开产品类型能力已选择性进入master，但CUT生产写事务内原子重验合同与唯一装配尚未闭合；`src/test`受控替身只支撑CUT自身正向验证。生产API接线继续阻断生产Controller装配、真实浏览器和Implementation Done

## 当前最小工作单元

- 当前进入Task 2，在Task 1已通过基础上接通P3工作台，并继续补齐自定义项移出与采集请求。
- 正向收益优先：先打通A/B/C按冻结配置生成DRAFT，DIRECT/MANUAL/CUSTOM保存，并原子提交到P4；不建设权限、冲突、乱序、跨租户、Provider失败或重试测试矩阵。
- V147落文件前重新确认连续空闲迁移号；若已被并行迁移占用，只整体顺延编号，不改变已批准语义。

## Task 1：CUT清单内核与前向迁移

状态：`PASS / GO@372f6895`

- [x] 为`cut_task`增加冻结配置三元组，并按获批历史规则完成NEW_PLATFORM唯一补齐；LEGACY_FORWARD保持空。
- [x] 新建`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`及最小权限菜单。
- [x] 实现按冻结revision读取、稳定匹配、GAP/CONFLICT、DIRECT/MANUAL/CUSTOM结果和Generate/Rematch/Save/Submit命令。
- [x] 复用F-CUT-002 Owner锁序、平台幂等与PLT公共文件事实；F-AST-002产品类型只保留CUT消费端口，使用`src/test`受控正向替身，不实现或注册跨模块生产Provider。
- [x] 验证历史唯一补齐、A/B/C正向生成与保存、提交后Checklist=SUBMITTED且Task=P4；CUT模块54/54及受影响Reactor构建通过。

完成口径：CUT内核与迁移具备可执行正向链后直接进入Task 2，不单独申请Implementation Done。

## Task 2：P3工作台与一次正向验收

状态：`IN_PROGRESS / FRONTEND_CONTROLLED_LOOP_PASS_GO@ec268ab9 / PRODUCTION_ACTIVATION_BLOCKED_BY_DEPENDENCY`

- [x] 已用显式测试装配实现CUT清单REST与文件策略候选；生产Owner未就绪时不注册生产Controller/Service/Fake，生产激活保持`BLOCKED_BY_DEPENDENCY`。
- [x] 在现有P3工作台接入Schema控件、冲突选择、DIRECT填写、CUSTOM增删、COLLECTION请求/刷新、MANUAL证据、暂存和提交；服务端`allowedActions`控制入口。
- [x] 使用真实Spring事务、MyBatis、MySQL 8.4与平台幂等/审计，配合仅存在于`src/test`的跨模块受控替身，完成A级P3生成→DIRECT+MANUAL→提交P4的数据库正向闭环；隔离空卷迁移至V156，聚焦MySQL测试1/1通过。
- [x] 补齐并运行挂载组件MANUAL证据选择→刷新后版本→提交P4正向交互；定向Vitest 5/5、`ts:check`和`build:local`通过，未用源码文本断言替代组件行为。
- [ ] 完成后运行定向前端验证，并以正式身份完成一次“A级P3→生成→DIRECT+MANUAL→暂存刷新→提交→P4”真实工作台闭环。
- [ ] 更新本Task与Feature追溯检查点，形成单一Implementation Done候选；不扩充异常Chromium矩阵。

Task 2增量状态：`23dff6cd`的CUSTOM移出、同一CollectionTask异步收敛、P3服务端动作投影及`CutoverChecklistItemResultLinked`同事务Outbox已独立复审`PASS / GO`；`d3161d9d`的真实MySQL受控正向闭环已独立复审`PASS / GO`。Controller/Service仍无生产注册，生产Owner装配与真实工作台验收尚未完成，Task继续`IN_PROGRESS / PRODUCTION_ACTIVATION_BLOCKED_BY_DEPENDENCY`。

## 完成边界

- Implementation Done只在两项Task完成、生产Owner真实接通、一次正式工作台正向链和数据库事实一致后申请。
- 本Task不包含INT-12/DAC Provider、V2导出、P4/P5/P6业务、旧`pms_cut_risk`改造或固定角色授权。

> 检查点：Task2正向REST/UI、CUSTOM/COLLECTION工作台最小整改已分别在`c8c75ce5`、`23dff6cd`独立复审`PASS / GO`；`d3161d9d`真实MySQL受控闭环与`ec268ab9`挂载组件MANUAL证据→刷新→提交P4交互均获独立裁决`GO`。最近Gate为跨模块生产Owner依赖关闭后的唯一生产装配；正式身份真实MySQL/浏览器和Done仍阻断。

## 代码事实选择性合入检查点（2026-09-03，ACC/INT/CUT三分支）

> 依据提交代码事实记录；Feature状态保持原值，代码接收不自动构成Implementation Done。

- 来源分支：`codex/f-cut-001-matrices`
- 本轮接收路径数：`1`
- 接收粒度：提交、文件；单个冲突或不符合文件不阻断同分支其他实现。
- 冲突与适配项见 `docs/traceability/code-fact-three-branch-integration-2026-09-03.md`。

已接收路径：

- `sql/migrations/V210__received_fcut003_p3_dynamic_checklist.sql`
