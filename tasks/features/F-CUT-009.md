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
> Open Question：`Q-FCUT009-001`

## 当前最小工作单元

- 形成完整CUT-03@V2 Feature边界：授权清单导出与流程跳转配置优化必须共同承接，不拆成Provider或导出碎片。
- 跨模块只预留正式消费端口，CUT单元/集成测试可用`src/test`受控替身推进正常正向闭环；不实现外部Provider，不修改Yudao。
- 最近Gate：Feature/API/Physical/Legacy Contract与`Q-FCUT009-001`独立裁决。

## 检查点

- [x] 已核对PRD CUT-03 V2、SDS API/数据库/权限/测试设计与F-CUT-003/F-CUT-001现状。
- [x] 已完成现有配置、清单、旧风险页面、跨模块端口及导出技术模式复用审计。
- [ ] `Q-FCUT009-001`关闭并回写导航目标机器合同。
- [ ] Feature Ready独立复审GO。
- [ ] 生成唯一Technical Plan并独立复审。
- [ ] 按计划实施、测试、Code Review及状态回写。

## 阻断

- `Q-FCUT009-001 / BLOCKED_BY_SPEC`：PRD只写“更灵活的流程跳转配置”，SDS只写“目标流程状态合法”，未唯一规定目标集合、触发时点及其是否改变业务状态。代码不得猜测。

## Phase-switch checkpoint

基线85603c06；CUT-03@V2为下一P0完整Feature。已复用F-CUT-003清单与F-CUT-001配置载体，跨模块仅端口+src/test替身。当前Gate为Q-FCUT009-001及Feature机器合同复审；未GO前不建计划、不写代码。
