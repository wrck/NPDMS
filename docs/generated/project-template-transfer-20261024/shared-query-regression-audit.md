# 迁移共享查询与自动完成回归复核（2026-10-24）

## 对比范围

- 原分支：`E:/AICoding/Projects/NPDMS`，`codex/domain-migration`，提交 `4d149f34faec4b66e93373794693d6bbd738b716`。
- 当前工作树：`M:/AICoding/CodexData/worktrees/853c/NPDMS`，HEAD `c674a8a87`，同时核对工作区已存在的修复；没有覆盖并行改动。
- 先筛选原分支已存在且迁移后变化的 Mapper Java/XML 与共享 SQL，再追踪新增过滤条件的消费者。新增迁移专用查询用于解释来源及历史边界，不将此复核宣称为全部迁移器业务验收。

## 发现与处理

发现一处此前最终用户自动完成修复的遗漏消费者：`ProjectExecutionTimeProjection.memberTime` 仍读取 `ProjectAssignmentCompletionMapper.members`。该查询在迁移整改中增加 `authorization_confirmed=1`，因此已完成任务冻结快照引用的外部最终用户会被排除，导致加入生效时间显示为空，使用该时间的阶段展示也可能为空。

本次让最终用户时间投影复用已存在的 `endUserMembers` 查询；人员指派时间仍使用 `members`。保留冻结 assignmentId 匹配、租户/项目/删除边界和时间不得晚于该轮完成时间的检查。不按当前成员替代历史成员，不更新 submittedAt、endedAt、状态或完成快照。

此前 `ProjectEndUserCompletionService` 已改用专用查询，其真实浏览器自动完成证据见 [运行验收](end-user-completion-runtime-acceptance.md)。本次新增修复与此前已经部署的修复分开记录。

## 其他共享查询结论

| 查询组 | 调用方与边界 | 本次结论 |
| --- | --- | --- |
| ProjectAssignmentCompletionMapper.members | ProjectAssignmentCompletionProvider；ProjectExecutionTimeProjection | 指派完成要求有效、已确认管理角色，保留过滤；最终用户时间消费者已修复 |
| ProjectMemberAssignmentMapper 当前成员、参与者、责任与指派状态 | ProjectAuthorizationGuard、ProjectTreeScopeService、任务动作、阶段推进、ProjectParticipantFactApiImpl、ProjectBusinessContextApiImpl、人员维护与批量改派 | 授权及有效指派继续排除未确认角色；当前最终用户列表已存在角色例外，历史列表独立；不放宽权限查询 |
| ClosureProjectMapper.selectPrimaryServiceManagersForUpdate | NormalClosureAccess | 用于结项操作主体资格，已确认条件必要 |
| DeliveryScopeMapper.confirmedCurrent 与 Java 查询 | AssignedDeliveryScopeQueryService、DeliveryScopeAcceptanceLockApiImpl、范围分配/拆分/兼容及 LineFact 服务 | 正式分配事实和锁重验保留 allocation_confirmed；分页历史查询未被共用片段过滤，未确认迁移来源不冒充正式分配 |
| AuthorityScopeImpactMapper | CommerceAuthorityIngestService 的已分配范围冲突处理 | 仅影响正式分配，不能把未确认来源历史当成当前分配 |
| ContractMapper / SalesOrderMapper.rootReadScope | ContractAccessService 列表、详情、计数、读取分页 | 提取共用条件并补空集合返回空；不用于自动完成，未发现新增误过滤 |
| ProjectCommerceMapper / ProjectContractRelationMapper | 项目商务聚合、合同关系查询 | 核对直接关系、正式分配、迁移执行单补充链和历史入口；工作区已有执行链修复保留，未重复改写 |
| SalesOrderAuthorityMapper / OrderLineAuthorityMapper / ErpOrderSyncMapper | 权威同步写入及查重 | ERP 对已映射 DPPMS 来源的精确键兼容与空集合保护；未发现把历史来源过滤器用于任务完成 |
| CrmExecutionOrderMapper | ProjectCommerceSourceServiceImpl 的创建来源锁重验 | 空集合返回空、公司/来源身份重验保留 |
| ContractDeviceReadScopeSql | 设备列表合同组织授权 | HEAD 与原分支存在权限差异，但当前工作区已有修复，当前文件与原分支相同；未覆盖该修复 |
| 资产与工程既有 Mapper | 设备锁、发货来源/展示、安装历史、附件 Owner 锁、需求草稿撤销 | 差异主要为字段投影、专用历史/锁入口；未向既有完成查询新增授权确认过滤 |
| DeliveryMaterialMapper | 履约读取与默认交付件入口 | 原履约查询补 version；新增默认交付件筛选未替换原履约路径，历史与被引用附件保护仍在 |
| SyncBinding/SyncTask/SyncRun 与平台迁移 Mapper | 来源身份、迁移批次、重放及列表 | 与项目自动完成独立；保留来源、租户和空集合约束，不改写来源证据 |
| AdminUserMapper / UserCompanyDepartmentScopeMapper | AdminUserServiceImpl 登录、唯一性检查及组织候选 | 手机/邮箱改多候选后由 Service 显式拒绝歧义；组织候选补工号，无最终用户完成消费者 |

以上是源码与调用链复核结论，不代表所有模块均重新做过浏览器验收。

## 用户追加反馈：编辑最终用户补充资料报错

浏览器项目84572的“编辑项目成员”对话框保留了用户填写的备注和保存失败提示，未代用户重试提交。运行日志明确记录 `OrdinaryProjectMemberService.close → externalMutation`，SQL把 `authorization_confirmed` 一并更新，触发 `ck_proj_member_confirmed_identity_audit`。

根因为 `new ProjectMemberAssignmentDO()` 默认授权值为true，而关闭区间用部分更新对象，不应改变该值。外部最终用户没有系统账号，数据库约束正确阻止了错误的授权确认。本次关闭区间的更新对象显式置空该字段，由既有MyBatis非空更新策略保留数据库原值；编辑继续关闭旧区间并追加新区间，移出继续只关闭旧区间。没有删除约束、授予外部用户权限或覆盖旧资料。

新增真实MySQL测试先确认默认更新对象确实被约束拒绝，再调用实际关闭区间方法，验证未确认外部用户和已确认内部用户均保留原授权、身份、姓名及创建证据。服务层测试覆盖编辑和移出的更新载荷。

扩大定向测试共51项，50项通过，1项既有断言失败：`ProjectMemberHistoryMySqlTest.existingIdentityUniquenessRemainsInForce` 在唯一性拒绝验证通过后仍要求表列数为34，当前指定测试库为37列。本次没有改表或修改该固定列数断言；新增真实关闭区间回归通过。其余结果：OrdinaryProjectMemberServiceTest 25/25、ProjectEndUserCompletionMySqlTest 3/3、ProjectExecutionTimeProjectionTest 8/8、ProjectAssignmentCompletionProviderTest 4/4，ProjectMemberHistoryMySqlTest 10/11。证据：`.run/shared-query-audit-20261024/member-edit-tests.log`。该轮Maven整体失败，不能表述成全套通过。

## 验证边界

定向验证使用 `scripts/worktree-toolchain.ps1` 和同一构建锁。MySQL 用指定 `24306/npdms_domain_test` 的连接级临时表，不修改共享业务表。`ProjectExecutionTimeProjectionTest` 8项、`ProjectEndUserCompletionMySqlTest` 3项、`ProjectAssignmentCompletionProviderTest` 4项，共15项通过，失败/错误/跳过均为0，Maven BUILD SUCCESS（2026-10-24 12:41）。日志：`.run/shared-query-audit-20261024/backend-tests.log`。

新增真实SQL用例覆盖：未确认外部最终用户区间关闭、已有替代成员后，冻结任务仍取原成员时间；管理角色查询仍无未确认成员；submittedAt、endedAt和DONE状态保持原值。既有用例同时覆盖跨租户/项目、删除、过期、未来、停用及系统账号不能形成当前最终用户完成事实，以及冻结成员缺失时不以替代成员补造时间。

没有提交、部署或重启运行服务；本次新增时间展示修复尚未做运行包加载后的真实浏览器验收。

## 待运行复验的冻结包

2026-10-24 12:50完成标准后端编译打包，构建前后源码指纹一致，22项既有修复保护通过。标准59191冻结仍因缺失历史 `backend-domain-test.pid` 未完成；既有独立59286封装按其原流程完成冻结，不能视为59191发布成功。清单 `.run/direct-test/latest-manifest.json`，应用SHA256 `f6844f4a337de46120f8ed1b0d47c02f01a3d15c86fb8b14005656fa365ec901`，启动辅助包已准备。包含关闭成员区间授权值保留及最终用户时间投影两项修复。运行PID43968未重启，重启59286及真实浏览器复验待明确批准。
