# 8项初始化失败与9项跳过的配置复验

## 2026-10-28 P04五表临时夹具复验完成，8项全部通过

用户明确授权仅在既有隔离库 `127.0.0.1:24306/npdms_reverify_20261014` 创建这5张连接级临时表并执行必要夹具写入。沿用受保护账号资料，凭据仅在子进程环境中使用；未访问178正式测试库执行P04，未提交BASE_P04同步Run，未操作59191或启停服务。

固定JDK25/Maven、工作树独立依赖库和同一互斥锁下，仅选择 `P04AccountBaseSyncTest`：**8项通过，0失败、0错误、0跳过**，源码前后保持。覆盖预览零写入及不生成凭据、首次批量插入与管理员保护、整秒审计及重放／人工后改拒绝、后续范围缺失整事务回滚、历史任职及未解析人员引用、已批准部门别名、旧元数据索引写入拒绝／预览允许、人工账号保护与租户配额。全部原测试方法、种子和业务／失败断言保持，未降低规则。

修改前后结果一致性证据：P04生产Java、来源XML和目标XML与拆分前HEAD相同；与原10月24日冻结运行包相比，当前编译出的P04类及两份XML共31个条目字节完全相同。原8项断言本轮全部通过，包含原首次写入计数4、已存在预览未变计数6、重放新增0及原异常／回滚预期。因此本次夹具拆分未改变被测P04同步实现及原预期行为。没有执行旧36表通用夹具，也没有把上述证据描述成正式BASE_P04任务前后各跑一轮；用户只授权5表临时夹具，既有真实任务不重跑。

隔离库5张同名持久表的全行摘要、行数及DDL前后完全一致，原／新同步运行表计数保持。所有测试数据只写各连接的临时表，测试后连接关闭；临时表不构成正式表结构变更。未修改生产代码、正式业务数据、其他任务或其回执，P18缺项及P22/P33既定边界保持。

证据 `D:/CodexTaskArtifacts/project-detail-followup/p04-five-table-verification-20261028/`：`proof.json`（逐项PASS）、`before-after-equivalence.json`（31条目及原断言对比）、`before.json`／`after.json`（持久表保护）、`test-run.json`（锁／工具链／源码一致性）、`maven.log`、`run.py`／`run.ps1`。本项从当前未完成事项移除；下文“未执行DDL／尚未复验”为之前授权阶段的历史，保留不改写。

## 2026-10-28 P04最小夹具已拆分，未执行DDL

按用户明确批准的五表边界，新增测试专用 `P04AccountMySqlSupport` 和 `basesync/p04-account-mysql.sql`，P04测试不再继承跨任务 `RemainingTaskMySqlSupport`。只加载 `P04TargetMapper.xml`，保留原单连接、真实MyBatis／Spring事务、来源会话及绑定替身行为；移除无关任务Mapper、来源表、读取副本及项目引用替身。原通用夹具及其他任务测试保持原样。

专用SQL仅含 `system_users`、`system_role`、`system_user_role`、`system_user_company_department_scope`、`system_tenant` 五张连接级临时表；逐条与原 `account-foundation-mysql.sql` 的对应定义相同。P04八个测试方法、种子、业务及失败断言逐行保持，只修改父类并在具体测试类直接标注原 `BASE_PRODUCT_TEMP_MYSQL_TEST=true` 启用条件，避免依赖不继承的父类注解。

固定JDK25/Maven、工作树独立依赖库和互斥锁下，仅选择 `P04AccountBaseSyncTest` 编译及检查关闭状态。环境显式设为false，清除数据库连接参数及驱动路径。构建成功，源码前后一致；JUnit发现8项，全部由测试类条件跳过，0失败／0错误。这里验证的是未启用时不进入BeforeEach，不是8项业务测试通过。未连接数据库、未执行临时DDL／夹具DML，也未运行其他任务测试或同步。

证据：`D:/CodexTaskArtifacts/project-detail-followup/p04-minimal-fixture-20261028/` 下 `static-check.json`（五表定义及测试正文保持）、`disabled-check.json`（8项及具体跳过原因）、`test-run.json`、`maven.log`、`check-disabled.ps1`。自审确认失败回滚、凭据保护、历史指纹、租户配额及重放断言均保留；未修改生产代码、服务或59191。当前仅“最小夹具拆分／未启用保护”完成；获得临时DDL执行授权前，不运行五表夹具，真实8项复验仍未完成。下文36表及未拆分描述保留原历史时点。

- 时间：2026-10-23T19:04:58.116956+08:00
- 工作树：`C:\Users\user\Documents\Codex\2026-10-06\task-3\project-detail-followup`
- 分支／代码提交：`fix/dppms-migration-consolidation-20261012` / `b143cfe890dda1ef51f21d34abb75a5b5fd43ee9`
- 本轮范围：复用已有受保护配置，完成无DDL只读测试；不修改业务代码、测试源码、原任务或59191。

## 结论

P22原9项跳过均已实际重跑通过，0失败、0错误、0跳过；原因为上轮测试启动器未加载既有连接参数。现有远端配置及JDBC驱动可用，不是正式数据或表结构缺失。

P04原8项报错均发生在继承的BeforeEach中：`RemainingTaskMySqlSupport.java:32`对空的`BASE_PRODUCT_MYSQL_DRIVER_JAR`执行`Path.of`。业务测试方法尚未进入，未尝试建立JDBC连接。这不能认定为P04同步实现失败。既有本地验证库受保护账号已只读连接成功，服务器8.4.10，库名和只读状态复核一致。

另有测试夹具门控缺陷：条件只标注在抽象父类，实际JUnit Jupiter API 6.0.3的`EnabledIfEnvironmentVariable`无`@Inherited`；原8项没有被该父类条件隔离，缺参数时进入初始化并报错。此为测试入口／夹具配置问题，不是生产任务业务校验或新增同步前置。

P04完整8项依赖同连接临时表：32条固定建表＋去重后4条读取副本建表，每项36条，8项共288条临时DDL；另有仅向这些临时表执行的夹具写入与回滚验证。现有测试没有无DDL且保持全部真实写入、历史保护、回放及回滚断言的替代路径。本次明确禁止DDL，因此这些8项尚未复验，不列为通过；已单独询问仅此临时夹具的例外，未获答复前不执行。

## 本轮P22逐项结果

| 测试 | 结果 |
|---|---|
| `ownershipAndCompletedNonPreviewRunAreRequired` | PASS |
| `postfillKeepsCurrentRunBoundaryAndExactOrderKeys` | PASS |
| `sameOrderNumberWithDifferentParentTypeCannotMatch` | PASS |
| `catalogueLookupKeepsExactProductText` | PASS |
| `ambiguousOwnedLinesAreExcluded` | PASS |
| `scopeIdentityCatalogueRetainsDuplicatesAndDeletedHistoryInTenant` | PASS |
| `scopePrimaryKeyReadsKeepTenantAndEmptySetBoundaries` | PASS |
| `emptyCatalogueReturnsNoCandidates` | PASS |
| `numericP14TypesAndStringLineNumbersMatchFormalVarcharTypes` | PASS |

## P04逐项验证边界

下列8项原初始化错误已定位；本轮仅核清前置、未执行临时表夹具。

| 测试 | 本轮结果 |
|---|---|
| `previewPlansNewAccountsMembershipAndScopesWithoutCredentialsOrAnyFormalWrite` | 未运行，等待临时DDL例外 |
| `nativeBatchInsertsUseRealPlannedParentIdsAndPreserveOriginalAdminThenReplay` | 未运行，等待临时DDL例外 |
| `generatedMembershipAuditMatchesWholeSecondColumnsAndRealReplayStillDetectsChanges` | 未运行，等待临时DDL例外 |
| `laterMissingScopeDepartmentRollsBackAccountAndMembershipWrites` | 未运行，等待临时DDL例外 |
| `historicalEmploymentUsesAlreadyApprovedJsonAndKeepsUnresolvedPersonnelReferences` | 未运行，等待临时DDL例外 |
| `fndPrimaryDepartmentUsesApprovedDirectoryAlias` | 未运行，等待临时DDL例外 |
| `oldMetadataIndexBlocksWritesButNotPreview` | 未运行，等待临时DDL例外 |
| `existingUnboundAccountIsProtectedAndTenantQuotaIsEnforced` | 未运行，等待临时DDL例外 |

## 配置与保护边界

- P22：复用`.run/base-sync-remote-test/target.encrypted.json`，目标`172.17.90.178:13306/npdms`，MySQL8.4.11；密码仅进程内解密传入子进程，不写入配置模板、日志、报告或命令参数。
- P04：复用`D:/CodexTaskArtifacts/project-detail-followup/preview-recheck-20261014/database-account.encrypted.json`，只读验证`127.0.0.1:24306/npdms_reverify_20261014`；测试硬编码此独立验证库，没有切换至npdms_domain_test。
- P22连接设置readOnly=true、autoCommit=false，测试只执行CTE SELECT，后处理仅提取正式UPDATE的读关系，不执行其SET或DML。未增加字符集转换。
- Maven沿用当前工作树固定JDK25／Maven、独立依赖仓库及`.run/build/worktree.lock`。只选择P22OrderSourceKeySqlTest；没有运行P04或其他写入测试。
- 后端源码树哈希在测试前后保持；本轮无DDL、持久化DML、Flyway、迁移、真实同步或任何服务启停。不以P22通过替代P04完整验证。

## 证据

- 私有完整证据：`D:\CodexTaskArtifacts\project-detail-followup\test-17-initialization-recheck-20261023`。
- `proof.json`：9项新鲜JUnit结果；`test-run.json`：构建入口及源码一致性；`connection-preflight.json`：远端只读连接。
- `p04-readonly-preflight.json`：既有本地验证库配置可用；`p04-prior-errors-and-condition.json`：旧8项错误帧与实际依赖。
- `p04-fixture-audit.json`及`p04-connection-local-fixture.review.sql`：现有36条连接级临时DDL的具体范围，尚未执行。

## 2026-10-23 用户质询后的范围更正

36张是现有通用夹具的默认建表数，不是P04这8项测试必须访问的表数。此前直接把完整通用夹具作为本次复验前置，范围过大。静态核清后，这8项测试合计只需要5张：`system_users`、`system_role`、`system_user_role`、`system_user_company_department_scope`及仅配额分支使用的`system_tenant`。其余27张固定夹具表与4张读取副本均不参与这8项测试的数据库访问。

这8项通过`doAnswer(seed)`替换`load()`，目录引用由测试数据直接提供，因此既不执行生产来源查询，也不读取`selectDirectory`。生产P04仍会读取公司／部门／岗位目录；不能据本测试的5表范围宣称生产只依赖5表。业务写入复用现有用户、角色、用户角色、用户公司部门范围，有岗位数据时写用户岗位表；没有新建业务表的实现要求。

本次回复没有批准DDL；36表方案不再作为下一步执行前提。保留原8项全部断言后，应先把P04夹具从跨任务通用夹具中分离，再核验最小范围。当前仅完成静态核查，未修改夹具代码、未重跑P04，未执行任何DDL／迁移或59191操作。只读P22的9项本轮通过结论保持。5表具体定义仅供复核：`D:/CodexTaskArtifacts/project-detail-followup/test-17-initialization-recheck-20261023/p04-minimal-five-tables.review.sql`，未执行。
