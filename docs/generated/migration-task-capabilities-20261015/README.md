# 迁移、同步与任务公共能力整理

更新：2026-10-15。代码核对基准：`7094787dd2059cb04c782942860e73f863594b68`，目标分支 `fix/visio-template-followup`。

## 结论与提交范围

现有正式实现已进入 Git 历史。本次整理不重写迁移引擎，不把设备/发货验收样例当成公共框架，不把基线实验的生产代码回退发布为新优化。

本目录保存公共能力导航、接入与恢复约束、验收事实，以及两份已编译的历史测试驱动源码。驱动存入 `fixtures/` 作为复现证据，不自动进入 Maven 测试发现，也不是可直接在任意环境执行的通用 API。其数据库名、运行 ID 和测试规模是明确的隔离现场约束。不要通过删除断言把它们用于其他数据库。

**整体迁移性能及全链路验收仍未通过。测试编译通过不等于迁移成功。**

## 一、迁移与同步共用的执行链

源码根：`pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/`。

| 公共职责 | 实现入口 | 已核对的行为与边界 |
|---|---|---|
| 配置聚合 | `sync/SyncDefinition.java` | 来源、目标映射、参数、过滤、列投影、模式、读取策略、分块大小、证据策略和恢复策略统一承载 |
| 任务管理 | `sync/SyncTaskService.java` | `checkConfiguration/save/schedule/page/get/required/locked`；租户范围、配置版本、来源身份和活动运行保护 |
| 运行命令 | `sync/SyncRunService.java` | `Start/start/execute/maintain/refreshCache`；请求键幂等、配置快照、运行所有权、锁、事务、检查点、失败记录及关联重试 |
| 流式读取 | `sync/SpringJdbcStreamingReader.java` | `stream` 按来源顺序读取；只读快照、源主键排序、重复主键检查、有界 chunk 与读取上界 |
| 分页读取 | `sync/MysqlSyncReader.java` 与 `SyncRunService.executePaged` | 分页运行和检查点沿用公共服务，不能把 OFFSET 全量扫描包装成增量能力 |
| 映射与批量计划 | `sync/generic/GenericSyncEngine.java` | 元数据驱动的目标计划、批量预取、lookup、当前块读己之写、实际差异写入 |
| SQL 写入 | `dal/mysql/sync/GenericSyncJdbcStore.java` | 公共导入基础设施的授权表/列与参数化值；已有多值批量写入。普通业务复杂查询仍使用 Mapper/XML |
| 绑定与来源证据 | `SyncRunService`、`sync/SyncBindingSnapshots.java`、`sync/SyncEvidenceSelection.java` | 绑定紧凑存储、证据选择及无变化复用；业务、绑定、证据、检查点保持一致事务边界 |
| 执行承载 | `sync/SyncBatchLauncher.java` | Spring Batch 元数据与执行恢复；Batch tasklet 的读写计数不能代替业务已提交行数 |

迁移和后续同步应共享上述配置、映射、执行、证据及任务服务。新增业务首先提供合法的来源身份、源主键、过滤/参数、目标元数据和真实业务约束，不复制一个设备专用或发货专用同步器。

### 任务配置与权限边界

- `save` 校验配置版本；活动运行期间不允许修改配置。
- 已有绑定后，来源系统、连接、来源对象和主键等身份不能随意替换。
- `schedule` 启用前要求当前配置版本已完成完整预览确认；加载前截断或重置映射不能直接启用自动调度。
- 查询/锁定任务使用当前租户范围。增加业务入口不能绕过权限、租户、身份校验和目标目录约束。
- 请求幂等键与配置版本是不同概念，不能通过改请求键掩盖未知执行结果。

### 读取与增量边界

`SyncDefinition` 已包含 `SNAPSHOT`、`KEYSET_PAGING`、`STREAMING_CURSOR` 等读取策略，以及 `mode`、`updatedAt`、`overlapSeconds`、`maxBytes`、`fetchSize`、`chunkSize` 等配置。

有增量字段和水位并不等于任意复杂关联 SQL 都已实现高效增量。合同/组织等关联变化如何传播到未变的主记录、硬删除如何识别、迟到乱序如何核对，仍需专项正确性证明。当前大规模“变更源全扫描”不能标为只读取变更数据的增量验收。

保留既定业务键约定：字符串统一小写、去尾空格；复合键组成值不允许分隔符。本次不改这些语义。

## 二、公共恢复与重试契约

准确命令签名：

```java
new SyncRunService.Start(taskId, expectedVersion, requestKey,
    preview, full, adoptExisting, retryOf, confirmPreparation)
```

1. 有活动 `activeRunId` 时先核对进程、锁和实际运行记录，不直接 SQL 清除所有权。
2. `maintain()` 在同一作用域锁内恢复中断的 Batch 执行，将符合恢复条件的旧运行标为 FAILED 并释放任务所有权。它会扫描维护范围，不能在未核对范围时当成单个 run 的任意重置按钮。
3. `retryOf` 必须指向同一任务的 FAILED 主运行；配置版本、preview 等必须满足校验。
4. 新重试创建新 run，保留旧 run、失败原因、关联关系与原验收文件。
5. `RESTART_ALL` 从头重放。记录了 `sourceIndex/committedRows` 并不意味着这项策略会使用断点跳过。
6. 流式 `CHECKPOINT_KEY` 才读取持久化恢复位置；必须满足稳定源键、源边界与配置约束。`NO_RESTART` 不允许关联重试。
7. 已有目标上的恢复或重放单独计时，不能写成空库首次迁移性能。

### 本次超时已知事实

2026-10-15 重试在 `SpringJdbcStreamingReader.streamSource` 内的 JDBC 查询执行路径失败，最深原因为 `SocketTimeoutException: Read timed out`；日志最后成功收发包间隔约 60,067 ms。

`SyncDefinition.queryTimeoutSeconds` 默认 0；reader 仅在大于 0 时调用 `PreparedStatement.setQueryTimeout`。**该默认值不能证明连接的 socket timeout 为无限，也不能证明本次是 SQL 本身、驱动配置或环境网络造成。** 完整 SQL/源连接设置与数据库同期状态仍须核对，不在本次整理中伪造根因或盲目扩大超时。

## 三、项目任务与业务默认实现的边界

迁移运行任务和项目交付任务不能混为同一个运行状态模型。项目模板应消费业务公共事实，业务实体本身不应依赖活动流程令牌才能 CRUD。

已确认目标及历史缺口以以下文档为入口：

- [默认业务实现完整交接](../default-business-handoff-20261008/README.md)
- [统一默认实现决策](../../decisions/2026-10-07-unified-business-default-implementation.md)
- [业务继承接入指南](../../coding/project-business-inheritance.md)

核心要求继续有效：

- 允许薄实体、Controller、Service、Mapper 和前端入口；通过默认实现继承公共能力，特殊业务重载钩子或增加操作。
- 实体、权限、API、服务事务、SQL/数据层、字段配置、扩展、版本化、前端与交付件形成完整链。继承 `BaseBusinessEntity` 不代表验收完成。
- 公共交付件按项目 ID、业务类型、业务实体键、交付件类型定位，公共上传/CRUD/历史/归集和完成判定复用同一实体。
- 业务已确认、指定类型交付件有有效上传等真实事实用于公共规则判定；不得用手工置完成伪装自动判断。
- 通用前后钩子保留事务边界、权限、版本、幂等、审计和提交后事件；专业计算不进入公共核心。

本次没有重新完成前端真实浏览器验收，也没有将 10 月 8 日交接中的命名配置、关联子表、条件判定、全实体接入等未闭合项改标为已完成。

## 四、历史结果与本次验收事实

详见 [acceptance-evidence.json](acceptance-evidence.json)。以下时间不能跨不同任务口径直接排名：

| 运行 | 数据/口径 | 时间 | 结论 |
|---|---|---:|---|
| V7 历史首次迁移 | 完整初次链，4,722,800 来源行 | 4,262.754054886 秒 | 历史比较基准 |
| 历史稳定重放 E | 已归集目标，4,723,801 来源行 | 1,027.765815752 秒 | 不是空库首迁 |
| 2026-10-14 基线空库复验 | 仅 source 0，120,639 行 | 470.704568802 秒 | 性能失败，后续停止，首迁未完成 |
| 2026-10-15 关联重试 | source 0 已有目标，120,639 行 | 167.557242887 秒 | 该段完成；source 1 读取超时，整次失败 |

10 月 15 日重试 `2110566377844752385` 关联旧 run `2110405569336590338`；失败时进度为 120,639 行、242 块、sourceIndex=1，summary 为 UNCHANGED=120639、EVIDENCE_REUSED=120639。observer PASS、0 错误、磁盘保护未触发，不代表业务验收 PASS。

用户仅对这次继续迁移取消 1.5 倍耗时自动停止；仍保存性能记录、2 GiB 磁盘保护和正确性断言。此决定不覆盖历史结果，不构成今后所有验收永久关闭性能门槛的规则。

## 五、提交与复现注意事项

- 正式代码起点：`7094787dd2059cb04c782942860e73f863594b68`。
- 云工作树 6 M / 12 D 共 18 处 tracked 差异全为基线复验临时还原；18 份正式副本与 HEAD Git blob 一致，已在仓库外备份。不要 `git add -A` 误提交回退。
- 本提交只增加整理文档、结构化验收摘要和两份原样测试驱动快照；不变更正式生产源码。
- 两份驱动在历史基线输入下编译通过。重试驱动最后一次 JDK 25 `test-compile` 耗时 48.721903801 秒，真实运行失败如上。当前正式 HEAD 下的全量构建、前端和新一轮真实数据库验收不由此证明。
- 复现需要原隔离库、固定模拟源、正确 JDK/Maven、基线输入哈希与独立输出目录；不包含凭据、数据库原始转储或巨量 JFR 展开文件。
- 继续工作顺序：保存所有失败记录 → 获取超时 SQL/驱动/数据库证据 → 最小修复及定向回归 → 完成迁移与完整关系校验 → 单独执行未变重放和变更数据同步 → 按相同数据、配置、环节及环境比较性能。
