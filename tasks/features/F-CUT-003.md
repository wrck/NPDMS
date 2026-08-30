# F-CUT-003 P3动态采集清单、直接填写与人工降级闭环

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@ea986d61`
> Technical Plan Gate：`PASS / GO@ac740458`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-03@V1=FULL`
> Feature Spec：`specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-003-p3-dynamic-checklist.md`
> 前置Feature：`F-CUT-001`、`F-CUT-002`
> 外部硬依赖：F-CUT-002生产Owner接线只阻断生产Controller装配、真实浏览器和Implementation Done；不得注册生产Fake或复制其他Context Owner

## 当前最小工作单元

- 当前进入Task 1，只实现CUT自有三表、任务冻结配置读取、匹配、填写、人工降级与提交内核。
- 正向收益优先：先打通A/B/C按冻结配置生成DRAFT，DIRECT/MANUAL/CUSTOM保存，并原子提交到P4；不建设权限、冲突、乱序、跨租户、Provider失败或重试测试矩阵。
- V147落文件前重新确认连续空闲迁移号；若已被并行迁移占用，只整体顺延编号，不改变已批准语义。

## Task 1：CUT清单内核与前向迁移

状态：`IN_PROGRESS`

- [ ] 为`cut_task`增加冻结配置三元组，并按获批历史规则完成NEW_PLATFORM唯一补齐；LEGACY_FORWARD保持空。
- [ ] 新建`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`及最小权限菜单。
- [ ] 实现按冻结revision读取、稳定匹配、GAP/CONFLICT、DIRECT/MANUAL/CUSTOM结果和Generate/Rematch/Save/Submit命令。
- [ ] 复用F-CUT-002 Owner锁序、平台幂等与PLT公共文件事实；不实现第三方采集或外部数据Provider。
- [ ] 实现完成后验证历史唯一补齐、A/B/C正向生成与保存、提交后Checklist=SUBMITTED且Task=P4；只运行CUT聚焦验证和受影响后端构建。

完成口径：CUT内核与迁移具备可执行正向链后直接进入Task 2，不单独申请Implementation Done。

## Task 2：P3工作台与一次正向验收

状态：`NOT_STARTED`

- [ ] 在生产Owner可用后接入清单REST与文件策略；依赖未到位时保持`BLOCKED_BY_DEPENDENCY`，不使用生产Fake。
- [ ] 在现有P3工作台接入Schema控件、冲突选择、DIRECT填写、CUSTOM项、MANUAL证据、暂存和提交。
- [ ] 完成后运行定向前端验证，并以正式身份完成一次“A级P3→生成→DIRECT+MANUAL→暂存刷新→提交→P4”真实工作台闭环。
- [ ] 更新本Task与Feature追溯检查点，形成单一Implementation Done候选；不扩充异常Chromium矩阵。

## 完成边界

- Implementation Done只在两项Task完成、生产Owner真实接通、一次正式工作台正向链和数据库事实一致后申请。
- 本Task不包含INT-12/DAC Provider、V2导出、P4/P5/P6业务、旧`pms_cut_risk`改造或固定角色授权。

> 检查点：基线=`28e6db2c`；当前=Task 1 IN_PROGRESS。冻结revision精确读取及READY/GAP/CONFLICT匹配已实现，受影响Reactor编译成功、聚焦3/3通过；三表/V147正并行落位。下一步接持久化与DIRECT/MANUAL/CUSTOM提交P4正向链，跨模块仅用test替身。
