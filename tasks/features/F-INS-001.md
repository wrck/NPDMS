# F-INS-001 巡检规则版本与字段配置基础

> Feature实施状态：`TECHNICAL_PLAN_READY`
> Technical Plan Gate：`PASS / NPDMS-FINS001-TECHPLAN-20260830-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：`BLOCKED_BY_SPEC`——AST产品分类公开契约对应的独立Feature Spec与当前Task尚未建立；不阻断F-INS-001计划评审及后续不依赖AST的领域/草稿工作，但阻断发布、工程师选择和Implementation Done
> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`
> 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`
> 锁定规格提交：`829a00ac`

## 当前最小工作单元

- Technical Plan Gate已通过；下一单元先建立AST产品分类外部依赖的独立Feature Spec与Task，或仅推进不依赖AST的领域/草稿工作。

## 已完成

- 已读取PRD V1.8、工程链、文档治理、SRV领域规格及巡检相关SDS。
- 已确认最近适用Gate为Feature Ready，INS-03与INS-09应合并为一个纵向业务Feature。
- 已完成旧规则后端、前端、迁移、菜单、字典和测试审计，结论为`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。
- 已由独立裁决关闭30秒上限冲突并形成`CHG-PRD-2026-08-30-009`：只允许1～30秒，不建设未定义的超30秒审批分支。
- 已在正式SDS冻结规则状态、八字段、命令从属关系、产品适用关系、安全审核事实、权限、API、数据、页面和验收边界。
- 已明确第三方采集平台、设备凭证和任务执行不在本Feature实现范围。
- 已生成并自审唯一Technical Plan，覆盖AST外部Gate、安全审核、后端、迁移、前端、测试、真实浏览器和追溯收口；历次NO-GO问题已整改，独立复审GO。
- 已确认当前AST仅提供设备摘要查询，尚无Feature Spec要求的设备产品分类公开查询契约；等待AST Owner独立Feature/Task补齐并交付，F-INS-001仅验收消费，不允许Inspection猜测产品类型或直读AST业务表。

## 首轮Technical Plan评审核销

| 原问题 | 整改位置 | 核销方式 |
|---|---|---|
| 1. 锁定规格提交未产生 | Plan Locked Inputs、Task头部 | 固定为`829a00ac` |
| 2. AST DTO、文件和责任不闭合 | Plan Task 2、Q-FINS001-002 | Owner边界已明确并转外部Gate；AST交付缺口持续阻断发布/选择/Done，F-INS仅验收消费 |
| 3. 安全审核依赖无法提供的角色贡献解析 | Plan Task 3、8 | 改为服务端专用权限守卫，不解析角色贡献关系 |
| 4. 幂等与审计未绑定平台公开API | Plan Task 8 | 复用`PlatformCommandExecutionApi`、`OperationAuditApi` |
| 5. 产品类型示例可能猜造 | Plan Task 2、5 | 只引用AST Owner批准值；未确认则阻断 |
| 6. 前端测试位置不符合惯例 | Plan Task 10 | 测试放在页面目录`inspection-rule.spec.ts` |
| 7. 静态门禁伪造RED | Plan实施边界、Task 1 | 新目录不存在时PASS；新能力先实现后测试 |
| 8. 分页名称与产品类型筛选语义不明 | Plan Task 6 | 固定`ruleNameKeyword`包含匹配和XML `EXISTS` |

## 阻断

`BLOCKED_BY_SPEC`：AST产品分类公开契约必须由AST Owner的独立Feature Spec与当前Task冻结并交付，当前仓库尚未建立该状态源；F-INS-001不得代建AST字段、API、迁移、种子或测试。该缺口不阻断Technical Plan评审及后续不依赖AST的领域规则和草稿工作，但在外部Gate通过前阻断发布、工程师选择和Feature Implementation Done。命令安全审核采用租户内显式授予`pms:inspection-rule:security-review`的动态权限包成员，在Inspection revision上记录并绑定内容摘要；不新增固定角色、审批节点或状态。

## 已知边界

- 旧接口、页面、菜单和旧类保持不变且不双写；本Feature交付旧`pms_srv_rule`可证明字段的受控前向迁移，不完整记录进入迁移问题或兼容只读。
- 附件或旧页面只帮助取得名称和界面样式，缺行、缺名或数量差异不构成阻断。
- `srv_inspection_task_rule_snapshot`及INS-01/02运行时消费后置，不提前实现。
- Yudao基础平台未获明确允许不得修改；仅复用其现有通用能力。

## 检查点

基线=829a00ac；当前Gate=Technical Plan已通过；证据=唯一计划、历次整改、独立复审GO/NPDMS-FINS001-TECHPLAN-20260830-01；阻塞=Q-FINS001-002阻断发布/选择/Done；下一步=提交计划基线。