# 8项初始化失败与9项跳过的配置复验

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
