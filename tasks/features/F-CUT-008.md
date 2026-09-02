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

- Feature Ready、唯一Technical Plan及Task 1均已独立复审GO；Task 2“两表前向Schema、DO、既有V1写入口兼容及渠道隔离Mapper”候选已形成，等待独立Code Review／隔离MySQL Gate。
- 本Feature覆盖完整`CUT-05@V2`：A/B专项提前时间判断与INT-10/INT-05定义渠道提醒，不拆成单一计算器或Provider碎片。
- 跨模块发送只预留端口，并以`src/test`受控实现完成正常正向闭环；不修改Yudao、不实现第三方Provider、不注册生产Fake/fallback。

## Gate清单

- [x] Feature/API/Physical/External Port/Legacy Machine Contract Gate：`PASS / GO @ d9b43077`。
- [x] Feature Ready独立复审：`READY / GO @ d9b43077`。
- [x] 唯一Technical Plan独立复审：`PASS / GO @ e09b150a`。
- [x] Task 1领域规则与不可变快照Codec独立Code Review Gate：`PASS / GO @ a3443210`。
- [ ] 实现、适用验证、独立Code Review与状态回写。

## 最近检查点

- 基线`57e6d3db`；Task 2候选完成两表前向扩展、旧V1写入口显式`IN_PLATFORM`兼容及站内/外部领取和CAS隔离。聚焦测试18/18通过；隔离MySQL 8.4已验证V156→V157升级、旧行确定性回填、无伪默认值、四种外部状态合法路径与非法`DELIVERY_UNKNOWN`更新失败关闭。当前最近Gate为Task 2独立Code Review／隔离MySQL复审；本记录不提前宣告PASS。INT-10/INT-05生产Provider、Job激活、真实渠道/浏览器与Implementation Done继续排除。
