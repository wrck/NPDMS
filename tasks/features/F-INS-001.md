# F-INS-001 巡检规则版本与字段配置基础

> Feature实施状态：`NOT_STARTED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无
> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`
> 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
> Technical Plan：Feature Ready规格提交锁定后生成
> 锁定规格提交：`尚未产生`

## 当前最小工作单元

- 验证并提交Feature Ready规格资产；下一轮从仓库状态恢复后生成唯一Technical Plan。

## 已完成

- 已读取PRD V1.8、工程链、文档治理、SRV领域规格及巡检相关SDS。
- 已确认最近适用Gate为Feature Ready，INS-03与INS-09应合并为一个纵向业务Feature。
- 已完成旧规则后端、前端、迁移、菜单、字典和测试审计，结论为`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。
- 已由独立裁决关闭30秒上限冲突并形成`CHG-PRD-2026-08-30-009`：只允许1～30秒，不建设未定义的超30秒审批分支。
- 已在正式SDS冻结规则状态、八字段、命令从属关系、产品适用关系、安全审核事实、权限、API、数据、页面和验收边界。
- 已明确第三方采集平台、设备凭证和任务执行不在本Feature实现范围。

## 阻断

当前无直接业务语义阻断。命令安全审核由PRD定义的审批/任务角色组在Inspection revision上记录并绑定内容摘要；本Feature不新增审批角色、节点或状态。

## 已知边界

- 旧接口、页面、菜单和旧类保持不变且不双写；本Feature交付旧`pms_srv_rule`可证明字段的受控前向迁移，不完整记录进入迁移问题或兼容只读。
- 附件或旧页面只帮助取得名称和界面样式，缺行、缺名或数量差异不构成阻断。
- `srv_inspection_task_rule_snapshot`及INS-01/02运行时消费后置，不提前实现。
- Yudao基础平台未获明确允许不得修改；仅复用其现有通用能力。

## 检查点

基线=PRD V1.8修订009、SDS Phase1-3；当前Gate=Feature Ready已通过；证据=F-INS-001 Spec、复用审计、独立裁决；阻塞=无；下一步=提交规格基线后生成唯一Technical Plan。