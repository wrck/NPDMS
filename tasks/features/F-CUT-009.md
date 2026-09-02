# F-CUT-009 P3授权清单导出与受控流程跳转

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_CREATED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-03@V2=FULL`
> Feature Spec：`specs/features/F-CUT-009-p3-authorized-export-and-navigation.md`
> 机器合同：`specs/features/F-CUT-009-api-contract.json`、`specs/features/F-CUT-009-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-009-legacy-reuse-audit.md`
> 已决问题：`Q-FCUT009-001（RESOLVED / OPTION_A）`

## 当前最小工作单元

- 形成完整CUT-03@V2 Feature边界：授权清单导出与流程跳转配置优化必须共同承接，不拆成Provider或导出碎片。
- 跨模块只预留正式消费端口，CUT单元/集成测试可用`src/test`受控替身推进正常正向闭环；不实现外部Provider，不修改Yudao。
- 最近Gate：导出范围、导航机器语义与XLSX线协议最小整改复审。

## 检查点

- [x] 已核对PRD CUT-03 V2、SDS API/数据库/权限/测试设计与F-CUT-003/F-CUT-001现状。
- [x] 已完成现有配置、清单、旧风险页面、跨模块端口及导出技术模式复用审计。
- [x] `Q-FCUT009-001`采用方案A并回写导航目标机器合同。
- [ ] Feature Ready独立复审GO。
- [ ] 生成唯一Technical Plan并独立复审。
- [ ] 按计划实施、测试、Code Review及状态回写。

## 阻断

- Feature Ready仍为`REVIEW_REQUIRED`：须先通过导出范围、无条件导航结构与XLSX线协议的最小整改复审。

## Phase-switch checkpoint

基线7bc8f98d；独立裁决确认CUT-03@V2完整Feature及导航方案A。现已移除双机导出、锁定无条件双目标和XLSX线协议；跨模块仍仅端口+src/test替身。当前Gate为机器合同最小整改复审；未GO前不建计划、不写代码。
