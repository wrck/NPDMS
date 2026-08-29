# F-CUT-001 割接统一配置版本与采集项基础 Implementation Plan

> Requirement ID：`CUT-07（V1/P0）`
> Feature Ready：`PASS / NPDMS-FCUT001-FEATURE-READY-20260829-01`
> Feature Spec：`specs/features/F-CUT-001-cutover-unified-configuration-foundation.md`
> Physical Contract：`specs/features/F-CUT-001-physical-contract.json`
> 实施状态：`SUPERSEDED_FOR_REOPENED_SCOPE / CUT-07子范围已完成`
> 替代关系：F-CUT-001因正式SDS同一Feature承接CUT-09/10而重新打开；扩展规格复审后生成新的唯一Technical Plan。

## 边界

- 新增CUT配置领域，不修改旧`pms_cut_task/risk/plan`业务、接口、页面或数据。
- XLSX/HTML只作名称、字段和界面参考，不参与业务裁决、发布门禁或完成状态。
- V1只实现CUT-07；不夹带CUT-09/10专用矩阵、CUT-01～06运行态、CUT-08或其他V2能力。
- 方案模板章节保存在配置根结构化快照中，保持ADR-0031三表模型。

## Task 1：失败测试与领域规则

**Files**

- Create `pms-module-cutover/src/main/java/.../domain/configuration/CutoverConfigurationRules.java`
- Create `pms-module-cutover/src/test/java/.../domain/configuration/CutoverConfigurationRulesTest.java`

覆盖草稿可编辑、发布后不可编辑、双机所属子表约束、非双机禁填、外部数据源完整性、重复稳定键和绑定冲突。

## Task 2：前向物理模型与种子

**Files**

- Create final-numbered Flyway migrations under `sql/migrations/`

新增三表、唯一当前发布约束、索引、菜单权限、正式字典值和覆盖关键组合的示例配置。旧迁移不修改，参考附件行数不固化为数据库约束。

## Task 3：后端配置聚合

**Files**

- Create configuration DO/Mapper/query classes
- Create request/response VOs, Controller and Service
- Modify `ErrorCodeConstants.java`

实现分页、详情、创建、整体保存、复制修订、发布预检、原子发布和停用。除首次创建和无副作用预检外，写命令要求`If-Match`；Service重复校验状态和版本；发布失败保持草稿且旧发布版本继续有效。

## Task 4：管理界面

**Files**

- Create `src/api/pms/cutover/cutover-config/index.ts`
- Create `src/views/pms/cutover/cutover-config/index.vue`

实现修订列表、基本信息、四维定义、方案章节、三类采集项、绑定规则和发布错误展示；已发布版本只读，具备空/错/无权状态和响应式布局。

## Task 5：验证与收口

- 后端定向测试与模块构建。
- 前端类型检查和定向组件测试。
- 全新MySQL执行迁移并验证三表、唯一约束和种子。
- 重新分配不冲突端口启动前后端，使用真实浏览器验证创建、编辑、发布、历史只读、权限拒绝和刷新持久化。
- 自审变更范围并更新`tasks/features/F-CUT-001.md`；未取得全部证据前保持Implementation未完成。

## 风险与处理

- 共享Flyway编号可能与并行Feature冲突：使用当前已知空闲编号实现，集成前再次核对并只前向改号。
- 参考附件内容不完整：只落正式字典和示例组合，不臆造正式业务项目，不以示例种子宣称附件清单完成。
- 旧割接页面仍存在：新增独立菜单和权限，不复用或改写旧页面。

## Technical Plan Gate

结论：`PASS / NPDMS-FCUT001-TECHPLAN-20260829-01`。计划覆盖规格、物理Owner、状态、权限、迁移、API、UI、负向测试、真实MySQL和真实浏览器，且没有越过CUT-07边界。
