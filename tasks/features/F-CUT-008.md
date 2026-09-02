# F-CUT-008 P5提前时间判断与外部提醒

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO @ d9b43077`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-05@V2=FULL`
> Feature Spec：`specs/features/F-CUT-008-p5-lead-time-and-external-reminders.md`
> 机器合同：`specs/features/F-CUT-008-api-contract.json`、`specs/features/F-CUT-008-physical-contract.json`、`specs/features/F-CUT-008-external-notification-contract.json`
> 旧实现审计：`specs/features/F-CUT-008-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-008-p5-lead-time-external-reminders.md`

## 当前最小工作单元

- Feature Ready已在锁定提交`d9b43077`独立复审GO；唯一Technical Plan候选已形成并等待独立复审，计划GO前不得实施。
- 本Feature覆盖完整`CUT-05@V2`：A/B专项提前时间判断与INT-10/INT-05定义渠道提醒，不拆成单一计算器或Provider碎片。
- 跨模块发送只预留端口，并以`src/test`受控实现完成正常正向闭环；不修改Yudao、不实现第三方Provider、不注册生产Fake/fallback。

## Gate清单

- [x] Feature/API/Physical/External Port/Legacy Machine Contract Gate：`PASS / GO @ d9b43077`。
- [x] Feature Ready独立复审：`READY / GO @ d9b43077`。
- [ ] 唯一Technical Plan独立复审。
- [ ] 实现、适用验证、独立Code Review与状态回写。

## 最近检查点

- 基线`ff1dc761`；Technical Plan首次独立复审NO-GO，仅剩迁移中间态写入兼容、外部领取真实事务所有权和前端测试配置三项计划可执行性整改。当前仅修订唯一计划并重送最小复审；无DDL或实现授权。
