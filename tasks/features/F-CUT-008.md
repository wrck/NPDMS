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

- Feature Ready、唯一Technical Plan、Task 1～7均已独立复审GO；当前进入Task 8受控真实MySQL正向闭环收口候选。
- 本Feature覆盖完整`CUT-05@V2`：A/B专项提前时间判断与INT-10/INT-05定义渠道提醒，不拆成单一计算器或Provider碎片。
- 跨模块发送只预留端口，并以`src/test`受控实现完成正常正向闭环；不修改Yudao、不实现第三方Provider、不注册生产Fake/fallback。

## Gate清单

- [x] Feature/API/Physical/External Port/Legacy Machine Contract Gate：`PASS / GO @ d9b43077`。
- [x] Feature Ready独立复审：`READY / GO @ d9b43077`。
- [x] 唯一Technical Plan独立复审：`PASS / GO @ e09b150a`。
- [x] Task 1领域规则与不可变快照Codec独立Code Review Gate：`PASS / GO @ a3443210`。
- [x] Task 2 Schema、DO、V1兼容与渠道隔离Mapper独立Code Review／隔离MySQL Gate：`PASS / GO @ 5804f57b`。
- [x] Task 3审批创建冻结与FULL详情投影独立Code Review／正向组合验证Gate：`PASS / GO @ 1edc713e`。
- [x] Task 4节点激活三渠道请求创建独立Code Review／聚焦测试Gate：`PASS / GO @ aa2376d2`。
- [x] Task 5 correlation provenance Machine Contract Gate：`PASS / GO @ 1c181df8`。
- [x] Task 4A Schema/Writer Amendment Code Review／正向闭环Gate：`PASS / GO @ 8889fe96`。
- [x] Task 5受控外部端口、投递服务与未装配Job独立Code Review／正向闭环Gate：`PASS / GO @ 373a7883`。
- [x] Task 6 P5提前时间展示卡片组件／正向交互Gate：`PASS / GO @ 8d09fdc3`。
- [x] Task 7暂停Job种子与迁移合同／隔离MySQL Gate：`PASS / GO @ aa281aa0`。
- [ ] 实现、适用验证、独立Code Review与状态回写。

## 最近检查点

- Task 7 Gate=`PASS / GO @ aa281aa0`：V159全量迁移及seed重放后外部/站内Job均唯一暂停、审批与通知行均为0，合同5/5。当前进入Task 8受控MySQL收口；生产Provider、Job激活、真实渠道/浏览器及Implementation Done继续排除。
