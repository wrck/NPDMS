# 项目模板实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 实现项目模板功能，创建项目时选择模板一次性生成阶段 + 任务（WBS）+ 团队角色记录。

**Architecture:** 新增 `ProjectTemplateDO` 主表 + `TemplateSnapshot` JSON 快照（含 phases/tasks/teamRoles 三类子模板）。在 `pms-module-project` 内通过同模块直接调用完成实例化（不跨模块 SPI）。复用现有 `ProjectPhaseService`、`ProjectTaskDO`、`ProjectTeamMemberDO` 基础设施。

**Tech Stack:** Spring Boot + MyBatis-Plus（后端）、Vue3 + Element Plus（前端）、Flyway（迁移）、MySQL JSON 字段（快照存储）

**Spec:** `docs/superpowers/specs/2026-08-04-project-template-design.md`

---

## 文件结构

### 后端新增（pms-module-project）
- `dal/dataobject/projecttemplate/ProjectTemplateDO.java` — 项目模板主表 DO
- `dal/dataobject/projecttemplate/TemplateSnapshot.java` — JSON 快照 DTO（含 PhaseDef/TaskDef/TeamRoleDef 内部类）
- `dal/mysql/projecttemplate/ProjectTemplateMapper.java` — Mapper
- `controller/admin/projecttemplate/vo/ProjectTemplateSaveReqVO.java` — 创建/更新请求 VO
- `controller/admin/projecttemplate/vo/ProjectTemplatePageReqVO.java` — 分页请求 VO
- `controller/admin/projecttemplate/vo/ProjectTemplateRespVO.java` — 响应 VO
- `controller/admin/projecttemplate/vo/ProjectCreateFromTemplateReqVO.java` — 从模板创建项目请求 VO
- `controller/admin/projecttemplate/ProjectTemplateController.java` — Controller
- `service/projecttemplate/ProjectTemplateService.java` — Service 接口
- `service/projecttemplate/ProjectTemplateServiceImpl.java` — Service 实现

### 后端修改
- `enums/ErrorCodeConstants.java` — 新增 1-014-023-000 段错误码
- `dal/dataobject/project/ProjectDO.java` — 新增 `templateId` 字段
- `controller/admin/project/vo/ProjectSaveReqVO.java` — 新增 `templateId` 字段
- `service/project/ProjectServiceImpl.java` — `createProject` 保留 `templateId`

### 数据库迁移
- `sql/migrations/V47__pms_project_template.sql` — 建表 + 字段扩展 + 字典 + 菜单 + 种子数据

### 前端新增
- `src/api/pms/project/project-template/index.ts` — API 接口
- `src/views/pms/project/project-template/index.vue` — 模板管理页

### 前端修改
- `src/utils/dict.ts` — 新增 `PMS_PROJECT_TYPE` 字典常量
- `src/views/pms/project/project/index.vue` — 新增"从模板创建"按钮和对话框
- `src/router/index.ts` 或菜单数据 — 注册模板管理页路由（通过迁移菜单实现，无需改路由文件）

---

## Task 1: 数据库迁移 V47

**Files:**
- Create: `sql/migrations/V47__pms_project_template.sql`

- [x] **Step 1: 创建迁移文件**

创建 `sql/migrations/V47__pms_project_template.sql`，内容包含 6 部分：建表、字段扩展、字典类型、字典数据、菜单、种子数据。

```sql
-- 1. 项目模板主表
CREATE TABLE IF NOT EXISTS `pms_project_template` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板编号',
    `code`           VARCHAR(64)  NOT NULL COMMENT '模板编码（全局唯一）',
    `name`           VARCHAR(128) NOT NULL COMMENT '模板名称',
    `project_type`   VARCHAR(64)  NULL COMMENT '适用项目类型（字典 pms_project_type）',
    `description`    VARCHAR(500) NULL COMMENT '描述',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=启用 1=停用',
    `sort`           INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `snapshot_json`  JSON         NULL COMMENT '模板内容快照（phases+tasks+teamRoles）',
    `creator`        VARCHAR(64)  NULL DEFAULT '',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        VARCHAR(64)  NULL DEFAULT '',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`   DATETIME     NULL,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`, `tenant_id`),
    KEY `idx_status_type` (`status`, `project_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 项目模板';

-- 2. 现有表字段扩展
ALTER TABLE `pms_project` ADD COLUMN `template_id` BIGINT NULL COMMENT '来源项目模板编号' AFTER `manager_user_id`;
ALTER TABLE `pms_project_phase_template` ADD COLUMN `project_template_id` BIGINT NULL COMMENT '所属项目模板编号（NULL=独立阶段模板）' AFTER `project_type`;

-- 3. 字典类型：项目类型
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(2115, 'PMS-项目类型', 'pms_project_type', 0, '项目类型（售前测试/标准交付/复杂工程/割接/巡检/维保）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- 4. 字典数据：项目类型（编码与 V19 种子数据的 projectType 一致）
INSERT IGNORE INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(1, '网络集成', 'NETWORK_INTEGRATION', 'pms_project_type', 0, 'primary', '', '网络集成类项目', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2, '安全部署', 'SECURITY_DEPLOYMENT', 'pms_project_type', 0, 'success', '', '安全部署类项目', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(3, '运维服务', 'MAINTENANCE_SERVICE', 'pms_project_type', 0, 'info', '', '运维服务类项目', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(4, '售前测试/POC', 'PRE_SALES_TEST', 'pms_project_type', 0, 'warning', '', '售前测试或POC', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(5, '独立割接服务', 'CUTOVER_SERVICE', 'pms_project_type', 0, 'danger', '', '独立割接服务', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(6, '主动巡检服务', 'INSPECTION_SERVICE', 'pms_project_type', 0, '', '', '主动巡检服务', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- 5. 菜单：项目模板管理（父菜单 18000，复用阶段模板同级）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18042, '项目模板管理', 'pms:project-template:query', 2, 42, 18000, 'project-template', 'ep:document-copy', 'pms/project/project-template/index', 'PmsProjectTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18043, '项目模板维护', 'pms:project-template:create', 3, 43, 18000, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `update_time`=NOW(), `deleted`=b'0';

-- 6. 种子数据：3 个项目模板（对齐 V19 阶段模板的 3 种 projectType）
-- 6.1 网络集成项目模板
INSERT INTO `pms_project_template` (`id`, `code`, `name`, `project_type`, `description`, `status`, `sort`, `snapshot_json`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 'TPL-NET-01', '网络集成标准模板', 'NETWORK_INTEGRATION', '网络集成类项目标准阶段与任务模板', 0, 1,
'{"schemaVersion":1,"phases":[{"phaseCode":"STARTUP","phaseName":"启动阶段","sortOrder":1,"entryCriteria":"项目已立项","exitCriteria":"项目启动会已召开"},{"phaseCode":"IMPLEMENT","phaseName":"实施阶段","sortOrder":2,"entryCriteria":"启动会已召开","exitCriteria":"设备安装调试完成"},{"phaseCode":"ACCEPTANCE","phaseName":"验收阶段","sortOrder":3,"entryCriteria":"实施完成","exitCriteria":"客户签署验收报告"}],"tasks":[{"taskCode":"T-STARTUP-01","taskName":"项目启动会","parentTaskCode":null,"phaseCode":"STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开项目启动会，明确范围与职责"},{"taskCode":"T-IMPL-01","taskName":"设备到货确认","parentTaskCode":null,"phaseCode":"IMPLEMENT","priority":1,"sortOrder":1,"estimatedHours":2,"description":"确认设备到货情况"},{"taskCode":"T-IMPL-02","taskName":"设备安装","parentTaskCode":"T-IMPL-01","phaseCode":"IMPLEMENT","priority":2,"sortOrder":2,"estimatedHours":16,"description":"按方案安装设备"},{"taskCode":"T-IMPL-03","taskName":"设备调试","parentTaskCode":"T-IMPL-02","phaseCode":"IMPLEMENT","priority":2,"sortOrder":3,"estimatedHours":24,"description":"设备配置与联调"},{"taskCode":"T-ACC-01","taskName":"验收测试","parentTaskCode":null,"phaseCode":"ACCEPTANCE","priority":1,"sortOrder":1,"estimatedHours":8,"description":"执行验收测试用例"},{"taskCode":"T-ACC-02","taskName":"验收报告签署","parentTaskCode":"T-ACC-01","phaseCode":"ACCEPTANCE","priority":1,"sortOrder":2,"estimatedHours":2,"description":"客户签署验收报告"}],"teamRoles":[{"roleCode":"PROJECT_MANAGER","roleName":"项目经理","requiredCount":1},{"roleCode":"TECH_LEAD","roleName":"技术负责人","requiredCount":1},{"roleCode":"ENGINEER","roleName":"工程师","requiredCount":2}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 'TPL-SEC-01', '安全部署标准模板', 'SECURITY_DEPLOYMENT', '安全部署类项目标准阶段与任务模板', 0, 2,
'{"schemaVersion":1,"phases":[{"phaseCode":"STARTUP","phaseName":"启动阶段","sortOrder":1,"entryCriteria":"项目已立项","exitCriteria":"项目启动会已召开"},{"phaseCode":"IMPLEMENT","phaseName":"实施阶段","sortOrder":2,"entryCriteria":"启动会已召开","exitCriteria":"安全设备部署完成"},{"phaseCode":"ACCEPTANCE","phaseName":"验收阶段","sortOrder":3,"entryCriteria":"实施完成","exitCriteria":"客户签署验收报告"}],"tasks":[{"taskCode":"T-STARTUP-01","taskName":"项目启动会","parentTaskCode":null,"phaseCode":"STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开项目启动会"},{"taskCode":"T-IMPL-01","taskName":"安全设备部署","parentTaskCode":null,"phaseCode":"IMPLEMENT","priority":1,"sortOrder":1,"estimatedHours":20,"description":"部署安全设备"},{"taskCode":"T-IMPL-02","taskName":"安全策略配置","parentTaskCode":"T-IMPL-01","phaseCode":"IMPLEMENT","priority":2,"sortOrder":2,"estimatedHours":16,"description":"配置安全策略"},{"taskCode":"T-ACC-01","taskName":"安全验收","parentTaskCode":null,"phaseCode":"ACCEPTANCE","priority":1,"sortOrder":1,"estimatedHours":6,"description":"执行安全验收"}],"teamRoles":[{"roleCode":"PROJECT_MANAGER","roleName":"项目经理","requiredCount":1},{"roleCode":"SECURITY_LEAD","roleName":"安全负责人","requiredCount":1}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 'TPL-MAIN-01', '运维服务标准模板', 'MAINTENANCE_SERVICE', '运维服务类项目标准阶段与任务模板', 0, 3,
'{"schemaVersion":1,"phases":[{"phaseCode":"SERVICE_STARTUP","phaseName":"服务启动阶段","sortOrder":1,"entryCriteria":"合同已签订","exitCriteria":"服务启动会已召开"},{"phaseCode":"STABLE_RUN","phaseName":"稳定运行阶段","sortOrder":2,"entryCriteria":"服务启动完成","exitCriteria":"服务交付完成"},{"phaseCode":"SERVICE_CLOSURE","phaseName":"服务收尾阶段","sortOrder":3,"entryCriteria":"服务期结束","exitCriteria":"服务总结报告已提交"}],"tasks":[{"taskCode":"T-SUP-01","taskName":"服务启动会","parentTaskCode":null,"phaseCode":"SERVICE_STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开服务启动会"},{"taskCode":"T-RUN-01","taskName":"日常巡检","parentTaskCode":null,"phaseCode":"STABLE_RUN","priority":1,"sortOrder":1,"estimatedHours":8,"description":"按计划执行日常巡检"},{"taskCode":"T-RUN-02","taskName":"故障处理","parentTaskCode":null,"phaseCode":"STABLE_RUN","priority":2,"sortOrder":2,"estimatedHours":16,"description":"处理客户报障"},{"taskCode":"T-CLS-01","taskName":"服务总结","parentTaskCode":null,"phaseCode":"SERVICE_CLOSURE","priority":1,"sortOrder":1,"estimatedHours":4,"description":"编写服务总结报告"}],"teamRoles":[{"roleCode":"SERVICE_MANAGER","roleName":"服务经理","requiredCount":1},{"roleCode":"ENGINEER","roleName":"工程师","requiredCount":2}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1);
```

- [x] **Step 2: 执行迁移验证**

Run: `docker compose up migrate`（在项目根目录）
Expected: 输出 `Successfully applied 1 migration to schema "pms"`，schema 版本变为 `47`

- [x] **Step 3: 验证种子数据**

Run: `docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT id, code, name, project_type, status FROM pms_project_template;"'`
Expected: 3 条记录（TPL-NET-01 / TPL-SEC-01 / TPL-MAIN-01）

- [x] **Step 4: Commit**

```bash
git add sql/migrations/V47__pms_project_template.sql
git commit -m "feat(pms): 新增项目模板数据库迁移 V47（建表+字段扩展+字典+菜单+种子数据）"
```

---

## Task 2: 后端错误码 + DO + DTO + Mapper

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttemplate/ProjectTemplateDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttemplate/TemplateSnapshot.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttemplate/ProjectTemplateMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/project/ProjectDO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/phasetemplate/ProjectPhaseTemplateDO.java`

- [x] **Step 1: 新增错误码**

在 `ErrorCodeConstants.java` 的 `GOVERNANCE_ACTION_TYPE_INVALID` 行之后追加：

```java
    // ========== 项目模板模块 1-014-023-000 ==========
    ErrorCode PROJECT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_014_023_000, "项目模板不存在");
    ErrorCode PROJECT_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_014_023_001, "项目模板编码已存在");
    ErrorCode PROJECT_TEMPLATE_IN_USE = new ErrorCode(1_014_023_002, "项目模板已被项目引用，无法删除");
    ErrorCode PROJECT_TEMPLATE_NOT_ENABLED = new ErrorCode(1_014_023_003, "项目模板未启用");
    ErrorCode PROJECT_TEMPLATE_SNAPSHOT_INVALID = new ErrorCode(1_014_023_004, "项目模板快照校验未通过：{}");
```

- [x] **Step 2: 创建 ProjectTemplateDO**

```java
package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 项目模板 DO
 * <p>
 * 模板内容以 JSON 快照形式存储在 {@link #snapshotJson}，包含 phases/tasks/teamRoles 三类子模板。
 */
@TableName(value = "pms_project_template", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplateDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 模板编码（全局唯一）
     */
    private String code;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 适用项目类型（字典 pms_project_type）
     */
    private String projectType;
    /**
     * 描述
     */
    private String description;
    /**
     * 状态：0启用 1停用
     */
    private Integer status;
    /**
     * 排序号
     */
    private Integer sort;
    /**
     * 模板内容快照（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private TemplateSnapshot snapshotJson;

}
```

注意：需在 import 中加入 `import com.baomidou.mybatisplus.annotation.TableField;`

- [x] **Step 3: 创建 TemplateSnapshot DTO**

```java
package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目模板内容快照 DTO（JSON 序列化存储）
 */
@Data
public class TemplateSnapshot implements Serializable {

    private Integer schemaVersion = 1;
    private List<PhaseDef> phases;
    private List<TaskDef> tasks;
    private List<TeamRoleDef> teamRoles;

    @Data
    public static class PhaseDef implements Serializable {
        /** 阶段编码，模板内唯一（稳定键） */
        private String phaseCode;
        private String phaseName;
        private Integer sortOrder;
        private String entryCriteria;
        private String exitCriteria;
    }

    @Data
    public static class TaskDef implements Serializable {
        /** 任务编码，模板内唯一（稳定键） */
        private String taskCode;
        private String taskName;
        /** 父任务编码，null=顶层 */
        private String parentTaskCode;
        /** 所属阶段编码 */
        private String phaseCode;
        /** 优先级：0低 1中 2高 */
        private Integer priority;
        private Integer sortOrder;
        private BigDecimal estimatedHours;
        private String description;
    }

    @Data
    public static class TeamRoleDef implements Serializable {
        private String roleCode;
        private String roleName;
        private Integer requiredCount;
    }
}
```

- [x] **Step 4: 创建 ProjectTemplateMapper**

```java
package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectTemplateMapper extends BaseMapperX<ProjectTemplateDO> {

    default ProjectTemplateDO selectByCode(String code) {
        return selectOne(ProjectTemplateDO::getCode, code);
    }

    default List<ProjectTemplateDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getStatus, 0)
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }

    default List<ProjectTemplateDO> selectEnabledListByType(String projectType) {
        return selectList(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getStatus, 0)
                .eqIfPresent(ProjectTemplateDO::getProjectType, projectType)
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }

    default Long selectCountByProjectType(String projectType) {
        return selectCount(new LambdaQueryWrapperX<ProjectTemplateDO>()
                .eq(ProjectTemplateDO::getProjectType, projectType));
    }

    default PageResult<ProjectTemplateDO> selectPage(ProjectTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectTemplateDO>()
                .likeIfPresent(ProjectTemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectTemplateDO::getName, reqVO.getName())
                .eqIfPresent(ProjectTemplateDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ProjectTemplateDO::getStatus, reqVO.getStatus())
                .orderByAsc(ProjectTemplateDO::getSort)
                .orderByDesc(ProjectTemplateDO::getId));
    }
}
```

注意：需补充 import `cn.iocoder.yudao.framework.common.pojo.PageResult;`

- [x] **Step 5: 扩展 ProjectDO 新增 templateId**

在 `ProjectDO.java` 的 `managerUserId` 字段之后、`version` 字段之前新增：

```java
    /**
     * 来源项目模板编号（仅记录，不外键约束）
     */
    private Long templateId;
```

- [x] **Step 6: 扩展 ProjectPhaseTemplateDO 新增 projectTemplateId**

在 `ProjectPhaseTemplateDO.java` 的 `projectType` 字段之后新增：

```java
    /**
     * 所属项目模板编号（NULL=独立阶段模板，兼容现有数据）
     */
    private Long projectTemplateId;
```

- [x] **Step 7: 编译验证**

Run: `cd pms-module-project && mvn compile -q`（在项目根目录）
Expected: BUILD SUCCESS

- [x] **Step 8: Commit**

```bash
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/
git commit -m "feat(pms): 新增项目模板 DO/DTO/Mapper 与错误码，扩展 ProjectDO.templateId"
```

---

## Task 3: 后端 VO

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/vo/ProjectTemplateSaveReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/vo/ProjectTemplatePageReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/vo/ProjectTemplateRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/vo/ProjectCreateFromTemplateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/project/vo/ProjectSaveReqVO.java`

- [x] **Step 1: 创建 ProjectTemplateSaveReqVO**

```java
package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目模板创建/修改 Request VO")
@Data
public class ProjectTemplateSaveReqVO {

    @Schema(description = "模板编号", example = "1")
    private Long id;

    @Schema(description = "模板编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "TPL-NET-01")
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "网络集成标准模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "适用项目类型", example = "NETWORK_INTEGRATION")
    @Size(max = 64, message = "项目类型长度不能超过 64 个字符")
    private String projectType;

    @Schema(description = "描述", example = "网络集成类项目标准模板")
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    @Schema(description = "状态（0启用 1停用）", example = "0")
    private Integer status;

    @Schema(description = "排序号", example = "1")
    private Integer sort;

    @Schema(description = "模板内容快照")
    private TemplateSnapshot snapshotJson;
}
```

- [x] **Step 2: 创建 ProjectTemplatePageReqVO**

```java
package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectTemplatePageReqVO extends PageParam {

    @Schema(description = "模板编码")
    private String code;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "项目类型")
    private String projectType;

    @Schema(description = "状态")
    private Integer status;
}
```

- [x] **Step 3: 创建 ProjectTemplateRespVO**

```java
package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目模板 Response VO")
@Data
public class ProjectTemplateRespVO {

    @Schema(description = "模板编号")
    private Long id;
    @Schema(description = "模板编码")
    private String code;
    @Schema(description = "模板名称")
    private String name;
    @Schema(description = "适用项目类型")
    private String projectType;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "排序号")
    private Integer sort;
    @Schema(description = "模板内容快照")
    private TemplateSnapshot snapshotJson;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
```

- [x] **Step 4: 创建 ProjectCreateFromTemplateReqVO**

```java
package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 从模板创建项目 Request VO")
@Data
public class ProjectCreateFromTemplateReqVO {

    @Schema(description = "项目模板编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目模板编号不能为空")
    private Long templateId;

    @Schema(description = "项目编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PMS202608001")
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目A")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "客户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "客户编号不能为空")
    private Long customerId;

    @Schema(description = "合同编码", example = "HT202608001")
    @Size(max = 64, message = "合同编码长度不能超过 64 个字符")
    private String contractCode;

    @Schema(description = "来源系统", requiredMode = Schema.RequiredMode.REQUIRED, example = "MANUAL")
    @NotBlank(message = "来源系统不能为空")
    @Size(max = 64, message = "来源系统长度不能超过 64 个字符")
    private String sourceSystem;

    @Schema(description = "来源业务键", requiredMode = Schema.RequiredMode.REQUIRED, example = "MANUAL-PMS202608001")
    @NotBlank(message = "来源业务键不能为空")
    @Size(max = 128, message = "来源业务键长度不能超过 128 个字符")
    private String sourceBusinessKey;

    @Schema(description = "项目经理用户编号", example = "1")
    private Long managerUserId;
}
```

- [x] **Step 5: 扩展 ProjectSaveReqVO 新增 templateId**

在 `ProjectSaveReqVO.java` 的 `status` 字段之后新增：

```java
    @Schema(description = "来源项目模板编号", example = "1")
    private Long templateId;
```

- [x] **Step 6: 编译验证**

Run: `cd pms-module-project && mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 7: Commit**

```bash
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/
git commit -m "feat(pms): 新增项目模板 VO（Save/Page/Resp/CreateFromTemplate），扩展 ProjectSaveReqVO.templateId"
```

---

## Task 4: 后端 Service

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/ProjectTemplateService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/ProjectTemplateServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/project/ProjectServiceImpl.java`

- [x] **Step 1: 创建 Service 接口**

```java
package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectCreateFromTemplateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;

import java.util.List;

public interface ProjectTemplateService {

    Long createProjectTemplate(ProjectTemplateSaveReqVO reqVO);

    void updateProjectTemplate(ProjectTemplateSaveReqVO reqVO);

    void deleteProjectTemplate(Long id);

    ProjectTemplateDO getProjectTemplate(Long id);

    PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO reqVO);

    List<ProjectTemplateDO> getEnabledProjectTemplateList();

    List<ProjectTemplateDO> getEnabledProjectTemplateListByType(String projectType);

    Long createProjectFromTemplate(ProjectCreateFromTemplateReqVO reqVO);
}
```

- [x] **Step 2: 创建 Service 实现类（CRUD 部分）**

```java
package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectCreateFromTemplateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.TemplateSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.phase.ProjectPhaseMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam.ProjectTeamMemberMapper;
import cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProjectTemplateServiceImpl implements ProjectTemplateService {

    @Resource
    private ProjectTemplateMapper projectTemplateMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectPhaseMapper projectPhaseMapper;
    @Resource
    private ProjectTaskMapper projectTaskMapper;
    @Resource
    private ProjectTeamMemberMapper projectTeamMemberMapper;

    @Override
    public Long createProjectTemplate(ProjectTemplateSaveReqVO reqVO) {
        // 校验编码唯一
        validateCodeUnique(null, reqVO.getCode());
        ProjectTemplateDO template = BeanUtils.toBean(reqVO, ProjectTemplateDO.class);
        if (template.getStatus() == null) {
            template.setStatus(0);
        }
        if (template.getSort() == null) {
            template.setSort(0);
        }
        projectTemplateMapper.insert(template);
        return template.getId();
    }

    @Override
    public void updateProjectTemplate(ProjectTemplateSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        validateCodeUnique(reqVO.getId(), reqVO.getCode());
        ProjectTemplateDO updateObj = BeanUtils.toBean(reqVO, ProjectTemplateDO.class);
        projectTemplateMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectTemplate(Long id) {
        validateExists(id);
        // 校验未被项目引用
        Long projectCount = projectMapper.selectCount(ProjectDO::getTemplateId, id);
        if (projectCount != null && projectCount > 0) {
            throw exception(PROJECT_TEMPLATE_IN_USE);
        }
        projectTemplateMapper.deleteById(id);
    }

    @Override
    public ProjectTemplateDO getProjectTemplate(Long id) {
        return projectTemplateMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectTemplateDO> getProjectTemplatePage(ProjectTemplatePageReqVO reqVO) {
        return projectTemplateMapper.selectPage(reqVO);
    }

    @Override
    public List<ProjectTemplateDO> getEnabledProjectTemplateList() {
        return projectTemplateMapper.selectEnabledList();
    }

    @Override
    public List<ProjectTemplateDO> getEnabledProjectTemplateListByType(String projectType) {
        return projectTemplateMapper.selectEnabledListByType(projectType);
    }

    private void validateExists(Long id) {
        if (id == null || projectTemplateMapper.selectById(id) == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        ProjectTemplateDO existing = projectTemplateMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(PROJECT_TEMPLATE_CODE_DUPLICATE);
        }
    }

    // createProjectFromTemplate 在下一步实现
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectFromTemplate(ProjectCreateFromTemplateReqVO reqVO) {
        // 1. 校验模板存在且启用
        ProjectTemplateDO template = projectTemplateMapper.selectById(reqVO.getTemplateId());
        if (template == null) {
            throw exception(PROJECT_TEMPLATE_NOT_EXISTS);
        }
        if (!Integer.valueOf(0).equals(template.getStatus())) {
            throw exception(PROJECT_TEMPLATE_NOT_ENABLED);
        }
        // 2. 校验项目编码唯一、来源业务键唯一、客户存在（复用 ProjectServiceImpl 的校验逻辑）
        // 这里直接调用 projectMapper 校验，避免循环依赖
        if (projectMapper.selectByCode(reqVO.getCode()) != null) {
            throw exception(ErrorCodeConstants.PROJECT_CODE_DUPLICATE);
        }
        if (projectMapper.selectBySourceSystemAndBusinessKey(
                reqVO.getSourceSystem(), reqVO.getSourceBusinessKey()) != null) {
            throw exception(ErrorCodeConstants.PROJECT_SOURCE_KEY_DUPLICATE);
        }
        if (reqVO.getCustomerId() != null
                && projectMapper.selectCount() > 0L) {
            // 客户存在性校验由 CustomerMapper 负责，这里简化：仅校验非空
        }

        // 3. 校验快照
        TemplateSnapshot snapshot = template.getSnapshotJson();
        validateSnapshot(snapshot);

        // 4. 创建项目主记录
        ProjectDO project = new ProjectDO();
        project.setCode(reqVO.getCode());
        project.setName(reqVO.getName());
        project.setCustomerId(reqVO.getCustomerId());
        project.setContractCode(reqVO.getContractCode());
        project.setProjectType(template.getProjectType());
        project.setSourceSystem(reqVO.getSourceSystem());
        project.setSourceBusinessKey(reqVO.getSourceBusinessKey());
        project.setStatus(0); // 待指派
        project.setTemplateId(template.getId());
        project.setManagerUserId(reqVO.getManagerUserId());
        // 初始化为根项目
        project.setParentId(null);
        project.setDepth(0);
        project.setSort(0);
        projectMapper.insert(project);
        // 回填树字段
        ProjectDO treeUpdate = new ProjectDO();
        treeUpdate.setId(project.getId());
        treeUpdate.setRootId(project.getId());
        treeUpdate.setPath("/" + project.getId() + "/");
        projectMapper.updateById(treeUpdate);

        // 5. 批量创建阶段
        Map<String, Long> phaseCodeToIdMap = new LinkedHashMap<>();
        if (snapshot.getPhases() != null) {
            for (TemplateSnapshot.PhaseDef phaseDef : snapshot.getPhases()) {
                ProjectPhaseDO phase = new ProjectPhaseDO();
                phase.setProjectId(project.getId());
                phase.setTemplateId(null);
                phase.setName(phaseDef.getPhaseName());
                phase.setCode(phaseDef.getPhaseCode());
                phase.setSort(phaseDef.getSortOrder() != null ? phaseDef.getSortOrder() : 0);
                phase.setStatus(0); // 未开始
                phase.setEntryCriteria(phaseDef.getEntryCriteria());
                phase.setExitCriteria(phaseDef.getExitCriteria());
                projectPhaseMapper.insert(phase);
                phaseCodeToIdMap.put(phaseDef.getPhaseCode(), phase.getId());
            }
        }

        // 6. 批量创建任务（两阶段：先插入，再回填 parent/root/path/depth）
        Map<String, Long> taskCodeToIdMap = new LinkedHashMap<>();
        if (snapshot.getTasks() != null) {
            // 6.1 第一遍：插入全部任务
            for (TemplateSnapshot.TaskDef taskDef : snapshot.getTasks()) {
                ProjectTaskDO task = new ProjectTaskDO();
                task.setProjectId(project.getId());
                task.setName(taskDef.getTaskName());
                task.setCode(taskDef.getTaskCode());
                task.setDescription(taskDef.getDescription());
                task.setPriority(taskDef.getPriority());
                task.setSort(taskDef.getSortOrder() != null ? taskDef.getSortOrder() : 0);
                task.setEstimatedHours(taskDef.getEstimatedHours());
                task.setStatus(0); // 草稿
                task.setParentId(null);
                task.setDepth(0);
                projectTaskMapper.insert(task);
                taskCodeToIdMap.put(taskDef.getTaskCode(), task.getId());
            }
            // 6.2 第二遍：回填 parentTaskId/rootId/path/depth
            for (TemplateSnapshot.TaskDef taskDef : snapshot.getTasks()) {
                Long taskId = taskCodeToIdMap.get(taskDef.getTaskCode());
                ProjectTaskDO updateTask = new ProjectTaskDO();
                updateTask.setId(taskId);
                if (taskDef.getParentTaskCode() == null || taskDef.getParentTaskCode().isEmpty()) {
                    // 顶层任务
                    updateTask.setParentId(null);
                    updateTask.setRootId(taskId);
                    updateTask.setPath("/" + taskId + "/");
                    updateTask.setDepth(0);
                } else {
                    Long parentId = taskCodeToIdMap.get(taskDef.getParentTaskCode());
                    if (parentId == null) {
                        throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                                "任务【" + taskDef.getTaskCode() + "】的父任务编码【" + taskDef.getParentTaskCode() + "】不存在");
                    }
                    // 查询父任务获取 rootId/path/depth
                    ProjectTaskDO parentTask = projectTaskMapper.selectById(parentId);
                    updateTask.setParentId(parentId);
                    updateTask.setRootId(parentTask.getRootId());
                    updateTask.setPath(parentTask.getPath() + taskId + "/");
                    updateTask.setDepth(parentTask.getDepth() + 1);
                }
                projectTaskMapper.updateById(updateTask);
            }
        }

        // 7. 批量创建团队角色（待分配人员）
        if (snapshot.getTeamRoles() != null) {
            for (TemplateSnapshot.TeamRoleDef roleDef : snapshot.getTeamRoles()) {
                ProjectTeamMemberDO member = new ProjectTeamMemberDO();
                member.setProjectId(project.getId());
                member.setUserId(null); // 待分配
                member.setRoleCode(roleDef.getRoleCode());
                member.setRoleName(roleDef.getRoleName());
                member.setStatus(0); // 启用
                projectTeamMemberMapper.insert(member);
            }
        }

        return project.getId();
    }

    /**
     * 校验快照完整性
     */
    private void validateSnapshot(TemplateSnapshot snapshot) {
        if (snapshot == null) {
            throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "快照内容为空");
        }
        if (snapshot.getPhases() == null || snapshot.getPhases().isEmpty()) {
            throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "阶段定义不能为空");
        }
        // 校验阶段编码唯一
        Set<String> phaseCodes = new HashSet<>();
        for (TemplateSnapshot.PhaseDef phase : snapshot.getPhases()) {
            if (phase.getPhaseCode() == null || phase.getPhaseCode().isEmpty()) {
                throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "阶段编码不能为空");
            }
            if (!phaseCodes.add(phase.getPhaseCode())) {
                throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                        "阶段编码【" + phase.getPhaseCode() + "】重复");
            }
        }
        // 校验任务编码唯一与引用完整性
        if (snapshot.getTasks() != null) {
            Set<String> taskCodes = new HashSet<>();
            for (TemplateSnapshot.TaskDef task : snapshot.getTasks()) {
                if (task.getTaskCode() == null || task.getTaskCode().isEmpty()) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "任务编码不能为空");
                }
                if (!taskCodes.add(task.getTaskCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务编码【" + task.getTaskCode() + "】重复");
                }
                if (task.getPhaseCode() != null && !phaseCodes.contains(task.getPhaseCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务【" + task.getTaskCode() + "】引用的阶段编码【" + task.getPhaseCode() + "】不存在");
                }
                if (task.getParentTaskCode() != null && !task.getParentTaskCode().isEmpty()) {
                    if (task.getParentTaskCode().equals(task.getTaskCode())) {
                        throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                                "任务【" + task.getTaskCode() + "】不能以自身为父任务");
                    }
                }
            }
            // 二次遍历校验 parentTaskCode 引用存在
            for (TemplateSnapshot.TaskDef task : snapshot.getTasks()) {
                if (task.getParentTaskCode() != null && !task.getParentTaskCode().isEmpty()
                        && !taskCodes.contains(task.getParentTaskCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "任务【" + task.getTaskCode() + "】的父任务编码【" + task.getParentTaskCode() + "】不存在");
                }
            }
        }
        // 校验团队角色编码唯一
        if (snapshot.getTeamRoles() != null) {
            Set<String> roleCodes = new HashSet<>();
            for (TemplateSnapshot.TeamRoleDef role : snapshot.getTeamRoles()) {
                if (role.getRoleCode() == null || role.getRoleCode().isEmpty()) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID, "团队角色编码不能为空");
                }
                if (!roleCodes.add(role.getRoleCode())) {
                    throw exception(PROJECT_TEMPLATE_SNAPSHOT_INVALID,
                            "团队角色编码【" + role.getRoleCode() + "】重复");
                }
            }
        }
    }
}
```

- [x] **Step 3: 确认 ProjectMapper 有 selectByCode 和 selectBySourceSystemAndBusinessKey 方法**

读取 `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/project/ProjectMapper.java`，确认方法签名。若无 `selectByCode` / `selectBySourceSystemAndBusinessKey`，则补充：

```java
    default ProjectDO selectByCode(String code) {
        return selectOne(ProjectDO::getCode, code);
    }

    default ProjectDO selectBySourceSystemAndBusinessKey(String sourceSystem, String sourceBusinessKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getSourceSystem, sourceSystem)
                .eq(ProjectDO::getSourceBusinessKey, sourceBusinessKey));
    }
```

- [x] **Step 4: 修复 Service 中的客户校验**

Step 2 中的客户校验逻辑被简化了。正确做法是注入 `CustomerMapper` 并校验客户存在：

在 `ProjectTemplateServiceImpl` 的字段声明区新增：

```java
    @Resource
    private cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper customerMapper;
```

将 `createProjectFromTemplate` 中第 2 步的客户校验替换为：

```java
        // 校验客户存在
        if (reqVO.getCustomerId() == null
                || customerMapper.selectById(reqVO.getCustomerId()) == null) {
            throw exception(ErrorCodeConstants.PROJECT_CUSTOMER_NOT_EXISTS);
        }
```

删除原 `if (reqVO.getCustomerId() != null && projectMapper.selectCount() > 0L)` 代码块。

- [x] **Step 5: 编译验证**

Run: `cd pms-module-project && mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 6: Commit**

```bash
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/project/ProjectMapper.java
git commit -m "feat(pms): 实现项目模板 Service（CRUD + createProjectFromTemplate 实例化）"
```

---

## Task 5: 后端 Controller

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/ProjectTemplateController.java`

- [x] **Step 1: 创建 Controller**

```java
package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 项目模板")
@RestController
@RequestMapping("/pms/project-template")
@Validated
public class ProjectTemplateController {

    @Resource
    private ProjectTemplateService projectTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Long> createTemplate(@Valid @RequestBody ProjectTemplateSaveReqVO createReqVO) {
        return success(projectTemplateService.createProjectTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Boolean> updateTemplate(@Valid @RequestBody ProjectTemplateSaveReqVO updateReqVO) {
        projectTemplateService.updateProjectTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目模板")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:create')")
    public CommonResult<Boolean> deleteTemplate(@RequestParam("id") Long id) {
        projectTemplateService.deleteProjectTemplate(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询项目模板详情")
    @Parameter(name = "id", description = "模板编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<ProjectTemplateRespVO> getTemplate(@RequestParam("id") Long id) {
        ProjectTemplateDO template = projectTemplateService.getProjectTemplate(id);
        return success(BeanUtils.toBean(template, ProjectTemplateRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<PageResult<ProjectTemplateRespVO>> getTemplatePage(@Validated ProjectTemplatePageReqVO pageReqVO) {
        PageResult<ProjectTemplateDO> pageResult = projectTemplateService.getProjectTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectTemplateRespVO.class));
    }

    @GetMapping("/enabled-list")
    @Operation(summary = "查询全部启用项目模板")
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<ProjectTemplateRespVO>> getEnabledTemplateList() {
        List<ProjectTemplateDO> list = projectTemplateService.getEnabledProjectTemplateList();
        return success(BeanUtils.toBean(list, ProjectTemplateRespVO.class));
    }

    @GetMapping("/enabled-list-by-type")
    @Operation(summary = "按项目类型查询启用项目模板")
    @Parameter(name = "projectType", description = "项目类型", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<ProjectTemplateRespVO>> getEnabledTemplateListByType(
            @RequestParam("projectType") String projectType) {
        List<ProjectTemplateDO> list = projectTemplateService.getEnabledProjectTemplateListByType(projectType);
        return success(BeanUtils.toBean(list, ProjectTemplateRespVO.class));
    }

    @PostMapping("/create-project")
    @Operation(summary = "从模板创建项目")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<Long> createProjectFromTemplate(@Valid @RequestBody ProjectCreateFromTemplateReqVO reqVO) {
        return success(projectTemplateService.createProjectFromTemplate(reqVO));
    }
}
```

- [x] **Step 2: 编译验证**

Run: `cd pms-module-project && mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 3: Commit**

```bash
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttemplate/ProjectTemplateController.java
git commit -m "feat(pms): 新增项目模板 Controller（CRUD + enabled-list + create-project）"
```

---

## Task 6: 后端整体编译 + 后端重启

**Files:**
- 无新增文件，验证整体编译

- [x] **Step 1: 整体编译 yudao-server**

Run: `mvn clean compile -q -pl yudao-server -am`（项目根目录）
Expected: BUILD SUCCESS

- [x] **Step 2: 打包 jar**

Run: `mvn clean package -q -pl yudao-server -am -DskipTests`
Expected: `yudao-server/target/yudao-server.jar` 生成

- [x] **Step 3: 停止旧后端进程**

Run PowerShell: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*yudao-server.jar*npdms-dev*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`

- [x] **Step 4: 重启后端**

Run（非阻塞，cwd=`e:\AICoding\Projects\NPMS\yudao-server`）:
`& "C:\Program Files\Java\jdk-25.0.1+8\bin\java.exe" -jar target/yudao-server.jar --spring.profiles.active=npdms-dev`

- [x] **Step 5: 验证后端启动**

等待 30 秒后访问 `http://127.0.0.1:58080/actuator/health` 或查看日志输出"项目启动成功"。

- [x] **Step 6: Commit（如有残留改动）**

```bash
git add -A
git commit -m "chore(pms): 项目模板后端整体编译与重启验证"
```

---

## Task 7: 前端 API + 字典常量

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-template/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/utils/dict.ts`

- [x] **Step 1: 创建前端 API**

```typescript
import request from '@/config/axios'

export interface PhaseDef {
  phaseCode: string
  phaseName: string
  sortOrder: number
  entryCriteria?: string
  exitCriteria?: string
}

export interface TaskDef {
  taskCode: string
  taskName: string
  parentTaskCode?: string
  phaseCode?: string
  priority?: number
  sortOrder: number
  estimatedHours?: number
  description?: string
}

export interface TeamRoleDef {
  roleCode: string
  roleName: string
  requiredCount: number
}

export interface TemplateSnapshot {
  schemaVersion: number
  phases: PhaseDef[]
  tasks: TaskDef[]
  teamRoles: TeamRoleDef[]
}

export interface ProjectTemplateVO {
  id?: number
  code: string
  name: string
  projectType?: string
  description?: string
  status: number
  sort: number
  snapshotJson?: TemplateSnapshot
  createTime?: Date
}

export interface ProjectCreateFromTemplateVO {
  templateId: number
  code: string
  name: string
  customerId: number
  contractCode?: string
  sourceSystem: string
  sourceBusinessKey: string
  managerUserId?: number
}

const baseUrl = '/pms/project-template'

export const getProjectTemplatePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectTemplate = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectTemplate = (data: ProjectTemplateVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectTemplate = (data: ProjectTemplateVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectTemplate = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getEnabledProjectTemplateList = () =>
  request.get({ url: `${baseUrl}/enabled-list` })
export const getEnabledProjectTemplateListByType = (projectType: string) =>
  request.get({ url: `${baseUrl}/enabled-list-by-type`, params: { projectType } })
export const createProjectFromTemplate = (data: ProjectCreateFromTemplateVO) =>
  request.post({ url: `${baseUrl}/create-project`, data })
```

- [x] **Step 2: 新增 DICT_TYPE 常量**

在 `src/utils/dict.ts` 的 `DICT_TYPE` 枚举中（`PMS_CURRENCY` 之后）新增：

```typescript
  PMS_PROJECT_TYPE = 'pms_project_type', // 项目类型
```

- [x] **Step 3: ESLint 验证**

Run: `cd yudao-ui/yudao-ui-admin-vue3 && npx eslint "src/api/pms/project/project-template/index.ts" "src/utils/dict.ts" --no-error-on-unmatched-pattern`
Expected: 无错误

- [x] **Step 4: Commit**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-template/index.ts yudao-ui/yudao-ui-admin-vue3/src/utils/dict.ts
git commit -m "feat(pms-ui): 新增项目模板 API 与 PMS_PROJECT_TYPE 字典常量"
```

---

## Task 8: 前端模板管理页

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-template/index.vue`

- [x] **Step 1: 创建模板管理页**

创建 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-template/index.vue`，包含：
- 查询表单（模板编码/名称/项目类型字典选择器/状态）
- 表格（编码、名称、项目类型 dict-tag、状态 dict-tag、排序、操作）
- 编辑 Dialog（基本信息 + 阶段定义表格 + 任务定义树形表格 + 团队角色表格）

```vue
<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="模板编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="项目类型" prop="projectType">
        <el-select v-model="query.projectType" clearable class="!w-180px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option :value="0" label="启用" />
          <el-option :value="1" label="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-template:create']">
          <Icon icon="ep:plus" />新增模板
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无项目模板数据">
      <el-table-column prop="code" label="模板编码" min-width="140" />
      <el-table-column prop="name" label="模板名称" min-width="160" />
      <el-table-column prop="projectType" label="项目类型" width="140">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_TYPE" :value="row.projectType" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-template:create']">编辑</el-button>
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-template:create']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 模板编辑 Dialog -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑项目模板' : '新增项目模板'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="模板编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 TPL-NET-01" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板名称" prop="name">
            <el-input v-model="form.name" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目类型" prop="projectType">
            <el-select v-model="form.projectType" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="0">启用</el-radio>
              <el-radio :value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序号" prop="sort">
            <el-input-number v-model="form.sort" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="描述" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 阶段定义 -->
      <el-divider content-position="left">阶段定义</el-divider>
      <el-table :data="snapshot.phases" border size="small" style="margin-bottom: 8px">
        <el-table-column label="阶段编码" width="160">
          <template #default="{ row }">
            <el-input v-model="row.phaseCode" placeholder="如 STARTUP" />
          </template>
        </el-table-column>
        <el-table-column label="阶段名称" width="160">
          <template #default="{ row }">
            <el-input v-model="row.phaseName" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="准入条件">
          <template #default="{ row }">
            <el-input v-model="row.entryCriteria" />
          </template>
        </el-table-column>
        <el-table-column label="退出条件">
          <template #default="{ row }">
            <el-input v-model="row.exitCriteria" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.phases.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.phases.push({ phaseCode: '', phaseName: '', sortOrder: snapshot.phases.length + 1, entryCriteria: '', exitCriteria: '' })">
        <Icon icon="ep:plus" />添加阶段
      </el-button>

      <!-- 任务定义 -->
      <el-divider content-position="left">任务定义</el-divider>
      <el-table :data="snapshot.tasks" border size="small" :tree-props="{ children: 'children' }" row-key="taskCode" style="margin-bottom: 8px">
        <el-table-column label="任务编码" width="160">
          <template #default="{ row }">
            <el-input v-model="row.taskCode" placeholder="如 T-STARTUP-01" />
          </template>
        </el-table-column>
        <el-table-column label="任务名称" width="160">
          <template #default="{ row }">
            <el-input v-model="row.taskName" />
          </template>
        </el-table-column>
        <el-table-column label="父任务编码" width="160">
          <template #default="{ row }">
            <el-select v-model="row.parentTaskCode" clearable class="!w-full" placeholder="空=顶层">
              <el-option
                v-for="t in snapshot.tasks.filter(x => x.taskCode !== row.taskCode)"
                :key="t.taskCode"
                :label="`${t.taskCode} (${t.taskName})`"
                :value="t.taskCode"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="所属阶段" width="150">
          <template #default="{ row }">
            <el-select v-model="row.phaseCode" clearable class="!w-full">
              <el-option
                v-for="p in snapshot.phases"
                :key="p.phaseCode"
                :label="p.phaseName"
                :value="p.phaseCode"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-select v-model="row.priority" class="!w-full">
              <el-option :value="0" label="低" />
              <el-option :value="1" label="中" />
              <el-option :value="2" label="高" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="预估工时" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.estimatedHours" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.tasks.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.tasks.push({ taskCode: '', taskName: '', parentTaskCode: '', phaseCode: '', priority: 1, sortOrder: snapshot.tasks.length + 1, estimatedHours: 0, description: '' })">
        <Icon icon="ep:plus" />添加任务
      </el-button>

      <!-- 团队角色 -->
      <el-divider content-position="left">团队角色</el-divider>
      <el-table :data="snapshot.teamRoles" border size="small" style="margin-bottom: 8px">
        <el-table-column label="角色编码" width="180">
          <template #default="{ row }">
            <el-input v-model="row.roleCode" placeholder="如 PROJECT_MANAGER" />
          </template>
        </el-table-column>
        <el-table-column label="角色名称" width="180">
          <template #default="{ row }">
            <el-input v-model="row.roleName" />
          </template>
        </el-table-column>
        <el-table-column label="需求人数" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.requiredCount" :min="1" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.teamRoles.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.teamRoles.push({ roleCode: '', roleName: '', requiredCount: 1 })">
        <Icon icon="ep:plus" />添加角色
      </el-button>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as TemplateApi from '@/api/pms/project/project-template'
import type { ProjectTemplateVO, TemplateSnapshot } from '@/api/pms/project/project-template'

defineOptions({ name: 'PmsProjectTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  projectType: '' as string,
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await TemplateApi.getProjectTemplatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 编辑表单
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ProjectTemplateVO>({
  code: '',
  name: '',
  projectType: '',
  description: '',
  status: 0,
  sort: 0
})
const snapshot = reactive<TemplateSnapshot>({
  schemaVersion: 1,
  phases: [],
  tasks: [],
  teamRoles: []
})
const rules = {
  code: [{ required: true, message: '请输入模板编码' }],
  name: [{ required: true, message: '请输入模板名称' }]
}

const open = async (row?: ProjectTemplateVO) => {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    projectType: '',
    description: '',
    status: 0,
    sort: 0
  })
  Object.assign(snapshot, { schemaVersion: 1, phases: [], tasks: [], teamRoles: [] })
  if (row?.id) {
    const detail = await TemplateApi.getProjectTemplate(row.id)
    Object.assign(form, detail)
    if (detail.snapshotJson) {
      Object.assign(snapshot, detail.snapshotJson)
    }
  }
  formVisible.value = true
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, snapshotJson: { ...snapshot } }
    if (form.id) {
      await TemplateApi.updateProjectTemplate(payload)
      message.success('更新成功')
    } else {
      await TemplateApi.createProjectTemplate(payload)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (row: ProjectTemplateVO) => {
  await message.delConfirm()
  await TemplateApi.deleteProjectTemplate(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
```

- [x] **Step 2: ESLint 验证**

Run: `cd yudao-ui/yudao-ui-admin-vue3 && npx eslint "src/views/pms/project/project-template/index.vue" --no-error-on-unmatched-pattern`
Expected: 无错误

- [x] **Step 3: Commit**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-template/index.vue
git commit -m "feat(pms-ui): 新增项目模板管理页（CRUD + 阶段/任务/团队角色编辑器）"
```

---

## Task 9: 前端项目列表页"从模板创建"

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/index.vue`

- [x] **Step 1: 读取现有项目列表页操作按钮区**

读取 `src/views/pms/project/project/index.vue` 第 83-110 行，确认"项目分类"和"指派项目经理"按钮的位置。

- [x] **Step 2: 新增"从模板创建"按钮**

在"指派项目经理"按钮（`openAssign`）之后、`</el-form-item>` 之前新增：

```vue
          <el-button
            type="warning"
            plain
            @click="openCreateFromTemplate()"
            v-hasPermi="['pms:project:create']"
          >
            <Icon icon="ep:document-copy" />从模板创建
          </el-button>
```

- [x] **Step 3: 新增从模板创建对话框**

在现有 Dialog（指派/分类）之后新增"从模板创建项目"Dialog。读取文件末尾结构，在最后一个 `</Dialog>` 之后、`</template>` 之前新增：

```vue
  <!-- 从模板创建项目 Dialog -->
  <Dialog v-model="tplCreateVisible" title="从模板创建项目" width="720px">
    <el-form ref="tplFormRef" :model="tplForm" :rules="tplRules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="24">
          <el-form-item label="项目模板" prop="templateId">
            <el-select v-model="tplForm.templateId" placeholder="请选择项目模板" class="!w-full" @change="onTemplateChange">
              <el-option
                v-for="t in enabledTemplates"
                :key="t.id"
                :label="`${t.code} - ${t.name}`"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目编码" prop="code">
            <el-input v-model="tplForm.code" placeholder="如 PMS202608001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目名称" prop="name">
            <el-input v-model="tplForm.name" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId">
            <PmsEntitySelect
              v-model="tplForm.customerId"
              :api="CustomerApi.getCustomerPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择客户"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="合同编码" prop="contractCode">
            <el-input v-model="tplForm.contractCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目经理" prop="managerUserId">
            <PmsEntitySelect
              v-model="tplForm.managerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择项目经理"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源系统" prop="sourceSystem">
            <el-input v-model="tplForm.sourceSystem" placeholder="如 MANUAL" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源业务键" prop="sourceBusinessKey">
            <el-input v-model="tplForm.sourceBusinessKey" placeholder="如 MANUAL-PMS202608001" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="tplCreateVisible = false">取消</el-button>
      <el-button type="primary" :loading="tplSaving" @click="saveCreateFromTemplate">创建</el-button>
    </template>
  </Dialog>
```

- [x] **Step 4: 新增 script 逻辑**

在 `<script setup>` 块中新增 import 和逻辑（在现有 import 之后）：

```typescript
import * as TemplateApi from '@/api/pms/project/project-template'
import type { ProjectCreateFromTemplateVO } from '@/api/pms/project/project-template'
import * as CustomerApi from '@/api/pms/project/customer'
import * as UserApi from '@/api/system/user'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
```

在 script 末尾（`onMounted` 之前）新增：

```typescript
// 从模板创建
const tplCreateVisible = ref(false)
const tplSaving = ref(false)
const tplFormRef = ref()
const enabledTemplates = ref<any[]>([])
const tplForm = reactive<ProjectCreateFromTemplateVO>({
  templateId: 0,
  code: '',
  name: '',
  customerId: 0,
  contractCode: '',
  sourceSystem: 'MANUAL',
  sourceBusinessKey: '',
  managerUserId: undefined
})
const tplRules = {
  templateId: [{ required: true, message: '请选择项目模板' }],
  code: [{ required: true, message: '请输入项目编码' }],
  name: [{ required: true, message: '请输入项目名称' }],
  customerId: [{ required: true, message: '请选择客户' }],
  sourceSystem: [{ required: true, message: '请输入来源系统' }],
  sourceBusinessKey: [{ required: true, message: '请输入来源业务键' }]
}

const openCreateFromTemplate = async () => {
  Object.assign(tplForm, {
    templateId: 0,
    code: '',
    name: '',
    customerId: 0,
    contractCode: '',
    sourceSystem: 'MANUAL',
    sourceBusinessKey: '',
    managerUserId: undefined
  })
  enabledTemplates.value = await TemplateApi.getEnabledProjectTemplateList()
  tplCreateVisible.value = true
}

const onTemplateChange = (templateId: number) => {
  const tpl = enabledTemplates.value.find((t) => t.id === templateId)
  if (tpl && !tplForm.code) {
    // 自动填充来源业务键建议
    tplForm.sourceBusinessKey = `MANUAL-${tpl.code}-${Date.now()}`
  }
}

const saveCreateFromTemplate = async () => {
  await tplFormRef.value.validate()
  tplSaving.value = true
  try {
    await TemplateApi.createProjectFromTemplate(tplForm)
    message.success('项目创建成功')
    tplCreateVisible.value = false
    await loadList()
  } finally {
    tplSaving.value = false
  }
}
```

- [x] **Step 5: ESLint 验证**

Run: `cd yudao-ui/yudao-ui-admin-vue3 && npx eslint "src/views/pms/project/project/index.vue" --no-error-on-unmatched-pattern`
Expected: 无错误

- [x] **Step 6: Commit**

```bash
git add yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/index.vue
git commit -m "feat(pms-ui): 项目列表页新增从模板创建项目功能"
```

---

## Task 10: 前端构建验证

**Files:**
- 无新增文件

- [x] **Step 1: 前端构建验证**

Run: `cd yudao-ui/yudao-ui-admin-vue3 && npx vue-tsc --noEmit 2>&1 | head -50`
Expected: 无新增类型错误（pre-existing 错误可忽略）

- [x] **Step 2: ESLint 全量验证新增文件**

Run: `cd yudao-ui/yudao-ui-admin-vue3 && npx eslint "src/api/pms/project/project-template/index.ts" "src/views/pms/project/project-template/index.vue" "src/views/pms/project/project/index.vue" "src/utils/dict.ts" --no-error-on-unmatched-pattern`
Expected: 无错误

- [x] **Step 3: Commit（如有修复）**

```bash
git add -A
git commit -m "chore(pms-ui): 项目模板前端构建与 ESLint 验证"
```

---

## Task 11: 业务验收（UI 闭环）

**Files:**
- 无新增文件

按 user_profile 约定：必须用 Trae 内置浏览器，点开所有菜单，截图每个界面。

- [x] **Step 1: 启动前端开发服务器**

Run（非阻塞，cwd=`e:\AICoding\Projects\NPMS\yudao-ui\yudao-ui-admin-vue3`）: `npm run dev`

- [x] **Step 2: 用 Trae 内置浏览器打开前端**

访问 `http://localhost:80` 或前端开发服务器输出的 URL，登录系统。

- [x] **Step 3: 验证项目模板管理页**

- 在左侧菜单找到"项目模板管理"，点击进入
- 截图：模板列表页（应显示 3 条种子数据）
- 点击"新增模板"，截图：模板编辑对话框（基本信息 + 阶段定义 + 任务定义 + 团队角色）
- 点击"编辑"种子模板（如 TPL-NET-01），截图：编辑对话框应正确加载快照数据
- 验证：项目类型下拉应显示字典选项（网络集成/安全部署/运维服务等）

- [x] **Step 4: 验证从模板创建项目**

- 进入"项目列表"页
- 截图：列表页应显示"从模板创建"按钮
- 点击"从模板创建"，截图：对话框应显示模板选择器和项目信息表单
- 选择模板"TPL-NET-01"，填写项目编码/名称/客户/来源业务键
- 点击"创建"，验证：
  - 提示"项目创建成功"
  - 项目列表新增一条记录
  - 截图：新项目记录

- [x] **Step 5: 验证项目模板实例化结果**

- 在项目列表点击新项目进入详情页
- 截图：项目详情页
- 验证"项目阶段"子表：应有 3 条阶段记录（启动/实施/验收）
- 验证"任务 WBS"子表：应有 6 条任务记录（含父子层级）
- 验证"团队成员"子表：应有 3 条角色记录（项目经理/技术负责人/工程师，userId 为空待分配）
- 截图：各子表数据

- [x] **Step 6: 验证引用字段显示**

- 确认项目列表页的"项目经理"列显示用户名称（UserTag），不是 ID
- 确认项目详情页的"客户"字段显示客户名称（CustomerTag），不是 ID

- [x] **Step 7: 验收报告**

汇总所有截图和验证结果，记录任何问题。

- [x] **Step 8: Commit（如有修复）**

```bash
git add -A
git commit -m "test(pms): 项目模板业务验收（UI 闭环）"
```

---

## Self-Review 结果

**1. Spec coverage:**
- ✅ 数据模型（ProjectTemplateDO + TemplateSnapshot DTO）→ Task 2
- ✅ 现有表扩展（ProjectDO.templateId + ProjectPhaseTemplateDO.projectTemplateId）→ Task 2
- ✅ 字典与菜单 → Task 1（迁移）
- ✅ 后端 Controller/Service/Mapper/VO → Task 2-5
- ✅ 实例化流程（阶段+任务两阶段+团队角色）→ Task 4
- ✅ 错误码 1-014-023-000 → Task 2
- ✅ 前端 API + 字典常量 → Task 7
- ✅ 前端模板管理页 → Task 8
- ✅ 前端项目列表页"从模板创建" → Task 9
- ✅ 业务验收 → Task 11

**2. Placeholder scan:** 无占位符，所有步骤含完整代码。

**3. Type consistency:**
- `ProjectTaskDO.priority` 为 Integer → TemplateSnapshot.TaskDef.priority 为 Integer，前端 el-option value 为 0/1/2 ✅
- `ProjectTeamMemberDO` 无 requiredCount → TeamRoleDef.requiredCount 仅存快照，实例化时不写入 ✅
- 错误码段 1-014-023-000 全计划一致 ✅
- 前端 API 函数名与后端路由一致 ✅
