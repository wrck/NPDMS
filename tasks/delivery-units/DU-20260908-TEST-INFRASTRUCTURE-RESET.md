# DU-20260908-TEST-INFRASTRUCTURE-RESET 固定测试环境恢复

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`按用户授权重建固定npdms_test及测试Redis，切换固定Flyway至当前master迁移目录，验证迁移与连接并回写准备现状；不启动业务开发`
> Owner：`任务01a07efb-aaea-7440-8c34-c093d3b841b3`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`e1d986237d23f38dc1fced23f83911a536f45551`
> 认领提交：`973714a0bc8101da5947a82ea1a70fd4666e07f1`
> 修改边界：`tasks/delivery-units/DU-20260908-TEST-INFRASTRUCTURE-RESET.md;tasks/delivery-units/README.md;tasks/delivery-units/DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION.md;docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md;tasks/implementation-baseline-status.md`
> 串行资源：`Compose项目npdms-50eb-test、数据库npdms_test、MySQL23316、Redis26379及固定migrate容器；用户确认窗口后独占恢复，2026-09-08 16:32:49+08:00验证完成，本回执提交后释放`
> 旧功能范围：`NONE`
> 验证：`精确容器/端口/卷/数据库核对；reset后migrate/info/validate/重复migrate；迁移历史全成功、测试Redis清空及连接；保留其他数据库与开发环境`
> 集成记录：`固定测试库已重建至master V204，173个迁移全成功，两次validate通过且重复migrate为0；MySQL/Redis原容器及卷保留，测试窗口释放；不产生业务验收或Feature Done`

## 目标与边界

- 批准依据：用户先答复“允许”丢弃并重建固定测试数据，再答复“确认”巡检任务暂停使用、将窗口交接给本任务。此次数据删除仅限上述隔离测试环境，不再重复索取相同授权。
- 治理依据：[开发运行说明](../../docs/development.md#固定测试验证环境)及[主线准备回执](DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION.md)。本次不改变业务Requirement、API、Schema设计、权限或生命周期；不修改历史迁移文件、业务代码、巡检DU或其工作树。
- 当前主干有173个版本化迁移；固定库此前记录146个已成功迁移、最高V150，与当前主干不一致。既有固定Flyway绑定巡检工作树，不能直接重启旧容器并声称已应用master。
- 先核对固定容器、卷、端口及既有账号来源；仅在当前进程注入运行容器的既有凭据，不输出密码、不写.env、不改账号。必要时仅重新创建无状态migrate容器以切换到当前master的只读迁移目录，MySQL/Redis持久卷保留。
- 复用现有reset入口的限定删除步骤；不执行compose down、down --volumes、Flyway repair/clean/忽略模式或生产操作。执行前若出现新的测试连接或目标偏离，停止依赖操作，不结束其他任务进程。
- 旧测试数据按授权丢弃，不另建含业务数据或凭据的备份。实际清除范围、迁移结果及可恢复性在本回执记录；业务运行、浏览器和Feature Done均不在本单元完成声明内。

## 执行与交接

1. master登记PLANNED、提交激活认领，再执行恢复；不倒签数据操作。
2. 核对目标与连接，临时对齐现有凭据，确认持久容器不被无关配置重建，刷新固定Flyway挂载。
3. 重建测试库及清空测试Redis，执行当前master迁移；若失败保留真实失败，不修历史脚本或篡改迁移记录。
4. 验证迁移状态、只读validate、重复migrate及连接，记录其他数据库与持久卷保留情况。
5. 更新主线准备现状，释放测试环境使用窗口并本地聚焦提交，不推送。

- 当前状态：已完成授权范围内的重建、迁移、认证连接及交接验证；本记录提交后不再占用恢复窗口。
- 已知限制：工作区默认.env与固定容器凭据不同，不能把测试环境凭据写回开发配置；后续测试须采用已核对的固定环境账号来源。

## 集成回执

以下`compose`操作均带`docker compose --project-name npdms-50eb-test`前缀，只作用于已确认的固定测试项目，不使用默认开发项目。

- 认领先于操作：PLANNED提交`0c3407f3`、激活提交`973714a0`；激活前SELF拒绝是预期，提交后37个DU及索引校验通过。本次只恢复测试基础设施并更新五份准备/回执文档，不改业务代码、SQL文件、仓库配置或巡检认领。
- 目标及窗口：用户已确认巡检暂停使用并交接。执行前确认MySQL、Redis无其他测试客户端，Compose项目、测试数据库、23316/26379端口及两个具名持久卷均与授权范围一致。原固定Flyway使用巡检工作树是既有配置，不是本轮刚发生的重建。
- 恢复方式：临时注入固定运行容器的既有凭据后，`compose --dry-run up -d --wait --pull never mysql redis`仍预演为重建持久容器，因此没有直接调用包含该up步骤的`test-infrastructure.ps1 reset`。沿用原脚本的限定清理步骤，保留健康的MySQL/Redis，仅以`compose up --no-start --no-deps --force-recreate --pull never migrate`刷新无状态Flyway，确认只读挂载`E:/AICoding/Projects/NPDMS/sql/migrations`和目标`npdms_test`后才删除数据。未修改恢复脚本。
- 删除事实：2026-09-08 16:30:07+08:00开始，仅对固定MySQL执行`DROP DATABASE IF EXISTS npdms_test; CREATE DATABASE npdms_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`，并对对应测试Redis执行`FLUSHALL`。旧测试库未备份、不可由本次操作恢复；Redis清理前已为0键。保留全部持久卷，不删除其他数据库或开发环境。
- 空库迁移：固定migrate容器执行当前master迁移，退出0；173个迁移全部成功，最高V204，Flyway报告迁移执行时间25.529秒，整体清理/迁移流程于16:30:48+08:00完成。历史表实查为`173 / 204 / 0失败`。既有SQL存在VALUES()弃用和清理不存在过程等提示，未造成迁移失败；没有修订历史迁移来消除提示。
- 迁移复验：通过当前根工作区的`compose run --rm --no-deps --pull never migrate -outputType=json`依次执行`info`、`validate`、`migrate`、`validate`。info显示173项均Success、版本204；两次validate均`validationSuccessful=true / validateCount=173 / invalidMigrations=[]`；重复migrate为`success=true / migrationsExecuted=0`。一次性容器均由--rm移除，没有执行repair、clean或忽略模式。
- 连接与保护：以原测试业务账号经宿主机发布端口23316查询历史表得到`npdms_test / 173 / 204 / 0失败`；Redis26379认证PING为PONG，INFO keyspace无数据库条目；宿主机127.0.0.1两端口TCP连接通过。MySQL容器`b24653f2aa0f`、Redis容器`6456770a38a9`及其持久卷全程复用且健康；其他非系统业务库对象清单前后相同（均未发现其他业务库的表记录）。
- 凭据交接：数据库账号/密码沿用固定MySQL容器的MYSQL_USER/MYSQL_PASSWORD，Redis密码沿用对应容器的REDIS_PASSWORD；后续验收在进程内映射至NPDMS_DB_USER/NPDMS_DB_PASSWORD/NPDMS_REDIS_PASSWORD，并显式指定`NPDMS_DB_NAME=npdms_test`、`NPDMS_MYSQL_PORT=23316`、`NPDMS_REDIS_PORT=26379`。不采用当前根工作区默认.env作为测试凭据来源，不将测试值写回开发.env或文档；本次临时环境变量均已恢复。
- 验证终点：2026-09-08 16:32:49+08:00。本次恢复的是空库基础设施基线，不是从旧分支数据升级的验收，也未执行Java业务用例、应用启动、浏览器或外部系统测试；不修改任何Feature Ready/Done，不宣称主线全部正式契约已准备完成。
- 自审与释放：实际删除目标、认领时点、迁移/连接结果和保留边界均已核对；本次为主任务自审，不冒充新的独立业务审查。主线准备入口引用本回执，窗口随本地交接提交释放；后续任务使用前仍需确认自身代码/迁移与该V204基线匹配，不能用旧Flyway挂载或默认开发凭据覆盖当前环境。
