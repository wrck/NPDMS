# F-PROJ-003 项目子树授权与统一数据范围 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本工程链延续当前会话内联执行，不启用子代理。

**Goal:** 按PRD V1.8实现角色与授权范围分离、显式项目/子树授权、统一项目数据范围、授权生命周期和响应式维护页面。

**Architecture:** 新建PLT模块物理拥有`plt_*`幂等、审计、Outbox和`AuthorizationGrant`事实，并通过非空`pms-module-platform-api`公开契约；PROJ通过`AuthorizationGrantApi`取得有效授权，在当前完整项目树版本上计算范围，并通过非空`pms-module-project-api`发布`ProjectScopeApi`。现有项目详情、列表、树、进度、拆分和闭环入口统一消费相同范围结果，角色本身只产生当前项目动作。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、Redis、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `specs/features/F-PROJ-003-project-subtree-authorization-and-unified-scope.md`

## Global Constraints

- 锁定规格提交为`69b2b48320a858f91d938ae6914146681c97fb0a`，本地manifest由NPDMS提交`5202a94`同步；实施不得修改受管规格快照。
- `specs/001-project-delivery-platform/`只作历史参考，不参与实施校验和门禁。
- V1.7代码、迁移、页面和测试只作复用审计证据；每项能力从未完成状态重新检查、改造和验证。
- 用户已禁用测试驱动顺序；不要求先制造失败测试，但每个任务提交前必须补齐并运行风险匹配的测试。
- 模块间不得依赖目标模块的`-biz`、Service、Mapper、Repository或业务表；只依赖公开API模块。
- 新增和改造查询遵循`docs/coding/database-query-interface.md`：除主键和稳定复合唯一键外，一个场景化Query对象；动态集合/锁查询进入Mapper XML；空权限集合返回空结果。
- 已执行Flyway V1～V76不修改；F-PROJ-003从V77开始只做前向、幂等迁移。
- Business API语义前缀为`/api/v1/pms`，当前Yudao管理端使用`/admin-api/pms/...`。
- UI优先复用Yudao页面组件，其次使用Element Plus；使用主题变量、响应式布局，避免页面级横向溢出和大量内联样式。
- 每个任务完成且验证通过后创建独立本地提交，不推送；当前阶段仍是Implementation，不生成Deployment、SIT、UAT或Release材料。

## 存量实现审计

| 资产 | 分类 | V1.8处置 |
|---|---|---|
| `ProjectTreeScopeService`按经理角色展开后代 | `REPLACE` | 删除角色自动穿透；当前成员只授予当前项目动作，后代仅由显式授权展开 |
| `ProjectMemberAssignmentMapper.selectActiveByUser(userId, at)` | `ADAPT` | 改为`ActiveProjectMemberQuery`单对象查询并保留空集合收缩语义 |
| PROJ内`service/platform`及`dal/*/platform` | `MOVE_TO_OWNER` | 迁移到PLT模块，PROJ通过公开`PlatformCommandExecutionApi/OperationAuditApi`调用 |
| `plt_idempotency_record/plt_operation_audit/plt_outbox_event` | `REUSE_AS_PLT_FACT` | 表不重建；DO、Mapper和事务服务归PLT物理持有 |
| `plt_authorization_grant` | `MISSING` | V77建立正式授权事实、当前标记唯一约束和有效区间索引 |
| 项目详情、树、进度、拆分、闭环守卫 | `REUSE_WITH_ADAPTATION` | 全部改为按动作消费同一ProjectScope；项目列表增加服务端可见ID过滤 |
| 旧单数`/pms/project`只读入口 | `RETIRED_COMPATIBILITY` | 不扩大旧入口能力；正式闭环只使用复数`/pms/projects`入口 |
| `project-master-detail/index.vue` | `REUSE_SHELL` | 增加独立授权面板，保持现有导轨、主题和四类视口布局 |
| F-PROJ-002授权测试 | `REVALIDATE` | 把“经理自动后代”断言改为显式授权后代，并补撤权、到期、移动和空范围测试 |

---

### Task 1: 建立PLT模块并归位平台事实所有权

**Files:**
- Create: `pms-module-platform/pms-module-platform-api/pom.xml`
- Create: `pms-module-platform/pom.xml`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/command/PlatformCommandExecutionApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/audit/OperationAuditApi.java`
- Move: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/platform/*` to `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/command/`
- Move: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/platform/*` to `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/command/`
- Move and adapt: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/platform/ProjectCommandExecutionService.java`
- Move and adapt: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/platform/ProjectOperationAuditService.java`
- Modify: `pom.xml`, `pms-module-project/pom.xml`, `yudao-server/pom.xml`
- Modify: PROJ services and tests importing the two moved services
- Move test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/platform/ProjectCommandExecutionServiceTest.java`

**Interfaces:**
- Consumes: 既有`plt_idempotency_record`、`plt_operation_audit`、`plt_outbox_event`表及F-PROJ-001/F-PROJ-002事务语义。
- Produces:

```java
public interface PlatformCommandExecutionApi {
    <T> ExecutionResult<T> execute(IdempotencyScope scope,
                                   String requestDigest,
                                   Class<T> responseType,
                                   CommandAction<T> action,
                                   SuccessFactsFactory<T> factsFactory);
}

public interface OperationAuditApi {
    void record(AuditCommand command);
}
```

- [x] **Step 1: 创建PLT API与实现模块并接入Maven reactor**

根`pom.xml`按API在实现前的顺序加入`pms-module-platform/pms-module-platform-api`和`pms-module-platform`；`yudao-server`装配实现模块；PROJ只依赖`pms-module-platform-api`。

- [x] **Step 2: 把平台DO、Mapper和事务实现物理迁入PLT**

保留现有表名、幂等作用域、请求摘要、完成结果、审计和Outbox语义；类名收敛为`PlatformCommandExecutionApiImpl`与`OperationAuditApiImpl`，实现类不暴露给PROJ。

- [x] **Step 3: 将PROJ调用替换为公开接口**

修改手工创建、服务经理指派、拆分应用、树移动、进度策略和闭环守卫调用；源码检查不得残留PROJ对`plt_*` DO/Mapper或PLT实现包的引用。

- [x] **Step 4: 运行所有受影响的单元与原子事务测试**

Run: `mvn.cmd -pl pms-module-platform,pms-module-project -am -Ppms-test-unit -DskipITs=true test`

Expected: 平台命令重放/冲突/处理中及F-PROJ-001/F-PROJ-002单元测试全部PASS。

- [x] **Step 5: 提交平台所有权归位**

```text
refactor(platform): 归位平台命令事实所有权
```

### Task 2: 实现PLT AuthorizationGrant物理模型与公开API

**Files:**
- Create: `sql/migrations/V77__fproj003_authorization_grant.sql`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/AuthorizationGrantApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantCreateCommand.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantRevokeCommand.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantPageQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantPageResult.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/authorization/dto/AuthorizationGrantDTO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/authorization/AuthorizationGrantDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/authorization/AuthorizationGrantMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/authorization/query/EffectiveAuthorizationGrantQuery.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/authorization/AuthorizationGrantService.java`
- Create: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/authorization/AuthorizationGrantServiceTest.java`
- Create: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/authorization/AuthorizationGrantMySqlTest.java`

**Interfaces:**
- Consumes: `PlatformCommandExecutionApi`和PLT本地Mapper；不接收项目路径、深度或树版本。
- Produces:

```java
public interface AuthorizationGrantApi {
    AuthorizationGrantDTO create(AuthorizationGrantCreateCommand command);
    AuthorizationGrantDTO revoke(AuthorizationGrantRevokeCommand command);
    AuthorizationGrantDTO get(Long grantId);
    List<AuthorizationGrantDTO> listEffective(AuthorizationGrantQuery query);
    AuthorizationGrantPageResult page(AuthorizationGrantPageQuery query);
}

public record AuthorizationGrantQuery(Long tenantId, String subjectTypeCode,
        Long subjectId, String resourceContextCode, String resourceTypeCode,
        Set<Long> resourceIds, String actionCode, LocalDateTime effectiveAt) {}
```

- [x] **Step 1: 以前向迁移建立`plt_authorization_grant`**

V77实现规格中的全部字段；`current_marker=1`占用当前授权键，撤权和确认到期置空；唯一键精确使用`tenant+subject+resource context/type/id+action+scope+current_marker`，有效区间满足`effective_to IS NULL OR effective_to > effective_from`。

- [x] **Step 2: 实现Query对象、Mapper与有效授权查询**

`listEffective`必须校验租户、状态、`effective_from <= effectiveAt`和`effective_to > effectiveAt`；`resourceIds`为空立即返回空集合。`page`按主体、动作、范围、状态和有效时点查询当前与历史，并以`granted_at DESC,id DESC`稳定分页；不得使用SQL注解、`${}`或`.last(...)`。

- [x] **Step 3: 实现创建、撤权、版本和幂等事务**

创建只接受主体类型`USER`、资源Context`PROJ`、资源类型`PROJECT`、动作`PROJECT_VIEW/PROJECT_MANAGE`和范围`CURRENT_PROJECT/PROJECT_AND_DESCENDANTS`；撤权以期望版本更新并记录撤销人、时间、原因，历史不删除；重复创建由当前标记唯一约束转换为明确冲突。

- [x] **Step 4: 验证授权生命周期与真实MySQL约束**

覆盖未生效、当前有效、到期、撤权、同键重放、同键不同摘要、并发重复授权和版本冲突。

Run: `mvn.cmd -pl pms-module-platform -am -DskipITs=false -DskipTests=false -Dtest=AuthorizationGrantServiceTest,AuthorizationGrantMySqlTest test`

Expected: 单元与MySQL测试PASS；数据库只存在一个当前有效事实，历史仍可查询。

- [x] **Step 5: 提交PLT授权事实**

```text
feat(platform): 实现项目授权事实与生命周期
```

### Task 3: 发布ProjectScopeApi并重写统一范围计算

**Files:**
- Create: `pms-module-project/pms-module-project-api/pom.xml`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/scope/ProjectScopeApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/scope/dto/ProjectScopeQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/scope/dto/ProjectScopeResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/scope/ProjectScopeApiImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ActiveProjectMemberQuery.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java`
- Modify: `pom.xml`, `pms-module-project/pom.xml`
- Modify test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeServiceTest.java`
- Modify test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeAuthorizationMySqlTest.java`

**Interfaces:**
- Consumes: `AuthorizationGrantApi.listEffective(...)`、当前成员区间和F-PROJ-002完整树版本。
- Produces:

```java
public interface ProjectScopeApi {
    ProjectScopeResult resolve(ProjectScopeQuery query);
}

public record ProjectScopeQuery(Long tenantId, Long subjectUserId,
        Long anchorProjectId, String actionCode, Long expectedTreeVersion) {}

public record ProjectScopeResult(Long rootProjectId, Long treeVersion,
        Set<Long> fullProjectIds, Set<Long> placeholderProjectIds) {}
```

- [x] **Step 1: 创建非空PROJ API模块和公开DTO**

公开契约只返回稳定ID、完整树版本与占位ID，不返回PROJ DO；项目实现模块依赖自身API，后续业务模块只能依赖`pms-module-project-api`。

- [x] **Step 2: 将成员查询改为场景化Query对象**

`selectActiveByUser(ActiveProjectMemberQuery query)`明确租户、用户和左闭右开有效时点；必填权限条件缺失时Service拒绝，空结果不扩大范围。

- [x] **Step 3: 重写范围合并算法**

成员关系只把自身`projectId`加入对应角色允许的当前项目动作；只有`PROJECT_AND_DESCENDANTS`授权调用`selectByAncestors`展开后代；`CURRENT_PROJECT`只加入锚点；`PROJECT_MANAGE`包含同范围查看；祖先仅形成结构占位，不返回同根摘要业务明细。

- [x] **Step 4: 验证移动、撤权、到期和空范围**

测试应断言：经理无显式授权时不能读后代；显式后代授权命中；撤权/到期立即收缩；项目移动后按同一授权锚点和新树版本重算；空成员和空授权返回空范围。

Run: `mvn.cmd -pl pms-module-project -am -DskipITs=false -Dtest=ProjectTreeScopeServiceTest,ProjectTreeAuthorizationMySqlTest test`

- [x] **Step 5: 提交统一项目范围**

```text
feat(project): 发布显式项目子树范围契约
```

### Task 4: 实现项目授权命令、查询与越界守卫

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/ProjectAuthorizationController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/vo/ProjectAuthorizationCreateReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/vo/ProjectAuthorizationPageReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/vo/ProjectAuthorizationRevokeReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/vo/ProjectAuthorizationRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectauthorization/ProjectAuthorizationApplicationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectauthorization/ProjectAuthorizationGuard.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`
- Create tests under: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectauthorization/`
- Create contract test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectauthorization/ProjectAuthorizationControllerContractTest.java`

**Interfaces:**
- Consumes: `ProjectScopeApi`用于授权人范围上界，`AuthorizationGrantApi`用于PLT事实，`PermissionApi`与当前服务经理成员关系用于功能/角色守卫。
- Produces: 规格5.1的创建、分页、详情、撤权端点；服务经理指派命令复用同一管理范围上界。

- [x] **Step 1: 实现授权人三重守卫**

必须同时命中`pms:project:authorization:manage`、当前有效`SERVICE_MANAGER_L1/L2`成员关系和目标项目`PROJECT_MANAGE`；授权对象同租户，动作/范围不得超过授权人；项目经理和普通成员拒绝。

- [x] **Step 2: 实现四类HTTP契约**

创建和撤权读取`Idempotency-Key`；撤权读取`If-Match`；分页页大小上限100并按`granted_at DESC,id DESC`稳定排序；越权详情按不存在处理；客户端不能提交路径、深度或项目ID集合。

- [x] **Step 3: 把服务经理指派接入相同范围上界**

保留公司、Department办事处和站点候选校验；增加授权人服务经理角色与`PROJECT_MANAGE`范围校验；被指派角色本身不产生后代授权。

- [x] **Step 4: 验证正向、越界和无副作用场景**

覆盖跨租户、平级项目、超范围动作、普通项目经理授权、无权限详情、撤权重放和错误版本；失败后无有效成员/授权和成功幂等事实。

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-unit -DskipITs=true test`

- [x] **Step 5: 提交授权业务入口**

```text
feat(project): 增加项目授权管理入口
```

### Task 5: 将当前PROJ入口统一到动作化ProjectScope

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/VisibleProjectPageQuery.java`
- Create: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMasterMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttree/ProjectTreeVersionMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManagerAssignmentApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterController.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeProjectionService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttree/ProjectTreeQueryService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/ProjectProgressQueryService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/ProjectProgressPolicyService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectprogress/ProjectProgressSnapshotService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitDraftService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectclosureguard/ProjectClosureGuardService.java`
- Modify and add focused tests for each service above

**Interfaces:**
- Consumes: `ProjectScopeApi.resolve(query)`，读入口使用`PROJECT_VIEW`，写入口使用`PROJECT_MANAGE`。
- Produces: 项目详情、分页、树、进度、拆分与闭环守卫对同一主体/动作/授权版本/树版本的一致结果。

- [x] **Step 1: 修正项目分页和详情的服务端范围过滤**

Controller从登录上下文传递主体；Service先解析可见ID，再构造`VisibleProjectPageQuery`；权限集合为空直接返回空页；Mapper不再接收长位置参数列表或Controller ReqVO。

- [x] **Step 2: 为树、进度和闭环读入口显式传入`PROJECT_VIEW`**

完整节点仅来自`fullProjectIds`；祖先占位继续通过`ProjectTreeViewSanitizer`只返回稳定ID；不再返回经理同根项目摘要。

- [x] **Step 3: 为拆分、进度策略、移动和闭环命令显式传入`PROJECT_MANAGE`**

任何空范围或陈旧树版本均拒绝；缓存不可用时回源，不能退化为租户全量。

- [x] **Step 4: 运行当前PROJ回归**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-unit -DskipITs=true test`

Expected: F-PROJ-001创建/指派与F-PROJ-002拆分、树、进度、移动、闭环守卫保持PASS，新权限负向用例PASS。

- [x] **Step 5: 提交入口统一过滤**

```text
fix(project): 统一项目入口数据范围
```

### Task 6: 补齐字典、菜单、示例授权和迁移验证

**Files:**
- Create: `sql/migrations/V78__fproj003_authorization_seed_and_menu.sql`
- Create: `sql/migrations/V79__fproj003_authorization_demo_seed.sql`
- Create: `scripts/tests/test_fproj003_v18_migration.py`
- Modify: `compose.yaml` only if current authoritative Flyway wiring cannot execute V77～V79 unchanged

**Interfaces:**
- Consumes: V77表结构、现有`system_dict_type/system_dict_data/system_menu`和F-PROJ-002高段示例项目树。
- Produces: `pms_project_authorization_action`、`pms_project_authorization_scope`、`pms:project:authorization:query/manage/revoke`及组合示例数据。

- [x] **Step 1: 写入幂等字典和菜单权限**

动作值固定`PROJECT_VIEW/PROJECT_MANAGE`，范围固定`CURRENT_PROJECT/PROJECT_AND_DESCENDANTS`；菜单挂在现有项目主档详情能力下，不创建第二套项目导航。

- [x] **Step 2: 写入高段组合示例授权**

覆盖精确当前项目、全部后代、未生效、已到期、已撤权、停用不参与和无匹配；引用现有真实用户/项目种子时使用可重复选择规则，不臆造CRM或外部授权值域。

- [x] **Step 3: 验证空库、V76升级、重复迁移和当前库**

Run: `docker compose -p npdms-50eb run --rm migrate`

Run: `C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest scripts.tests.test_fproj003_v18_migration -v`

Expected: 四种迁移路径PASS，校验表、索引、字典、菜单、组合样例和幂等性。

- [x] **Step 4: 提交迁移与初始化数据**

```text
feat(database): 初始化项目授权数据
```

### Task 7: 实现响应式项目授权维护界面

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/utils/dict.ts`

**Interfaces:**
- Consumes: 创建、分页、详情和撤权API；Yudao `ContentWrap`、权限指令、用户选择器、`dict-tag`和Element Plus表单/表格/分页/对话框。
- Produces: 项目详情导轨“项目授权”入口、授权列表、创建对话框、撤权二次确认及刷新后事实保持。

- [x] **Step 1: 增加严格TypeScript授权模型和API函数**

定义`ProjectAuthorizationAction/Scope/Status`联合类型；创建函数传`Idempotency-Key`，撤权函数同时传`Idempotency-Key`和`If-Match`；分页参数不携带租户或任意项目ID集合。

- [x] **Step 2: 实现授权列表和创建/撤权交互**

复用Yudao用户选择、权限控制和分页组件；撤权必须填写原因；按钮按`pms:project:authorization:manage/revoke`显示；接口失败保留表单内容并展示服务端错误。

- [x] **Step 3: 完成四类视口响应式样式**

桌面表格、窄桌面/平板可横向滚动表格区、手机卡片化；页面容器不横向溢出；颜色、间距、边框全部使用Element Plus主题变量，模板不堆叠`style`属性。

- [x] **Step 4: 运行组件、类型、样式和生产构建校验**

Run: `pnpm.cmd exec vitest run src/views/pms/project/project-master-detail/components/ProjectAuthorizationPanel.spec.ts`

Run: `pnpm.cmd ts:check && pnpm.cmd lint:eslint:check && pnpm.cmd lint:style:check && pnpm.cmd build:prod`

Expected: 组件用例、类型、目标文件ESLint/Stylelint/Prettier与生产构建PASS；
仓库历史样式基线问题单独登记，不把既有失败记录为本Task新增缺陷。

- [x] **Step 5: 提交授权维护页面**

```text
feat(ui): 增加响应式项目授权面板
```

> 执行记录（2026-08-24）：基础菜单刷新异常与主题初始化已先行修复并提交为
> `6722782`；授权维护页面提交为`c1c76d5`。组件测试`5/5`、TypeScript、目标
> ESLint/Stylelint/Prettier、生产构建通过；真实Edge完成创建、刷新ACTIVE、撤权、
> 刷新REVOKED及320/768/1024/1440四档视口闭环。详情页既有样式区仍保留仓库
> 历史Stylelint基线问题，新增授权面板本身无Stylelint错误，不阻断进入Task 8。

### Task 8: 完成真实闭环验证与Implementation Done证据

**Files:**
- Create: `docs/acceptance/F-PROJ-003-project-subtree-authorization.md`
- Modify: `tasks/features/F-PROJ-003.md`
- Modify after implementation evidence exists: `specs/features/F-PROJ-003-project-subtree-authorization-and-unified-scope.md` through the specification repository and managed sync only
- Modify after implementation evidence exists: `specs/features/README.md` through the specification repository and managed sync only

**Interfaces:**
- Consumes: Tasks 1～7全部提交、当前Compose MySQL/Redis、宿主机JDK 25后端和pnpm前端。
- Produces: AC-FPROJ003-001～009逐项证据与当前Feature Implementation Done结论；不宣称完整AUT-01/AUT-02或PM-04后续业务对象接入完成。

- [ ] **Step 1: 运行后端全量与架构边界校验**

Run: `mvn.cmd -Ppms-test-unit -DskipITs=true test`

Run: `mvn.cmd -Ppms-test-contract -DskipITs=true test`

Run: `mvn.cmd -Ppms-test-integration -DskipITs=false test`

Run: `rg -n "module\.pms\.(project|platform)\.(service|dal)" pms-module-project pms-module-platform pms-module-asset pms-module-commerce pms-module-engineering pms-module-cutover pms-module-service pms-module-outsourcing pms-module-analytics pms-module-integration`

Expected: 自动化全绿；跨模块源码只引用`api`包，不引用目标Service、Mapper、Repository、DO或业务表。

- [ ] **Step 2: 使用真实MySQL验证授权收缩和树移动**

依次验证当前项目、全部后代、平级拒绝、授权越界、撤权、到期、并发重复、空范围、Redis不可用回源和项目移动后重算；记录SQL事实ID、版本、树版本和返回错误码，不记录凭据。

- [ ] **Step 3: 优先使用内置浏览器完成真实页面闭环**

在桌面≥1200、窄桌面992～1199、平板768～991、手机≤767四档视口执行创建授权、切换范围、负向访问、撤权、刷新保持；检查页面级横向溢出、控制台和网络请求。内置浏览器不可用时才使用已获允许的外部浏览器并登记原因。

- [ ] **Step 4: 回写验收与任务状态**

验收文档逐项引用测试命令、浏览器步骤、数据库事实和提交；`tasks/features/F-PROJ-003.md`只在证据存在时勾选AC。规格仓库仅回写实施状态与NPDMS提交，不改变已批准业务语义或重开SDS门禁。

- [ ] **Step 5: 运行最终规格与工作树检查并提交闭环**

Run: `C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts/validate_specification_baseline.py`

Run: `git diff --check && git status --short`

Expected: 快照PASS；除明确登记的用户文件外无未提交工程变更。

```text
docs(feature): 完成 F-PROJ-003 实施闭环
```

## 计划自审结论

- 规格覆盖：BR-FPROJ003-001～006和AC-FPROJ003-001～009均映射到Tasks 1～8；AUT完整审批与后续未实施业务对象保持在Scope外。
- 文件边界：PLT物理拥有授权和平台事实，PROJ只编排项目语义；两个API模块均有确定调用方和非空契约。
- 查询规则：分页、有效授权、成员和可见项目查询均使用场景Query对象；动态集合与空权限行为已显式规定。
- 类型一致性：动作统一为`PROJECT_VIEW/PROJECT_MANAGE`，范围统一为`CURRENT_PROJECT/PROJECT_AND_DESCENDANTS`，ProjectScope输入输出在Task 3后保持不变。
- 验证边界：每项实现均有独立验证和提交；真实MySQL、真实浏览器、响应式和规格回写集中在最终闭环，不进入部署阶段。
