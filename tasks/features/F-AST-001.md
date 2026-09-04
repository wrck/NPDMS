# F-AST-001 设备序列号档案与时态归属

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_AUDIT`
> Feature Ready Gate：`PASS / SPEC-FAST001-FEATURE-READY-20260825-01`
> Technical Plan Gate：`HISTORICAL_RECORD_NOT_IN_MASTER_TASK_CHAIN`
> Implementation Done Gate：`PENDING_MASTER_REVALIDATION`
> 当前阻断：`历史实现证据存在，但此前缺少master Feature Task，不能由Spec或证据反推Done`
> 当前任务：`建立独立Delivery Unit，逐项复核a9f8b7c5证据与master当前代码后申请Done`
> Requirement ID：`EQP-01（V1）`
> Feature Spec：`specs/features/F-AST-001-device-serial-archive-and-temporal-assignment.md`
> 历史实现证据：`a9f8b7c5`

## 权威状态纠偏

- Feature Spec继续唯一维护Ready和业务Scope，不再维护Implementation状态。
- `a9f8b7c5`、自动化、MySQL、查询计划和浏览器记录均保留为历史候选证据；本任务建立前没有权威Feature Task，因此当前不得派生Implementation Done或EQP-01覆盖完成。
- 重新裁决必须基于master当前代码、完整DoD、旧设备写边界和全部适用证据，不重做无关测试，也不修改已冻结业务语义。

## 待完成

- [ ] 在master创建F-AST-001复核Delivery Unit并声明精确边界。
- [ ] 核对历史提交、当前master实现、Feature Spec和适用测试是否一致。
- [ ] 复核普通角色旧写退役、`super_admin`旧模型受控写及无AST代理/双写边界。
- [ ] 在master最终状态执行适用验证与Code Review。
- [ ] 只有独立Done Gate通过后，才把本文件更新为`IMPLEMENTATION_COMPLETE`并重新生成追溯投影。

本记录只补齐缺失权威链，不否定或覆盖历史证据，也不提前宣布Feature完成。

## 三分支按提交时间代码事实重放（2026-09-04）

> 状态以提交源码、测试、迁移、前端与构建文件为事实依据；Feature未关闭的Gate继续保留。

- 原实施状态记录：`> Feature实施状态：REVALIDATION_REQUIRED`
- 当前实施状态：代码已接收；未完成Feature保持 `IN_PROGRESS`。
- 已接收代码路径：`1`
- 已处理来源提交：`1`
- 来源分支：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。
- 接收原则：按提交时间逐提交重放；任何单文件或单hunk冲突均不阻断其他模块代码。
- 完整逐提交、逐文件记录：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

- `.spec-repo-f-ast-001`
