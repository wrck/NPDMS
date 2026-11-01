# 正式部署迁移流程双包隔离预演与差异复核（2026-11-01）

结论：**两套包在补齐迁移器驱动及受控触发器创建权限后，均可完成空库迁移，但不能原样作为当前代码的完整正式部署包。**
建议以 `production-178-v530-r2` 的正式配置和排除策略为底座，补入经来源核实的分支结构增量，形成新的冻结发布版本；不覆盖这两套既有文件，不直接拼接两个 B530，也不恢复已退役镜像表。

本次用户明确授权“两套都在隔离空库预演，比对差异和缺失内容”。应用源码锁定 `77bc691cbc5ad1e58bb51252f2a31dd5047fd6d6`；正式服务器、178、179、59191、48182均未操作。

## 1. 实际演练边界与结果

- 独立 Compose 项目 `npdms-rehearsal-20261101-202857`，独立数据卷、网络与回环随机端口，不复用现有业务库。
- MySQL **8.4.13**；启用 binlog、ROW、`sync_binlog=1`、`innodb_flush_log_at_trx_commit=1`、强制安全传输，保持 `log_bin_trust_function_creators=0`。
- 复用正式安装所用 **Flyway 11.10.5 Linux 原生发行包**，在本地 Ubuntu 系运行环境中执行其 CLI；新增显式 MySQL Connector/J **9.7.0**。发行包及驱动 SHA-256 见 [result.json](attempt-202857/result.json)。本地容器仅承载隔离基础设施与迁移工具，未承载业务应用。
- 独立 CA 与 SAN 证书，连接使用 `sslMode=VERIFY_IDENTITY`；正确主机名连接成功，错误主机名实测因证书身份不匹配拒绝。本次提供的密码、TLS私钥仅置于隔离 tmpfs，不写入报告或工作区。MySQL默认另生成的本地RSA密钥对已在停库后从本次专用卷移除，数据库文件保持；见[秘密清理记录](secret-cleanup.json)。该自动生成行为与正式模板关闭RSA自动生成不同，后续同构演练应同步关闭。
- 独立迁移账号、独立主库/DAC数据库；先验证库级权限创建触发器失败，再仅对隔离主库迁移账号临时授予所需权限。结束已撤销该权限，未关闭 binlog 或开启触发器信任开关。
- 执行冻结清单校验、复制发布目录、复制后逐文件 SHA-256 校验、`current` 符号链接切换，再使用正式 `operate.sh` 对应的 Flyway 强制参数执行 `info → migrate → validate → info → migrate`。
- 两主库均首次迁移成功，迁移后 validate 成功，重复 migrate 的**完整 Flyway 历史行保持不变**；均完成285组外键孤儿检查（0项），已发布割接配置删除被实际触发器拒绝；非空目标拒绝且标记数据保持、未新增业务表；篡改副本在 Flyway 前被 `release.py` 拒绝。
- DAC共13份迁移到 **V26**，24张业务表，首次迁移/validate/info/重复迁移成功，历史保持不变。
- 全部本次隔离项目已停止；数据库卷和原始证据保留。没有执行生产数据库迁移、应用发布、服务重启或Git提交/推送。

**验证限度**：没有执行正式主机的IP保护、systemd、挂载、SSH、目标机秘密恢复及备份恢复；未运行应用或真实浏览器业务验收。Linux原生CLI在隔离环境成功不等于正式服务器部署成功。发布复制/链接操作与强制迁移参数已覆盖，未伪装成完整执行 `operate.sh` 的目标主机检查。

## 2. 配置与数据差异

以下数量来自本次新库实查，不沿用旧报告代替执行。表/字段数量不含Flyway自身表。

| 核对项 | fresh-178-20261101 | production-178-v530-r2 |
|---|---:|---:|
| 表 / 字段 | 527 / 9644 | 518 / 9458 |
| 触发器 / 外键 | 240 / 285 | 240 / 285 |
| 初始化记录 | 7374 | 6026 |
| 项目模板 | 1套验收来源草稿 | 5套启用的正式模板 |
| 已发布项目模板修订 | 0 | 10；每份快照均覆盖S0～S6 |
| 流程定义 / 流程模型 / 静态资源 | 0 / 0 / 0 | 11 / 7 / 23 |
| 文档模板 / 版本 | 0 / 0 | 6 / 6 |
| 公司 / 部门 | 0 / 0 | 4 / 206 |
| 业务视图修订 / 动态表单修订 | 15 / 26 | 17 / 27 |
| 正式种子策略 | 失败：25个上游示例菜单 | 通过 |
| 菜单父引用缺失 | 4条 | 0条 |
| 其他现有逻辑引用检查 | 未发现其他违反项 | 通过 |

整合包唯一模板为 `FCV4_SOURCE_3D5C63BD453D`，模板与修订均为DRAFT；不能因为保留Flowable表结构就视作具有正式流程和模板。
正式包的5套模板为普通直签、工程直签、普通非直签、工程非直签及非直签原厂督导，V1/V2共10个发布修订；冻结快照中的显式表单、业务视图、流程引用未发现缺失。旧快照保留原JSON，不要求为本次复核回填零行的旧拆分定义表。

角色有效权限对按“启用菜单、未删除角色/关联”去重比较：正式包没有整合包缺失的权限对；整合包额外包含普通角色的 `bpm:oa-leave:create`、`bpm:oa-leave:query`，属于示例权限。不能以菜单/关联总数差异直接认定人员被少授权，且实际人员授权尚未配置。

两包割接配置存在相同ID但不同版本状态：`996610195020086` 在整合包中为DRAFT，在正式包中为PUBLISHED。不得直接合并种子或覆盖不可变配置。其余差异与明确记录见 [configuration-details.json](attempt-202857/configuration-details.json)、[comparison.json](attempt-202857/comparison.json)。

## 3. 正式包缺少的分支结构

整合包比正式包多9张表，其中8张有当前源码或历史证据读取路径，另1张是正式包已明确排除的退役表，不能全量搬回。

| 应逐项补齐来源核验的8张表 | 已有依据 |
|---|---|
| `ast_device_warranty_change_record` | 当前维保来源记录服务及实体 |
| `ast_device_warranty_fact_project` | 当前维保事实项目关系实体 |
| `ast_device_warranty_registration` | 当前维保登记来源服务及实体 |
| `ast_device_warranty_service_record` | 当前维保服务来源服务及实体 |
| `int_sync_project_scope_member` | `PublishedProjectScopeMapper.xml`、V482同步范围发布迁移 |
| `int_sync_project_scope_publication` | 同上；当前发布范围读取/写入路径 |
| `proj_legacy_detail_record` | `ProjectLegacyDetailMapper.xml`、V411来源证据迁移 |
| `proj_legacy_detail_snapshot` | 同上；来源快照读取/保存路径 |

`sol_engineering_briefing_retired`：正式包来源清单已明确排除；不得为追求527表的数量而恢复。

共同表还相差**39个字段**：设备网络版本事件1个、维保记录31个、客户联系关系5个、需求分析修订2个生成字段。8张新增表自身有118个字段，与上述39个合计157个源码新增字段；不包括退役表29个字段。

另有：

- `com_project_contract_relation.creator/updater`、`cus_project_customer_contact_relation.customer_id/customer_contact_id` 共4个字段的可空性不同，整合包为可空；必须沿已批准历史来源规则，不可仅凭178快照覆盖。
- 需求分析修订的 `uk_ra_draft`、`uk_ra_project_revision`、`uk_ra_revision` 三个唯一索引不同，整合包使用active生成字段；正式包缺这部分既有源码迁移语义。
- 正式包缺 `plt_migration_source_record.idx_plt_migration_global_source_identity` 索引。
- 整合包统一utf8mb4，保留二进制PAD/NO PAD区分；正式包保留来源字符集，仍含451个utf8mb3_bin字段及948个utf8mb4_0900_ai_ci字段。新整合版本须明确采用的字符集方案并复核唯一性，不能静默变换已有历史。

详细字段、索引和数量差异见 [comparison.json](attempt-202857/comparison.json)。原迁移版本存在重复，不能将整个 `sql/migrations` 目录直接追加或原样重放。

## 4. 自动实体扫描告警的人工裁决

扫描421个直接持久化实体及15个同步目标：整合包出现3组DO映射告警，正式包10组；两包同步目标列检查均无缺口。这不是“缺表数量”，也不是应用验收。

共同3组必须与旧路径区分：

1. `ast_device_software_observation` 已由批准的软件历史方案整理为 `ast_device_software_version_record`。引用旧表的是一次性旧V4纠正路径；[F-AST-001](../../../tasks/features/F-AST-001.md)明确该迁移边界，不应给新空库重建旧观察表。
2. `com_project_contract_history` 属于保留的旧导入类。当前全库源码未发现 `DppmsContractHistoryImportApi` 的生产调用方；[已批准历史关系规则](../../decisions/open-questions.md#q-legacy-contract-historical-owner-20261012-007--历史项目合同关系的既有17列业务规则)要求复用 `com_project_contract_relation`，不新增镜像表。
3. `ContractReceivableDO` 中8个旧来源字段仍被扫描到；当前正式迁移Mapper使用显式列，普通项目回款读取使用既有关系表及 `SELECT f.*`。未找到当前入口依赖旧历史导入API的证据，不能据DO字段给正式表追加8个镜像字段。

因此共同3组列为**旧代码映射/校验器范围问题**，不直接列为新库必须补建的结构；本次不删除代码、不降低规则、不将旧类不一致写成通过。正式包其余7组为当前维保/客户/网络版本实体的明确缺口；另外还须补上DO扫描未覆盖的同步范围和来源证据表。

[formal-source-audit.json](attempt-202857/formal-source-audit.json) 的原始 `integrityPassed=false` 保留：10项错误均为上述实体映射告警；来源DDL、触发器、种子数量/摘要、FK及显式引用没有新增错误。不能引用历史“402实体通过”替代当前421实体的检查结果。

## 5. 正式部署流程发现

### 5.1 冻结文件字节不一致

正式包清单期望SHA-256：`cd4b480a9f41a6a94a239bd8ced5c3cec21ab0de9cb5d927fca262375034a0e1`。
当前工作树SQL为 `a23d5124f76a1cbfad9e13cc95712951b0c7baecd2ba7cdcfaf118bd559d29b0`，Git blob为 `52a34f8f53b922c132ee9a54dd7119d170edb9188821ff5184c587224c38a05c`，均不匹配。

原工作树 `E:/AICoding/Projects/NPDMS/sql/releases/production-178-v530-r2/migrations/B530__production_178_initialization.sql` 与清单完全匹配；原件混合13个CRLF，其余LF，当前Git换行转换改变了字节。统一换行后内容相等。
本次仅将**与来源清单匹配的原件**复制到隔离发布目录演练，未修改仓库原件或来源清单。因此当前仓库包的发布门禁仍不通过；后续须恢复已验证字节并限制该冻结包的换行转换，不能重算清单掩盖变化。

### 5.2 JDBC驱动与TLS参数

原生Flyway发行包含 `mariadb-java-client-2.7.11.jar`，没有MySQL Connector/J。按现有MySQL URL参数试连先得到RSA公钥错误，未完成预期TLS身份验证；该次失败不能当作错误主机名拒绝证据。

随后显式加载本地项目已使用的 `mysql-connector-j-9.7.0.jar`，指定 `com.mysql.cj.jdbc.Driver`，保持VERIFY_IDENTITY，正确主机名成功、错误主机名因SAN不匹配拒绝。正式发行工具需要纳入经过审核的驱动及其摘要/类名；仅安装Flyway发行包还不够。
官方说明：Flyway缺少MySQL驱动时可回退MariaDB驱动，驱动各自SSL参数不同。[Flyway MySQL文档](https://documentation.red-gate.com/fd/mysql-277579322.html)、[MariaDB TLS参数](https://mariadb.com/docs/connectors/mariadb-connector-j/using-tls-ssl-with-mariadb-java-connector)。

### 5.3 binlog下的触发器权限

库级 `ALL PRIVILEGES` 的迁移账号在独立探针库创建触发器实测失败：MySQL **1419 / SUPER privilege and binary logging is enabled**。
本次只在隔离库临时给主库迁移账号授予SUPER完成迁移，结束撤销。正式部署需DBA明确受控的触发器创建执行身份与撤权流程；本次不授权给正式账号授予全局权限，更没有通过开启信任开关使测试通过。

### 5.4 首次空库validate顺序

两套主库及DAC首次独立validate均报告 `Detected resolved migration not applied to database`，因为迁移尚未执行。现手册在首次migrate前要求validate的顺序不能直接作为空库必过门禁。
本次保留该失败，核对info与冻结清单后执行migrate，`validateOnMigrate=true`保持；执行后validate均通过。没有添加 `*:missing`、`*:pending` 通配豁免，没有repair、baseline或修改既有历史。该事实与已初始化数据库的升级前校验应分别处理。

## 6. 面向最终正式部署的处理顺序

1. 固定本次复核的代码版本和正式来源快照，以正式包的5套模板、10修订、流程、文档、组织及种子排除策略为配置底座。
2. 对照39个共同表字段、8张有效源码表、4个可空性差异、3个唯一索引及全局来源身份索引，按已有批准来源形成新整合版本；排除退役表，保留历史及不可变配置。不要导入整合包的验收草稿、示例菜单/权限及重复割接配置。
3. 锁定字符集策略；恢复冻结SQL正确字节，提交防换行转换约束；将原生Flyway、MySQL驱动、TLS配置要求及迁移账号权限一并纳入发布清单/操作方案。
4. 对新整合版本再次执行本次空库、重复迁移、失败保护、完整来源/引用/种子验证；随后再做应用启动与真实浏览器验收。管理员引导、文件存储、正式巡检规则、人员授权、外部连接及生产TLS/备份恢复仍须完成。

**本轮交付是预演及差异结论，不是已经生成最终整合包或批准生产发布。** 两套冻结包均未改动。

## 7. 证据索引

- [完整执行结果](attempt-202857/result.json)、[结构/权限/种子差异](attempt-202857/comparison.json)、[模板/流程/组织明细](attempt-202857/configuration-details.json)
- [整合包结构](attempt-202857/fresh-schema.json)、[正式包结构](attempt-202857/formal-schema.json)、[DAC结构](attempt-202857/dac-schema.json)
- [正式包来源和当前代码审计](attempt-202857/formal-source-audit.json)
- `attempt-202857/*.log`：原始CLI日志（含预期拒绝、待执行validate失败及正常迁移）；该目录所有文件摘要列于 [evidence-manifest.json](evidence-manifest.json)。
- `attempt-202612` 保留驱动/连接失败，`attempt-202759` 保留TLS身份拒绝、1419及首次validate失败；早期准备失败也保留，不充当数据库验收。
- 可复核的临时执行脚本为仓库 `.run/production-rehearsal-20261101/rehearse.py` 与 `supplement.py`；仅用于隔离演练，不是正式部署入口。
