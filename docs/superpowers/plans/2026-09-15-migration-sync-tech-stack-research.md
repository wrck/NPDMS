# 数据迁移与同步现成方案技术栈调研

> 文档状态：`DRAFT`（非基线调研，归属 docs/README.md「规格草案 / 工程计划（非基线）」类）
> 日期：2026-09-15；外部事实截止：2026-09-15（各来源 URL 的访问日期见第 10 章）
> 声明：本报告是技术调研材料，不改变任何 PRD/SDS/Feature/Gate/Question 状态，不构成实现授权；引用的权威依据以正式规格为准。
> 权威依据：PRD V1.8 §12.3、§13.2.4、§3.4（AI-MIG-000，L732）；SDS 08a（迁移策略代码）；SDS 09（plt_sync_batch / plt_external_key_mapping）；SDS 12（集成原则、通用处理链、系统集成总表、字段级操作契约总表）；附录 data-migration-and-core-business-ai-handoff.md；F-CUS-001 §2.2；F-INT-012；open-questions Q-REL-006 / Q-P3-008 / Q-FCUS-001 / Q-P3-007。

---

## 1. 结论摘要

**对"数据迁移和同步有没有现成的解决方案"的回答分三层：**

1. **内核层——已经在用现成方案。** 自建集成工作台的批处理内核就是业界标准框架 **Spring Batch 6.0.4**（随 Spring Boot 4.1.0 BOM 引入，[pom.xml](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pom.xml)），官方基线 Java 17+、兼容 JDK 25，2026-06 仍在发版。重启/恢复（`JobOperator.recover`）、容错（Skip/Retry）、本地多线程 chunk 均为框架内置。调度用 Quartz。这不是"重复造轮子"，轮子是现成的。

2. **业务规则层与证据层——结构性没有现成方案，且已自建完成。** 本项目迁移契约要求 10 种迁移策略代码、逐对象"禁止推断"、迁移证据（`pms_migration_source_record`）、问题清单（`pms_migration_issue`）、来源键映射（`plt_external_key_mapping`）、经接收 API 写入（`CommerceAuthorityIngestApi.ingestBatch`：sourceVersion CAS、同版本冲突隔离、全批回滚）、多租户隔离。**任何通用 ETL/CDC 工具都无法表达这些规则**——它们的设计目标是"高效搬运数据行"，而本项目要求的是"受控地形成业务事实"。这一层不需要也不应该引入外部工具。

3. **搬运/读取层——现成工具仅在两个场景有真实增量价值：**
   - **超大表并行搬运**（单表千万级以上、切换窗口紧张）：Apache SeaTunnel 2.3.13（容器隔离运行）可提供 JDBC 并行分片与 binlog CDC，是唯一全量+增量+断点+无 MQ 全部满足的开源引擎。
   - **割接窗口 binlog 增量追赶**（S3）：Debezium 3.6.x Embedded（嵌入应用进程、offset 存 MySQL）或 SeaTunnel MySQL-CDC，是唯一值得评估引入 CDC 组件的场景；且前提是需求方确认割接不允许短窗口停机。

**分场景结论（详见第 7 章）：S1 一次性历史迁移：维持现状（集成工作台）；S2 持续外部同步：维持现状（适配器 + 接收 API，来源以 API 为主，ETL/CDC 工具结构性不适用）；S3 割接增量追赶：有条件引入 Debezium Embedded（先 PoC）；S4 对 Q-REL-006：工具选型不显著改变该决策的主要变量（范围与业务对账人），引入外部工具不减少 PRD §13.2.4 的对账与回退演练责任。**

淘汰要点：DataX 官方停滞 3 年且仅 JDK 8（其活跃分支 Addax 仅作二线兜底）；Canal 停滞且不兼容 JDK 17+；Kettle CE 2022 年起停更；Flink CDC 的 pipeline 模式不支持 MySQL 作为 sink 且需引入 Flink 集群（过重）；Kafka Connect 强依赖 MQ（违反 C5）；CloudCanal/Tapdata 闭源许可或开源线停更，仅作对照。

**增补（2026-09-15，同日追加）**：针对新约束「来源表无主键 + 全表同步 + 零开发、纯界面配置 + 全量/增量 + 单表约 500 万」的专项评估见**第 12 章**——唯一全部满足的现成方案为 **CloudCanal 社区版**（附闭源许可、任务数限制与无主键校验订正缺失的代价）；同时揭示一个决定性待确认项：这些"无主键"表是否具备**唯一键**（有唯一键时多个工具的增量行为显著改善，见 Q-R7）。

---

## 2. 范围与方法

- **范围**：两条链路。①一次性历史迁移：dppms 旧库、`t_*`/`fnd_*` 旧权限表 → NPDMS 66 表核心迁移子集（appendices/data-migration-and-core-business-ai-handoff.md）；②持续外部系统同步：PRD §12.3 INT-01～INT-12 共 14 个外部系统（SDS 12 §4）。
- **方法**：先筛后深。先固化项目硬约束（第 3 章），再对约 15 个候选做全景扫描（第 4 章矩阵），对存活者深入分析（第 5 章），按 4 个场景给结论（第 7 章）。
- **口径**（已与需求方确认）：开源自托管优先，闭源/商业仅对照；中深度；交付本报告，不执行 PoC。
- **事实纪律**：所有版本号、License、JDK 支持均来自官方 GitHub releases / 官网 / Maven Central，来源与访问日期见第 10 章；查不到的标【待确认】（第 11 章），不臆造。

## 3. 项目约束基线

### 3.1 硬约束清单（淘汰矩阵依据）

| 编号 | 约束 | 依据 |
|---|---|---|
| C1 | 运行环境为宿主机 JDK 25 应用进程（[pom.xml](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pom.xml) `java.version=25`、`spring.boot.version=4.1.0`），或本地/独立 Docker 容器；JDK 版本过旧的工具需说明隔离运行代价 | docs/development.md、upstream-sources.md |
| C2 | 业务规则表达：逐对象迁移策略、字段级映射、禁止推断、问题清单（不允许静默丢弃/覆盖） | SDS 08a §2、PRD §13.2.4 |
| C3 | 写入通道：目标写入须经 `CommerceAuthorityIngestApi` 或等价 UPSERT + `plt_external_key_mapping` + 证据表；外部工具直写业务库即违反"不绕过服务端授权与数据范围" | SDS 12 §4.1、AGENTS.md |
| C4 | 幂等/断点/对账：批次水位（`plt_sync_batch`）、断点续传、多维度对账（非只对行数）、来源版本判重 | PRD §13.2.4、Q-FCUS-001 |
| C5 | 无消息队列：不引入 Kafka/RocketMQ 作为必选依赖；异步可靠性用 Outbox + Redis | compose.yaml、docs/development.md |
| C6 | 多租户：工具层无租户概念时，须在适配/映射层保证租户隔离 | AGENTS.md、F-CUS-001 |
| C7 | 持续同步来源以 API 为主（CRM/ERP/钉钉/HR/OA 等），字段级操作契约 + 乱序幂等；DB-CDC 仅对库到库场景有效 | SDS 12 §4/§4.1 |
| C8 | 上游锁定纪律：mini 为准、不整仓覆盖；外部引擎以独立进程/容器接入优先于嵌入应用进程 | AGENTS.md 技术基线 |

### 3.2 既有自建能力盘点（在位方案，作为基线候选）

- **集成工作台"迁移与同步任务"**：`pms-module-integration` 的 `sync` 包——`SyncTaskService`/`SyncRunService`/`SyncBatchConfiguration`/`SyncBatchLauncher`/`MysqlSyncReader`（游标分页读取）/`SyncFieldMapper`/`SyncEvidenceService`/`SyncDefinitionValidator`/`SyncPagingState`。
- **内核**：Spring Batch 6.0.4（`spring-batch-core`，由 Spring Boot 4.1.0 BOM 管理）；调度 Quartz（`DataSyncQuartzRegistrar`/`DataSyncDispatchJob`）。
- **模板与适配器**：`DppmsOrderSyncTemplate`（UPSERT + 自动分页游标 + 固定上界 + 断点重试 + 每页独立事务 + 来源证据，已实战处理 82,000 条源订单→81,673 条目标订单，见 docs/generated/dppms-order-migration-template-2026-09-14.md）；`EhrSyncTemplate`/`EhrOrganizationAdapter`（HR 组织人员同步）。
- **接收契约**：`CommerceAuthorityIngestApi.ingestBatch`（SDS 12 §4.1）：受信 tenant/eventId/batchId/sourceSystem/sourceWatermark，`expectedPreviousSourceVersion` 精确 CAS，同版本异载荷永久冲突隔离，任一冲突全批回滚，重放返回 ACCEPTED_NO_CHANGE。
- **基础设施表**：`plt_external_key_mapping`（来源键→目标，一个来源键一个当前有效目标，09-database-design.md L409）、`plt_sync_batch`（同步批次/水位，L417）、`pms_migration_source_record`、`pms_migration_issue`。
- **异步**：无 MQ，Outbox + Redis（C5）。

### 3.3 对初始假设的一处校准

计划阶段曾假设"Spring Boot 3.x"；实际核实为 **Spring Boot 4.1.0 → Spring Batch 6.0.4**（[pom.xml](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pom.xml) L67；spring.io 2026-06-10 发布说明佐证 Boot 4.1 配套 Spring Batch 6.0.x；并经本地 Maven 仓库 `D:\Maven\Repository\org\springframework\batch\spring-batch-core\6.0.4` 复核确认解析版本）。该事实加强而非削弱"在位方案已是最新一代标准框架"的结论。

## 4. 候选全景与淘汰矩阵

### 4.1 候选清单与关键事实

| 候选 | 最新版本/发布 | License | 运行形态/JDK | MySQL→MySQL | 断点/幂等 | 强依赖 | 维护状态 |
|---|---|---|---|---|---|---|---|
| **Spring Batch（在位）** | 6.0.4（2026-06-10，随 Boot 4.1.0 BOM） | Apache-2.0 | 应用内嵌；Java 17 基线，**官方兼容 JDK 25** | JDBC 自定义（已实现分页游标） | JobRepository 重启 + `JobOperator.recover`；幂等由业务层（ingest API）保证 | 无 | 活跃，随 Boot 节奏发版 |
| **Apache SeaTunnel** | 2.3.13（2026-03-12） | Apache-2.0 | Zeta 引擎：local 单进程/集群/官方镜像；官方 JDK 矩阵 8/11/17，17 有社区实测，**25【待确认】** | 全量 ✓（JDBC，主键分片并行）；增量 ✓（MySQL-CDC，2.3.12 起支持 MySQL 8.4+） | Zeta checkpoint + savepoint；CDC 从 checkpoint/GTID 恢复 | 无 MQ | 活跃，约每 2~4 个月一版 |
| **Debezium** | 3.6.2.Final（2026-09-01） | Apache-2.0 | ①Embedded：嵌入应用（Java 17+，25【待确认】）②Server：独立 Quarkus 进程（Java 21+，**无官方 JDBC sink**）③Kafka Connect（本项目不用） | 全量 ✓（initial snapshot）；增量 ✓（binlog，官方 tested 含 MySQL 8.4/9.x） | offset 可存 MySQL（`JdbcOffsetBackingStore`，2.5 起）；幂等由下游 sink 决定 | 无 MQ（①②） | Red Hat 支持，非常活跃 |
| **Apache Hop** | 2.19.0（2026-08-17） | Apache-2.0 | hop-run/hop-server 无头 + GUI + 官方镜像；官方验证 Java 21，**25【待确认】**；最低 1C/4G | 全量 ✓（表输入/输出/插入更新）；增量：自建增量 SQL，**无原生 binlog CDC** | 无框架级断点；幂等靠插入更新步骤 | 无 | 活跃，季度级发版 |
| Apache NiFi | 2.12.0（2026-09-13） | Apache-2.0 | 独立 JVM/容器；官方要求 Java 21，25【待确认】；镜像 ~1.26GB | 全量 ✓（QueryDatabaseTable）；增量 ✓（CaptureChangeMySQL，binlog） | 处理器 State 持久化 + FlowFile 仓库 | 无 MQ（单机） | 活跃，月级发版；1.x 已 EOL |
| DBSyncer | v2.1.4（2026-08-13） | Apache-2.0 + 收费专业版 | 独立 Spring Boot Web 应用 + 官方镜像；JDK 8 起步，25【待确认】 | 全量 ✓（游标分页/整库）；增量 ✓（binlog，全量+增量组合） | 位点文件/MySQL；幂等语义未文档化【待确认】 | 无 | 2026 年连续发版，但社区小（Gitee 3.5k star） |
| Addax（DataX 活跃分支） | 6.0.13（Maven Central） | Apache-2.0 | 单机单进程；JDK 8/11/17 | 全量 ✓（splitPk 并行）；**无 binlog 增量** | 无 checkpoint；幂等靠 writeMode | 无 | 活跃（wgzhao 维护） |
| DataX（alibaba 官方） | v202309（2023-09） | Apache-2.0 | 单机单进程；JDK 1.8 | 全量 ✓；无增量 | 无断点 | 无 | **停滞约 3 年** |
| Flink CDC | 3.6.0（2026-03-30） | Apache-2.0 | **必须 Flink 运行时**（1.20/2.2，JDK 11 起生产 17/21） | 全量+增量 ✓（binlog）；但 **pipeline 模式不支持 MySQL 作为 sink**，需自写 Flink 作业 | Flink checkpoint | Flink 集群 | 活跃 |
| Canal | 1.1.8（2025-01-16） | Apache-2.0 | server/adapter/admin 组件链，容器可跑；JDK 8，JDK 17+ 有兼容问题报告 | **全量不支持**；增量 ✓（binlog 伪装 slave） | Meta 文件/ZK；无内置幂等去重 | 单机无；HA 需 ZK | 发布稀疏（最后 release 2025-01，2026-07 仅安全修复），式微 |
| Kettle/PDI CE | 9.4 CE（2022-11-01） | Apache-2.0 | JVM 独立（Spoon/Kitchen/Pan）；JDK ≤17 | 全量 ✓；增量靠自建 SQL | 无框架级断点 | 无 | **CE 停更（10.x 仅企业版）** |
| Kafka Connect | - | - | - | - | - | **必须 Kafka** | -（C5 直接淘汰） |
| Sqoop | - | Apache-2.0 | - | - | - | - | Apache Attic（退役） |
| CloudCanal 社区版（对照） | 官网现行 | **闭源免费**（许可证续期制） | 自托管 Docker all-in-one | 全量+增量 ✓，DDL 同步 | 内置"校验与订正"（逐字段 diff + REPLACE 订正） | 无 | 商业公司维护；**社区版限 5 任务/500 表/单用户/无 HA，官网标注适用测试环境** |
| Tapdata（对照） | v3.27.0（2025-04-14） | Apache-2.0 | Docker/独立进程 | 全量+增量 ✓（含数据校验） | 平台管理 | 无 | **开源线约 17 个月无新版**，商业线 4.x 闭源 |
| DataPipeline（对照） | - | 商业闭源 | 私有部署 DaaS | ✓ | ✓ | - | 无开源社区版，仅列名 |

### 4.2 淘汰矩阵（候选 × 硬约束）

取值：✓ 通过；◐ 有条件通过（附条件）；✗ 违反（淘汰依据）。

| 候选 | C1（JDK 25/容器） | C5（无 MQ） | C8（独立进程优先） | C2/C3/C6（业务规则/写入通道/租户——结构性判断） | 矩阵结论 |
|---|---|---|---|---|---|
| Spring Batch 6.0.4（在位） | ✓ 官方兼容 JDK 25 | ✓ | ✓ 应用内置（在位形态） | ✓ 已承载（业务层即为本项目自建） | **存活（基线）** |
| Apache SeaTunnel 2.3.13 | ◐ 官方矩阵 8/11/17；以官方容器隔离运行可满足；宿主机直跑 25 未背书 | ✓ | ✓ Zeta local/容器 | ✗（结构性）通用工具无法承载 C2/C3，只能作"搬运层"，写入仍须回流接收 API | **存活（搬运/CDC 引擎候选）** |
| Debezium 3.6.2 | ◐ Java 17+ 下限满足；Embedded 嵌入 SB4.1 的依赖共存与 JDK 25 需 PoC【待确认】；Server 独立进程可容器化 | ✓（Embedded/Server 均不依赖 Kafka） | ◐ Server ✓；Embedded 嵌入应用须评估 kafka-clients/Jackson 共存 | ✗（结构性）同上；且**无官方 JDBC sink**，落库必须自研 | **存活（仅 S3 CDC 场景）** |
| Apache Hop 2.19 | ◐ 官方验证 Java 21，25 未验证；容器可隔离 | ✓ | ✓ hop-run/hop-server | ✗（结构性）同上；映射画布无法表达禁止推断/证据/问题清单 | **存活（二线，仅作对照深评）** |
| Apache NiFi 2.12 | ◐ Java 21，25 未验证 | ✓ | ✓ | ✗（结构性）；另资源占用重（镜像 ~1.26GB）、流式 UI 运维模型与本项目代码优先契约不匹配 | 有条件存活→**不推荐**（运维成本收益比劣于 SeaTunnel/Hop） |
| DBSyncer 2.1.4 | ◐ 容器可隔离，25 未验证 | ✓ | ✓ | ✗（结构性）；直写目标库设计违反 C3，需中转模式；社区规模小、幂等语义未文档化 | 二线观察→**不推荐主链** |
| Addax 6.0.x | ◐ JDK 8/11/17，容器隔离 | ✓ | ✓ | ✗（结构性）；无增量无断点 | 二线兜底→**不推荐**（能力是工作台子集） |
| DataX 官方 | ✗ 仅 JDK 8 且官方停滞 3 年 | ✓ | ✓ | ✗ | **淘汰**（如需其模型用 Addax，仍不推荐） |
| Flink CDC 3.6.0 | ◐ 容器可跑 | ✓ | ◐ 需 Flink 运行时（单机同步过重） | ✗（结构性）；且 pipeline 模式无 MySQL sink，MySQL→MySQL 需自写 Flink 作业 | **淘汰**（引入 Flink 集群代价与本项目规模不成比例） |
| Canal 1.1.8 | ✗ JDK 8 栈，JDK 17+ 兼容问题，仅容器勉强；发布停滞 | ✓（TCP 模式） | ◐ 组件链重 | ✗（结构性）；且全量不支持 | **淘汰** |
| Kettle CE 9.4 | ✗ CE 停更（2022-11），无 JDK 升级路线，MySQL 8.4 驱动兼容无保障 | ✓ | ✓ | ✗ | **淘汰** |
| Kafka Connect | - | ✗ 强依赖 Kafka | - | - | **淘汰（C5）** |
| Sqoop | - | - | - | - | **淘汰**（Apache Attic 退役） |
| CloudCanal 社区版（对照） | ✓（自带运行时） | ✓ | ✓ | ✗（结构性）；闭源许可 + 5 任务/500 表/单用户限制 + 官网标注适用测试环境 | 对照项，**不推荐主链** |
| Tapdata（对照） | ◐ | ✓ | ✓ | ✗（结构性）；开源线停更 17 个月 | 对照项，**不推荐** |
| DataPipeline（对照） | - | - | - | - | 对照项（无开源版，超出本次口径） |

**结构性判断说明（适用于所有外部引擎）：** C2（迁移策略代码、禁止推断、问题清单）、C3（经接收 API 写入 + 证据表）、C6（租户隔离）是业务契约，任何通用引擎的 connector/transform 模型都不承载这些语义。因此外部工具在本项目的唯一可行角色是**"搬运层/读取层"**：读取与并行化交给引擎，写入必须经以下两种回流方式之一——
- **中转表模式**：引擎写 NPDMS 库内的 staging 表（非业务表），应用消费 staging → 接收 API；代价：staging schema 管理 + 双跳写入 + 两段断点。
- **HTTP 回流模式**：引擎以 HTTP sink 调用应用侧薄适配端点（内部包装 `CommerceAuthorityIngestApi.ingestBatch`）。SeaTunnel HTTP sink 支持 POST JSON、批量数组（`array_mode`+`batch_size`）、重试与多表占位符（官方文档 2.3.13，已核实）；Debezium Server 需自研 sink。
两种模式都保留业务校验、证据、问题清单、租户校验在应用内，不违反 C3。

## 5. 存活候选深入分析

### 5.1 Spring Batch 6.0.4（在位方案，基线）

- **接入形态**：应用内嵌，即现状。C1 官方满足（Spring Framework 7 / Boot 4 系统要求 Java 17+，兼容至 JDK 25；spring.io 2025-11/2026-06 发布说明）。
- **6.0 相对 5.x 的相关变化**：`ChunkOrientedStep` 替代 `TaskletStep`、本地多线程 chunking（单机并行能力提升，缓解"大表并行"短板）、`JobOperator.recover` 恢复异常中断执行、容错（SkipPolicy/RetryPolicy）内置、JobRepository 与 JDBC 解耦（`@EnableJdbcJobRepository`）。
- **与既有写入通道衔接**：天然一体——业务校验、证据、问题清单、租户校验、接收 API 都在应用内，无回流问题（C2/C3/C6 全满足，这是外部工具做不到的）。
- **适配工作量**：零（已在运行，DPPMS 订单链路已实战 82,000 源行）。
- **风险**：随 Spring Boot 大版本升级（已随 4.1.0 完成 6.x 迁移，风险已消化）。
- **短板（诚实声明）**：JDBC 读取并行度依赖 `MysqlSyncReader` 现有单游标实现；若未来出现单表千万级以上搬运需求，Spring Batch 6 的本地多线程 chunking 或引入外部引擎是两条升级路径，当前无此规模证据，不预先实现。

### 5.2 Apache SeaTunnel 2.3.13（搬运/CDC 引擎首选，条件性引入）

- **接入形态**：官方镜像容器，Zeta local 单进程模式即可，无需集群（C8 ✓）。
- **能力匹配**：全量 JDBC source 支持主键分片并行、多表、schema/data 保存模式；增量走 MySQL-CDC（内嵌 Debezium 1.9.8，增量快照框架，2.3.12 起官方支持 MySQL 8.4+，2.3.13 支持按时间启动与 schema evolution）；checkpoint/savepoint 断点续传，REST API 管理。
- **与本项目衔接**：HTTP sink（POST JSON webhook，批量数组模式、重试、多表占位符）→ 应用薄适配端点 → `ingestBatch`。中转表模式亦可。
- **适配工作量估算**：容器编排 + 作业配置（每对象一份 HOCON/REST 提交）+ 薄适配端点 + 中转/回流证据打通 + 断点语义对齐（Zeta checkpoint 与 `plt_sync_batch` 水位的职责切分）。属于**新运维单元 + 新集成面**，不是即插即用。
- **不采用 C2 的代价**：字段映射可用 SQL Transform/FieldMapper，但"同业务键冲突进问题清单、不任选一条覆盖"这类规则要么前置到薄适配端点（等于把业务层重写一遍），要么依赖应用接收契约兜底——后者恰恰是本项目已实现的。因此**引入 SeaTunnel 的净收益只剩"并行分片吞吐"与"binlog CDC"两点**。
- **风险**：Zeta 引擎年轻（相对 Flink 生态）；官方 JDK 矩阵 8/11/17，容器内自带 JDK 规避宿主机 25 问题【待确认 21/25 官方声明】。
- **结论**：**条件性候选**。触发条件：出现单表千万级以上搬运、或 S3 割接增量需要且 Debezium Embedded PoC 失败。当前不引入。

### 5.3 Debezium 3.6.2（仅 S3 割接增量场景）

- **接入形态**：首选 Embedded Engine（`debezium-embedded` 构件，无 Spring 依赖），嵌入 Spring Boot 4.1 应用进程；备选 Debezium Server（独立 Quarkus 进程）。Kafka Connect 形态违反 C5，不用。
- **能力匹配**：initial snapshot + blocking snapshot、binlog 增量（官方 tested 含 MySQL 8.4/9.x）；offset 与 schema history 可存 MySQL（`JdbcOffsetBackingStore`/`JdbcSchemaHistory`，2.5 起），与应用共用基础设施，无新增部署单元（Embedded 形态）。
- **与本项目衔接**：事件消费回调内直接调用 `CommerceAuthorityIngestApi.ingestBatch` 或按事件类型路由到对应接收服务——**Debezium 负责定位"哪些行变了"，业务事实仍由应用形成**，C2/C3/C6 不受影响。
- **适配工作量估算**：依赖共存验证（kafka-clients、Jackson 2 与 Boot 4.1 Jackson 3 的包名隔离理论可行，需实测）、connector 配置（dppms 库白名单表）、offset 存储、事件→ingest 映射、重复消费幂等（ingest API 已保证）。**1~2 天级 PoC 可验证可行性**。
- **风险**：Embedded 在 JDK 25 的官方测试矩阵未列出（下限 17+ 满足）【待确认】；binlog 需要源库开启 ROW 格式与 REPLICA 权限（dppms 源库权限需确认）。
- **结论**：**S3 唯一推荐候选**，前提是需求方确认割接窗口不允许短时停机（否则全量重跑 + ingest 幂等更简单）。

### 5.4 Apache Hop 2.19（二线对照深评，不推荐主链）

- 能力与运维：轻量（1C/4G）、hop-run/hop-server 无头运行 + REST、官方镜像、季度发版；但无原生 binlog CDC、无框架级断点，幂等仅靠插入更新步骤。
- 对本项目的意义：字段映射画布对"探索性一次性导数"（非契约迁移）有易用性价值，但本项目的一次性迁移全部是契约迁移（AI-MIG-000 批次、逐对象策略、对账），画布无法承载，且新增引擎不减少任何 PRD §13.2.4 责任。**不推荐引入**；其价值定位与"集成工作台模板"重叠且弱于后者。

### 5.5 对照项一句话结论

- **CloudCanal 社区版**：功能覆盖最全（全量+增量+DDL+校验订正开箱即用），但闭源许可证续期制、社区版 5 任务/500 表/单用户/无 HA、官网标注适用测试环境，且直写目标库违反 C3——**不作为主链，可在隔离测试环境作对账参照工具**（可选，非必须）。
- **Tapdata**：开源线 2025-04 起约 17 个月无新版——**不推荐**。
- **NiFi / DBSyncer / Addax**：能力与在位方案重叠或为真子集，各自带重量级运维或小社区风险——**不推荐**。

## 6. 与在位自建方案的差距对比

| 维度 | 在位集成工作台（Spring Batch 6 + 业务层） | SeaTunnel（若引入作搬运层） | Debezium Embedded（若引入作 CDC） |
|---|---|---|---|
| 逐对象迁移策略/禁止推断 | ✓ 原生（契约代码） | ✗ 需回流应用 | ✗ 需回流应用 |
| 迁移证据/问题清单 | ✓ `pms_migration_source_record`/`pms_migration_issue` | ✗ 引擎侧无业务证据 | ✗ 同左 |
| 来源键映射/水位 | ✓ `plt_external_key_mapping`/`plt_sync_batch` | ○ 需另行打通 | ○ offset 存 MySQL（机制不同，需职责切分） |
| 写入通道 | ✓ 接收 API + CAS + 全批回滚 | ○ 经 HTTP 回流或中转表 | ○ 事件回调内调用接收 API |
| 多租户 | ✓ 应用层强制 | ✗ 引擎无概念 | ✗ 引擎无概念 |
| 断点续传 | ✓ 分页游标 + Spring Batch 重启 | ✓ checkpoint（引擎级） | ✓ offset（CDC 级） |
| 并行分片吞吐 | ○ 单游标；Batch 6 支持本地多线程（未启用） | ✓ splitPk 并行（核心优势） | —（CDC 非吞吐场景） |
| binlog 增量 | ✗（时间增量走原路径，无 CDC） | ✓ MySQL-CDC | ✓（核心优势，官方 tested 8.4） |
| 部署单元 | 无新增 | +1 容器引擎 | +0（嵌入）/ +1（Server） |
| 新增开发面 | 无 | 薄适配端点 + 作业配置 + 证据打通 | 依赖共存 + 事件映射 |
| 实战验证 | 82,000 源行（DPPMS 订单） | 无（本项目内） | 无 |

## 7. 分场景推荐与不采用项

### S1 一次性历史迁移（dppms / 旧权限库 → 66 表核心子集）

- **推荐：维持现状**——集成工作台（Spring Batch 6 + 业务层）。迁移程序已存在且经过实战与回归（DPPMS 订单模板、自动分页、断点重试、证据与问题清单、事务回滚均有测试与真实浏览器验收记录）。
- **条件性升级路径**（仅当出现单表千万级以上、或预演实测窗口不可接受时启动）：SeaTunnel 2.3.13 容器作搬运层，HTTP 回流或中转表落地，`plt_sync_batch` 水位与 Zeta checkpoint 职责切分需在启动前定义为该场景的 Feature 契约。
- **不采用**：Hop（能力弱于工作台且无 CDC）；Addax/DataX（无断点无增量，JDK 隔离代价，能力为工作台子集）；CloudCanal（闭源许可 + 任务限制 + 直写违反 C3）；Flink CDC（Flink 集群代价不成比例）。
- 理由核心：C2/C3/C6 是迁移的**主体工作量与风险所在**（SDS 08a 逐对象契约、PRD §13.2.4 多维度对账与回退），搬运层只占其中小部分，而搬运层在位实现已够用。

### S2 持续外部系统同步（INT-01～INT-12）

- **推荐：维持现状**——按 SDS 12 通用处理链的适配器 + 接收 API + Outbox/Redis + Quartz 调度；配置注册表按 Q-P3-007 已裁决的方案 B（平台级统一配置注册表，Feature 引用不可变版本）推进。
- **不采用一切 ETL/CDC 工具**，结构性理由：14 个外部系统中数据库直连型来源为少数，CRM/ERP/钉钉/HR/OA/UMC/授权/财务/采集平台均为 **API/回调型来源**，带字段级操作契约（SDS 12 §4.1）、来源版本判重、乱序不回退终态（F-INT-012/F-CUS-001）。CDC 工具只解决"数据库行变更捕获"，无法处理协议验签、字典待映射、业务确认语义（协议成功 ≠ 业务完成）。
- 备忘：若未来某个集成是纯"库到库镜像"（如某外部系统开放只读库），SeaTunnel CDC + HTTP 回流是届时再评估的选项；当前 14 系统无此类形态。

### S3 割接窗口 / 增量追赶（唯一值得引入现成组件的场景）

- **推荐（有条件）：Debezium 3.6.x Embedded**——嵌入应用、offset 存 MySQL、事件回调内调用接收 API；先 PoC 验证依赖共存与 JDK 25（第 9 章 P1）。
- **备选**：SeaTunnel MySQL-CDC 容器 + HTTP 回流（若 Embedded PoC 失败）。
- **触发前提**：需求方确认割接窗口不可短时停机、且增量窗口长到值得维护 CDC 链路（Q-REL-006 相关范围确认的前置输入）。若可停机窗口存在，**全量重跑 + ingest 幂等（OBJECT_REPLAY / ACCEPTED_NO_CHANGE）是更简单的路径**，不需要任何新组件。
- **不采用**：Canal（停滞 + JDK）；Flink CDC（过重）；Kafka Connect（C5）。

### S4 对 Q-REL-006（首发是否执行历史数据迁移）的决策输入

仅列材料，不替需求方裁决，不改变 Q-REL-006 状态：

| 维度 | 方案 A：不执行迁移 | 方案 B：执行迁移（在位工具链） | 方案 C：执行迁移（引入外部引擎） |
|---|---|---|---|
| 迁移程序成本 | 无 | 逐对象契约实现（SDS 08a）+ 工作台模板配置；DPPMS 订单已有先例 | 同左 + 引擎运维 + 薄适配/中转开发 + 证据链打通 |
| 对账责任（PRD §13.2.4-3） | 不适用（但历史事实保存义务 AI-MIG-000 仍需裁决） | 需求方指定业务对账人，按 9 类维度对账 | **同左，不因引入工具减少** |
| 回退演练（PRD §13.2.4-4） | 简化 | 需演练 | **同左，且新增引擎故障面** |
| 证据完整性 | — | 来源记录/问题清单/来源键映射原生齐全 | 引擎侧无业务证据，依赖回流完整性 |
| 主要风险 | 首发后历史依赖靠旧系统只读 | 执行人力与窗口 | 工具引入不解决任何 B 的风险，反增运维面 |

**结论：工具选型不是 Q-REL-006 的关键变量。** 该问题的决定因素是业务范围（迁移哪些对象、业务对账人、窗口）——与 open-questions Q-REL-006 已登记的待决事项一致。若需求方选择执行迁移，在位工具链（方案 B）已是成本与风险最低路径。

## 8. 回答用户原始问题的直接表述

"数据迁移和同步有没有现成的解决方案？"

- **有，而且分两种"现成"**：①**框架级现成**——项目已经采用 Spring Batch 6（业界标准批处理框架，官方兼容 JDK 25）作为内核，这本身就是在用现成方案；②**产品级现成**——SeaTunnel/Debezium/CloudCanal 等完整产品存在且成熟，但它们只覆盖"数据搬运"，不覆盖本项目的迁移契约（策略代码/证据/接收 API/租户/对账），整体替换会**丢失**而非获得能力。
- **没有现成方案的部分**：业务规则层与证据层——这层没有、也不应该有通用产品（它是本项目 PRD/SDS 定义的差异化契约），且已经自建完成并有实战验证。
- **建议引入现成组件的唯一场景**：割接窗口 binlog 增量追赶（Debezium Embedded，先 PoC）；次选大表并行搬运（SeaTunnel 容器）。

## 9. 可选后续 PoC 最小验证清单（本次不执行）

| 编号 | 内容 | 验证点 | 触发条件 |
|---|---|---|---|
| P1 | Debezium 3.6.x Embedded + Spring Boot 4.1.0/JDK 25 | 依赖共存（kafka-clients/Jackson 包隔离）、offset 存 MySQL、dppms 表变更→事件→接收 API 映射、延迟与 DDL 事件处理 | 需求方确认割接不可短停机（S3 成立） |
| P2 | SeaTunnel 2.3.13 Zeta local 容器 | MySQL-CDC/JDBC source 连通、HTTP sink → 薄适配端点吞吐、checkpoint 与水位职责切分 | 大表并行需求出现，或 P1 失败后的备选验证 |
| P3 | 工作台 `MysqlSyncReader` vs Spring Batch 6 本地多线程 chunking 吞吐对比 | 单表千万级模拟数据的窗口时长 | S1 预演实测窗口不可接受时（比 P2 更轻，优先） |

## 10. 参考资料清单（均于 2026-09-15 访问）

**项目内权威文档**
- PRD：docs/baseline/prd-v1.8.md §12.3、§13.2.4（L8937-8945）、§3.4（L732）
- SDS：docs/design/08a-domain-entity-migration-alignment.md（§2 策略代码）、docs/design/09-database-design.md（plt_external_key_mapping L409 / plt_sync_batch L417）、docs/design/12-integration-design.md（§1-§4.1）
- 附录：specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md
- Feature：specs/features/F-CUS-001-customer-master-and-local-lifecycle.md §2.2、F-INT-012
- 决策：docs/decisions/open-questions.md（Q-REL-006 / Q-P3-008 / Q-FCUS-001 / Q-P3-007）
- 实证：docs/generated/dppms-order-migration-template-2026-09-14.md；pms-module-integration/pom.xml（spring-batch-core）；根 pom.xml（java.version=25、spring.boot.version=4.1.0）

**外部事实来源**
- Spring Batch 6.0.4 & 5.2.6 发布（2026-06-10）：https://spring.io/blog/2026/06/10/spring-batch-6-0-4-and-5-2-6-available-now/
- Spring Batch 6 发布说明（2025-07-23）：https://spring.io/blog/2025/07/23/spring-batch-6/
- Spring Batch 6.0 What's New：https://docs.spring.io/spring-batch/reference/whatsnew.html
- Spring Boot 4.1 系统要求（Java 17+，兼容 25/26）：https://docs.spring.io/spring-boot/system-requirements.html
- Spring Framework 7.0 GA（2025-11-13）：https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability/
- SeaTunnel 下载页（2.3.13，2026-03-12）：https://seatunnel.apache.org/download
- SeaTunnel 2.3.13 release：https://github.com/apache/seatunnel/releases/tag/2.3.13
- SeaTunnel MySQL-CDC 文档（内嵌 Debezium 1.9.8）：https://seatunnel.apache.org/docs/2.3.13/connectors/source/MySQL-CDC/
- SeaTunnel 2.3.12 release（PR #9720 MySQL 8.4+）：https://github.com/apache/seatunnel/releases/tag/2.3.12
- SeaTunnel HTTP sink 文档（array_mode/batch_size/重试/多表占位符，本轮直接核实）：https://seatunnel.apache.org/docs/2.3.13/connectors/sink/Http
- SeaTunnel SQL Transform：https://seatunnel.apache.org/docs/2.3.13/transform-v2/sql
- Debezium MySQL connector Maven metadata（3.6.2.Final，2026-09-01）：https://repo1.maven.org/maven2/io/debezium/debezium-connector-mysql/maven-metadata.xml
- Debezium Server 文档（无官方 JDBC sink；独立 Quarkus 进程）：https://debezium.io/documentation/reference/3.6/operations/debezium-server.html
- Debezium JDBC offset 存储（2.5 起 JdbcOffsetBackingStore）：https://javadoc.io/doc/io.debezium/debezium-storage-jdbc/2.5.0.Beta1/index.html
- Apache Hop 下载页（2.19.0，2026-08-17）：https://hop.apache.org/download/
- Hop 支持的 JVM（官方验证 Java 21）：https://hop.apache.org/manual/latest/supported-jvms.html
- Apache NiFi 下载页（2.12.0，2026-09-13；1.28.1 为 1.x 末版）：https://nifi.apache.org/download/
- NiFi 系统要求（Java 21）：https://nifi.apache.org/nifi-docs/administration-guide.html#system_requirements
- NiFi CaptureChangeMySQL：https://nifi.apache.org/components/org.apache.nifi.cdc.mysql.processors.CaptureChangeMySQL/
- Flink CDC 3.6.0 发布公告（2026-03-30）：https://flink.apache.org/2026/03/30/apache-flink-cdc-3.6.0-release-announcement/
- Flink CDC pipeline connectors 总览（sink 不含 MySQL）：https://nightlies.apache.org/flink/flink-cdc-docs-release-3.6/docs/connectors/pipeline-connectors/overview/
- DataX 官方仓库与 releases（v202309 后停滞）：https://github.com/alibaba/DataX 、https://github.com/alibaba/DataX/releases
- Addax（DataX 活跃分支，6.0.13）：https://github.com/wgzhao/Addax 、https://mvnrepository.com/artifact/com.wgzhao.addax/mysqlwriter/versions
- Canal 1.1.8 release（2025-01-16）：https://github.com/alibaba/canal/releases/tag/canal-1.1.8
- Canal master pom（JDK 1.8）：https://raw.githubusercontent.com/alibaba/canal/master/pom.xml
- Kettle CE 停更与 Hop 迁移讨论：https://analytics.axxonet.com/comparison-of-and-migrating-from-pdi-kettle-to-apache-hop/
- kettle-core 9.4 最后 Maven 发版（2023-10-27）：https://mvnrepository.com/artifact/pentaho-kettle/kettle-core/9.4.0.0-32/used-by
- DBSyncer 仓库与 releases（v2.1.4，2026-08-13）：https://gitee.com/ghi/dbsyncer 、https://gitee.com/ghi/dbsyncer/releases
- CloudCanal 定价与社区版限制（5 任务/500 表）：https://www.clougence.com/pricing 、https://www.clougence.com/docs/price/product_price
- CloudCanal 校验与订正：https://www.clougence.com/docs/bestPractice/verification_and_correction
- Tapdata 开源仓库 releases（v3.27.0，2025-04-14）：https://github.com/tapdata/tapdata/releases
- MySQL Connector/J 9 与 Server 8.4 LTS 兼容矩阵：https://www.mysql.com/it/support/supportedplatforms/compatibility.html 、https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-0-0.html
- DataPipeline（商业闭源，列名）：https://www.datapipeline.com/productDetail/product

**第 12 章增补来源（无主键专项，均于 2026-09-15 访问）**
- CloudCanal MySQL→MySQL 链路说明（无主键 UPDATE/DELETE 需手动勾选）：https://www.clougence.com/docs/dataMigrationAndSync/connection/mysql2
- CloudCanal 校验与订正（无主键表默认忽略校验、无法订正）：https://www.clougence.com/docs/bestPractice/verification_and_correction
- CloudCanal release notes（4.7.0.0 唯一键自动作对端主键；4.4.2.0 无主键 UPDATE/DELETE 空数据修复；4.3.0.0 仅 Hana 源 $rowid$ 断点）：https://www.clougence.com/docs/releaseNote/rn-cloudcanal-4-7-0-0
- Tapdata 无主键表处理（全字段匹配、更新条件列、增量并发互斥）：https://docs.tapdata.net/faq/data-pipeline/
- Tapdata 数据同步最佳实践（重复行整批统一更新/删除）：https://docs.tapdata.net/case-practices/best-practice/data-sync/
- SeaTunnel FAQ（CDC 不支持无主键表原话）：https://seatunnel.apache.org/docs/2.3.13/faq
- SeaTunnel JDBC source（无主键/无唯一索引且未设 partition_column 时单并行）：https://seatunnel.apache.org/docs/2.3.13/connectors/source/Jdbc
- SeaTunnel JDBC sink（upsert 依赖 primary_keys 声明）：https://seatunnel.apache.org/docs/2.3.13/connectors/sink/Jdbc
- SeaTunnel Web 仓库与 1.0.2 release（2024-10-24，配套引擎 2.3.8）：https://github.com/apache/seatunnel-web 、https://github.com/apache/seatunnel-web/releases/tag/1.0.2
- Debezium MySQL connector 文档（无主键表快照/事件 key/message.key.columns）：https://debezium.io/documentation/reference/3.6/connectors/mysql.html
- Debezium UI 归档声明（Kafka Connect UI 不再开发）：https://github.com/debezium/debezium-ui
- Debezium 设计文档 DDD-3（快照顺序处理、并行化为非目标）：https://github.com/debezium/debezium-design-documents/blob/main/DDD-3.md

## 11. 【待确认】事项汇总

| 编号 | 事项 | 影响 | 建议澄清方式 |
|---|---|---|---|
| Q-R1 | SeaTunnel Zeta 对 JDK 21/25 的官方支持声明（当前官方矩阵 8/11/17，社区有 JDK 17 实测） | S1/S3 备选路径的容器内 JDK 选型 | 查 2.3.x 邮件列表/issue；或 P2 PoC 实测 |
| Q-R2 | Debezium Embedded 3.6.x 在 JDK 25 + Spring Boot 4.1（Jackson 3）下的官方验证与依赖共存 | S3 首选路径可行性 | P1 PoC 实测（官方下限 Java 17+ 已满足） |
| Q-R3 | NiFi/Hop 对 JDK 25 的支持（官方仅验证 21） | 二线候选（已不推荐主链，影响有限） | 如需启用再验证 |
| Q-R4 | DBSyncer 幂等语义的官方文档化说明 | 二线观察项成熟度评估 | 已不推荐主链，不阻塞 |
| Q-R5 | dppms 源库是否可开启 ROW binlog 并授予 REPLICA 权限 | S3 Debezium/SeaTunnel CDC 路径的前提 | 与 DPPMS 系统技术 Owner 确认（属外部集成登记事项，SDS 12 §2） |
| Q-R6 | ~~spring-batch-core 解析版本 6.0.4 系第三方依赖页佐证~~ **已确认**：本地 Maven 仓库 `D:\Maven\Repository\org\springframework\batch\spring-batch-core\6.0.4`（2026-09-15 复核） | 已关闭 | — |
| Q-R7 | **"无主键"来源表是否实际具备唯一索引或可组合的稳定列**（决定 CloudCanal UPDATE/DELETE 自动支持、Tapdata 更新条件列、SeaTunnel CDC primaryKeys 自定义是否可用） | 第 12 章方案可行性；无唯一键时无主键增量存在原理性边界 | 向数据 Owner 逐表确认索引情况；这是业务事实确认，不由工具选型代替 |
| Q-R8 | SeaTunnel MySQL-CDC 官方文档支持版本列仅到 8.0.x，而 2.3.12 release note（PR #9720）称支持 MySQL 8.4+——文档页与 release note 存在表述差异 | 第 12 章 SeaTunnel CDC 路径的 8.4 兼容结论 | 以实际连接测试为准（若走 P2） |

---

## 12. 增补评估：无主键 + 零开发界面配置约束（2026-09-15 追加）

> 本章针对追加约束独立成节，不推翻第 7 章结论（不同约束集下的不同答案）。追加约束：N1 来源表可能无主键；N2 全表同步；N3 零开发、纯 Web 界面配置；N4 全量+增量都要；N5 单表约 500 万行（中小规模，无并行分片刚需）。

### 12.1 在位工作台对无主键表的支持（代码证据）

现有集成工作台**不支持无主键来源表**，这是代码强制而非配置缺失：
- [MysqlSyncReader.java:84-89](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/MysqlSyncReader.java#L84-L89)：`自动分页要求正整数 Long 来源主键`（游标分页按 `id > cursor` 推进）；
- [SyncFieldMapper.java:55](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/SyncFieldMapper.java#L55)：`目标主键要求无损的正整数 Long，来源主键无效`；
- [MysqlSyncReader.java:74/145](file:///c:/Users/user/.trae-cn/worktrees/NPDMS/migration-sync-research-f36p0S/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/MysqlSyncReader.java#L74-L145)：来源主键缺失或重复直接抛异常。

因此**在"零开发"约束（N3）下，在位工作台出局**——同步无主键表需要为工作台开发新的读取/映射模式。这构成一个明确的权衡点：要么引入外部工具接受其合规性缺口，要么为工作台开发无主键支持（开发量：MysqlSyncReader 增加无主键/唯一键分页策略 + SyncFieldMapper 放宽主键断言 + 测试，属中小型改动，但违反 N3）。

### 12.2 候选工具无主键行为矩阵（官方文档核实）

| 工具 | 无主键·全量 | 无主键·增量（目标写入策略） | 纯界面配置全量+增量 | 判定 |
|---|---|---|---|---|
| **CloudCanal 社区版** | ✓ 支持（逻辑迁移顺序扫描；MySQL 源断点续传【官方未明确】） | **默认仅同步 INSERT**；UPDATE/DELETE 需任务中手动勾选；有唯一键的表 4.7.0.0 起自动以第一个唯一键为对端主键并自动勾选；**无主键表不参与校验与订正任务** | ✓ 纯 Web 创建任务（选库→勾"数据同步+全量数据初始化"→选表列） | **唯一同时满足 N1~N5 的方案**（代价见 12.4） |
| Tapdata 开源版 | ✓ 支持 | 全字段匹配定位行（官方明示高并发增量场景慢）；可在 UI 手动指定列组合作"更新条件列"缓解；同内容重复行整批统一更新/删除；**增量并发与无主键表互斥** | ✓ 拖拽式 UI + 全量/增量自动切换 | 功能透明度最高，但**开源线停更约 17 个月**，风险自担 |
| DBSyncer v2.1.4 | 【官方未明确】 | 【官方未明确】（binlog 行级增量确认，但无主键写入策略无文档） | ✓ Web 控制台配置表映射 | 无主键行为需自行验证，社区规模小 |
| SeaTunnel 2.3.13 | ✓ JDBC source 无主键自动降级单并行（官方 tips 原话） | ✗ **CDC 对无主键表官方明确不支持**（FAQ 原话：无法定位重复行的删改；`table-names-config.primaryKeys` 可手工指定主键列，前提是表有可用唯一列） | ✗ 引擎配置为 HOCON/脚本；**SeaTunnel Web 1.0.2 停更于 2024-10、官方配套 2.3.8，无 2.3.13 配套** | N3 不成立；N1 增量被官方否决 |
| Debezium 3.6.2 | ✓ initial snapshot 支持无主键表（顺序扫描）；binlog 流式可行（事件 key 取唯一键或为空） | ◐ 增量快照按主键分块，无主键表行为【官方未明确】（支持 surrogate-key 信号指定） | ✗ **无官方任务管理 UI**（debezium-ui 已归档；Debezium Platform 仅管理 Debezium Server）且无 JDBC sink | N3/N4 不成立，出局 |
| NiFi 2.12 | ◐ QueryDatabaseTable 要求 max-value 列；CDC 读可行 | ◐ PutDatabaseRecord 的 UPDATE 需要主键 | ◐ 流式画布属"流编程"而非业务配置，且运维重 | 不推荐（同第 7 章） |

### 12.3 原理性边界（不因选型消失）

无主键且无唯一键的表做增量同步，存在所有工具共有的原理性约束：binlog 行事件不携带行唯一标识，目标端只能①全列 WHERE 匹配（慢，且 NULL 比较语义各库有差异）、②对内容相同的重复行整批更新/删除（Tapdata 行为，语义须业务确认）、③仅追加 INSERT（CloudCanal 默认）。**任何工具都无法凭空创造行唯一性**。因此选型前必须先确认 Q-R7（这些表是否有唯一键/稳定列组）；若完全没有，"增量同步"的目标端语义本身需要数据 Owner 定义，属于业务契约缺失，不是工具缺陷。

另注：单表 500 万行全量重跑为分钟级成本，若增量仅用于小范围追赶且接受分钟级延迟，"定时全量 + 唯一键 UPSERT"是更简单的替代形态（前提同样是表有唯一键）——是否接受该替代由需求方裁决。

### 12.4 结论（新约束集下）

- **推荐：CloudCanal 社区版**——唯一同时满足 N1~N5 的现成方案。须接受的代价：①闭源免费许可，许可证约 3 个月续期；②社区版 5 任务/500 表/单用户/无 HA，官网标注适用测试环境（本场景单表 500 万、表数有限，任务数限制需按实际表数量核算）；③无主键表不参与其内置校验订正——**对账责任回到需求方**（PRD §13.2.4 的多维度对账不因工具内置校验缺失而免除）；④无主键表 UPDATE/DELETE 需勾选开启，且匹配策略官方未明确，上线前必须实测。
- **对照：Tapdata 开源版**——无主键策略文档最透明，但停更风险使其不适合作为依赖项。
- **不采用**：SeaTunnel（CDC 无主键官方否决 + 无可用 Web 控制台）、Debezium（无 UI 无 sink）、DBSyncer（无主键行为无文档）、在位工作台（N3 下需开发；若需求方允许中小型开发，工作台仍是合规性最优路径，见 12.1）。
- 本场景与第 7 章 S1/S2 结论的关系：若需求方为无主键表同步选择 CloudCanal，该工具直写目标库，**绕过接收 API 与证据体系（C3/C6 缺口）**——作为对既有迁移契约的例外，需要按工程链明确该批次的 Owner、对账与审计责任边界，不能静默引入。
