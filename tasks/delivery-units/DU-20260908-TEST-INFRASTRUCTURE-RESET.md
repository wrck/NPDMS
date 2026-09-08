# DU-20260908-TEST-INFRASTRUCTURE-RESET 固定测试环境恢复

> DU状态：`PLANNED`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`按用户授权重建固定npdms_test及测试Redis，切换固定Flyway至当前master迁移目录，验证迁移与连接并回写准备现状；不启动业务开发`
> Owner：`任务01a07efb-aaea-7440-8c34-c093d3b841b3`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`e1d986237d23f38dc1fced23f83911a536f45551`
> 认领提交：`SELF`
> 修改边界：`tasks/delivery-units/DU-20260908-TEST-INFRASTRUCTURE-RESET.md;tasks/delivery-units/README.md;tasks/delivery-units/DU-20260908-PROJECT-DELIVERY-MAINLINE-PREPARATION.md;docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md;tasks/implementation-baseline-status.md`
> 串行资源：`Compose项目npdms-50eb-test、数据库npdms_test、MySQL23316、Redis26379及固定migrate容器；用户已确认巡检任务暂停使用并交接窗口，恢复验证完成前由本任务独占`
> 旧功能范围：`NONE`
> 验证：`精确容器/端口/卷/数据库核对；reset后migrate/info/validate/重复migrate；迁移历史全成功、测试Redis清空及连接；保留其他数据库与开发环境`
> 集成记录：`NONE`

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

- 当前状态：仅完成授权、窗口确认与只读检查；尚未执行重置。
- 已知限制：工作区默认.env与固定容器凭据不同，不能把测试环境凭据写回开发配置；后续测试须采用已核对的固定环境账号来源。

## 集成回执

待实际执行后记录，不预填PASS或环境就绪。
