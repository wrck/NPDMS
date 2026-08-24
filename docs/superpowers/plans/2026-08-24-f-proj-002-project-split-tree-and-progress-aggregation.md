# F-PROJ-002 项目拆分、项目树与进度汇总 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划由当前会话内联执行，不启用子代理。

**Goal:** 按 PRD V1.8 完成组合拆分草稿、原子批量创建、版本化项目树、权限裁剪、版本化进度汇总和全部后代闭环守卫，并以响应式真实页面形成完整闭环。

**Architecture:** `proj_project.parent_id`继续作为项目树唯一可写真值，`proj_project_tree_path`只发布完整版本投影；PROJ 保存拆分申请与发生时快照，通过 Commerce、AST 和基础平台公开契约校验并分配范围。进度不再以`proj_project.progress/aggregation_weight`作为正式真值，而由审批版本化策略、进度事实和不可变快照解释；所有写命令复用平台幂等、审计和 Outbox 同事务能力。

**Tech Stack:** Java 25、Spring Boot、MyBatis-Plus、MySQL 8.4、Flyway 11、Yudao BPM、Vue 3、TypeScript、Element Plus、pnpm 9。

**Spec:** `specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`

## Global Constraints

- 规格输入锁定为`0be4056e6334be4c5b0c9ae9810bd04c782f81c1`；SDS Phase 1/2/3保持`BASELINE`，不重新审核。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与实施门禁。
- V1.7 Spec、代码、迁移、页面和测试只作复用审计证据；每项任务从未完成状态开始验证。
- 用户已禁用测试驱动顺序；不要求先制造失败测试，但每项任务提交前必须执行与风险匹配的验证。
- 模块间只依赖公开 API，不依赖目标模块的 Service、Mapper、Repository或业务表。
- 已执行 Flyway 迁移 V1～V69不得修改；所有 Schema 修正使用 V70 及以后前向迁移。
- 项目树不设置业务深度上限；测试深度30只是性能基准。
- Business API语义前缀为`/api/v1/pms`，当前Yudao管理端实现使用`/admin-api/pms/...`。
- UI优先复用Yudao组件，其次使用Element Plus；响应式样式使用主题变量，避免内联样式和页面级横向溢出。
- 每个任务完成并验证后创建独立本地提交，不推送。

## 存量实现审计

| 资产 | 分类 | V1.8处置 |
|---|---|---|
| `ProjectManualCreationServiceImpl`、`ProjectCodeAllocator`、`TemplateInstantiator` | `REUSE_WITH_ADAPTATION` | 提取无独立幂等完成点的子项目创建原语，供拆分批次在外层事务复用 |
| `ProjectCreationPlatformFactService`及平台事实表 | `REUSE_WITH_RENAME` | 泛化为项目写命令执行器，继续保证幂等、审计、Outbox同事务 |
| `proj_project.parent_id/root_id/tree_path/tree_depth` | `REUSE_AS_TRUTH_AND_COMPATIBILITY` | `parent_id`保留为真值；其余字段降为当前完整投影兼容缓存 |
| `ProjectTreeServiceImpl`（`projectmanual`） | `REPLACE` | 改为版本化树命令、投影和查询服务；禁止逐行暴露半完成投影 |
| `proj_project.progress/aggregation_weight/weight_source` | `REPLACE_AS_SOURCE_OF_TRUTH` | 仅作V1.7兼容字段；新策略、事实和快照表成为正式解释来源 |
| `ProjectMasterController`现有树/权重端点 | `ADAPT` | 收敛到单一正式入口，增加查询类型、游标、`treeVersion`、幂等键和`If-Match` |
| `project-master-detail/index.vue` | `REUSE_SHELL` | 拆成拆分、树、进度组件并完成四类视口响应式改造 |
| `projecttree`旧Service/Controller、旧页面/API、`pms_project`树链 | `RETIRE` | 删除运行时入口与Bean，只保留历史迁移和审计证据 |
| V60～V62与旧验收记录 | `EVIDENCE_ONLY` | 不修改；新增前向迁移、测试和V1.8验收证据 |
| Commerce DeliveryScope、AST SN校验、拆分草稿、树版本、策略快照、闭环守卫 | `MISSING` | 按以下任务新增，不将缺失误报为规格阻断 |

---

### Task 1: 锁定 Feature 前向物理与机器契约

**Files:**
- Modify in specification repo: `docs/design/08-data-model.md`
- Modify in specification repo: `docs/design/09-database-design.md`
- Modify in specification repo: `docs/design/10-api-design.md`
- Modify in specification repo: `docs/design/11-event-design.md`
- Create in specification repo: `specs/features/F-PROJ-002-physical-contract.json`
- Modify in specification repo: `specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`

**Interfaces:**
- Consumes: 已批准的 PM-02、PM-04切片、COM-01切片和CLO-02守卫语义。
- Produces: 以下表、API和事件的唯一正式命名；不改变SDS阶段门禁状态。

```text
Commerce:
  com_order_line
  com_delivery_scope
  com_delivery_scope_detail
  com_outbox_event
PROJ split:
  proj_project_split_request
  proj_project_split_item
  proj_project_split_scope
PROJ tree:
  proj_project_tree_version
  proj_project_tree_path
  proj_project_tree_change
PROJ progress:
  proj_project_progress_fact
  proj_project_progress_policy_revision
  proj_project_progress_policy_item
  proj_project_progress_snapshot
  proj_project_progress_snapshot_detail
Events:
  DeliveryScopeAssigned
  DeliveryScopeReleased
  ProjectTreeChanged
```

- [x] **Step 1: 在规格仓库追加 Feature-forward 物理契约**

明确每张表的Owner、主键、租户键、唯一约束、版本字段、生效区间和索引；明确`parent_id`是真值、树路径是完整版本投影、`progress/aggregation_weight`是兼容字段。保持 Phase 1/2/3 gate-status不变，不生成重审材料。

- [x] **Step 2: 更新独立Feature机器契约和Feature Spec物理引用**

独立`F-PROJ-002-physical-contract.json`必须把`ProjectHierarchy`、`ProjectAncestorProjection`、`DeliveryScope`、`ProgressPolicyRevision`和`ProjectProgressSnapshot`映射到上述Owner表；不得修改已审核SDS的`domain-object-table-map.json`或迁移对象/来源计数。Feature Spec只补正式名称，不改变已批准业务语义。

- [x] **Step 3: 提交规格仓库并同步NPDMS受管快照**

Run:

```powershell
py -3.13 -B scripts/validate_sds_phase1.py
py -3.13 -B scripts/validate_sds_phase2.py
py -3.13 -B scripts/validate_sds_phase3.py
```

同步后运行：

```powershell
py -3.13 -B scripts/validate_specification_baseline.py
py -3.13 -B -m unittest scripts.tests.test_specification_baseline
```

Expected: 两仓`source.commit`一致，全部PASS，SDS仍为`BASELINE`。

- [x] **Step 4: Commit**

Specification repo: `docs(feature): 锁定 F-PROJ-002 前向物理契约`

NPDMS: `docs(feature): 同步 F-PROJ-002 物理契约`

---

### Task 2: 建立Commerce与AST公开校验契约

本任务只实现F-PROJ-002稳定调用所需的DeliveryScope查询、预览和分配切片，不宣称完成COM-01的合同/订单全量同步、人工补录、对账或管理页面。

**Files:**
- Create: `pms-module-commerce/pms-module-commerce-api/pom.xml`
- Create: `pms-module-commerce/pom.xml`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeApi.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/DeliveryScopeSliceDTO.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/SplitScopePreviewCommand.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/SplitScopeApplyCommand.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/SplitScopeApplyResult.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeApiImpl.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/scope/{OrderLineDO,DeliveryScopeDO,DeliveryScopeDetailDO}.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/{OrderLineMapper,DeliveryScopeMapper,DeliveryScopeDetailMapper}.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/outbox/CommerceOutboxEventDO.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/outbox/CommerceOutboxEventMapper.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeService.java`
- Create: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeServiceTest.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/AssetDeviceScopeApi.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/SerialScopeValidationResult.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/AssetDeviceScopeApiImpl.java`
- Modify: `pom.xml`
- Modify: `pms-module-project/pom.xml`
- Modify: `yudao-server/pom.xml`
- Create: `sql/migrations/V70__commerce_delivery_scope_slice.sql`

**Interfaces:**
- Produces:

```java
public interface DeliveryScopeApi {
    List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId, Long expectedScopeVersion);
    SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command);
    SplitScopeApplyResult applySplit(SplitScopeApplyCommand command);
}

public interface AssetDeviceScopeApi {
    SerialScopeValidationResult validateAssignableSerials(
            Long tenantId, Long parentProjectId, List<String> serialNumbers);
}
```

- `SplitScopePreviewCommand`以`clientItemKey`关联尚未创建的子项目方案；`SplitScopeApplyCommand`在子项目ID产生后使用相同`clientItemKey`完成原子分配。
- `applySplit`必须基于订单行锁和`expectedScopeVersion`拒绝超分配，在同一事务写`DeliveryScopeAssigned/Released` Commerce Outbox；不得访问PROJ表或Service。

- [x] **Step 1: 增加Commerce API与业务模块装配**

根POM同时声明`pms-module-commerce-api`和`pms-module-commerce`，PROJ只依赖API，`yudao-server`装配业务模块。

- [x] **Step 2: 创建V70权威范围表与约束**

约束至少包括来源键版本唯一、当前范围唯一、`allocated_qty > 0`、明细合计由Service同事务校验、`allocation_version`乐观并发和订单行可用数量行锁。

- [x] **Step 3: 实现范围预览/应用和AST序列号校验**

预览无写入；应用只移动或拆分Commerce自有范围事实并返回新版本。AST接口只返回存在性、租户、当前可分配状态和失败SN，不返回凭证或敏感设备明细。

- [x] **Step 4: 验证**

Run:

```powershell
mvn -pl pms-module-commerce,pms-module-asset,pms-module-project -am test
```

Expected: 精确分配、部分分配、超配、重复SN、失效SN、陈旧版本和并发争用均有断言且PASS。

- [x] **Step 5: Commit**

`feat(commerce): 建立拆分范围公开契约`

---

### Task 3: 建立拆分、树版本和进度正式载体

**Files:**
- Create: `sql/migrations/V71__fproj002_split_tree_progress_carriers.sql`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectsplit/{ProjectSplitRequestDO,ProjectSplitItemDO,ProjectSplitScopeDO}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectsplit/{ProjectSplitRequestMapper,ProjectSplitItemMapper,ProjectSplitScopeMapper}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttree/{ProjectTreeVersionDO,ProjectTreePathDO,ProjectTreeChangeDO}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttree/{ProjectTreeVersionMapper,ProjectTreePathMapper,ProjectTreeChangeMapper}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectprogress/{ProjectProgressFactDO,ProjectProgressPolicyRevisionDO,ProjectProgressPolicyItemDO,ProjectProgressSnapshotDO,ProjectProgressSnapshotDetailDO}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectprogress/{ProjectProgressFactMapper,ProjectProgressPolicyRevisionMapper,ProjectProgressPolicyItemMapper,ProjectProgressSnapshotMapper,ProjectProgressSnapshotDetailMapper}.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectMasterDO.java`
- Create: `scripts/tests/test_fproj002_v18_migration.py`

**Interfaces:**
- `ProjectSplitRequestDO.status`: `DRAFT/APPLIED`；失败校验只更新校验摘要，不改变DRAFT。
- `ProjectTreeVersionDO.status`: `BUILDING/ACTIVE/FAILED`；查询只读取ACTIVE。
- `ProjectProgressPolicyRevisionDO.status`: `DRAFT/APPROVING/ACTIVE/REJECTED/SUPERSEDED`。
- `ProjectProgressSnapshotDO.status`: `READY/PENDING`，并保存`policyRevisionId/treeVersion/sourceWatermark/calculatedAt`。

- [x] **Step 1: 创建V71前向迁移**

所有业务表含`tenant_id`、审计字段、逻辑删除和必要版本；策略生效区间不可重叠由Service锁定父项目后校验；快照以`project_id + policy_revision_id + tree_version + source_watermark`唯一。

- [x] **Step 2: 明确V1.7兼容字段处置**

保留V60字段以兼容旧读模型，但Java注释和服务入口明确禁止把`progress/aggregation_weight/weight_source`当作新写命令真值；不得修改V60～V62。

- [x] **Step 3: 增加DO、Mapper和迁移合同测试**

迁移测试必须验证表、约束、索引、Owner前缀、V70/V71顺序和禁止编辑旧迁移。

- [x] **Step 4: 验证**

```powershell
py -3.13 -B -m unittest scripts.tests.test_fproj002_v18_migration
mvn -pl pms-module-project -am -DskipTests compile
```

- [x] **Step 5: Commit**

`feat(project): 建立拆分树与进度正式载体`

---

### Task 4: 实现持久化拆分草稿、预览和校验

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectsplit/ProjectSplitRules.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitDraftService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitPreviewService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/platform/ProjectOperationAuditService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitMetrics.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/command/{ProjectSplitDraftCommand,ProjectSplitPreviewCommand}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectsplit/ProjectSplitRequestController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectsplit/vo/{ProjectSplitDraftSaveReqVO,ProjectSplitRequestRespVO,ProjectSplitPreviewRespVO}.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/domain/projectsplit/ProjectSplitRulesTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitPreviewServiceTest.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`

**Interfaces:**

```java
ProjectSplitRequestRespVO saveDraft(ProjectSplitDraftCommand command, Actor actor);
ProjectSplitPreview preview(Long requestId, Integer expectedDraftVersion, Actor actor);
```

- 草稿项使用`clientItemKey`稳定关联；范围项显式保存`orderLineId/quantity/officeDepartmentCode/serialNumbers/sourceScopeVersion`。
- 预览依次校验父项目、租户、ProjectTreeScope、组织部门编码、Commerce可分配量和AST序列号；任何失败均返回逐项结果且不创建Project。

- [x] **Step 1: 实现自由组合规则和草稿版本控制**

允许订单行、数量、办事处部门编码和SN任意组合；同一SN不可在两个方案项重复；数量为正且总量不超过父项目可分配范围。

- [x] **Step 2: 实现服务端预览**

客户端提交的预览摘要不可回传作为确认依据；服务端生成`previewHash`和`validatedAt`，持久化逐项结果与权威水位。

草稿保存、预览、重新校验和失败结果写平台操作审计；Micrometer指标至少记录预览成功/失败、失败类型和耗时，不记录范围正文、SN或商务敏感值。

- [x] **Step 3: 暴露草稿、读取、更新、预览和重新校验接口**

正式路由：

```text
POST /pms/project-split-requests
GET  /pms/project-split-requests/{id}
PUT  /pms/project-split-requests/{id}
POST /pms/project-split-requests/{id}/actions/preview
POST /pms/project-split-requests/{id}/actions/validate
```

- [x] **Step 4: 验证**

```powershell
mvn -pl pms-module-project -am -Dtest=ProjectSplitRulesTest,ProjectSplitPreviewServiceTest test
```

- [x] **Step 5: Commit**

`feat(project): 实现拆分草稿与方案预览`

---

### Task 5: 实现原子批量应用拆分方案

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitApplicationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/command/{ApplyProjectSplitCommand,ApplyProjectSplitResult}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectChildCreationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/platform/ProjectCommandExecutionService.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCreationPlatformFactService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectsplit/ProjectSplitRequestController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectsplit/vo/ApplyProjectSplitReqVO.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitApplicationServiceTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitMySqlIntegrationTest.java`

**Interfaces:**

```java
ApplyProjectSplitResult apply(ApplyProjectSplitCommand command, Actor actor);

record ApplyProjectSplitCommand(
    Long requestId, Integer expectedDraftVersion, Integer expectedParentVersion,
    Long expectedScopeVersion, Long expectedTreeVersion,
    String idempotencyKey, String requestDigest) {}
```

- `ProjectChildCreationService`创建并实例化一个子项目，但不自行完成幂等、审计或Outbox；外层`ProjectCommandExecutionService`只为整个批次写一个完成点。
- 同一事务顺序：锁草稿/父项目/范围版本→服务端重验→批量创建子项目与五要素→Commerce分配→发布树版本→标记APPLIED→审计/Outbox/幂等成功。

- [x] **Step 1: 提取可复用子项目创建原语并保持F-PROJ-001回归**

子项目必须冻结父模板版本并产生独立不可复用编码；不复制父项目的实时进度、闭环结果或敏感授权。

- [x] **Step 2: 实现批次应用与故障注入点**

测试注入子项目第N个失败、模板实例化失败、Commerce分配失败、审计失败和Outbox失败，断言Project、范围、树和完成点全部回滚，草稿仍可修正重试。

指标记录批次成功/失败、失败阶段、范围冲突和总耗时；失败标签使用受控错误分类，不写项目名称、SN或范围正文。

- [x] **Step 3: 增加正式确认接口**

```text
POST /pms/project-split-requests/{id}/actions/apply
Headers: Idempotency-Key, If-Match
Body: expectedParentVersion, expectedScopeVersion, expectedTreeVersion
```

- [x] **Step 4: 验证**

```powershell
mvn -pl pms-module-project,pms-module-commerce -am -Dtest=ProjectSplitApplicationServiceTest,ProjectSplitMySqlIntegrationTest,ProjectManualCreationApplicationServiceTest test
```

- [x] **Step 5: Commit**

`feat(project): 原子应用项目拆分方案`

---

### Task 6: 发布完整项目树版本并收敛单一正式入口

**Files:**
- Replace: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectTreeService.java`
- Replace: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectTreeServiceImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeProjectionService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeQueryService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeMetrics.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/command/{MoveProjectSubtreeCommand,ProjectTreeQuery}.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/{ProjectTreeQueryReqVO,ProjectTreeQueryRespVO}.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeService.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeServiceImpl.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttree/ProjectTreeController.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttree/vo/`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projecttree/{ProjectTreeProjectionServiceTest,ProjectTreeQueryServiceTest,ProjectTreeMoveConcurrencyMySqlTest}.java`

**Interfaces:**

```java
ProjectTreeQueryResult query(ProjectTreeQuery query, Actor actor);
MoveProjectSubtreeResult move(MoveProjectSubtreeCommand command, Actor actor);

enum QueryType { CHILDREN, DESCENDANTS, ANCESTORS, BUSINESS_LEVEL, LOCATE }
```

- 每个受影响根创建`BUILDING`版本，批量写完整祖先投影后原子切换`ACTIVE`；查询读取同一`treeVersion`，游标继续读取同一版本。
- 移动按稳定项目ID顺序锁节点，校验同租户、非自身/后代和期望版本；跨根移动同时重建源根与目标根。

- [x] **Step 1: 实现根级完整投影构建与原子激活**

构建失败保留上一ACTIVE版本并记录FAILED；不得把BUILDING行暴露给查询或授权。

- [x] **Step 2: 实现五类查询和游标**

统一端点：

```text
GET /pms/projects/{id}/tree?queryType=CHILDREN|DESCENDANTS|ANCESTORS|BUSINESS_LEVEL|LOCATE
```

响应固定包含`treeVersion/items/nextCursor/updating`。

指标记录投影构建延迟、失败版本数、五类查询耗时、节点数和版本陈旧命中；不得以项目ID作为高基数指标标签。

- [x] **Step 3: 改造移动命令**

`POST /pms/projects/{id}/actions/move`要求`Idempotency-Key`与`If-Match`，成功生成唯一`changeBatchId`和`ProjectTreeChanged`。

- [x] **Step 4: 退役旧pms_project树运行面**

删除旧Bean、Controller和VO；历史`pms_project`表及V7迁移不删除。更新运行面扫描规则，禁止`/pms/project-tree`重新出现。

- [x] **Step 5: 验证**

```powershell
mvn -pl pms-module-project -am -Dtest=ProjectTreeProjectionServiceTest,ProjectTreeQueryServiceTest,ProjectTreeMoveConcurrencyMySqlTest test
py -3.13 -B -m unittest scripts.tests.test_implementation_baseline_inventory
```

- [x] **Step 6: Commit**

`feat(project): 发布版本化项目树`

---

### Task 7: 实施ProjectTreeScope与有限同根可见性

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeViewSanitizer.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeQueryService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitDraftService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitApplicationService.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectscope/{ProjectTreeScopeServiceTest,ProjectTreeAuthorizationMySqlTest}.java`

**Interfaces:**

```java
ProjectTreeScope resolve(Long actorId, Long anchorProjectId, long treeVersion);
ProjectTreeNodeView sanitize(ProjectMasterDO project, Visibility visibility);

enum Visibility { FULL, ROOT_SUMMARY, PATH_PLACEHOLDER, NONE }
```

- 功能权限控制能否拆分/移动；项目成员区间和完整树版本控制数据范围。
- 同父平级默认不可见；获授权服务经理可查看同根树名称、状态、阶段、里程碑进度、交付件目录和齐套状态，不能查看任务明细、人员、凭证、商务敏感字段和正文。

- [x] **Step 1: 计算Actor的直接节点、后代范围与同根摘要范围**

权限无法计算时拒绝，不降级为租户全量；所有查询先过滤后组装VO。

- [x] **Step 2: 在拆分、移动、树查询、进度查询中统一接入Scope**

客户端根节点、路径、深度、业务层级或游标均不得扩大Scope。

- [x] **Step 3: 验证负向矩阵**

覆盖平级隔离、祖先/后代、有限摘要、敏感字段、跨租户、陈旧树版本和零副作用。

```powershell
mvn -pl pms-module-project -am -Dtest=ProjectTreeScopeServiceTest,ProjectTreeAuthorizationMySqlTest test
```

- [x] **Step 4: Commit**

`feat(project): 实施项目树数据范围`

---

### Task 8: 实现审批版本化权重和进度快照

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectprogress/ProjectProgressRules.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/{ProjectProgressPolicyService,ProjectProgressSnapshotService,ProjectProgressQueryService}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/ProjectProgressMetrics.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/ProjectProgressPolicyBpmListener.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/command/{CreateProgressPolicyCommand,ProjectProgressFact,ProjectProgressResult}.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectprogress/ProjectProgressController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectprogress/vo/{ProjectProgressPolicyReqVO,ProjectProgressPolicyRespVO,ProjectProgressRespVO}.java`
- Remove from controller: `ProjectMasterController.updateChildWeights/getProgress`
- Retire: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectChildWeightsReqVO.java`
- Replace: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectProgressRespVO.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/{ProjectProgressRulesTest,ProjectProgressPolicyServiceTest,ProjectProgressSnapshotMySqlTest}.java`

**Interfaces:**

```java
Long createRevision(CreateProgressPolicyCommand command, Actor actor);
String submitForApproval(Long revisionId, Integer expectedVersion, Actor actor);
void onApprovalResult(String processInstanceId, Integer status, String reason);
ProjectProgressResult getCurrent(Long projectId, Actor actor);
```

- 默认等权也形成不可变系统策略版本；人工策略全部直接子项目权重合计必须100%。
- BPM使用`BpmProcessInstanceApi`和`BpmProcessInstanceStatusEventListener`；流程定义键来自配置，不硬编码审批岗位。
- 任何必要直接子项目无有效进度事实时生成`PENDING`快照和缺失项；不得用0或旧快照替代。
- 新策略只影响生效后的新快照，历史快照不追溯重算。

- [x] **Step 1: 实现策略草稿、提交审批和幂等回调**

重复/乱序回调不重复生效；激活新版本时关闭旧版本生效区间。

- [x] **Step 2: 实现叶子进度事实与逐级快照**

叶子读取`proj_project_progress_fact`，非叶子读取直接子项目当前事实或快照；快照保存解释明细和水位。

指标至少覆盖待计算项目数、缺失事实数、快照计算耗时和策略回调重复/乱序次数；日志不输出未授权子项目明细。

- [x] **Step 3: 收敛正式API**

```text
POST /pms/projects/{id}/progress-policies
POST /pms/progress-policies/{id}/actions/submit
GET  /pms/projects/{id}/progress
GET  /pms/projects/{id}/progress-policies
```

- [x] **Step 4: 验证**

```powershell
mvn -pl pms-module-project -am -Dtest=ProjectProgressRulesTest,ProjectProgressPolicyServiceTest,ProjectProgressSnapshotMySqlTest test
```

- [ ] **Step 5: Commit**

`feat(project): 版本化项目进度汇总`

---

### Task 9: 提供全部后代闭环守卫

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosureguard/ProjectClosureGuardService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosureguard/ClosureStatePort.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosureguard/ProjectClosureGuardResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapter.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureServiceImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectclosureguard/ProjectClosureGuardController.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosureguard/ProjectClosureGuardServiceTest.java`

**Interfaces:**

```java
ProjectClosureGuardResult evaluate(Long projectId, long expectedTreeVersion, Actor actor);

record ProjectClosureGuardResult(
    boolean allowed, long treeVersion,
    List<BlockingProject> blockers, List<Long> pendingProgressProjects) {}
```

- PROJ按当前完整树版本检查全部层级后代；ACC适配器只提供执行/暂停/关闭审批状态，不把审批表或Mapper暴露给PROJ。
- 任一后代未关闭或必要汇总`PENDING`时拒绝；成功只表示可进入CLO-02，不直接通过或归档闭环。

- [ ] **Step 1: 实现版本一致的全后代守卫**

返回未满足项目的授权后摘要；无权项目只返回稳定ID和阻断类型，不泄露敏感信息。

- [ ] **Step 2: 在现有闭环提交入口调用守卫适配器**

这只是当前模块化单体兼容接线，不改变ACC/CLO Owner；后续物理拆分时由ACC模块继续依赖PROJ公开守卫。

- [ ] **Step 3: 验证**

```powershell
mvn -pl pms-module-project -am -Dtest=ProjectClosureGuardServiceTest test
```

- [ ] **Step 4: Commit**

`feat(project): 增加全部后代闭环守卫`

---

### Task 10: 实现响应式拆分、项目树和进度界面

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-splits/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectSplitWizard.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTreePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectProgressPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectClosureGuardPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Already retired in Task 6: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-tree/index.vue`
- Already retired in Task 6: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-tree/index.ts`
- Create: `scripts/tests/test_fproj002_frontend_contract.py`

**Interfaces:**
- `ProjectSplitWizard`: 草稿保存、组合范围、预览、逐项错误、确认应用；刷新后按requestId恢复。
- `ProjectTreePanel`: 五类查询、按需展开、定位、移动和`treeVersion`提示。
- `ProjectProgressPanel`: 当前策略、待计算缺失项、历史版本、创建/提交策略。
- `ProjectClosureGuardPanel`: 只展示守卫结果，不执行闭环审批。

- [ ] **Step 1: 建立TypeScript契约并移除旧树API**

所有写请求显式传`Idempotency-Key`和版本头；不得继续调用`/pms/project-tree`或无版本的`child-weights`。

- [ ] **Step 2: 拆分项目详情大组件并复用Yudao/Element Plus**

使用`ContentWrap`、`Dialog`、`el-form`、`el-table`、`el-tree-v2`或现有树组件、`el-result`和`el-skeleton`；不引入新UI库。

- [ ] **Step 3: 完成四类视口布局**

桌面`>=1200px`为侧栏+内容；窄桌面`992～1199px`压缩侧栏；平板`768～991px`改顶部Tabs；手机`<768px`使用卡片列表、全宽Dialog和纵向操作。颜色、边框、文字和背景使用`--el-*`主题变量；表格只允许组件内部滚动，页面级无横向溢出。

- [ ] **Step 4: 增加静态合同测试并构建**

```powershell
py -3.13 -B -m unittest scripts.tests.test_fproj002_frontend_contract
cd yudao-ui/yudao-ui-admin-vue3
pnpm ts:check
pnpm build:local
```

- [ ] **Step 5: Commit**

`feat(ui): 完成项目拆分树与进度工作台`

---

### Task 11: 初始化数据、全量验证与V1.8验收闭环

**Files:**
- Create: `sql/migrations/V72__fproj002_v18_seed_and_menu.sql`
- Modify: `scripts/tests/test_fproj002_v18_migration.py`
- Modify: `tasks/implementation-baseline-inventory.json`
- Modify: `tasks/features/F-PROJ-002.md`
- Create: `output/f-proj-002-v18/database-evidence.md`
- Create: `output/f-proj-002-v18/browser-acceptance.md`
- Create: `output/f-proj-002-v18/regression-summary.md`
- Create: `output/f-proj-002-v18/performance-evidence.md`
- Create: `scripts/verify_fproj002_performance.py`

**Interfaces:**
- 种子覆盖精确命中、部分限定、优先级让位、无匹配、停用不参与、深度30、业务层级跨深度、等权/人工策略、待计算、同根有限可见性和后代闭环阻断。
- 所有示例使用高段ID/专用前缀和`creator='seed'`，前向且幂等；不臆造ERP权威数量或CRM属性值。

- [ ] **Step 1: 创建V72示例与菜单配置**

ERP未同步的数量标记`PENDING_AUTHORITY`且不可分配；另建明确`CONFIRMED`示例用于完整拆分验收。菜单只收敛到项目详情正式入口，旧项目树菜单隐藏。

- [ ] **Step 2: 执行数据库和模块验证**

```powershell
docker compose up -d mysql redis migrate
docker compose run --rm migrate validate
py -3.13 -B -m unittest scripts.tests.test_fproj002_v18_migration scripts.tests.test_fproj002_frontend_contract
mvn -pl pms-module-commerce,pms-module-asset,pms-module-project,yudao-server -am test
```

另以空库、重复迁移和V69→V72升级三种路径验证，不修改任何已执行迁移。

运行`verify_fproj002_performance.py`构造`max(实际迁移项目量×2, 200000)`项目、单树10000节点、直接子项目2000和深度30数据集，记录五类权限过滤查询与树页面API的P50/P95、SQL次数和投影水位；P95必须不超过2秒且不存在逐层N+1查询。

- [ ] **Step 3: 执行真实浏览器验收**

优先使用Codex内置浏览器，必要时使用已授权外部浏览器。覆盖：草稿恢复、自由组合预览、校验失败无副作用、原子批量创建、五类树查询、移动冲突、权限负向、策略审批版本、待计算、闭环守卫和四类视口；刷新后事实保持，控制台与网络无未解释错误。

- [ ] **Step 4: 完成存量分类和Feature状态**

把`ProjectTreeAndDetail`从`V1_7_REVALIDATION_REQUIRED`改为按文件列出的`REUSED/ADAPTED/RETIRED/REPLACED`结果；只有AC-FPROJ002-001～012均有当前证据时，才将`Implementation Done Gate`改为`PASS`。

- [ ] **Step 5: 全量回归和最终提交**

```powershell
py -3.13 -B scripts/validate_specification_baseline.py
py -3.13 -B scripts/validate_implementation_baseline_inventory.py
git diff --check
```

Commit: `feat(project): 完成 F-PROJ-002 V1.8 闭环`

---

## 计划覆盖矩阵

| Feature AC | 实施任务 |
|---|---|
| AC-FPROJ002-001 组合拆分与预览 | Task 2、4、10 |
| AC-FPROJ002-002 校验失败保留草稿 | Task 4、10 |
| AC-FPROJ002-003 原子批量创建 | Task 5 |
| AC-FPROJ002-004 任意深度与无环移动 | Task 6 |
| AC-FPROJ002-005 五类树查询 | Task 6、10 |
| AC-FPROJ002-006 权限与有限可见性 | Task 7、10 |
| AC-FPROJ002-007 权重与审批版本 | Task 8、10 |
| AC-FPROJ002-008 进度待计算 | Task 8、10 |
| AC-FPROJ002-009 全部后代闭环守卫 | Task 9、10 |
| AC-FPROJ002-010 幂等、并发与完整版本 | Task 2、5、6、8 |
| AC-FPROJ002-011 性能 | Task 6、7、8、11 |
| AC-FPROJ002-012 真实浏览器与响应式 | Task 10、11 |

## 阻断处理规则

- 会改变Owner、业务状态、权限语义、正式表/API命名或不可逆数据处置的问题，必须当前确认。
- 只影响局部实现细节且可前向修正的问题，登记到`tasks/features/F-PROJ-002.md`后继续不依赖该问题的任务。
- 可由现有正式契约、代码事实或工具输出唯一推导的高可信问题，直接采用最小修正并在任务提交中记录。
- Commerce、AST或BPM的当前代码缺失属于本计划内工作，不作为跨仓或外部阻断。
