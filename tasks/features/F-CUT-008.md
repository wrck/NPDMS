# F-CUT-008 P5提前时间判断与外部提醒

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO @ d9b43077`
> Technical Plan Gate：`PASS / GO @ e09b150a`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-05@V2=FULL`
> Feature Spec：`specs/features/F-CUT-008-p5-lead-time-and-external-reminders.md`
> 机器合同：`specs/features/F-CUT-008-api-contract.json`、`specs/features/F-CUT-008-physical-contract.json`、`specs/features/F-CUT-008-external-notification-contract.json`
> 旧实现审计：`specs/features/F-CUT-008-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-008-p5-lead-time-external-reminders.md`

## 当前最小工作单元

- Feature Ready、唯一Technical Plan及Task 1、Task 2均已独立复审GO；Task 3“审批创建冻结与FULL详情投影”候选已形成，等待独立Code Review Gate。
- 本Feature覆盖完整`CUT-05@V2`：A/B专项提前时间判断与INT-10/INT-05定义渠道提醒，不拆成单一计算器或Provider碎片。
- 跨模块发送只预留端口，并以`src/test`受控实现完成正常正向闭环；不修改Yudao、不实现第三方Provider、不注册生产Fake/fallback。

## Gate清单

- [x] Feature/API/Physical/External Port/Legacy Machine Contract Gate：`PASS / GO @ d9b43077`。
- [x] Feature Ready独立复审：`READY / GO @ d9b43077`。
- [x] 唯一Technical Plan独立复审：`PASS / GO @ e09b150a`。
- [x] Task 1领域规则与不可变快照Codec独立Code Review Gate：`PASS / GO @ a3443210`。
- [x] Task 2 Schema、DO、V1兼容与渠道隔离Mapper独立Code Review／隔离MySQL Gate：`PASS / GO @ 5804f57b`。
- [ ] 实现、适用验证、独立Code Review与状态回写。

## 最近检查点

- Task 3首轮候选`2011aa5a`独立复审NO-GO：P4提交原先在审批启动后才写方案`submitted_at`，A/B无法从DRAFT方案行取得提交时间。
- 单点整改候选：P4提交外层事务先分配唯一服务端`planSubmittedAt`，同值进入审批启动命令及幂等摘要，并在审批启动成功后原样写入方案revision；审批继续锁定方案身份但不从尚未提交的方案行读取该时间。C/D、FULL投影、最终结果/改派隔离及决策不受影响边界保持不变。
- 聚焦验证：审批/方案/API八个测试类共35/35通过；独立MySQL 8.4空库迁移至V157后，`CutoverApprovalPositiveLoopMySqlTest`6/6通过，并证明方案`submitted_at`与审批快照`planSubmittedAt`完全一致。专用容器、网络和卷已清理。
- 当前最近Gate为Task 3该单点运行整改Code Review／正向组合验证复审；INT-10/INT-05生产Provider、Job激活、真实渠道/浏览器与Implementation Done继续排除。
