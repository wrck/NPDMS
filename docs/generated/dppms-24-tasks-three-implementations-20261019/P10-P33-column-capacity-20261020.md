# P10、P33列容量审阅方案

状态：**PROPOSED_NOT_EXECUTED**。2026-10-20 08:16（Asia/Shanghai）完成当前库和原始来源只读核对。本轮只形成审阅材料；P10已有255裁决不重问，P33完整原文承接规则不重问。未运行DDL、迁移或真实同步，原任务及59191未操作，自动迁移和作业保持关闭。

目标固定为`127.0.0.1:24306/npdms_reverify_20261014`。这是独立验证库内的正式业务表，不是测试库`npdms_domain_test`；修改列容量会作用于本库对应整张表，本轮没有实施。DDL也不写入任何同步任务或Flyway资源。

## 当前定义、原始长度与建议

| 项目 | 来源正式类型 | 当前目标列 | 原始数据最大长度 | 超当前容量 | 建议容量 |
|---|---|---|---:|---:|---|
| P10 客户名称 | SQL Server DPtech_v_account.Name，NVARCHAR(160)，可空 | cus_customer_master.name VARCHAR(128)，NOT NULL，无显式默认值，注释“客户名称” | 143字符，最长记录UTF-8为429字节；SQL Server原存储286字节 | 4条，分别130/131/131/143字符 | VARCHAR(255)，沿用已批准规则 |
| P33 安装位置 | dppms.pm_project_shipment.installAddress，TEXT，可空，utf8_general_ci | imp_eng_installation.install_location VARCHAR(255)，NULL DEFAULT NULL，注释“安装位置” | 571字符／1505字节 | 6111条 | 建议VARCHAR(1024)，本批超过1024为0 |

两列当前字符集均为utf8mb4、排序规则均为utf8mb4_unicode_ci。建议只改变长度，保留字符集/排序、可空性、默认值、注释、列位置及所有既有约束。

P10来源共59,544条；原值最大长度和现有LTRIM/RTRIM映射后最大长度均143。4条超长名称额外按原UTF-16字节在内存解码，Unicode码点数也与上述长度一致，排除了SQL Server LEN不计尾空格或非BMP计数差异影响本次结论；未保存客户姓名/联系方式等正文。其余记录SQL Server UTF-16长度不超过128，因此不会产生更大的Unicode字符长度。全部当前名称<=255，且255覆盖来源声明的160单位容量。

P33按照新P16/P33相同的原项目别名/最大ID主档选择和有效type10范围核查：83,550条原项目形成79,442个允许的来源项目键，范围内1,063,717条原发货安装来源，最长571，超255为6111，超1024为0；超255中纯空白记录为0。整张pm_project_shipment共有1,064,774条，最长及超长数量相同，但不把范围外记录纳入新P33。该核查在只读REPEATABLE_READ一致快照中完成；本轮没有重跑任务预览。

P33源列是TEXT，其允许范围大于1024字符。1024是覆盖本次已审核现存原文的最小改动建议，不声称未来任意TEXT值都能承接；后续超过1024仍由已有校验明确拒绝，不能截断、填NULL或静默跳过。当前没有需要再扩大容量的数据。

## 当前正式表定义

本次涉及列的SHOW CREATE TABLE原定义如下：

```sql
-- cus_customer_master
`name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称'

-- imp_eng_installation
`install_location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置'
`location_snapshot` text COLLATE utf8mb4_unicode_ci
```

两张表均为InnoDB/Dynamic、表默认utf8mb4_unicode_ci。两张完整SHOW CREATE TABLE输出已保存到[正式表定义快照](P10-P33-current-table-definitions-20261020.sql)，不是建表执行脚本。

客户name参与现有非唯一索引`idx_cus_customer_master_tenant_name(tenant_id,name)`，不需要另改索引定义；目标MySQL8.4.10、16KiB页、Dynamic行格式，调整后声明索引字段约8+255×4=1028字节。`tenant_id+code`唯一键保持。安装位置列未参与索引；设备唯一键、外键和CHECK均保持。

location_snapshot现为TEXT，容量65,535字节，足以承接本批原文的既有fallbackLocation快照；本次不修改快照列、不修改快照键、不创建地址管理记录，不重新裁决原文承接方式。

## 最小DDL：仅供审阅，未执行

```sql
ALTER TABLE `npdms_reverify_20261014`.`cus_customer_master`
  MODIFY COLUMN `name` VARCHAR(255)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NOT NULL COMMENT '客户名称';

ALTER TABLE `npdms_reverify_20261014`.`imp_eng_installation`
  MODIFY COLUMN `install_location` VARCHAR(1024)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NULL DEFAULT NULL COMMENT '安装位置';
```

[独立审阅SQL](P10-P33-column-capacity.review.sql)只有两条ALTER、两个列长度调整；没有数据UPDATE、索引/外键/触发器改动、任务参数更新或自动执行入口。

新P10的两处名称上限已为255，并在每次运行核查实际列容量；新P33每次运行从information_schema读取install_location实际容量。若将来另获执行授权，列调整可由既有逻辑在下次运行读取；不需要把DDL加进同步逻辑，也不需要仅为这两项容量重启服务。本轮未改代码或重启。

## 可追溯原始身份

P10全部4条超长原身份对应客户码与长度：

| 原客户码 | 原文字符数 | 原文UTF-8字节 |
|---|---:|---:|
| 162014000109 | 131 | 393 |
| 162025001801 | 130 | 390 |
| 162014000948 | 131 | 393 |
| 162016001782 | 143 | 429 |

P33最长值的5条来源样例：

| 原sourceId | 原projectId | 原文字符数 | 原文UTF-8字节 |
|---|---|---:|---:|
| 463407 | 29196 | 571 | 1505 |
| 463408 | 29196 | 571 | 1505 |
| 463409 | 29196 | 571 | 1505 |
| 463410 | 29196 | 571 | 1505 |
| 463411 | 29196 | 571 | 1505 |

## 只读边界与复核

目标表在核查前后定义完全一致；客户仍7条、现存名称最长16字符，安装记录仍34条、现存安装位置最长13字符，均未写入或变更。Flyway历史版本473保持；59284 PID25036未重启，自动Flyway/Flowable结构更新、Quartz、Batch、事件驱动仍关闭，迁移容器exited。本轮没有操作原任务、原运行、原绑定、测试库或59191。

首次P33联表只读统计在Creating sort index阶段耗时175秒，精确确认仅本次查询534后取消；改为项目目录和安装表长度分组各一次平坦读取再归并。首次归并发现旧库disabled为BIT且驱动返回bytes，改为只读投影CAST(disabled AS UNSIGNED)，没有改业务数据或正式任务代码。P10中文终端解码差异通过只读UTF-16十六进制投影复核解决；上述失败/调整不计作成功数据证据。最终结果只取已完成的查询和断言。

[机器可读核查结果](P10-P33-column-capacity-20261020.json)。只读SQL、原字段类型、Unicode复核、两表SHOW CREATE输出、最终证明及脚本位于`D:/CodexTaskArtifacts/project-detail-followup/p10-p33-column-capacity-audit-20261020/`：P10-source-lengths.sql、P10-unicode-length-check.sql、P33-source-lengths-flat.sql、read-only-proof.json。所有DDL仍未执行。
