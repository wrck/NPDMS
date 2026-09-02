# F-CUT-008 P5提前时间判断与外部提醒

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_CREATED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-05@V2=FULL`
> Feature Spec：`specs/features/F-CUT-008-p5-lead-time-and-external-reminders.md`
> 机器合同：`specs/features/F-CUT-008-api-contract.json`、`specs/features/F-CUT-008-physical-contract.json`、`specs/features/F-CUT-008-external-notification-contract.json`
> 旧实现审计：`specs/features/F-CUT-008-legacy-reuse-audit.md`

## 当前最小工作单元

- 完成Feature Ready独立复审；通过前不得生成Technical Plan或实施。
- 本Feature覆盖完整`CUT-05@V2`：A/B专项提前时间判断与INT-10/INT-05定义渠道提醒，不拆成单一计算器或Provider碎片。
- 跨模块发送只预留端口，并以`src/test`受控实现完成正常正向闭环；不修改Yudao、不实现第三方Provider、不注册生产Fake/fallback。

## Gate清单

- [ ] Feature/API/Physical/External Port/Legacy Machine Contract Gate。
- [ ] Feature Ready独立复审。
- [ ] 唯一Technical Plan独立复审。
- [ ] 实现、适用验证、独立Code Review与状态回写。

## 最近检查点

- 基线`f5c66d57`；Feature Ready首轮NO-GO仅要求WAITING改派不得提前通知，以及合同/Owner错误落入同键`PENDING_RETRY`。当前Gate为两项最小整改复审；其余阈值、快照、FULL投影、V1兼容和三渠道方向不重开。
