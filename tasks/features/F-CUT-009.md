# F-CUT-009 P3授权清单导出与受控流程跳转

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO`
> Technical Plan Gate：`PASS / GO`
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
- Task 1导航列、配置聚合与决定内核已通过独立Code Review Gate（`PASS / GO@2b30664d`）。
- Task 2授权XLSX导出内核、REST与安全审计已通过独立Code Review Gate（`PASS / GO@f9022765`）。
- 最近Gate：Task 3“配置UI、P3导出与受控正向闭环”实施及独立Code Review。

## 检查点

- [x] 已核对PRD CUT-03 V2、SDS API/数据库/权限/测试设计与F-CUT-003/F-CUT-001现状。
- [x] 已完成现有配置、清单、旧风险页面、跨模块端口及导出技术模式复用审计。
- [x] `Q-FCUT009-001`采用方案A并回写导航目标机器合同。
- [x] Feature Ready独立复审GO（锁定基线`51239c53`）。
- [x] 已生成唯一Technical Plan候选。
- [x] Technical Plan独立复审GO（锁定基线`e9b2a25c`）。
- [ ] 按计划实施、测试、Code Review及状态回写（Task 1、2已完成）。

## 阻断

- Task 3只可汇总受控`ProjectScope`替身下的CUT正向闭环；生产Owner依赖仍阻断生产装配、真实浏览器与Implementation Done。

## Phase-switch checkpoint

基线f9022765；Task 1、2独立复审GO，导航决定内核与授权XLSX导出/安全审计已完成。当前进入Task 3配置UI、P3导出与受控正向闭环；跨模块仍仅预留端口+`src/test`受控替身。最近Gate为Task 3 Code Review。
