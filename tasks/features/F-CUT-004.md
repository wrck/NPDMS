# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@644816f2`
> Technical Plan Gate：`PASS / GO@9ef7545d`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`

## 当前最小工作单元

- Task 1～5均已通过；当前进入Task 6的REJECTED、SOURCE_REPLACED与批准后联系人变更正向闭环；DUTY_CHANGED按`Q-FCUT004-001`保持`BLOCKED_BY_SPEC`。
- 后续按计划先完成每个Task最小正向实现，再补正向验证；生产CUT-05/PLT Provider缺失继续阻断生产装配、浏览器和Implementation Done。

## Gate清单

- [x] 独立Feature边界裁决：CUT-04独立于CUT-05。
- [x] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过（`87b0b066`）。
- [x] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过（`87b0b066`）。
- [x] API/Physical/Legacy Machine Contract Gate通过（`87b0b066`）。
- [x] Feature Ready最终裁决通过（状态基线`644816f2`）。
- [x] 唯一Technical Plan独立复审通过（`9ef7545d`）。

## Task 1：CUT-05审批消费Java合同

状态：`PASS / GO@38fd6cfd`

- [x] 实现`CutoverApprovalFactApi`、精确Command/Query/Fact/Result records与稳定公共异常。
- [x] 在`src/test`提供确定性受控审批事实实现并补实现后的合同测试（5/5通过）。
- [x] 通过独立Contract/Code Review Gate；不注册生产审批Bean（`GO@38fd6cfd`）。

> 检查点：独立复审确认四方法、精确records、封闭状态/错误与`src/test`正向链成立，无生产Provider或fallback；Task 1 Gate通过。

## Task 2：三表Schema、阶段前向约束与Mapper合同

状态：`PASS / GO@e8134586`

- [x] 使用实际下一空闲Flyway版本`V150`前向创建三表并收敛P4/P5/P6阶段约束。
- [x] 实现DO、场景Query、Mapper XML及迁移/Mapper合同测试（6/6通过）。
- [x] 通过独立Schema/迁移Gate；不写业务Service（`GO@ddda602f`）。

> 检查点：独立复审确认V150、三表、Mapper及两值CHECK约束成立；Task 2 Gate通过，进入Task 3。

> 迁移整改检查点：Task 4隔离MySQL 8.4.10空库复验发现原V150的`chk_cut_stage_trigger`顶层`COALESCE`被判为非布尔表达式；`e8134586`仅追加显式`= TRUE`，新空卷V1→V150、三表和原七条转换复验通过，独立裁决恢复Task 2 `PASS / GO`。

## Task 3：内容Codec、来源冻结与PLT文件事实消费端口

状态：`PASS / GO@b6ca8f71`

- [x] 实现三种可写方案联合、legacy只读联合及严格内容Codec。
- [x] 实现来源冻结和值对象，并预留最窄`CutoverPlanFilePort`。
- [x] 使用`src/test`受控端口完成正向聚焦测试并通过独立Domain/Port Gate（5/5通过，`GO@b6ca8f71`）。

> 检查点：独立复审确认DRAFT/完整性分层、D模板集合语义和正向受控测试成立；Task 3 Gate通过。生产PLT初稿生成仍阻断后续生产闭环。

## Task 4：创建、保存与详情正向闭环

状态：`PASS / GO@08d98e0b`

- [x] 根据独立裁决形成`createDraft`封闭请求联合与`PlanSourceSnapshot.failedRiskFacts`完整冻结规格候选。
- [x] 补齐创建事务的`ASSESSMENT_STALE/CHECKLIST_STALE`来源陈旧错误闭包；D级不产生清单陈旧。
- [x] Task 4可执行性机器合同最小补丁独立复审通过（`GO@94157db2`）。
- [x] 实现创建/保存命令、版本与幂等事务边界；同键同载荷重放在可变Owner/聚合重验前返回首次结果。
- [x] 实现当前详情、legacy只读投影与服务端`allowedActions`。
- [x] 使用Task 3受控端口完成标准/简易/上传正向聚焦测试及真实MySQL原子性证据，并通过独立Application/MySQL Gate（`GO@08d98e0b`）。

> 检查点：独立复审确认NEW_PLATFORM来源快照规范投影闭包，Task 4 Application/MySQL Gate通过。正向聚焦测试17/17、隔离MySQL真实平台命令1/1；生产Service Bean、PLT初稿生成及CUT-05审批Provider仍不接通，受控替身只用于`src/test`。

## Task 5：初稿下载、提交P5与来源失效

状态：`PASS / GO@d559c02c`

- [x] 实现下载初稿文件事实但不推进plan/task版本（`bd4ead05`）。
- [x] 实现提交与CUT-05审批启动同成同败、DRAFT→SUBMITTED、P4→P5及阶段历史（`a9b87b47`）。
- [x] 实现来源失效暂停审批、SUBMITTED→INVALIDATED、P5→P4及阶段历史（`fe50aaf9`）。
- [x] 使用受控PLT/CUT-05端口完成正向链并通过独立Submission/MySQL Gate（`GO@d559c02c`）。

> 检查点：独立复审确认下载前锁定重验冻结来源、提交/失效状态与事务闭环成立；正向聚焦12/12、隔离MySQL 2/2通过。Task 5 Gate通过，生产PLT/CUT-05仍阻断生产装配、浏览器和Implementation Done。

## Task 6：修订链与批准后联系人变更

状态：`IN_PROGRESS / DUTY_CHANGED_BLOCKED_BY_SPEC`

- [ ] REJECTED派生新DRAFT并保留旧提交/审批历史。
- [ ] PAUSED_SOURCE_INVALIDATED派生SOURCE_REPLACED，新revision提交时引用旧审批实例。
- [ ] 派生DRAFT以当前锁定Owner事实重建设备/风险投影，只继承允许的用户内容；等级/编辑方式或模板不可唯一映射时插入前失败关闭。
- [ ] APPROVED后仅联系人、电话、到位时间按方案根If-Match更新并保存平台前后审计。
- [ ] `DUTY_CHANGED`等待`Q-FCUT004-001`锁定P6→P4 Owner、历史触发器与旧批准/闭环处置；不得实现替代迁移。
- [ ] 通过Task 6 Lifecycle/MySQL独立Gate。
