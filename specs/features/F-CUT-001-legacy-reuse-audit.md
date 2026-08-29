# F-CUT-001 旧割接实现复用审计

> Requirement ID：`CUT-07（V1/P0）`、`CUT-09（V1/P0）`、`CUT-10（V1/P1）`
> 审计结论：`NEW_ONLY / PRESERVE_LEGACY`
> Feature Spec：`specs/features/F-CUT-001-cutover-unified-configuration-foundation.md`

## 1. 审计范围

- 后端：`pms-module-cutover`中的task、risk、plan Controller/Service/Mapper/DO、状态规则和治理Provider。
- 前端：`views/pms/cutover/cut-task`、`cut-risk`、`cut-plan`及对应API。
- 数据库：`V12__pms_cutover_tables.sql`、`V13__pms_cutover_menus.sql`、`V50__retire_excluded_cutover_runtime_surfaces.sql`。
- 测试：旧任务状态、Mapper查询治理和项目治理守卫Provider测试。

## 2. 逐项判断

| 存量对象 | 当前语义 | 结论 | F-CUT-001处理 |
|---|---|---|---|
| `pms_cut_task`及cut-task页面/API | 旧割接运行任务CRUD与旧状态 | 不可复用为CUT-07配置 | 保持不变，不双写、不迁移 |
| `pms_cut_risk`及cut-risk页面/API | 运行任务下的风险记录 | 不可复用为统一采集项、风险矩阵或调研矩阵主数据 | 保持不变；不得反推风险/调研定义、绑定规则或发布版本 |
| `pms_cut_plan`及cut-plan页面/API | 运行任务下的方案记录 | 不可复用为后台配置或方案模板 | 保持不变；不得写入`CutoverConfigurationRevision` |
| `CutTaskStatusRules`等旧状态规则 | 旧任务/风险/方案状态变化 | 与`DRAFT/PUBLISHED/DISABLED`不兼容 | 不复用，不修改 |
| 现有租户、分页、`CommonResult`、权限注解模式 | Yudao平台通用技术模式 | 可直接复用 | 新类按项目现有惯例实现 |

## 3. 状态、数据与兼容边界

- F-CUT-001的CUT-07基础已新建配置领域类、接口、页面和前向表；CUT-09/10补全继续复用该聚合，不能增强旧task/risk/plan类后改变旧行为。
- 旧表没有可证明的配置revision、稳定项键、字典快照或绑定规则版本，迁移策略固定为`NONE_NEW`；XLSX/HTML仅作名称、字段和界面参考，不作为业务裁决、迁移来源或发布门禁。
- 新菜单与权限不得恢复V50明确退役的旧运行时语义，也不得把旧页面改名后声明为CUT-07完成。
- 后续CUT-01～CUT-06若迁移旧运行时数据，须在各自Feature中重新审计，不能由本Feature预先处理。

## 4. 结论

现有旧实现保持不变是正确的。F-CUT-001只复用平台通用技术模式，风险/调研矩阵是同一配置聚合的投影视图；参考附件不建立第二事实源，方案模板章节使用配置根结构化版本快照。
