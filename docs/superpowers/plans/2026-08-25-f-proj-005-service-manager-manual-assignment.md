# F-PROJ-005 服务经理人工指派与责任分布 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本工程链在当前会话内联执行，不启用子代理。

**Goal:** 完成PM-08 V1的服务经理候选、即时人工指派/改派、树内责任分布及可靠站内信通知。

**Architecture:** SYSTEM通过公开API提供精确公司—部门有效候选和持久幂等站内信；PROJ拥有成员时态关系、项目状态与人工指派API；PLATFORM拥有Outbox领取/完成/重试。指派事务冻结不可变通知payload，异步处理器只消费该快照，不从当前可变状态重建消息。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `specs/features/F-PROJ-005-service-manager-manual-assignment.md`

## Global Constraints

- 锁定规格提交为`9c55a7b965cadd85e893bab92c2def5881490cb7`；受管快照只由同步工具维护，不直接编辑。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与本Feature实施校验。
- V1.7代码、页面和测试只是复用审计输入；必须逐项改造并重新验证，不能据既有实现判断完成。
- 用户已禁用测试驱动顺序；不先制造失败测试，每个Task结束前仍补齐风险匹配的自动化验证。
- 办事处即SYSTEM部门，稳定编码为`system_dept.code`；站点不绑定公司或办事处，区划映射只提供部门候选。
- 候选必须是同租户、项目公司、确认部门的当前启用用户和有效公司—部门范围；提交时重新校验。
- V1只支持服务端事务时间即时生效，不接受客户端`effectiveFrom`，不建设预约生效调度。
- `ASSIGNED`仅在当前节点同时存在有效PRIMARY服务经理和有效项目经理时成立；COLLABORATOR不参与，不修改阶段和生命周期。
- 新查询遵循`docs/coding/database-query-interface.md`：分页与范围查询只接收场景Query对象；锁SQL进入Mapper XML；空范围返回空结果。
- PROJ不得访问SYSTEM或PLATFORM业务表；模块间只调用公开API。PLATFORM独占`plt_outbox_event`，SYSTEM独占组织和站内信表。
- 只新增前向Flyway V83及后续种子版本，不修改V1～V82。
- UI优先复用Yudao组件，其次Element Plus；支持320/768/1024/1440视口、主题变量和无页面级横向溢出。
- 每个Task验证后独立本地提交，不推送；当前不生成Deployment、SIT、UAT或Release材料。

## 存量实现审计

| 资产 | 处置 |
|---|---|
| `ProjectManagerAssignmentApplicationService` | 保留授权/幂等骨架；补候选重验、原因、类型、状态重算和完整事件payload |
| `ProjectManualCreationServiceImpl.assignServiceManager` | 改为服务端即时生效；按责任范围关闭主责区间，不无条件覆盖协同 |
| `proj_project_member_assignment` | 复用时间表；新增`department_id/assignment_type/site_id/change_reason`，不新建历史表 |
| `OrganizationScopeApi` | 加法增加精确候选分页，不让PROJ直查SYSTEM表 |
| `NotifyMessageSendApi` | 请求增加可空`deliveryKey`；PM-08必填并由SYSTEM物理去重 |
| `plt_outbox_event` | 复用重试事实；由PLATFORM公开领取/完成/重试API，不跨模块使用Mapper |
| 既有列表“指派一级服务经理”弹窗 | 改造成主责/协同、L1/L2、站点/部门候选和原因表单 |

---

### Task 1: 建立SYSTEM与成员物理基础

**Files:**
- Create: `sql/migrations/V83__fproj005_service_manager_assignment.sql`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectMemberAssignmentDO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/notify/NotifyMessageDO.java`
- Create: `scripts/tests/test_fproj005_v18_migration.py`
- Create: `tasks/features/F-PROJ-005.md`

**Interfaces:**
- Consumes: F-PROJ-005物理契约。
- Produces: 成员列`departmentId/assignmentType/siteId/changeReason`；站内信列`deliveryKey`及`uk(tenant_id,user_type,delivery_key)`。

- [x] **Step 1: 编写V83前向迁移**

使用幂等DDL新增四个成员列和可空`system_notify_message.delivery_key varchar(128)`；建立通知唯一键及成员当前责任查询索引。不得创建成员历史、通知历史或重试表。

- [x] **Step 2: 对齐DO字段**

`ProjectMemberAssignmentDO`增加`Long departmentId/String assignmentType/Long siteId/String changeReason`；`NotifyMessageDO`增加`String deliveryKey`。

- [x] **Step 3: 增加迁移契约验证**

断言V83包含全部列、通知唯一键和禁止建表清单，并断言V1～V82未修改。

- [x] **Step 4: 验证并提交**

Run: `python -m unittest scripts.tests.test_fproj005_v18_migration`

Run: `mvn.cmd -pl pms-module-project,yudao-module-system -am -DskipTests compile`

Expected: 全部PASS。提交：`feat(project): 建立服务经理指派物理基础`

---

### Task 2: 提供SYSTEM候选与幂等站内信API

**Files:**
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/dto/OrganizationUserCandidatePageReqDTO.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/dto/OrganizationUserCandidateRespDTO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApi.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApiImpl.java`
- Create: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/permission/query/OrganizationUserCandidatePageQuery.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/permission/UserCompanyDepartmentScopeMapper.java`
- Create: `yudao-module-system/src/main/resources/mapper/permission/UserCompanyDepartmentScopeMapper.xml`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/notify/dto/NotifySendSingleToUserReqDTO.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notify/NotifyMessageService.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notify/NotifyMessageServiceImpl.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notify/NotifySendService.java`
- Modify: `yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/notify/NotifyMessageSendApiImpl.java`
- Test: `yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/api/permission/OrganizationScopeApiImplTest.java`
- Test: `yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/api/notify/NotifyMessageSendApiImplTest.java`

**Interfaces:**
- Produces:

```java
PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(
        OrganizationUserCandidatePageReqDTO request);

public class NotifySendSingleToUserReqDTO {
    Long userId;
    String templateCode;
    Map<String, Object> templateParams;
    String deliveryKey; // 既有调用可空，F-PROJ-005必填
}
```

- [x] **Step 1: 实现精确组织候选分页**

Service校验`companyId/departmentId/departmentCode/pageNo/pageSize`和ID/编码一致性；Mapper XML按当前时点联接启用用户、部门和有效范围，固定`user_id`排序兜底。合法空范围返回空页，禁止父部门或跨办事处回退。

- [x] **Step 2: 实现站内信持久幂等**

有`deliveryKey`时在SYSTEM事务内插入；唯一键冲突后读取原消息，收件人/模板/参数摘要一致则返回原ID，否则抛投递键冲突。空键保持上游原行为。

- [x] **Step 3: 补API自动化验证**

覆盖精确命中、启用/停用、过期范围、部门ID/编码冲突、空页、页大小上限；覆盖站内信首次写、崩溃窗重放、异载荷冲突和空键兼容。

- [x] **Step 4: 验证并提交**

Run: `mvn.cmd -pl yudao-module-system -am -DskipTests -DskipITs=false -Dtest=OrganizationScopeApiImplTest,NotifyMessageSendApiImplTest test`

Expected: 定向测试及编译PASS。提交：`feat(system): 提供服务经理候选与幂等通知`

---

### Task 3: 改造PROJ指派事务与状态语义

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectAssignManagerReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectAssignManagerRespVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/AssignServiceManagerCommand.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/AssignServiceManagerResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ProjectServiceManagerAssignedPayload.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java`
- Create: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml`
- Test: existing projectmanual service/controller tests

**Interfaces:**
- Request: `managerId/levelCode/assignmentType/siteId/departmentId/departmentCode/changeReason`，不接收`effectiveFrom`。
- Event payload: `assignmentId/projectId/recipientUserId/templateCode/templateParamsSnapshot/assignmentType/levelCode/effectiveFrom`。

- [ ] **Step 1: 收敛请求与提交重验**

服务端重新校验租户、项目公司、部门ID/编码、用户启用、有效组织范围、节点/站点、功能权限和MANAGE范围；L2必须有当前项目站点，L1允许无站点。

- [ ] **Step 2: 实现即时主责/协同时间关系**

以一次服务端事务时间为`effectiveFrom`。Project版本CAS成功后，XML锁查询按项目、角色、assignmentType、site范围读取重叠关系；PRIMARY改派关闭原区间并新增，COLLABORATOR不关闭其他协同。

- [ ] **Step 3: 重算assignment_status**

当前节点存在有效PRIMARY服务经理且有效PROJECT_MANAGER时写`ASSIGNED`，否则写`UNASSIGNED`；不改`current_stage/lifecycle_status`。

- [ ] **Step 4: 冻结审计与事件payload**

审计保存前后主责、范围、原因和操作者。Outbox payload使用新record，不再序列化不含收件人的`AssignServiceManagerResult`；模板参数在事务内冻结，禁止后续重建。

- [ ] **Step 5: 验证并提交**

Run: `mvn.cmd -pl pms-module-project -am -DskipITs=false -Dtest=ProjectManualCreationServiceImplTest,ProjectManagerAssignmentApplicationServiceTest,ProjectMasterControllerContractTest test`

Expected: 状态组合、并发版本、主责/协同、即时生效、重验及payload测试PASS。提交：`feat(project): 完善服务经理指派事务`

---

### Task 4: 增加候选与责任分布查询API

**Files:**
- Create candidate/responsibility ReqVO、RespVO under `pms-module-project/.../controller/admin/projects/vo/`
- Modify: `pms-module-project/.../controller/admin/projects/ProjectMasterController.java`
- Create: `pms-module-project/.../service/projectmanual/ProjectServiceManagerQueryService.java`
- Create: `pms-module-project/.../dal/mysql/projectmanual/query/ServiceManagerResponsibilityPageQuery.java`
- Extend: `ProjectMemberAssignmentMapper.java` and XML
- Modify frontend API: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Test: controller contract, query service and real-MySQL query tests

**Interfaces:**

```java
PageResult<ServiceManagerCandidateRespVO> getCandidates(
        Long projectId, ServiceManagerCandidatePageReqVO request, Actor actor);
PageResult<ServiceManagerResponsibilityRespVO> getResponsibilities(
        Long rootProjectId, ServiceManagerResponsibilityPageReqVO request, Actor actor);
```

- [ ] **Step 1: 实现候选查询编排**

PROJ先校验项目/站点/部门及MANAGE范围，再调用SYSTEM公开API；响应只暴露规格字段，合法无候选返回空页。

- [ ] **Step 2: 实现责任分布分页**

按ProjectTreeScope解析可见节点；空集合直接空页。Mapper XML按当前时点查询实际节点的主责/协同、站点/部门和节点状态，固定树序与ID排序。

- [ ] **Step 3: 验证并提交**

覆盖空范围、跨租户、任意树深度、主责/协同和稳定分页。提交：`feat(project): 增加服务经理责任查询`

---

### Task 5: 完成Outbox站内信投递闭环

**Files:**
- Create API/DTO under `pms-module-platform/pms-module-platform-api/.../outbox/`
- Create PLATFORM service/query/mapper XML under `pms-module-platform/.../outbox/`
- Create: `pms-module-project/.../service/projectmanual/ProjectServiceManagerNotificationJob.java`
- Add notification template/menu seed in a forward migration if V83未包含模板
- Test: PLATFORM领取/CAS、PROJECT处理器、SYSTEM崩溃窗重放

**Interfaces:**

```java
List<PlatformOutboxMessageDTO> claimDue(PlatformOutboxClaimQuery query);
void markDelivered(String eventId, int expectedRetryCount);
void scheduleRetry(String eventId, int expectedRetryCount, LocalDateTime nextRetryTime);
```

- [ ] **Step 1: 由PLATFORM公开Outbox投递API**

锁查询只在PLATFORM Mapper XML；领取只限`ProjectServiceManagerAssigned`和到期PENDING事件，状态/CAS防重复完成。

- [ ] **Step 2: 实现PROJECT通知处理器**

反序列化8字段payload，直接构造`NotifyMessageSendApi`请求，`deliveryKey=eventId`。成功标记投递；异常按有界退避写`retryCount/nextRetryTime`，不回滚指派。

- [ ] **Step 3: 验证并提交**

覆盖首次发送、重复领取、消息已创建但Outbox未完成、改派后旧事件重试内容不变、失败重试。提交：`feat(project): 打通服务经理通知投递`

---

### Task 6: 改造响应式界面并完成Feature验收

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectServiceManagerPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Add focused frontend specs beside components
- Modify: `tasks/features/F-PROJ-005.md`

**Interfaces:** Consumes Task 3/4 APIs；不在浏览器缓存候选或权限真值。

- [ ] **Step 1: 改造指派表单**

展示订单办事处、站点、映射建议、精确候选、PRIMARY/COLLABORATOR、L1/L2和必填原因；移除客户端生效时间。提交前刷新候选并保留服务端最终重验。

- [ ] **Step 2: 增加责任分布面板**

在项目详情复用Yudao表格/抽屉/权限组件，按节点分页显示主责、协同、站点/部门和assignmentStatus。

- [ ] **Step 3: 执行自动化与真实MySQL**

Run: PROJ、SYSTEM、PLATFORM定向测试和三个模块完整回归；执行V83真实MySQL迁移、并发同版本唯一成功、时间区间、状态组合与Outbox重试校验。

- [ ] **Step 4: 使用真实浏览器验收**

优先内置浏览器；必要时使用已授权外部Chromium。验证候选、主责/协同、改派、责任分布、刷新持久化、越权/跨租户及320/768/1024/1440视口；检查控制台和失败请求。

- [ ] **Step 5: 独立Implementation Done复审并提交**

把自动化、MySQL、浏览器、提交和遗留边界写入Task记录，按固定结构送裁决官。GO后回写Feature/Task状态并分别提交NPDMS与规格仓库；不生成Deployment/SIT/UAT/Release材料。

---

## Self-Review

- Spec覆盖：候选、组织/地点、主责/协同、即时区间、状态、权限、API、物理字段、Outbox payload、持久通知幂等、责任分布和响应式UI分别落入Task 1～6。
- 禁止能力：计划不含V2自动指派、PM-11指派、预约生效、新历史/通知表、固定层级、自动后代成员或Deployment材料。
- 类型一致性：`departmentId/departmentCode`、`assignmentType`、8字段事件payload及`deliveryKey=eventId`在SYSTEM、PROJ、PLATFORM和UI间一致。
- 占位扫描：无TBD/TODO或未定义实施步骤；具体错误码沿用各模块现有错误码体系并在对应Task测试中固定。
