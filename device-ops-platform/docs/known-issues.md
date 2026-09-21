# 已知问题记录

本文档记录已复现的问题、根因、修复方式和关闭证据。问题关闭后保留记录，防止相同问题因契约漂移再次出现。

## DEVOPS-KI-001：Telnet 长命令在第一页后停止

- 状态：已修复，待提交
- 发现日期：2026-08-03
- 影响：Telnet 长命令只能采集第一页，后续分页内容缺失。
- 根因：设备仅在第一页输出可见分页标记，后续页通过终端控制序列刷新；旧实现要求每页都重新出现分页标记。
- 修复：SSH 和 Telnet 共用有证据约束的分页状态机；首次识别分页标记后保持分页状态，识别到命令提示符后停止。
- 防回归：`TerminalPagerControllerTest` 覆盖后续页面不重复输出分页标记的场景。
- 验证：真实 Telnet 长命令连续执行 3 次，均成功采集 441 行、18 页、约 12.5 KiB；分页标记和分页输入均未进入采集记录。

## DEVOPS-KI-002：Core 测试桩未同步增量输出持久化契约

- 状态：已修复，待提交
- 发现日期：2026-08-03
- 影响：`CollectionWorkerTest.claimsExecutesOnlyFrozenContextAndPersistsParsedFacts` 错误进入 `FAILED`。
- 根因：测试持久化桩没有实现 `appendOutput`，调用了接口的“不支持”默认实现。
- 修复：测试桩实现增量输出事件持久化边界，并继续验证最终状态及解析结果。
- 验收：`CollectionWorkerTest` 4 项全部通过，Core 模块 46 项全部通过。

## DEVOPS-KI-003：SSH 适配器测试仍断言历史契约

- 状态：已修复，待提交
- 发现日期：2026-08-03
- 影响：`MinaCommandExecutionAdapterTest` 有 4 项失败，掩盖真实回归信号。
- 根因：测试仍假定主机指纹必填、Shell 自动发送 `exit`、输出按单流限制 1 MiB，以及认证阶段耗尽共享连接期限仍返回认证失败。
- 修复：嵌入式 SSH Shell 改为模拟真实交互提示符；断言同步为主机指纹可选、Shell 以最终提示符完成、输出使用总预算、共享连接期限耗尽返回连接超时。
- 验收：`MinaCommandExecutionAdapterTest` 18 项全部通过；真实 SSH 长命令成功采集 441 行、18 页。

## DEVOPS-KI-004：SSH 客户端关闭时出现异步执行器竞态

- 状态：已修复，待提交
- 发现日期：2026-08-04
- 影响：测试和运行日志可能出现 `Executor has been shut down`，干扰真实错误识别。
- 根因：每次连接都创建并立即停止一个 `SshClient`；`stop()`关闭其执行器时，与 Windows NIO 尚未完成的回调竞争。
- 修复：适配器生命周期内复用线程安全的 `SshClient`，每个会话通过连接上下文独立携带主机指纹策略，Spring 停止时统一关闭客户端。
- 验收：SSH 适配器 18 项测试及真实 SSH 长命令均通过，测试与运行日志中的异步执行器关闭异常计数均为 0。

## DEVOPS-KI-005：持久化测试把凭据存储表误判为主档耦合

- 状态：已修复，待提交
- 发现日期：2026-08-04
- 影响：完整测试中 `CollectionPersistenceTest` 和 `ScriptArtifactPersistenceTest` 各有一项失败。
- 根因：测试仍要求不存在 `DEVICE_OPS_CREDENTIAL`；该表已用于保存连接的加密凭据，并不是项目或设备主档。
- 修复：继续禁止平台创建项目、设备主档表，并将“采集快照不得包含凭据字段”的检查限定到采集任务及目标表。
- 验收：持久化模块 10 项测试全部通过；设备平台完整 Maven 测试构建成功。

## DEVOPS-KI-006：连接器缺少同连接并发契约和可观测性

- 状态：已修复，待提交
- 发现日期：2026-08-04
- 影响：此前只有全局采集线程池，没有同一连接的并发限流；共享 SSH 连接关闭还存在竞态，无法从低基数指标判断线程池、连接等待和命令执行结果。
- 根因：采集任务只受全局执行器容量约束，同一连接没有公平准入和租约等待；共享连接生命周期没有完整覆盖任务隔离、重入关闭和并发关闭；运行指标也没有统一的低基数边界。
- 修复：增加同连接公平 gate、有限租约等待和任务级连接隔离，补齐共享 SSH 的重入安全关闭，并暴露不含连接身份、命令或凭据的执行器、连接等待及协议/outcome 指标。
- 聚焦回归：`LocalConnectionConcurrencyGateTest.defaultLimitSerializesTheSameConnection`、`LocalConnectionConcurrencyGateTest.fairSemaphoreGrantsSameKeyInFifoOrder`、`LocalConnectionConcurrencyGateTest.timeoutUsesSafeConnectionCapacityFailure`、`CollectionWorkerTest.acquiresBeforeExecutionAndReleasesAfterExecution`、`CollectionWorkerTest.releasesPermitWhenCommandExecutionFails`、`MinaCommandExecutionAdapterTest.concurrentSessionsKeepHostKeyPolicyAndOutputIsolated`、`MinaCommandExecutionAdapterTest.progressListenerCanCloseAdapterWithoutDeadlock`、`MinaCommandExecutionAdapterTest.repeatedCloseIsSafeAndKeepsAdapterClosed`、`DeviceOpsConcurrencyMetricsTest.exposesDynamicLowCardinalityExecutorAndConnectionMetrics`、`MeteredCommandExecutionPortTest.recordsOnlyProtocolAndBoundedOutcomeForSuccessTimeoutAndFailure`。
- 最终审查补充根因：旧 gate 位于全局 worker 内部，同 key waiter 会先占用稀缺 worker，造成跨 key 队头阻塞和错误容量拒绝；JDBC claim 又提前写入 `CONNECTING`。执行器、SSH bean 和一次性启动恢复之间没有统一生命周期，导致关闭顺序、硬超时和租约到期后的持续回收均缺少确定契约。
- 最终架构（JDK 25 关闭契约修订）：以 `KeyedCollectionDispatcher` 作为唯一连接并发真源，在进入全局有界执行器前按 `(protocol, host, port, username)` 做 FIFO 准入；总 outstanding 容量仍与实例执行器容量一致，等待期保持 `QUEUED`，真正执行协议前才写 `CONNECTING`。claim 在取得数据库连接后即为 target/attempt 建立相同的 `shutdown-recovery-lease` 短基线；dispatcher-owned 批量 heartbeat 覆盖 WAITING、DISPATCHED、RUNNING，并只延长租约，每批在阻塞 setup/owner 检查之后重算完整窗口。恢复租约至少为 heartbeat interval 的三倍，并覆盖总 shutdown deadline 加两个 heartbeat interval。batch 发现 RUNNING owner 丢失时只发出 best-effort interrupt/cancel 标记，上下文仍由原 worker wrapper 清理。关闭遵循 Oracle JDK 25 `ExecutorService` 两阶段语义：先停止 intake 和新的 maintenance 调度，让已接纳任务在 `shutdown-graceful-period` 内排空；超时后对 worker/maintenance 调用 `shutdownNow()`，同步处理其返回的未启动任务，并在剩余的同一绝对 deadline 内再次等待终止。`forceClose()` 只是在 forced 阶段帮助活动 SSH I/O 响应取消，不再承诺不可实现的广义 stop-last。forced 路径不获取新 JDBC 连接或提交新事务；停止 owned heartbeat 后，未启动任务直接依赖已持久化的短租约恢复基线并同步关闭上下文。尚未完成 claim 的任务会明确报告 `failedHandoffs`/`unfinishedWork`，忽略中断的活动任务或 maintenance 会报告 `terminated=false`，不得假报完整关闭。首次及周期 recovery sweep 也注册在同一 owned maintenance executor，并继续通过 target 行锁避开 live owner、在同一事务合并增量输出证据、关闭 target/attempt、写幂等 terminal outbox。
- 最终回归证据：`KeyedCollectionDispatcherTest` 覆盖 orderly/forced、returned queue、interrupt-responsive/ignoring、shutdown caller interrupt、claim race、blocking maintenance、RUNNING batch heartbeat、迟到 heartbeat 短窗口、SSH cancellation aid 与无 detached cleanup；`CollectionPersistenceTest.pendingClaimAndBatchHeartbeatKeepTargetAndAttemptOnOneShortLease` 验证 target/attempt 同一短基线、只延长和 lost-owner 结果；`CollectionRecoveryOnReadyTest.startupAndPeriodicSweepUseBoundedStaleSafeRecoveryWindow` 验证首次及周期 sweep 由 owned scheduler 注册；`DeviceOpsSpringWiringTest.springLifecycleForceClosesSshBeforeTheForcedTerminationAwait` 验证 lifecycle 顺序；SSH/Telnet 分页回归保持不变。
- 最终验证：JDK 25 聚焦回归 72 项及全量 Maven reactor 均为 0 失败；SSH reactor 连续 3 轮均退出 0，且每轮日志中的 `Executor has been shut down` 与 `ClosedSelectorException` 命中数均为 0。

## 维护规则

1. 已修复问题必须保留问题编号、根因、回归测试和验证证据。
2. 修改连接、认证、提示符、分页或输出预算契约时，必须同步 SSH、Telnet 和 Core 测试。
3. 长命令验收必须包含真实多页输出，不能用短命令代替。
4. 分页控制输入不得进入采集输出或采集记录。
