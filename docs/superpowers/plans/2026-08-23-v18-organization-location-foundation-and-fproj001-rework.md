# V1.8 组织与地点基础能力及 F-PROJ-001 重做实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐公司、部门编码和用户组织范围，建立统一地址—站点—位置树能力，并从首个 Feature 重新改造项目创建、服务经理指派、工勘、安装和设备当前位置。

**Architecture:** 复用既有 `pms-module-asset` 统一拥有地址、站点、站点内部位置树、来源映射、行政区划—部门映射和设备当前位置；`pms-module-customer` 仅引用地址/站点，`yudao-module-system` 继续拥有公司、部门和用户组织范围。项目和工程模块只通过资产域公共 API 引用稳定 ID 和版本，文本位置仅在结构化站点未维护时作为 `UNRESOLVED` 降级输入。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway 11.10.5、Vue 3、TypeScript、Element Plus、pnpm 9.15.5、Node Test Runner、Docker Compose、Codex 内置浏览器。

**Spec:** `docs/superpowers/specs/2026-08-23-location-site-address-model-design.md`

## Global Constraints

- 规格仓库是唯一业务与设计事实源；实现前必须把组织与地点设计进入规格仓库、提交、重新同步，并让 `docs/specification-baseline/manifest.json.source.commit` 锁定新提交。
- `specs/001-project-delivery-platform/` 只作历史参照，不校验、不驱动 Gate，也不在 NPDMS 直接修改。
- 不使用 `docs/superpowers/plans/2026-08-21-f-proj-001-manual-project-creation-and-template-initialization.md` 或任何旧 F-PROJ-001 计划判断完成度。
- PRD V1.8 从首个 Feature 重新检查；V1.7 已有实现不构成“已实现”证据。
- 禁用测试驱动：每个任务先实现最小闭环，再补测试和验证；不得先写失败测试。
- 每个任务完成后检查变更范围并按情况自动创建一个本地提交；提交前必须重新读取 `$git-commit` skill；禁止自动 push。
- 只新增前向 Flyway 迁移；不得修改 V1～V63 已执行迁移。
- 公司与部门是分离主数据；办事处是部门；禁止通过部门推导公司。
- 部门编码统一为主数据 `system_dept.code`，跨模块统一为 `department_id/department_code/department_name`。
- 地点字段统一为 `area_code/area_level`；禁止重新引入 `administrative_division_code`、`office_code` 或 `office_department_code`。
- 站点不绑定公司或办事处；服务办事处通过 `area_code + area_level -> department_code` 映射解析。
- 地点实体由 `pms-module-asset` 拥有，统一使用 `ast_` 表前缀；不得新建 `pms-module-location`，CUS 只保存客户对地址/站点的稳定引用。
- 模块间不得依赖目标模块 Service、Mapper、Repository 或业务表；只能调用公共 API 或消费公开事件。
- UI 闭环优先使用 Codex 内置浏览器；静态页面、编译、HTTP 200 或单元测试不能替代业务验收。

---

### Task 0: 锁定新的正式规格输入

**Files:**
- Modify after source approval: `docs/specification-baseline/allowlist.json`
- Modify by sync tool only: `docs/specification-baseline/manifest.json`
- Modify by sync tool only: the managed PRD/SDS/Feature files reported as `REPLACE` or `ADD`
- Verify: `scripts/sync_specification_baseline.py`
- Verify: `scripts/validate_specification_baseline.py`
- Verify: `scripts/validate_repository_baseline_rules.py`

**Interfaces:**
- Consumes: approved organization/location changes committed in `M:/AICoding/CodexData/worktrees/09b5/项目交付平台`.
- Produces: a new 40-character `manifest.json.source.commit` whose managed files explicitly define `system_company`, `system_dept.code`, user company-department scope, Address/Site/SiteLocation, area-department mapping, project-site relation, fallback status, and cross-module APIs.

- [ ] **Step 1: Verify the source commit contains every approved contract**

Run from this NPDMS worktree after the specification owner has committed the source change:

```powershell
$specRepo = 'M:/AICoding/CodexData/worktrees/09b5/项目交付平台'
$specCommit = git -C $specRepo rev-parse HEAD
git -C $specRepo show --stat --oneline $specCommit
rg -n "system_company|system_dept\.code|SiteLocation|ast_area_department_mapping|AssetLocationApi|location_resolution_status|proj_project_site" $specRepo/docs
```

Expected: the commit is a full 40-character ID and all six contract groups have explicit PRD/SDS/Feature evidence. If any group is absent, record the missing group and stop this task; do not compensate in NPDMS code.

- [ ] **Step 2: Preview the managed snapshot update**

```powershell
& 'C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
```

Expected: only intentional `ADD/REPLACE/KEEP` entries, no `CONFLICT`, and no changes under `specs/001-project-delivery-platform/` outside the managed sync result.

- [ ] **Step 3: Apply and validate the new snapshot**

```powershell
& 'C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json --apply
& 'C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' scripts/validate_specification_baseline.py
& 'C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' scripts/validate_repository_baseline_rules.py
```

Expected: all validators print `PASS`; `manifest.json.source.commit` equals `$specCommit`.

- [ ] **Step 4: Commit only the synchronized specification snapshot**

```powershell
git add docs/specification-baseline/allowlist.json docs/specification-baseline/manifest.json docs/baseline docs/design docs/decisions docs/engineering docs/traceability features
git diff --staged --name-only
git commit -m "docs(spec): 锁定组织与地点正式规格"
```

Expected: no business code, SQL migration, local plan, `specs/001` manual edit, or environment file is staged.

---

### Task 1: 建立公司、部门编码和用户组织范围

**Files:**
- Create: `sql/migrations/V64__system_company_department_scope.sql`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/company/CompanyDO.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/company/CompanyMapper.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/company/CompanyService.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/company/CompanyServiceImpl.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/company/CompanyApi.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/company/dto/CompanyRespDTO.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/permission/UserCompanyDepartmentScopeDO.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/permission/UserCompanyDepartmentScopeMapper.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApi.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/dto/UserCompanyDepartmentScopeRespDTO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dept/DeptDO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/dept/DeptApi.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/dept/dto/DeptRespDTO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptService.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImpl.java`
- Modify: `yudao-module-system/src/test/resources/sql/create_tables.sql`
- Test: `yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/company/CompanyServiceImplTest.java`
- Test: `yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/dept/DeptServiceImplTest.java`
- Test: `yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApiImplTest.java`

**Interfaces:**
- Consumes: current tenant, user, department and data-permission infrastructure.
- Produces: `CompanyApi#getCompanyByCode(String)`, `DeptApi#getDeptByCode(String)`, `OrganizationScopeApi#getActiveScopes(Long)`, and stable DTOs exposing ID, code, name, status and version.

- [ ] **Step 1: Add the forward organization schema**

Implement V64 with these invariant-bearing columns and keys:

```sql
ALTER TABLE system_dept
    ADD COLUMN code VARCHAR(64) NULL COMMENT '统一部门编码',
    ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    ADD UNIQUE KEY uk_system_dept_code (tenant_id, code, deleted);

CREATE TABLE system_company (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_company_code (tenant_id, code, deleted)
);

CREATE TABLE system_user_company_department_scope (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    company_code VARCHAR(64) NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    department_id BIGINT NULL,
    department_code VARCHAR(64) NULL,
    department_name VARCHAR(128) NULL,
    scope_role VARCHAR(32) NOT NULL,
    is_primary BIT(1) NOT NULL DEFAULT b'0',
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    status TINYINT NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_user_org_scope_current (tenant_id, user_id, status, effective_to),
    CONSTRAINT chk_user_scope_department_pair CHECK
      ((department_id IS NULL AND department_code IS NULL) OR
       (department_id IS NOT NULL AND department_code IS NOT NULL))
);
```

Do not create a global company-to-department relation or derive company from `system_dept`.

- [ ] **Step 2: Implement organization data objects, services and public APIs**

Use these signatures exactly:

```java
public interface CompanyApi {
    CompanyRespDTO getCompany(Long id);
    CompanyRespDTO getCompanyByCode(String code);
    void validateCompanyList(Collection<Long> ids);
}

public interface OrganizationScopeApi {
    List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId);
    boolean hasScope(Long userId, Long companyId, Long departmentId);
}

// Add to DeptApi
DeptRespDTO getDeptByCode(String code);
```

`DeptRespDTO` must expose `id/code/name/parentId/status/version`; `CompanyRespDTO` must expose `id/code/name/status/version`. Scope validation must match company and department from the same active row; department alone never grants cross-company access.

- [ ] **Step 3: Add organization tests after implementation**

Cover these concrete cases:

```java
assertEquals("DEPT-HZ-01", deptApi.getDeptByCode("DEPT-HZ-01").getCode());
assertTrue(scopeApi.hasScope(userId, companyAId, departmentId));
assertFalse(scopeApi.hasScope(userId, companyBId, departmentId));
assertThrows(ServiceException.class, () -> companyApi.getCompanyByCode("DISABLED"));
```

Run:

```powershell
mvn -pl yudao-module-system -Dtest=CompanyServiceImplTest,DeptServiceImplTest,OrganizationScopeApiImplTest test
```

Expected: all named tests pass; no existing system test fails.

- [ ] **Step 4: Commit the organization foundation**

```powershell
git add sql/migrations/V64__system_company_department_scope.sql yudao-module-system
git commit -m "feat(system): 补齐公司与部门组织契约"
```

---

### Task 2: 在资产域建立地点核心表

**Files:**
- Create: `sql/migrations/V65__asset_location_core.sql`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/location/AddressDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/location/SiteDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/location/SiteLocationDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/location/LocationSourceMappingDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/location/AreaDepartmentMappingDO.java`
- Create: corresponding Mapper files under `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/location/`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/enums/LocationResolutionStatus.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/enums/LocationMatchStatus.java`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/LocationSchemaContractTest.java`

**Interfaces:**
- Consumes: `DeptApi#getDeptByCode(String)` from Task 1.
- Produces: five AST-owned tables: `ast_address`, `ast_site`, `ast_site_location`, `ast_location_source_mapping`, `ast_area_department_mapping`.

- [ ] **Step 1: Establish AST physical ownership**

Place all location DOs, Mappers, services and transaction write entries in `pms-module-asset`. Do not create a new Maven module and do not place physical holders in CUS, PROJ or engineering. Customer references remain stable IDs without cross-module database foreign keys.

- [ ] **Step 2: Create the V65 location schema**

Implement all spec fields, with these required keys:

```sql
UNIQUE KEY uk_ast_site_code (tenant_id, code, deleted),
UNIQUE KEY uk_ast_site_location_code (tenant_id, site_id, code, deleted),
UNIQUE KEY uk_ast_location_source_key (tenant_id, source_system, object_type, source_key, deleted),
UNIQUE KEY uk_ast_area_department_effective
  (tenant_id, area_code, area_level, mapping_type, effective_from),
CHECK (area_level IN ('COUNTRY','PROVINCE','CITY','DISTRICT')),
CHECK (location_resolution_status IN ('UNRESOLVED','RESOLVED'))
```

Every owned table must include tenant, audit, soft-delete and version columns. Address fingerprints are indexed candidate values, never unique merge keys. Site has nullable `customer_id` and no company/department columns.

- [ ] **Step 3: Implement DOs, mappers and enum constants**

Use stable enum values:

```java
public enum LocationResolutionStatus { UNRESOLVED, RESOLVED }
public enum LocationMatchStatus { PENDING, MATCHED, CONFLICT, INVALID }
```

`AddressDO` stores country/province/city/district code/name pairs plus detail/full address. `SiteLocationDO` stores `parentId/treePath/treeDepth/treeSort` without fixed-depth validation.

- [ ] **Step 4: Add schema contract tests after implementation**

The test must parse V65 and assert table names, forbidden site columns, field names and constraints:

```java
assertTrue(sql.contains("`area_code`"));
assertTrue(sql.contains("`department_code`"));
assertFalse(sql.contains("administrative_division_code"));
assertFalse(siteDdl.contains("company_id"));
assertFalse(siteDdl.contains("department_id"));
```

Run:

```powershell
mvn -pl pms-module-asset -am -DskipTests package
mvn -pl pms-module-asset -Dtest=LocationSchemaContractTest test
```

- [ ] **Step 5: Commit AST location schema**

```powershell
git add pms-module-asset sql/migrations/V65__asset_location_core.sql
git commit -m "feat(asset): 建立地址站点位置基础模型"
```

---

### Task 3: 实现地址、站点、位置树和映射公共 API

**Files:**
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApi.java`
- Create: DTOs under `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/dto/`
- Create: address, site, location-tree and mapping services under `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/location/`
- Create: location admin controllers and VOs under `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/location/`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/enums/ErrorCodeConstants.java`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/location/AssetLocationApiImplTest.java`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/location/SiteLocationTreeServiceImplTest.java`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/location/AreaDepartmentMappingServiceImplTest.java`

**Interfaces:**
- Consumes: five location tables and `DeptApi`.
- Produces:

```java
public interface AssetLocationApi {
    LocationReferenceDTO maintain(LocationMaintenanceCommand command);
    AddressRespDTO getAddress(Long addressId, Integer expectedVersion);
    SiteRespDTO getSite(Long siteId, Integer expectedVersion);
    SiteLocationRespDTO getSiteLocation(Long locationId, Integer expectedVersion);
    List<SiteLocationRespDTO> getLocationTree(Long siteId);
    AreaDepartmentMappingRespDTO resolveDepartment(String areaCode, String areaLevel);
    void validateSites(Collection<Long> siteIds);
}
```

- [ ] **Step 1: Implement address and site lifecycle**

`maintain` must accept either existing IDs with expected versions or nested new data:

```java
public record LocationMaintenanceCommand(
        Long projectId,
        AddressInput address,
        SiteInput site,
        SiteLocationInput siteLocation,
        String fallbackLocation,
        String sourceBusinessType,
        String sourceBusinessId) {}
```

An existing shared address/site update with a stale version throws a version-conflict error. New survey/install-created data records source business type, source ID and actor audit fields. A normalized fingerprint returns candidates only; it never merges automatically.

- [ ] **Step 2: Implement arbitrary-depth tree rules**

The tree service must reject self-parenting, descendant-parent cycles, cross-site moves, duplicate site-local codes and disabling nodes with active descendants or current devices. Moving a node recomputes `treePath/treeDepth` for that subtree in one transaction.

- [ ] **Step 3: Implement exact area-to-department resolution**

Resolution must query exactly `tenant + areaCode + areaLevel + SERVICE_OFFICE + active interval`, call `DeptApi#getDeptByCode`, and return no result when the mapping is missing or the department is disabled. It must not try city/province fallback and must not infer company.

- [ ] **Step 4: Add service tests after implementation**

Cover same-address multi-site, arbitrary-depth tree, cycle rejection, stale version, exact mapping, missing mapping, disabled department, sync version replay and conflict status:

```java
assertNotEquals(siteA.id(), siteB.id());
assertEquals(6, rackU.getTreeDepth());
assertThrows(ServiceException.class, () -> treeService.move(rootId, childId, version));
assertEquals("DEPT-HZ-01", api.resolveDepartment("330106", "DISTRICT").departmentCode());
assertNull(api.resolveDepartment("330100", "CITY"));
```

Run:

```powershell
mvn -pl pms-module-asset -Dtest=AssetLocationApiImplTest,SiteLocationTreeServiceImplTest,AreaDepartmentMappingServiceImplTest test
```

- [ ] **Step 5: Commit location behavior and APIs**

```powershell
git add pms-module-asset
git commit -m "feat(asset): 提供地点维护与区划映射接口"
```

---

### Task 4: 改造项目多站点与服务经理人工指派

**Files:**
- Create: `sql/migrations/V66__project_site_location_resolution.sql`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectSiteDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectSiteMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectSiteReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectSiteRespVO.java`
- Modify: `pms-module-project/pom.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectMasterDO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectCreateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectRespVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectAssignManagerReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ManualProjectCreateCommand.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/AssignServiceManagerCommand.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationService.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectSiteApplicationServiceTest.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationServiceTest.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: `AssetLocationApi`, `CompanyApi`, `DeptApi`, `OrganizationScopeApi`.
- Produces: project-site relations, `ProjectCreateReqVO.sites`, `ProjectRespVO.sites`, `locationResolutionStatus`, and assignment input `siteId/departmentCode`.

- [ ] **Step 1: Add V66 project-site schema**

```sql
CREATE TABLE proj_project_site (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    primary_site BIT(1) NOT NULL DEFAULT b'0',
    scope_status VARCHAR(32) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    site_code_snapshot VARCHAR(64) NOT NULL,
    site_name_snapshot VARCHAR(128) NOT NULL,
    address_snapshot JSON NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_site_effective (tenant_id, project_id, site_id, effective_from)
);

ALTER TABLE proj_project
    ADD COLUMN location_resolution_status VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED';
```

Keep `implementation_location` as fallback text; do not add a second text authority.

- [ ] **Step 2: Replace raw organization and location inputs**

Use stable selections:

```java
public class ProjectCreateReqVO {
    private Long orderOfficeCompanyId;
    private Long orderOfficeDepartmentId;
    private List<ProjectSiteReqVO> sites;
    private String implementationLocation; // fallback only
}

public class ProjectAssignManagerReqVO {
    private Long managerId;
    private String roleCode;
    private String levelCode;
    private Long siteId;
    private String departmentCode;
    private LocalDateTime effectiveFrom;
}
```

Server APIs load code/name snapshots from company, department and location APIs. A request with non-empty sites is `RESOLVED`; an empty site list requires nonblank fallback text and is `UNRESOLVED`. Never accept a client-supplied arbitrary `locationId` or numeric `officeId`.

- [ ] **Step 3: Implement project-site atomic creation and assignment checks**

Within the existing project creation transaction: validate all sites and versions, create the project and template instances, insert all site relations, then write audit/outbox facts. Assignment uses exact area mapping for a suggested department but allows an authorized manual `departmentCode`; it validates active department and candidate organization scope before idempotency success is recorded.

- [ ] **Step 4: Add project tests after implementation**

Cover multi-site create, same-site reuse, fallback-only create, disabled site, stale site version, mapping miss with manual department, cross-company scope rejection and idempotent replay:

```java
assertEquals(2, projectSiteMapper.selectActiveByProjectId(projectId).size());
assertEquals("RESOLVED", projectMapper.selectById(projectId).getLocationResolutionStatus());
assertEquals("UNRESOLVED", fallbackProject.getLocationResolutionStatus());
assertThrows(ServiceException.class, () -> assignWithCrossCompanyScope());
```

Run:

```powershell
mvn -pl pms-module-project -Dtest=ProjectSiteApplicationServiceTest,ProjectManagerAssignmentApplicationServiceTest,ProjectManualCreationMySqlIntegrationTest test
```

- [ ] **Step 5: Commit project multi-site rework**

```powershell
git add sql/migrations/V66__project_site_location_resolution.sql pms-module-project
git commit -m "feat(project): 改造项目多站点与地点降级"
```

---

### Task 5: 改造工勘、安装和设备当前位置事实

**Files:**
- Create: `sql/migrations/V67__engineering_asset_location_fact.sql`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/AssetLocationApi.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/location/dto/EquipmentLocationEffectiveCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentLocationEffectiveService.java`
- Modify: `pms-module-engineering/pom.xml`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/sitesurvey/SiteSurveyDO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/sitesurvey/vo/SiteSurveySaveReqVO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/sitesurvey/SiteSurveyServiceImpl.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/installation/InstallationDO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/installation/vo/InstallationSaveReqVO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationServiceImpl.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/equipment/EquipmentDO.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/equipment/vo/EquipmentSaveReqVO.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/equipment/vo/EquipmentRespVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/sitesurvey/SiteSurveyLocationServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationLocationServiceTest.java`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentLocationEffectiveServiceTest.java`

**Interfaces:**
- Consumes: `AssetLocationApi#maintain`, `AssetLocationApi#getSiteLocation`, `AssetLocationApi#effectEquipmentLocation`, project authorization, and equipment ID.
- Produces:

```java
public record EquipmentLocationEffectiveCommand(
        Long equipmentId,
        Long installationId,
        Long siteId,
        Long siteLocationId,
        String locationText,
        String resolutionStatus,
        String locationSnapshot,
        LocalDateTime effectiveFrom) {}
```

- [ ] **Step 1: Add V67 structure without removing fallback fields**

Add nullable `site_id/site_location_id`, non-null `location_resolution_status`, address/location snapshots and effective interval fields to survey, installation and equipment tables. Keep existing `location/install_location` text columns as fallback. Add a uniqueness guard ensuring one current effective installation position per equipment.

- [ ] **Step 2: Allow inline location maintenance in survey and installation**

Add an optional nested `LocationMaintenanceCommand` to survey/install save requests. When supplied, call `AssetLocationApi#maintain` and persist returned IDs, versions and `RESOLVED`; otherwise require fallback text and persist `UNRESOLVED`. Survey confirmation never changes equipment current location.

Add the public command without exposing the asset service implementation:

```java
void effectEquipmentLocation(EquipmentLocationEffectiveCommand command);
```

- [ ] **Step 3: Make completed installation the equipment-location authority**

On `completeInstallation`, validate the equipment and location, close the prior effective installation interval, complete the new record, and call `AssetLocationApi#effectEquipmentLocation`. The AST command is idempotent by installation business key, updates `siteLocationId/location/locationResolutionStatus`, and appends the existing equipment version history in the caller transaction. Command failure rolls back installation completion. AST must not depend on engineering events, Service, Mapper, Repository or tables.

Remove `location` from writable equipment create/update behavior; it remains readable and is changed only by installation events or explicitly authorized fallback reconciliation.

- [ ] **Step 4: Add engineering and asset tests after implementation**

Cover survey-created location, installation-created location, fallback installation, completed installation update, move replacing the current interval, removal leaving no current structured location, listener rollback and survey non-effect:

```java
assertEquals("RESOLVED", completed.getLocationResolutionStatus());
assertEquals(locationId, equipmentMapper.selectById(equipmentId).getSiteLocationId());
assertNull(surveyOnlyEquipment.getSiteLocationId());
assertThrows(RuntimeException.class, () -> completeWhenAssetLocationCommandFails());
```

Run:

```powershell
mvn -pl pms-module-engineering,pms-module-asset -am -Dtest=SiteSurveyLocationServiceTest,InstallationLocationServiceTest,EquipmentLocationEffectiveServiceTest test
```

- [ ] **Step 5: Commit engineering and asset location facts**

```powershell
git add sql/migrations/V67__engineering_asset_location_fact.sql pms-module-engineering pms-module-asset
git commit -m "feat(engineering): 统一安装位置与设备当前位置"
```

---

### Task 6: 建立组织与地点管理端页面

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/system/dept/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/system/dept/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/system/company/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/system/company/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/location/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location/address/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location/site/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location/site/LocationTreeDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location/area-department/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location/location-contract.spec.ts`
- Create: `sql/migrations/V68__organization_location_menu_seed.sql`

**Interfaces:**
- Consumes: system company/dept APIs and location admin controllers.
- Produces: selectors and maintenance screens reused by project, survey and installation forms.

- [ ] **Step 1: Add company and department-code maintenance UI**

Department forms must show required `code`; company UI supports code/name/status/version. No form may ask users to encode company ownership in the department tree.

- [ ] **Step 2: Add address, site, tree and mapping UI**

Address form fields are exactly country/province/city/district code/name plus detail address and optional coordinates. Site form selects a reusable address and optional customer. The tree drawer supports arbitrary depth. Mapping form uses `areaCode/areaLevel/departmentCode` and labels the department as“服务办事处”。

- [ ] **Step 3: Add menus, permissions and representative seed data**

V68 must add menu/button permissions for company and location administration plus representative data: one address with two sites, a six-level location chain, exact district mapping, disabled mapping and an unresolved location example. IDs use the repository's dedicated high-range seed convention and creators use `v68-org-location`.

- [ ] **Step 4: Add frontend contract tests after implementation**

```ts
assert.match(siteSource, /customerId/)
assert.doesNotMatch(siteSource, /companyId|departmentId/)
assert.match(mappingSource, /areaCode/)
assert.match(mappingSource, /departmentCode/)
assert.doesNotMatch(mappingSource, /administrativeDivisionCode|officeCode/)
```

Run:

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
node --test src/views/pms/asset/location/location-contract.spec.ts
pnpm ts:check
```

- [ ] **Step 5: Commit organization and location management UI**

```powershell
git add sql/migrations/V68__organization_location_menu_seed.sql yudao-ui/yudao-ui-admin-vue3/src/api/system yudao-ui/yudao-ui-admin-vue3/src/views/system/dept yudao-ui/yudao-ui-admin-vue3/src/views/system/company yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/location yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/location
git commit -m "feat(asset): 增加组织地点管理页面"
```

---

### Task 7: 改造项目、工勘、安装和设备页面闭环

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/site-survey/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/site-survey/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/installation/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/installation/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/equipment/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/equipment/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsLocationSelector/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsLocationSelector/locationSelector.spec.ts`

**Interfaces:**
- Consumes: location selectors, project multi-site APIs, inline maintenance commands and equipment current-location response.
- Produces: V1.8 browser-visible create/assign/survey/install/device workflows.

- [ ] **Step 1: Replace project raw inputs with selectors and fallback mode**

Company and department are selectors backed by system APIs. Project creation supports multiple site rows with one primary site. “站点未维护” explicitly switches to fallback text and shows `UNRESOLVED`; raw company/department codes, numeric office IDs and numeric location IDs are removed from the UI.

- [ ] **Step 2: Add inline location maintenance to survey and installation**

`PmsLocationSelector` supports selecting existing Address/Site/SiteLocation or entering a new nested address, site and arbitrary-depth location. Survey and installation forms submit the nested command in their own save request so the backend transaction remains atomic.

- [ ] **Step 3: Make equipment location read-only and traceable**

Equipment create/update no longer edits current location. Detail shows resolution status, current site/location tree and fallback text. Installation completion refreshes the equipment detail and location history.

- [ ] **Step 4: Add frontend tests after implementation**

```ts
assert.doesNotMatch(projectSource, /officeId|locationId/)
assert.match(projectSource, /sites/)
assert.match(projectSource, /locationResolutionStatus/)
assert.match(installationSource, /locationMaintenance/)
assert.doesNotMatch(equipmentSource, /v-model="formData\.location"/)
```

Run:

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
node --test src/views/pms/project/projects/index.spec.ts src/components/PmsLocationSelector/locationSelector.spec.ts
pnpm ts:check
pnpm build:local
```

- [ ] **Step 5: Commit the cross-domain UI workflow**

```powershell
git add yudao-ui/yudao-ui-admin-vue3/src/api/pms yudao-ui/yudao-ui-admin-vue3/src/views/pms yudao-ui/yudao-ui-admin-vue3/src/components/PmsLocationSelector
git commit -m "feat(project): 接入多站点与安装位置界面"
```

---

### Task 8: 完成 MySQL、模块边界和全量回归

**Files:**
- Modify only if a real defect is found: `tests/infrastructure/verify-pms-module-boundaries.ps1`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/location/LocationMySqlIntegrationTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationLocationMySqlIntegrationTest.java`
- Create: `output/location-v18/mysql-acceptance.md`
- Create: `output/location-v18/regression-summary.md`

**Interfaces:**
- Consumes: V64～V68, all backend modules and frontend build.
- Produces: empty-database migration evidence, upgrade migration evidence, real MySQL location invariants and complete regression results.

- [ ] **Step 1: Run an isolated empty-database migration**

```powershell
docker compose -p npdms-50eb-location-v18 down -v
docker compose -p npdms-50eb-location-v18 up -d mysql redis
docker compose -p npdms-50eb-location-v18 run --rm migrate
docker compose -p npdms-50eb-location-v18 run --rm migrate info
docker compose -p npdms-50eb-location-v18 run --rm migrate validate
```

Expected: V1～V68 succeed once, repeat migrate is a no-op, and validate passes. This isolated project name must not reuse `npdms-t8-mysql-1`.

- [ ] **Step 2: Run real MySQL integration tests**

```powershell
mvn -pl pms-module-project,pms-module-engineering,pms-module-asset -am -Dtest=LocationMySqlIntegrationTest,ProjectManualCreationMySqlIntegrationTest,InstallationLocationMySqlIntegrationTest test
```

Verify exact match/miss/disabled area mapping, same-address multi-site, arbitrary tree depth, multi-site project creation, fallback project, install/move/remove device position and transactional rollback.

- [ ] **Step 3: Run module-boundary and regression checks**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tests/infrastructure/verify-pms-module-boundaries.ps1
mvn -pl yudao-server -am test
Set-Location yudao-ui/yudao-ui-admin-vue3
node --test src/views/pms/project/projects/index.spec.ts src/views/pms/asset/location/location-contract.spec.ts src/components/PmsLocationSelector/locationSelector.spec.ts
pnpm ts:check
pnpm build:local
```

Expected: no module imports another module's Service/Mapper/Repository; all backend tests, Node tests, type-check and build pass.

- [ ] **Step 4: Record evidence and commit test additions**

Write exact commands, image/container identity, Flyway versions, test counts, skips and failures to the two output files. Do not claim a governance Gate, UAT or release GO.

```powershell
git add pms-module-asset/src/test pms-module-engineering/src/test output/location-v18
git commit -m "test(asset): 验证地点模型数据库闭环"
```

---

### Task 9: 使用内置浏览器完成业务验收

**Files:**
- Create: `output/location-v18/browser-acceptance.md`
- Create when captured: `output/location-v18/screenshots/location-admin.png`
- Create when captured: `output/location-v18/screenshots/project-multi-site.png`
- Create when captured: `output/location-v18/screenshots/installation-device-location.png`

**Interfaces:**
- Consumes: host backend on `58080`, host frontend on `18081`, isolated MySQL/Redis, V64～V68 and seeded users/permissions.
- Produces: real-browser evidence for organization, location, project, survey, installation and equipment flows.

- [ ] **Step 1: Start host applications against the isolated infrastructure**

```powershell
mvn -pl yudao-server -am package -DskipTests
java -jar yudao-server/target/yudao-server.jar
```

In a separate host terminal:

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm dev
```

Expected: backend health and frontend login are reachable; Docker runs only MySQL, Redis and Flyway.

- [ ] **Step 2: Verify organization and location administration with the built-in browser**

Use the Codex built-in browser to log in, then:

1. create a company and a department with `department_code`;
2. create one structured address;
3. create two sites at that address;
4. create a six-level location tree;
5. map district `area_code` to the office `department_code`;
6. refresh each page and verify versions and values persist.

- [ ] **Step 3: Verify project and assignment flows**

Create a project with both sites, confirm one primary site and `RESOLVED`, then assign a service manager using the mapped candidate and manual confirmation. Create a second project with only fallback location text and verify `UNRESOLVED`, “待维护” display, no automatic office resolution and successful authorized manual assignment.

- [ ] **Step 4: Verify survey, installation and device flows**

From an authorized project, create a new location during survey and confirm that equipment location is unchanged. Complete an installation at that location and verify the device current location updates. Move the device through a second installation and verify the current pointer changes while history remains. Trigger an invalid/stale location operation and verify no partial installation result appears after refresh.

- [ ] **Step 5: Record and commit browser evidence**

Record each scenario's URL, actor, input, visible result, refresh result, relevant network status and screenshot path. State explicitly that browser acceptance is Feature evidence only and not UAT, release or governance GO.

```powershell
git add output/location-v18/browser-acceptance.md output/location-v18/screenshots
git commit -m "test(asset): 完成地点业务浏览器验收"
```

---

## Final Completion Conditions

The plan is complete only when all conditions hold:

1. The new managed specification snapshot is locked and validates offline.
2. Company, department code and same-row user company-department scope exist and are exposed through stable APIs.
3. Address, Site and arbitrary-depth SiteLocation are physically owned by `pms-module-asset`; CUS only references them and no `pms-module-location` exists.
4. Sites contain no company/office binding; exact area-to-department mapping uses `area_code/area_level/department_code`.
5. Project creation supports multiple sites and explicit unresolved fallback.
6. V1 service-manager assignment is manually confirmed and no longer accepts raw numeric office/location IDs.
7. Survey and installation can maintain locations within project authorization.
8. Completed installation drives equipment current location transactionally; survey alone does not.
9. V64～V68 pass empty DB, repeat migrate and validate checks in the isolated Compose project.
10. Backend regression, module-boundary checks, frontend tests, type-check and build pass.
11. Built-in-browser scenarios pass after refresh and are recorded without overstating Gate status.
12. Every completed task has a scoped local commit and no automatic push occurred.
