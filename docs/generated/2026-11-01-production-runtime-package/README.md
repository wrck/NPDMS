# 最终交付：178运行结构迁移包

**结论：按需求方最后确认的“以178当前运行库实表为准”封装，主库518张表、DAC独立24张表。
剩余7列已查完；不再增加仅源码存在的结构。来源库只读，未部署。**

交付：[包说明](../../../sql/releases/production-178-runtime-20261101/README.md)。

## 权威范围的修正

原本依据“补齐源码结构缺口”制作的526表候选，在需求方明确运行库优先后撤回。它补入的8张表，
以及39列、4处可空性、4处索引和2条CHECK都不进入最终包；候选与失败/成功测试记录保留，未对178执行。

[当前178逐表复核](current-178-schema-comparison.json)显示520张物理表与原审阅快照一致，
表及触发器新增、删除、变化均为0；[8表实查](current-178-table-presence.json)确认它们在178均不存在。
仅忽略运行中的AUTO_INCREMENT水位。按已有正式筛选规则排除源Flyway历史和退役表后，最终是518张表。

保留6026条正式配置。字符集和排序规则的已批准修订不回退，B531的SQL字节也不变。原值、不可变
快照、原始历史和正式配置的筛选策略不因本次范围调整而变化。仅源码存在的结构差异不再作为本包补入依据。

## 剩余7列全量检查

[原始结果](remaining-seven-completed.json)：同一来源列在REPEATABLE-READ只读事务中，分别计算
原排序规则、unicode_ci及二者组合的COUNT DISTINCT。每条查询限定30秒，未修改原库全局或持久配置。
仅保存聚合数量，无业务原值或明文凭据。

| 来源表 | 字段 | 原规则／目标规则／组合的等价组数 |
|---|---|---:|
| fb_shipment_barcode | pack_id | 157596 |
| fb_shipment_barcode | item | 8814 |
| fb_shipment_barcode | barcode | 3506715 |
| fb_shipment_barcode | orderNumber | 76513 |
| fb_shipment_barcode | uuid | 4305590 |
| pm_project_shipment | barcode | 1094991 |
| pm_project_soft_version | barCode | 749199 |

7列三种计数均相等。累计365列中328列相等分组保持、37列变化、0列未证明。原值字节转换检查
365列均完成、差异为0。此前5秒超时证据仍保留，不覆盖为通过。各列是独立读取时点，不宣称跨库
统一快照；相等分组检查也不是ORDER BY全排序、组合业务键、哈希/Java/BINARY身份或端到端导入证明。

此前字节证据：[首轮](source-value-comparison.json)、[单独聚合](source-byte-recheck.json)、
[完整主键分段](source-byte-partitions.json)；比较语义：[补查](source-equivalence-recheck.json)、
[37列明细](comparison-differences.md)。用户已明确维持unicode_ci、保留原值并接受比较语义变化。

## 迁移与初始化验证

- [主库验证](main-migration-validation.json)中fresh为本次同字节B531，formal为178原正式B530。
  两者都是518表，全部显式正式配置一致，原触发器和其他结构属性除预期字符声明外一致。
- 首次迁移、validate、重复执行历史保持、非空库拒绝且标记数据保留、已发布配置删除拒绝、篡改文件
  在执行前拒绝均已实测。初始租户／管理员4个执行时间默认值单独留证，不改写或忽略来源历史。
- [DAC验证](dac-validation.json)为同一冻结组件的13份迁移至V26，24表、20组外键、初始化业务行0，
  首次迁移、校验和重复执行通过。其源报告本身完成成功，组件SQL哈希与最终包相同。
- 新采集的178结构与原审阅来源完全一致，主库及DAC SQL也都没有变化，因此复用上述有效隔离验证。
  文件来源和SHA-256见[evidence-provenance.json](evidence-provenance.json)，汇总见[summary.json](summary.json)。

运行验证采用独立Compose MySQL8.4.13、Flyway11.10.5和明确Connector/J9.7.0；TLS主机身份失败测试
通过，binlog保持开启，触发器信任开关保持关闭。隔离临时权限已撤销，隔离服务已停止；原开发服务不动。

## 未发生的操作

来源DPPMS/178的业务写入、DDL、同步、部署、服务重启均为0。没有Git提交或推送，没有编译后端或
执行浏览器业务验收。当前运行制品与整个工作树的源码一致性不由此推定；本包明确采用178运行结构。
结项守卫的源码表名引用修正是独立工作区变更，没有成为最终迁移包中的新表或结构增量。
管理员、外部连接、存储及缺正式来源的巡检判定规则仍按部署配置办理，不从测试配置或样例猜测。
