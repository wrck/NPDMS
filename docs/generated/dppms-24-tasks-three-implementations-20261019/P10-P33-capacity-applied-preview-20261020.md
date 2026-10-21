# P10、P33容量补丁执行与真实预览

状态：**两项授权容量补丁已执行，两项真实预览已完成**。核验时间：2026-10-20T08:58:01.964392+08:00。仅在`127.0.0.1:24306/npdms_reverify_20261014`操作；测试库npdms_domain_test、原任务和59191未操作，自动迁移与作业仍关闭。

## 授权及实际结构变化

用户本轮明确允许仅执行此前两条列容量补丁：P10名称255、P33安装位置1024，随后重跑两项真实预览。严格按[既有审阅SQL](P10-P33-column-capacity.review.sql)的两条语句执行；历史审阅材料及原表定义快照保留其当时状态，不改写为执行证据。

| 正式列 | 执行前 | 执行后 | 保留的属性 |
|---|---|---|---|
| cus_customer_master.name | VARCHAR(128) | VARCHAR(255) | utf8mb4 / utf8mb4_unicode_ci；NOT NULL；无显式默认值；注释“客户名称” |
| imp_eng_installation.install_location | VARCHAR(255) | VARCHAR(1024) | utf8mb4 / utf8mb4_unicode_ci；NULL DEFAULT NULL；注释“安装位置” |

实际执行且仅执行以下两条DDL：

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

客户名称索引及所有其他索引、外键、CHECK、触发器和字段定义保持。MySQL在SHOW CREATE中为上述两列显式回显CHARACTER SET utf8mb4，与批准语句一致；逐字比较两张完整表定义，只有批准的两处列定义变化。第一次文本核验遗漏该显式字符集展示，已在只读复核中按实际批准语句修正预期；没有执行第三条DDL或重跑补丁。

执行前保存了两表完整定义及7条客户、34条安装记录的加密备份，并成功解密回读验证。执行后41条记录的全部字段摘要与执行前一致，未更新任何业务内容。

## 两项真实预览终态

仅调用新BASE套件preview入口，真实登录tenant1；未调用execute或任何原同步任务。

| 任务 | Run | 终态 | read | 规划insert / update / unchanged | 完成时间（Asia/Shanghai） |
|---|---|---|---:|---:|---|
| 新P10 | 2112346849270747138 | PREVIEW_READY | 59,544 | 59,544 / 0 / 0 | 2026-10-20 08:55:29.800000 |
| 新P33 | 2112346937229496321 | PREVIEW_READY | 1,035,329 | 0 / 0 / 0 | 2026-10-20 08:57:38.908000 |

P10回执已无P10_EXECUTION_SCHEMA_REQUIRED名称容量告警。规划插入是预览结果，实际客户数据及新来源绑定均未写入。

P33回执已无P33_INSTALL_LOCATION_CAPACITY_PENDING告警，6111条旧255容量问题不再登记。完整原文承接、空地址过滤、UNRESOLVED及仅fallbackLocation的快照规则保持，不创建地址管理记录。当前仍登记的正式输入/既定暂缓以本次回执原文为准：

```text
SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P33_TRANSFER_DEFERRED:2;P33_FORMAL_DEVICE_PENDING:1035329;P33_BLANK_ADDRESS_FILTERED_AFTER_READ:2386
```

缺少正式设备引用仍按原审核只计读取，不伪造设备ID或可写数量；原sourceId1161000、1161002继续暂缓串货，不重复裁决、不扩写功能。正式项目、设备当前选择、权限及历史保护仍保留。预览通过不代表已授权或已完成正式写入。

当前24项任务的最新Run均为PREVIEW_READY；本轮只新增P10、P33各一次预览，其余22项沿用原回执。

## 隔离与实际验证

- 完整SHOW CREATE及列元数据核对：仅两列按批准调整，字符集、排序、可空、默认及注释正确；预览期间结构未再变化。
- 原46任务、209运行、211绑定全部字段摘要逐行无差异，180项计数/最大ID/原任务版本等保护无差异；原BASE回执保持，本轮只新增两条preview=true的PREVIEW_READY回执。
- 7条客户和34条安装历史的全部字段摘要均未变化；新来源绑定0、非预览Run0、无在途任务。
- 新P10/P33原有实现每次读取真实列容量，本轮未改代码/任务配置，也未重启服务。59284 PID25036保持，Flyway、Flowable自动结构更新、Quartz、Batch及事件驱动关闭，迁移容器exited。
- 没有运行Flyway、迁移运行器、真实同步、业务后处理、其他DDL、授权变更或测试库写入；59191未操作。验证采用结构与数据前后核查及两项真实预览，没有将未执行的单测写成通过。

[机器可读结果及24项回执](P10-P33-capacity-applied-preview-20261020.json)。私有证据：`D:/CodexTaskArtifacts/project-detail-followup/p10-p33-capacity-apply-preview-20261020/`，包含加密备份、两条逐语句成功收据、patch-execution-proof.json、P10-preview.json、P33-preview.json及final-protection-proof.json。执行入口apply-authorized-capacity.py仅含固定白名单的两条批准DDL；其后的验证入口verify-applied-patches.py只读，不属于任何正式同步任务。
