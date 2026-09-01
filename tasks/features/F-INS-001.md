# F-INS-001 巡检规则版本与字段配置基础

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> Technical Plan Gate：`PASS / NPDMS-FINS001-TECHPLAN-20260830-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无；Task 1/2前置单元已通过`GO NPDMS-FINS001-TASK1-2-CLOSEOUT-REVIEW-20260901-01`，F-AST-002外部交付依赖已关闭；Inspection真实失败关闭消费待Task 7/8/9生产入口实现后验证
> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`
> 复用审计：`specs/features/F-INS-001-legacy-reuse-audit.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`
> 锁定实施输入提交：`68bc56ec`

## 当前最小工作单元

- Task 1实施边界门禁与Task 2 AST外部Gate/API边界预验收已完成并独立复审GO；当前最小工作单元为提交前置单元，随后进入Task 3。

## 已完成

- 已读取PRD V1.8、工程链、文档治理、SRV领域规格及巡检相关SDS。
- 已确认最近适用Gate为Feature Ready，INS-03与INS-09应合并为一个纵向业务Feature。
- 已完成旧规则后端、前端、迁移、菜单、字典和测试审计，结论为`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。
- 已由独立裁决关闭30秒上限冲突并形成`CHG-PRD-2026-08-30-009`：只允许1～30秒，不建设未定义的超30秒审批分支。
- 已在正式SDS冻结规则状态、八字段、命令从属关系、产品适用关系、安全审核事实、权限、API、数据、页面和验收边界。
- 已明确第三方采集平台、设备凭证和任务执行不在本Feature实现范围。
- 已生成并自审唯一Technical Plan，覆盖AST外部Gate、安全审核、后端、迁移、前端、测试、真实浏览器和追溯收口；历次NO-GO问题已整改，独立复审GO。
- 已通过`CHG-PRD-2026-08-30-010`和`F-AST-002` Spec/Task关闭AST状态源缺口；F-INS-001仅验收消费，不允许Inspection猜测产品类型、直读AST业务表或实现CRM/MES连接器。
- Task 1已建立仓库级唯一计划与临时副本扫描、锁定输入祖先及正式输入漂移检查、复用审计旧资产相对锁定提交的Git差异保护、Owner/查询/Mapper参数边界及显式Requirement ID追溯门禁；9项Python定向测试PASS。
- Task 2已验收F-AST-002 Implementation Done证据、Inspection专用双查询API形状、Query身份边界、结果事实字段及Service仅依赖`pms-module-asset-api`边界；3项Maven契约测试实际执行PASS，22模块Reactor BUILD SUCCESS，依赖树仅包含`pms-module-asset-api`。
- 唯一Technical Plan已明确Task 2只做外部Gate/API边界预验收，不核销未知、停用、未解析、跨租户、空设备范围或AST异常下的Inspection生产消费行为；这些义务分别在Task 7/8发布预检与发布、Task 9工程师选择入口实现后验证；`git diff --check` PASS。

## 首轮Technical Plan评审核销

| 原问题 | 整改位置 | 核销方式 |
|---|---|---|
| 1. 锁定规格提交未产生 | Plan Locked Inputs、Task头部 | 历史锁定值`829a00ac`已由包含修订010及F-AST-002 Done的实施输入`68bc56ec`替代 |
| 2. AST DTO、文件和责任不闭合 | Plan Task 2、Q-FINS001-002 | Owner边界已明确并转外部Gate；AST交付缺口持续阻断发布/选择/Done，F-INS仅验收消费 |
| 3. 安全审核依赖无法提供的角色贡献解析 | Plan Task 3、8 | 改为服务端专用权限守卫，不解析角色贡献关系 |
| 4. 幂等与审计未绑定平台公开API | Plan Task 8 | 复用`PlatformCommandExecutionApi`、`OperationAuditApi` |
| 5. 产品类型示例可能猜造 | Plan Task 2、5 | 只引用AST Owner批准值；未确认则阻断 |
| 6. 前端测试位置不符合惯例 | Plan Task 10 | 测试放在页面目录`inspection-rule.spec.ts` |
| 7. 静态门禁伪造RED | Plan实施边界、Task 1 | 新目录不存在时PASS；新能力先实现后测试 |
| 8. 分页名称与产品类型筛选语义不明 | Plan Task 6 | 固定`ruleNameKeyword`包含匹配和XML `EXISTS` |

## 阻断

无。Task 1/2前置单元已通过`GO NPDMS-FINS001-TASK1-2-CLOSEOUT-REVIEW-20260901-01`。F-AST-002公开契约、迁移、真实MySQL和Implementation Done证据已完成，AST外部交付依赖已关闭。Task 2只验收API形状、模块依赖和后续消费所需事实字段，不把测试辅助逻辑或AST Provider测试替代为Inspection生产消费证据。F-INS-001仍不得代建AST字段、API、迁移、种子、连接器或测试。命令安全审核采用租户内显式授予`pms:inspection-rule:security-review`的动态权限包成员，在Inspection revision上记录并绑定内容摘要；不新增固定角色、审批节点或状态。

## 已知边界

- 旧接口、页面、菜单和旧类保持不变且不双写；本Feature交付旧`pms_srv_rule`可证明字段的受控前向迁移，不完整记录进入迁移问题或兼容只读。
- 附件或旧页面只帮助取得名称和界面样式，缺行、缺名或数量差异不构成阻断。
- `srv_inspection_task_rule_snapshot`及INS-01/02运行时消费后置，不提前实现。
- Yudao基础平台未获明确允许不得修改；仅复用其现有通用能力。

## 检查点

基线=68bc56ec；当前Gate=Task1/2前置单元提交；证据=9项Python、3项Maven、22模块构建、API依赖树、diff检查及独立复审GO；阻塞=无，真实失败关闭消费留Task7/8/9；下一步=提交后进入Task3。
