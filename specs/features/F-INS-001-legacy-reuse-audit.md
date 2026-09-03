# F-INS-001 旧巡检规则实现复用审计

> Requirement ID：`INS-03（V2/P1）`、`INS-09（V2/P1）`、`NFR-02@V2（支撑）`
> 审计结论：`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`
> Feature Spec：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`

## 1. 审计范围

- 后端：`pms-module-service`中的`srv-rule` Controller、Service、Mapper、DO和VO。
- 前端：`views/pms/service/srv-rule`及对应API。
- 数据库：`V14__pms_service_tables.sql`、`V15__pms_service_menus.sql`、`V16__pms_business_button_permissions.sql`、`V19/V20`示例数据和`V43`字典。
- 测试：现有service模块测试与巡检页面冒烟证据。

## 2. 逐项判断

| 存量对象 | 当前语义 | 结论 | F-INS-001处理 |
|---|---|---|---|
| `pms_srv_rule`与`SrvRuleDO` | 单行规则编码、名称、类型、版本字符串和`content`长文本 | 不可复用为规则稳定身份、不可变revision、命令子项和适用产品关系真值 | 旧类保持不变；旧表只作为受控迁移来源，新Feature使用正式SDS目标表组 |
| `SrvRuleServiceImpl` | 草稿可发布、已发布可停用，但更新仍覆盖核心字段，删除不检查历史引用 | 生命周期名称可参考，业务实现不可复用 | 不增强旧Service，不双写；新聚合独立实现发布原子性和历史不可变 |
| `/pms/srv-rule/*`接口 | 旧管理CRUD，发布与维护共用更新权限 | 不符合新增PMS Business API和维护/发布权限分离 | 保持旧接口不变；新接口使用`/api/v1/pms/inspection-rules` |
| `srv-rule/index.vue` | 富文本内容编辑、直接编辑和删除 | 可参考现有名称与页面样式，不能承载八字段和版本历史 | 旧页面保持不变；新页面复制现有视觉惯例后增强，不覆盖旧功能 |
| 旧菜单、权限、规则类型/状态字典 | 历史功能入口和旧状态值存在语义差异 | 不直接复用为新业务权限或正式字典事实 | 新Feature使用独立权限；十类分类和严重级别复用基础平台字典能力但新增正式值 |
| Yudao分页、租户、`CommonResult`、权限注解和审计模式 | 平台通用技术模式 | 可直接复用 | 新类按现有项目惯例实现，不修改Yudao基础平台 |

## 3. 状态、数据与兼容边界

- 正式规则状态为`DRAFT -> PUBLISHED -> DISABLED`，发布revision不可覆盖；旧表一行一版本且可被更新，不能作为新真值。
- 正式目标表为`srv_inspection_rule`、`srv_inspection_rule_revision`和后续任务消费的`srv_inspection_task_rule_snapshot`；本Feature只实现前两类规则基础，不提前实现任务快照。
- 旧接口、页面、菜单和旧类保持不变，不双写、不改名；旧`pms_srv_rule`按正式CURRENT_FORWARD字段级复核，只迁可证明字段，完整业务字段不可证明的记录进入迁移问题或兼容只读。
- 附件或旧页面只能帮助取得现有名称和界面样式，缺行、缺名或数量差异不构成Feature Ready、发布或验收阻断。
- `V43`旧规则状态字典把0/1解释为启用/停用，与正式三态不一致，不得直接沿用。

## 4. 结论

F-INS-001采用`COPY_THEN_ENHANCE / PRESERVE_LEGACY / CURRENT_FORWARD_FIELD_REVIEW`。复用Yudao通用技术模式和旧页面视觉惯例；旧接口、页面及权限保持原功能。旧表只作为受控前向迁移来源，不完整记录不得升级为可选发布revision，也不反向改变正式规格。