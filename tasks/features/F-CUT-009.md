# F-CUT-009 P3授权清单导出与受控流程跳转

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-03@V2=FULL`
> Feature Spec：`specs/features/F-CUT-009-p3-authorized-export-and-navigation.md`
> 机器合同：`specs/features/F-CUT-009-api-contract.json`、`specs/features/F-CUT-009-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-009-legacy-reuse-audit.md`
> Technical Plan：`docs/superpowers/plans/2026-09-02-f-cut-009-p3-authorized-export-and-navigation.md`
> 已决问题：`Q-FCUT009-001（RESOLVED / OPTION_A）`

## 当前最小工作单元

- 形成完整CUT-03@V2 Feature边界：授权清单导出与流程跳转配置优化必须共同承接，不拆成Provider或导出碎片。
- 跨模块只预留正式消费端口，CUT单元/集成测试可用`src/test`受控替身推进正常正向闭环；不实现外部Provider，不修改Yudao。
- 最近Gate：生成F-CUT-009唯一Technical Plan并独立复审。

## 检查点

- [x] 已核对PRD CUT-03 V2、SDS API/数据库/权限/测试设计与F-CUT-003/F-CUT-001现状。
- [x] 已完成现有配置、清单、旧风险页面、跨模块端口及导出技术模式复用审计。
- [x] `Q-FCUT009-001`采用方案A并回写导航目标机器合同。
- [x] Feature Ready独立复审GO（锁定基线`51239c53`）。
- [x] 已生成唯一Technical Plan候选。
- [ ] Technical Plan独立复审GO。
- [ ] 按计划实施、测试、Code Review及状态回写。

## 阻断

- Technical Plan处于`REVIEW_REQUIRED`；独立复审GO前不得进入DDL、后端、前端或测试实现。

## Phase-switch checkpoint

基线24d2cff4；Feature Ready已GO，唯一Technical Plan候选已形成，Implementation仍NOT_STARTED。计划按导航内核→授权导出→UI正向链推进；跨模块仅端口+src/test替身，生产Provider继续阻断真实联调/Done。
