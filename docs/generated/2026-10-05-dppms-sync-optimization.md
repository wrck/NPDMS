# DPPMS 迁移与同步优化专项结果（2026-10-05）

本次按用户专项授权实施，不使用项目记忆，不推进 PRD、SDS、Feature、DU 或 Gate 状态。基础优化和同步模板已实现；真实源数据的 1 行发货合同空主键按需求方裁决保留异常证据并跳过业务写入，完整迁移未执行。下述源码、数据量和运行结果均来自本次工作区及现行数据源核查。

## 已交付能力

- Spring Batch 同步执行器默认并发 4，可通过 `pms.integration.sync.concurrent-runs` 配置为 1～16。
- 通用任务按“租户＋实际写入表”获取锁，覆盖全部次要写入目标，按稳定顺序获取并反向释放。目标互不重叠的任务可并发，共享写入目标保持互斥；非通用适配器保留原适配器锁并加入声明的目标表锁。重试维护使用同一锁范围。
- 单个流式任务用一个有界预读槽重叠来源读取与目标提交，来源、证据、绑定、检查点仍按块顺序处理，目标事务保持原子性。源端失败传播、写入失败取消预读和租户上下文清理均有失败检查。
- 去掉通用引擎每行复制整个分块缓存的二次方开销；目标字段元数据按运行缓存；可直接求值的目标键及关联条件按块预取，重复条件去重。
- DPPMS 模板默认 Fetch Size / Chunk Size 均为 2,000，失败恢复使用 `CHECKPOINT_KEY`。已有任务定义不被模板默认值自动覆盖。
- 合同主档 SQL 把逐字段相关子查询改为按来源分组后关联，保留最新非空值、发货合同优先补充、公司身份约束和合同客户冲突检查。

新增模板 `delivery-full-sync`（DPPMS 合同、装箱单与发货记录完整同步）复用现有模板能力，来源顺序为：

`CONTRACT → SHIPMENT_CONTRACT → PACKAGE → DEVICE_SN → DEVICE_RELATION`

实际写入范围为 `com_contract`、`com_shipment_contract_reference`、`com_shipment_package`、`ast_device_shipment`、`ast_device_relationship`。发货记录和设备关系复用现有承接表复制路径，发货记录源查询一次关联装箱单及合同；保留订单行号、产品编码、附加 SN、内序列号、RMA、利润中心、保修、来源时间和 UUID 等既有字段及原始载荷。

完整模板不包含设备主档维护。原独立发货条码模板的业务映射保持原样。本次没有新增或修改业务表结构，没有覆盖不可变发货历史或放宽来源绑定、租户、删除保护及业务冲突检查。

需求方在当前会话确认空主键行保留原始异常证据并跳过。完整模板的发货合同来源使用独立 SQL 资源：保留原始 `contract_id`，正常行的 `sync_evidence_key` 沿用原主键；异常行用完整 12 个原始字段的内容摘要生成独立证据标识，并输出 `sync_source_issue`。既有通用引擎在任何业务步骤之前将其记为来源问题，平台保存原始行，禁止生成业务记录及目标绑定，其余行继续处理。没有替换或补造合同业务主键；旧独立模板保持原样。

## 本次只读核查的来源

数据源为已配置的 DPPMS 连接 `2101561624635936769`，指向本机 MySQL `dppms`；实际核查 `information_schema.columns`、`information_schema.statistics` 和行数。目标验证库为现有 Compose `npdms-domain-test` 的 `npdms_domain_test`（MySQL 24306 / Redis 24379），应用运行在宿主机。

| 来源表 | 实际行数 | 本次核实的键及有关字段 |
| --- | ---: | --- |
| `fb_contract` | 120,639 | `contract_id varchar(64)` 唯一索引；合同号、办事处、客户/项目名、市场/系统及备注字段 |
| `fb_shipment` | 156,368 | `packlist_id varchar(64)` 唯一索引，`con_id varchar(64)` 索引；发货/保修时间、收货人及快递字段 |
| `fb_shipment_barcode` | 4,194,864 | `id bigint` 主键、`uuid varchar(64)` 唯一索引；SN/附加 SN/内序列号、产品、订单行、RMA、保修、利润中心及同步时间字段 |
| `fb_shipment_barcode_relation` | 65,406 | `id int` 主键；SN/产品配对、合同及来源时间字段 |
| `pm_order_data_from_erp` | 91,572 | `id int` 主键、合同号索引；公司、客户和合同身份来源 |
| `sms_ofst_contract_head_sap` | 81,547 | `id bigint` 主键、合同号索引；来源更新时间及合同补充字段 |

合同主档查询产生 81,522 个“公司＋合同”身份。设备关系保留现有 `sn1 <> sn2` 规则，来源 65,406 行中符合条件 65,046 行，另有 360 行自关系。完整任务按现有来源定义预计读取 4,618,439 行，尚未完成这批全量迁移。

目标现行结构已包含上述原始业务列；本次实际样本及关联查询可读写，相关业务键排序规则为 `utf8mb4_unicode_ci`。当前实例已有的公司编码 `01 → 001` 转换在新任务配置中沿用，没有新增来源值域或公司映射。

## 效率实测

同一真实 DPPMS 源库，读取优化前后完整合同查询结果并逐字段比对：

| 合同来源读取 | 结果数 | 耗时 |
| --- | ---: | ---: |
| 原逐字段相关子查询 | 81,522 | 9.868 秒 |
| 分组聚合后关联 | 81,522 | 4.138 秒 |

两份结果无差异、无额外合同身份；本次来源读取约提速 2.38 倍。这是来源查询对比，不能作为 419 万条发货记录新增写入的端到端吞吐结论。

实际 MySQL、Redis 环境并发执行四个互不重叠的任务，每个来源限定 2,000 条：合同主档、装箱单、发货记录、设备关系。轮询在 0.481 秒时观察到四个运行均处于 `READING`；合计读取 8,000 条，约 12.737 秒全部结束。四个运行均为 `SUCCESS`，其中合同主档 357 条按原规则保留问题、1,643 条未变化，其余三任务各 2,000 条未变化。样本主要验证并发、事务、证据及重复同步，不表示新增记录吞吐。

四个并发运行编号为 `2107041702294413314`、`2107041702294413315`、`2107041702294413316`、`2107041702294413317`。统计按实际 `READING` / `APPLYING` 状态计算；校验脚本初次误用不存在的 `RUNNING` 状态，已依据原始状态快照纠正，没有重复运行这批样本。

## 验证与可复查运行

- 原 76 项定向检查通过，涵盖通用映射、分块事务、维护、Spring Batch 并发、来源流式读取、配置、预读失败和目标锁。空主键裁决实现后新增一项“保留原始问题、不生成业务绑定、继续下一有效行”失败检查，受影响的 26 项通用引擎检查通过，共覆盖 77 项。
- JDK 25 / Maven 完整 `yudao-server` 打包通过。Windows 运行中的 JAR 会阻止重打包，因此只在核实无活动同步后重启本次启动的后端；既有 Docker 基础设施和其他应用未停用。
- 真实浏览器已登录工作台，完成任务配置保存、重开调度/性能参数、点击样本预览和执行、重开运行历史及结果；四个并发样本的历史记录也已在浏览器复核。
- 有效主键限定样本每来源 500 条，共 2,500 条。预览 `2107040784429703170` 为 `PREVIEW_READY`（7.959 秒），执行 `2107041136130482178` 为 `SUCCESS`（9.179 秒）；均为 335 条 `CONTRACT_CUSTOMER_CONFLICT`、2,165 条 `UNCHANGED`。执行后已核对五种来源绑定，共 2,165 条；问题记录没有业务绑定。
- 空主键策略确认后的样本明确包含真实异常行，每来源 500 条，共 2,500 条。预览 `2107047110832648194` 为 `PREVIEW_READY`（9.444 秒），执行 `2107047315678261250` 为 `SUCCESS`（12.470 秒），结果为 336 条问题（335 个合同客户冲突＋1 个空主键）及 2,164 条未变化。浏览器显示预览及执行正常结束，已重开结果复核。
- 原始异常证据记录 `2107047360360182260` 归属上述执行，12 个原始字段逐个核对一致，`contract_id` 仍为空，问题类型为 `SOURCE_DATA_INVALID`、原因为 `SOURCE_KEY_MISSING`。异常证据标识未进入发货合同业务表和同步绑定；正常的五种来源绑定合计 2,164 条，其中发货合同为 499 条。
- 最终完整任务的异常来源 SQL 和 `sync_evidence_key` 已在浏览器重开、保存；保存后通过 API 核对 `sync_source_issue` 及异常保留 SQL 仍完整保留，当前配置版本为 4。
- 此处样本 SQL 的有效主键筛选只用于明确限定的验收任务，完整任务保留原全量来源定义。

完整任务：`2107037031219654657`，已包含异常保留策略，当前保持暂停且未执行。有效主键样本任务：`2107040712333811713`；异常保留样本任务：`2107046961754501121`。

[真实浏览器异常保留验收结果](http://10.210.0.11:19191/data-integration/workspace?tab=runs&run=2107047315678261250)

本次验证命令：

```powershell
mvn.cmd -pl pms-module-integration -am test '-Dtest=GenericSyncEngineTest,SyncRunTransactionTest,SyncRunMaintenanceTest,SyncBatchFrameworkTest,SpringJdbcStreamingReaderTest,SyncConfigurationTest,SyncStreamingPipelineTest,SyncTargetLocksTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dfile.encoding=UTF-8' --no-transfer-progress
mvn.cmd -pl pms-module-integration -am test '-Dtest=GenericSyncEngineTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dfile.encoding=UTF-8' --no-transfer-progress
mvn.cmd -pl yudao-server -am package '-DskipTests' '-Dfile.encoding=UTF-8' --no-transfer-progress
```

当前工作区 `.run/sync-opt-source-audit.json`、`sync-opt-contract-benchmark.json`、`sync-opt-source-quality.json`、`sync-opt-valid-sample.json`、`sync-opt-checked-sample.json`、`sync-opt-concurrent-runtime.json` 保存本次来源和运行结果，认证密码及 Token 不写入这些结果文件；检查及打包日志在同目录。

## 完整迁移的实际边界

1. `fb_contract.contract_id` 有 1 行为空字符串（NULL 为 0）；`fb_shipment.packlist_id` 无 NULL/空主键。原始限定预览 `2107037032293396481` 在该发货合同来源失败，保留此前已处理的 500 行预览证据，未执行业务写入。需求方随后裁决保留该行原始异常证据并跳过业务写入，新完整模板已采用上述处理，`Q-SYNC-SOURCEKEY-20261005-001` 已关闭。该行的合同号也为空，因此不参与合同主档的按合同号补充关联。
2. 真实全源合同查询有 818 个合同身份存在客户编码或名称冲突。现有规则已定义为 `CONTRACT_CUSTOMER_CONFLICT`，继续保留问题、不猜选客户；无需为基础优化改变该规则。样本运行成功不等于这些合同已完成业务落库。
3. 本次完成源码、模板和限定真实运行验收；没有执行 4,618,439 行完整任务，没有据样本外推全量写入时间，没有自动提交或推送代码。
