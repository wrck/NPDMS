# 项目模板设计文档

- 日期：2026-08-04
- 状态：已确认
- 参考实现：`E:\AICoding\Trae\workspace\NewPMS\network-equipment-pms` 的项目模板模块

## 1. 背景与目标

### 1.1 现状

- 本项目已有"阶段模板"（`ProjectPhaseTemplateDO`，按 `projectType` 字符串分组的单条阶段），但创建项目时不会自动实例化阶段，需调用方循环调用 `instantiateFromTemplate`。
- 项目创建表单尚未在前端落地，项目主要来自外部系统同步。
- spec 第 89-98 行定义了 6 种项目类型及其阶段模板，但缺少"项目模板"这一上层聚合对象来组织一组阶段及其下挂任务、团队角色。
- spec 第 312 行"项目阶段的标准顺序和名称最终以哪套口径为准"仍是待确认事项，种子数据（`启动/实施/验收`）与 spec（L1-L7）口径不一致。

### 1.2 目标

- 新增"项目模板"上层聚合对象，绑定一组阶段模板及阶段下挂的任务模板、团队角色模板。
- 创建项目时选择项目模板，一次性生成阶段 + 任务（WBS）+ 团队角色记录。
- 复用现有 `ProjectPhaseTemplateDO`、`ProjectPhaseService.instantiateFromTemplate` 等基础设施。
- 遵守 AGENTS.md 模块边界：项目模板相关代码全部在 `pms-module-project` 内，不跨模块依赖。

### 1.3 非目标（YAGNI）

- 不做版本管理（单版本，修改不影响已创建项目，已创建项目不回溯模板变更）。
- 不做里程碑、交付件、任务依赖、审批计划、分配规则模板。
- 不做生命周期方案绑定（生命周期仍由 `ProjectDO.status` 现有状态机驱动）。
- 不做模板复制功能。
- 不做模板导入导出。
- 不做模板审批流程。

## 2. 数据模型

### 2.1 ProjectTemplateDO（项目模板主表，新增）

表名：`pms_project_template`
模块：`pms-module-project`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| code | VARCHAR(64) | 模板编码（全局唯一） |
| name | VARCHAR(128) | 模板名称 |
| project_type | VARCHAR(64) | 适用项目类型（字典 `pms_project_type`） |
| description | VARCHAR(500) | 描述 |
| status | TINYINT | 0=启用 / 1=停用（与 `ProjectPhaseTemplateDO.status` 一致） |
| sort | INT | 排序 |
| snapshot_json | JSON | 模板内容快照（phases + tasks + teamRoles） |

### 2.2 TemplateSnapshot DTO（JSON 快照结构）

模块：`pms-module-project`，不落独立表，序列化为 `ProjectTemplateDO.snapshotJson`。

```java
public class TemplateSnapshot implements Serializable {
    private Integer schemaVersion = 1;
    private List<PhaseDef> phases;
    private List<TaskDef> tasks;
    private List<TeamRoleDef> teamRoles;

    public static class PhaseDef {
        String phaseCode;   // 稳定键，模板内唯一
        String phaseName;
        Integer sortOrder;
        String entryCriteria;
        String exitCriteria;
    }
    public static class TaskDef {
        String taskCode;        // 稳定键，模板内唯一
        String taskName;
        String parentTaskCode;  // 父任务稳定键，null=顶层
        String phaseCode;       // 所属阶段稳定键
        String priority;        // LOW/MEDIUM/HIGH
        Integer sortOrder;
        Integer estimatedHours;
        String description;
    }
    public static class TeamRoleDef {
        String roleCode;        // 角色编码（如 PROJECT_MANAGER）
        String roleName;        // 角色名称
        Integer requiredCount;  // 需求人数
    }
}
```

### 2.3 现有表扩展

#### ProjectDO 扩展
- 新增 `template_id BIGINT NULL`：来源模板 ID（仅记录，不外键约束）。

#### ProjectPhaseTemplateDO 扩展
- 新增 `project_template_id BIGINT NULL`：关联到项目模板。`NULL` 表示独立阶段模板（兼容现有数据）。
- 现有 `ProjectPhaseTemplateDO` 继续作为"单条阶段定义"使用，项目模板的 `snapshotJson.phases` 与之并存：
  - 项目模板的 `snapshotJson.phases`：模板内置的阶段定义（创建项目时实例化为 `pms_project_phase`）。
  - `ProjectPhaseTemplateDO`：仍可作为独立阶段模板使用（现有 `/pms/phase-template/enabled-list-by-type` 接口不变）。
  - 本期项目模板的 `phases` 数据可在保存模板时同步写入 `ProjectPhaseTemplateDO`（设 `projectTemplateId`），保持两套接口都能查到，但实例化以 `snapshotJson` 为准。

### 2.4 字典与菜单

#### 新增字典 `pms_project_type`
- 类型：`pms_project_type`，名称："PMS-项目类型"
- 字典项（对齐 spec 第 89-98 行，编码与种子数据 `ProjectType` 常量对齐）：
  - `PRE_SALES_TEST` 售前测试/POC
  - `STANDARD_DELIVERY` 标准设备交付
  - `COMPLEX_ENGINEERING` 复杂工程/多节点项目
  - `CUTOVER_SERVICE` 独立割接服务
  - `INSPECTION_SERVICE` 主动巡检服务
  - `MAINTENANCE_RENEWAL` 维保续保项目

#### 新增菜单
- `项目模板管理` 路由 `/pms/project/project-template`，权限 `pms:project-template:query`
- `项目模板维护` 权限 `pms:project-template:create`

## 3. 后端实现

### 3.1 包结构

```
pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/
├── controller/admin/projecttemplate/
│   ├── ProjectTemplateController.java
│   └── vo/
│       ├── ProjectTemplateSaveReqVO.java
│       ├── ProjectTemplatePageReqVO.java
│       ├── ProjectTemplateRespVO.java
│       └── ProjectCreateFromTemplateReqVO.java
├── dal/dataobject/projecttemplate/
│   └── ProjectTemplateDO.java
├── dal/mysql/projecttemplate/
│   └── ProjectTemplateMapper.java
├── service/projecttemplate/
│   ├── ProjectTemplateService.java
│   └── ProjectTemplateServiceImpl.java
└── dal/dataobject/projecttemplate/
    └── TemplateSnapshot.java（DTO）
```

### 3.2 ProjectTemplateController 路由

路由前缀：`/pms/project-template`，遵循 PMS Business API 规范。

| HTTP | 路由 | 权限 | 用途 |
|---|---|---|---|
| POST | `/create` | `pms:project-template:create` | 创建模板 |
| PUT | `/update` | `pms:project-template:create` | 更新模板（含快照） |
| DELETE | `/delete` | `pms:project-template:create` | 删除模板（仅启用且未被项目引用） |
| GET | `/get` | `pms:project-template:query` | 查询模板详情（含快照） |
| GET | `/page` | `pms:project-template:query` | 分页查询 |
| GET | `/enabled-list` | `pms:project-template:query` | 启用模板列表（项目创建时选择） |
| GET | `/enabled-list-by-type` | `pms:project-template:query` | 按项目类型查启用模板 |
| POST | `/create-project` | `pms:project:create` | 从模板实例化项目 |

### 3.3 ProjectTemplateService 核心方法

```java
Long createProjectTemplate(ProjectTemplateSaveReqVO reqVO);
void updateProjectTemplate(ProjectTemplateSaveReqVO reqVO);
void deleteProjectTemplate(Long id);
ProjectTemplateDO getProjectTemplate(Long id);
PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO reqVO);
List<ProjectTemplateDO> getEnabledProjectTemplateList();
List<ProjectTemplateDO> getEnabledProjectTemplateListByType(String projectType);
Long createProjectFromTemplate(ProjectCreateFromTemplateReqVO reqVO);
```

### 3.4 实例化流程 `createProjectFromTemplate`

整个方法 `@Transactional`，在 `pms-module-project` 内完成，不跨模块。

1. 校验模板存在且 `status=0`（启用）。
2. 解析 `snapshotJson` 为 `TemplateSnapshot`，调用 `validateSnapshot` 校验：
   - `phases` 至少一条，`phaseCode` 唯一非空。
   - `tasks` 的 `taskCode` 唯一非空，`parentTaskCode`/`phaseCode` 引用可解析。
   - `teamRoles` 的 `roleCode` 唯一。
3. 创建 `ProjectDO`：
   - 复用现有 `ProjectServiceImpl.createProject` 的校验逻辑（code 唯一、customer 存在等）。
   - 扩展 `ProjectSaveReqVO` 支持 `templateId`。
   - `ProjectDO.templateId` 设为模板 ID。
   - `ProjectDO.projectType` 设为模板的 `projectType`（若 reqVO 未显式覆盖）。
4. 批量创建阶段（`pms_project_phase`）：
   - 遍历 `snapshot.phases`，按 `sortOrder` 升序。
   - 复用 `ProjectPhaseDO` 字段：`projectId, templateId=null, name=phaseName, code=phaseCode, sort=sortOrder, status=0(未开始), entryCriteria, exitCriteria`。
   - 构建 `phaseCode → phaseId` 映射。
5. 批量创建任务（`pms_project_task`）两阶段：
   - 第一遍：遍历 `snapshot.tasks` 按 `phaseCode` 分组，每组按 `sortOrder` 升序。插入 `ProjectTaskDO`（`parentTaskId=null, rootId=null, path=null, depth=0`），字段：`projectId, name=taskName, code=taskCode, priority, sort=sortOrder, estimatedHours, description, status=0(草稿)`。构建 `taskCode → taskId` 映射。
   - 第二遍：回填 `parentTaskId`（按 `parentTaskCode` 查映射）、`rootId`（顶层任务 rootId=自身，子任务继承父 rootId）、`path`（顶层 `/{id}/`，子任务 `{父path}{id}/`）、`depth`（父 depth+1）。`updateById` 持久化。
6. 批量创建团队角色（`pms_project_team`，复用现有 `ProjectTeamDO`）：
   - 遍历 `snapshot.teamRoles`，插入 `ProjectTeamDO`（`projectId, userId=null, role=roleCode, requiredCount`）。
   - 若 `ProjectTeamDO` 无 `requiredCount` 字段，则跳过该字段，仅记录 `role`。
7. 返回新项目 ID。

### 3.5 错误码

在 `ErrorCodeConstants` 新增段 `1-014-023-000`（`1-014-009-000` 已被项目风险模块占用）：
- `PROJECT_TEMPLATE_NOT_EXISTS = 1_014_023_000`
- `PROJECT_TEMPLATE_CODE_DUPLICATE = 1_014_023_001`
- `PROJECT_TEMPLATE_IN_USE = 1_014_023_002`（被项目引用，不可删除）
- `PROJECT_TEMPLATE_NOT_ENABLED = 1_014_023_003`
- `PROJECT_TEMPLATE_SNAPSHOT_INVALID = 1_014_023_004`（快照校验失败，附具体原因）

### 3.6 类型适配说明

- `ProjectTaskDO.priority` 为 `Integer`（0=低,1=中,2=高，与现有任务数据一致），`TemplateSnapshot.TaskDef.priority` 同步使用 `Integer`。
- `ProjectTeamMemberDO` 无 `requiredCount` 字段，`TemplateSnapshot.TeamRoleDef.requiredCount` 仅存快照，实例化时不写入 `pms_project_team_member`（仅写 `roleCode`/`roleName`，`userId=null` 表示待分配）。

## 4. 前端实现

### 4.1 API

文件：`yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-template/index.ts`

```typescript
export interface ProjectTemplateVO {
  id?: number
  code: string
  name: string
  projectType: string
  description?: string
  status: number
  sort: number
  snapshotJson?: TemplateSnapshot
}
export interface TemplateSnapshot {
  schemaVersion: number
  phases: PhaseDef[]
  tasks: TaskDef[]
  teamRoles: TeamRoleDef[]
}
// ...子接口同后端 DTO
export const createProjectTemplate = (data) => request.post('/pms/project-template/create', data)
export const updateProjectTemplate = (data) => request.put('/pms/project-template/update', data)
export const deleteProjectTemplate = (id) => request.delete(`/pms/project-template/delete?id=${id}`)
export const getProjectTemplate = (id) => request.get(`/pms/project-template/get?id=${id}`)
export const getProjectTemplatePage = (params) => request.get('/pms/project-template/page', { params })
export const getEnabledProjectTemplateList = () => request.get('/pms/project-template/enabled-list')
export const getEnabledProjectTemplateListByType = (projectType) => request.get('/pms/project-template/enabled-list-by-type', { params: { projectType } })
export const createProjectFromTemplate = (data) => request.post('/pms/project-template/create-project', data)
```

### 4.2 模板管理页

文件：`yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-template/index.vue`

- 查询表单：模板编码/名称/项目类型（字典选择器）/状态。
- 表格列：编码、名称、项目类型（dict-tag）、状态（dict-tag）、排序、操作（编辑/删除）。
- 编辑 Dialog：
  - 基本信息：编码（创建后不可改）、名称、项目类型（字典选择器）、描述、排序、状态。
  - 阶段定义：el-table 行编辑，列 phaseCode/phaseName/sortOrder/entryCriteria/exitCriteria，上下箭头调序。
  - 任务定义：el-table 树形（`tree-props`），列 taskCode/taskName/parentTaskCode/phaseCode/priority/sortOrder/estimatedHours/description。
  - 团队角色：el-table，列 roleCode/roleName/requiredCount。
- 保存时 `buildSnapshot()` 收集三个表格数据为 `TemplateSnapshot`，随主表一起提交。

### 4.3 项目列表页扩展

文件：`yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/index.vue`

- 新增"从模板创建"按钮，打开 `ProjectTemplateSelector` 对话框。
- `ProjectTemplateSelector`：选择模板 → 填写项目基本信息（编码/名称/客户/合同/经理等）→ 调用 `createProjectFromTemplate` → 成功后刷新列表。
- 内联在项目列表页，不单独抽组件（YAGNI）。

## 5. 数据库迁移

文件：`sql/migrations/V47__pms_project_template.sql`

内容：
1. 建表 `pms_project_template`。
2. `ALTER TABLE pms_project ADD COLUMN template_id BIGINT NULL`。
3. `ALTER TABLE pms_project_phase_template ADD COLUMN project_template_id BIGINT NULL`。
4. 字典类型 `pms_project_type` + 6 项字典数据。
5. 菜单：`项目模板管理`（路由 `/pms/project/project-template`，权限 `pms:project-template:query`）、`项目模板维护`（权限 `pms:project-template:create`）。
6. 种子数据：3 个项目模板（对齐现有 V19 阶段模板的 3 种 projectType：`NETWORK_INTEGRATION`/`SECURITY_DEPLOYMENT`/`MAINTENANCE_SERVICE`），每个模板的 `snapshotJson` 包含对应阶段 + 示例任务 + 团队角色。

## 6. 验证

### 6.1 后端单元测试
- `ProjectTemplateServiceImplTest`：create/update/delete/page/getEnabledList/createProjectFromTemplate 的成功与失败路径。
- 重点测试 `createProjectFromTemplate`：阶段、任务（含父子层级）、团队角色正确生成，taskPath/depth/rootId 正确回填。

### 6.2 前端 ESLint
- 所有新增/修改的 .vue 和 .ts 文件通过 ESLint。

### 6.3 业务验收（UI 闭环）
- 用 Trae 内置浏览器访问项目模板管理页，新增/编辑/删除模板。
- 从模板创建项目，验证阶段、任务、团队角色正确生成。
- 遵守 user_profile：必须用 Trae 内置浏览器，点开所有菜单，截图每个界面。

## 7. 风险与待确认

- **阶段口径**：spec 第 312 行"项目阶段的标准顺序和名称"仍待确认。本期种子数据沿用 V19 现有口径（`启动/实施/验收`），不强行对齐 L1-L7，待业务确认后通过迁移脚本补充。
- **projectType 字典编码**：本期采用 `PRE_SALES_TEST`/`STANDARD_DELIVERY` 等编码，与 V19 种子数据的 `NETWORK_INTEGRATION` 等编码不一致。解决方案：V47 迁移中同时补充 V19 缺失的项目类型字典项，种子模板按 V19 现有编码录入，确保与现有阶段模板 `projectType` 字段一致。
- **ProjectTeamDO 字段**：需确认是否已有 `requiredCount` 字段，若无则团队角色仅记录 `role`，不记录需求人数。
