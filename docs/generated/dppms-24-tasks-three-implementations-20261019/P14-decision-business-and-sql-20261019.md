# P14-1决策补充：真实表名、查询SQL、业务形成与正式承载限制

**最新P14裁决：MAX(orderExecNumber)**（同来源、公司、类型、订单号），169组其他字段一致，按现有一单一头承接；无需新增执行单关联表，不再提请该旧方案。只读核查及40条缺订单号/1579条父头问题见[当前两表核查](P14-split-unique-review-20261019.md)。该裁决尚未写入产品代码或重跑真实预览；以下此前建议/结果按原时间保留。


**当前来源裁决（2026-10-19T22:12:17.889348+08:00）：用户明确旧系统订单已拆分完成。新P14只以pm_order_data_from_erp及pm_order_line_from_erp为业务来源，不回查总代借货、未拆分ERP原表/视图，不重新拆单，不用上游值重选执行单。此前上游/短号分析只保留为历史追溯，不再作为同步输入、外部前置或预览门槛。**

[当前两张原表的直接查询SQL](P14-current-split-source-20261019.sql)。这两条用于来源/承接键核查，不代表已经替换产品SQL或执行同步。当前P14来源XML实际也只引用这两张表；此前额外上游读取发生在核查材料，未进入任务实现。本次没有产品代码修改。

尚需核清的是：已经拆好的原订单行如何完整承接到正式com_sales_order及com_sales_order_line，尤其正式订单业务唯一键不含执行单号时的承载方式。原表同订单号多执行单不能仅因此认定为来源异常或要求回源消歧。基于未拆分ERP主号的选取建议撤回；新增关系表、SMS主号NULL也均未获批准，不自动实施。


以下内容保留为此前核查及未批准方案的历史材料，当前实施边界以本段来源裁决为准。

状态：**PENDING_BUSINESS_REVIEW / 未批准新增关系表或主号选择**。用户指出此前缺少查询SQL、表名和业务说明，材料不足。旧“一单一头＋正式关联，SMS主号NULL”只是未批准的提议；本次补齐可执行SQL并细分问题，不要求依据原二选一直接决策。

当前SQL核验时间：2026-10-19T21:57:41.495266+08:00。源为MySQL 5.7.26-log的dppms；正式承载核查为专用验证库npdms_reverify_20261014（MySQL 8.4.10，127.0.0.1:24306）。两边全部连接设置session只读，本次实际执行9条源SELECT、4条目标SELECT，均正常返回。没有调用旧过程、执行迁移/真实同步、修改原任务或59191。

## 1. 真实对象及业务关系

| 库/对象 | 真实类型 | 业务含义与键 |
|---|---|---|
| dppms.pm_order_data_from_erp_source | 物理表 | ERP进入旧系统、尚未经过借货展开的订单头；主键id。字段source＋compCode＋orderType＋orderNumber定位业务订单，原执行单为orderExecNumber |
| dppms.pm_order_data_from_sap_source | 视图 | 直接SELECT上述erp_source物理表，无JOIN、分组或版本筛选；名称带sap，但样例实际source=D365。不能把视图和底表当两套独立数据 |
| dppms.pm_project_soleagent_lend_from_sms | 物理表 | 总代借货来源；主键id，soleAgentLendId为“总代借货跟踪”，contract为合同号，orderCodes注释“合并的执行单号”，orderExecNumberShort注释“忽略版本执行单号” |
| dppms.pm_order_data_from_erp | 物理表 | 旧splitSoleAgentLendOrderInfo处理后的订单头；主键id。P14当前读取这里，已包含借货展开和-L派生；表注释虽称原始合同信息，不能据此当作未展开ERP输入 |
| dppms.pm_order_line_from_erp | 物理表 | 旧订单明细；主键id，父订单业务码为orderNumber，行号lineNum，实际执行单realOrderExecNumber |
| npdms_reverify_20261014.com_sales_order | 正式业务表 | ERP销售订单主档；主键id；业务唯一键tenant_id＋source_system＋company_code＋order_type＋order_no；execution_no为单值主执行单号 |
| npdms_reverify_20261014.com_sales_order_line | 正式业务表 | 订单行及数量快照；order_id关联本租户订单，real_execution_no保存该行实际执行单 |
| npdms_reverify_20261014.com_crm_execution_order | 正式业务表 | CRM执行单实体，execution_no是执行单编号；不是销售订单/执行单的多关系表 |

历史映射材料还列出com_execution_order_merge_batch/member，但本次对专用库information_schema实查，这两张表均不存在；仓库commerce运行实现也未找到这两表的读写。它们的旧设计是执行单合并批次/成员，不能直接当作当前已有的销售订单关联能力。上次建议的com_sales_order_execution_relation也不存在，未创建、未批准。

## 2. 原业务如何形成重复订单头

旧过程按**合同号**连接ERP原始订单头和借货来源：b.contract=a.contractNo，而不是按一个唯一借货ID或订单行直接关联。一份合同匹配多条借货原行时，一个ERP订单头被展开为多行。

执行单取IFNULL(b.orderExecNumber,a.orderExecNumber)。当借货完整执行单非NULL且借货短号与ERP短号不同，输出订单号追加-L，合同号使用借货合同号追加-L，source改为SMS；同短号时保留原订单号/合同号/source，但完整执行单仍可被借货来源的另一个版本号替换。

原过滤逻辑：没有借货记录时保留；SAP有借货时比较orderCodes逗号前后片段前6字符，不同时保留（原注释称利润中心）；非SAP有借货时保留。下列SQL保持原条件及NULL行为，不添加新的业务筛选，也不执行存储过程。

视图本身可用以下只读语句核对，本次已读取其定义：

```sql
SHOW CREATE VIEW dppms.pm_order_data_from_sap_source;
```

实际定义仅把pm_order_data_from_erp_source各字段直接别名投影，没有JOIN或版本筛选；不需要再次解析出另一套ERP来源。

### 2.1 ERP未展开原头：先查这张物理表

```sql
SELECT id, source, compCode, orderType, orderNumber, contractNo,
       orderExecNumber, orderExecNumberShort, customerCode, customerName, salesType
FROM dppms.pm_order_data_from_erp_source
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075423')
ORDER BY orderNumber, id;
```
| 原ERP id | 来源/公司/类型 | 订单号 | 合同号 | ERP完整执行单 | ERP短号 |
|---|---|---|---|---|---|
| 9131 | D365/01/0 | SO074723 | 16202523112915N | 1620252311221X301 | 162025231122X301 |
| 9370 | D365/01/0 | SO075153 | 16200523112415N | 1620052311163X302 | 162005231116X302 |
| 9522 | D365/01/0 | SO075423 | 16100023121116N | 1610002312013X301 | 161000231201X301 |

### 2.2 借货来源：按同一合同看匹配到的全部原行

```sql
SELECT id, soleAgentLendId, `contract`, orderCodes,
       orderExecNumber, orderExecNumberShort, profitCenter, dataSource
FROM dppms.pm_project_soleagent_lend_from_sms
WHERE `contract` IN ('16202523112915N', '16200523112415N', '16100023121116N')
ORDER BY `contract`, id;
```
| 借货原行id | 借货跟踪ID | 合同号 | orderCodes原值 | 借货完整执行单 | 忽略版本短号 |
|---|---|---|---|---|---|
| 118 | 906 | 16100023121116N | 1610002312013J301,1620252312113X303 | 1610002312013J301 | 161000231201J301 |
| 119 | 798 | 16100023121116N | 1610002312013J301,1620252312111X303 | 1610002312013J301 | 161000231201J301 |
| 1307 | 798 | 16100023121116N | 1610002312013J301,1620252312111X303 | 1620252312111X303 | 162025231211X303 |
| 1308 | 906 | 16100023121116N | 1610002312013J301,1620252312113X303 | 1620252312113X303 | 162025231211X303 |
| 313 | 778 | 16200523112415N | 1620052311161X302,1620252311201X303 | 1620052311161X302 | 162005231116X302 |
| 314 | 885 | 16200523112415N | 1620052311163X302,1620252311203X303 | 1620052311163X302 | 162005231116X302 |
| 315 | 782 | 16200523112415N | 1620052311163X302,1620252311201X303 | 1620052311163X302 | 162005231116X302 |
| 1293 | 778 | 16200523112415N | 1620052311161X302,1620252311201X303 | 1620252311201X303 | 162025231120X303 |
| 1294 | 782 | 16200523112415N | 1620052311163X302,1620252311201X303 | 1620252311201X303 | 162025231120X303 |
| 1295 | 885 | 16200523112415N | 1620052311163X302,1620252311203X303 | 1620252311203X303 | 162025231120X303 |
| 342 | 781 | 16202523112915N | 1620252311223X301,1620062311201X301 | 1620062311201X301 | 162006231120X301 |
| 343 | 779 | 16202523112915N | 1620252311221X301,1620062311201X301 | 1620062311201X301 | 162006231120X301 |
| 1298 | 779 | 16202523112915N | 1620252311221X301,1620062311201X301 | 1620252311221X301 | 162025231122X301 |
| 1299 | 781 | 16202523112915N | 1620252311223X301,1620062311201X301 | 1620252311223X301 | 162025231122X301 |

### 2.3 原过程的只读展开SQL：每个结果同时带ERP和借货原身份

```sql
SELECT a.id AS erp_source_id, a.orderNumber AS erp_order_no,
       a.contractNo AS erp_contract_no,
       a.orderExecNumber AS erp_execution_no,
       a.orderExecNumberShort AS erp_execution_short,
       b.id AS lend_row_id, b.soleAgentLendId, b.orderCodes,
       b.orderExecNumber AS lend_execution_no,
       b.orderExecNumberShort AS lend_execution_short,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN CONCAT(a.orderNumber, '-L') ELSE a.orderNumber END AS result_order_no,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN CONCAT(b.`contract`, '-L') ELSE a.contractNo END AS result_contract_no,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN 'SMS' ELSE a.source END AS result_source,
       IFNULL(b.orderExecNumber, a.orderExecNumber) AS result_execution_no,
       IFNULL(b.orderExecNumberShort, a.orderExecNumberShort) AS result_execution_short
FROM dppms.pm_order_data_from_sap_source a
LEFT JOIN dppms.pm_project_soleagent_lend_from_sms b
       ON b.`contract` = a.contractNo
WHERE a.orderNumber IN ('SO074723', 'SO075153', 'SO075423')
  AND (b.id IS NULL
       OR (a.source = 'SAP' AND
           SUBSTR(b.orderCodes, 1, 6) <>
           SUBSTR(b.orderCodes, LOCATE(',', b.orderCodes) + 1, 6))
       OR (a.source <> 'SAP' AND b.id IS NOT NULL))
ORDER BY a.orderNumber, a.id, b.id;
```

### 2.4 P14实际读取的已展开订单头

```sql
SELECT id, source, compCode, orderType, orderNumber, contractNo,
       orderExecNumber, customerCode, customerName, salesType, syncTime
FROM dppms.pm_order_data_from_erp
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075153-L',
                      'SO075423', 'SO075423-L')
ORDER BY orderNumber, id;
```
| 已展开id | 来源 | 公司/类型 | 订单号 | 合同号 | 完整执行单 | syncTime |
|---|---|---|---|---|---|---|
| 9134 | D365 | 01/0 | SO074723 | 16202523112915N | 1620252311221X301 | 2026-08-02 23:51:24 |
| 9135 | D365 | 01/0 | SO074723 | 16202523112915N | 1620252311223X301 | 2026-08-02 23:51:24 |
| 9383 | D365 | 01/0 | SO075153 | 16200523112415N | 1620052311161X302 | 2026-08-02 23:51:24 |
| 9384 | D365 | 01/0 | SO075153 | 16200523112415N | 1620052311163X302 | 2026-08-02 23:51:24 |
| 9385 | D365 | 01/0 | SO075153 | 16200523112415N | 1620052311163X302 | 2026-08-02 23:51:24 |
| 9386 | SMS | 01/0 | SO075153-L | 16200523112415N-L | 1620252311201X303 | 2026-08-02 23:51:24 |
| 9387 | SMS | 01/0 | SO075153-L | 16200523112415N-L | 1620252311201X303 | 2026-08-02 23:51:24 |
| 9388 | SMS | 01/0 | SO075153-L | 16200523112415N-L | 1620252311203X303 | 2026-08-02 23:51:24 |
| 9555 | SMS | 01/0 | SO075423-L | 16100023121116N-L | 1610002312013J301 | 2026-08-02 23:51:24 |
| 9556 | SMS | 01/0 | SO075423-L | 16100023121116N-L | 1610002312013J301 | 2026-08-02 23:51:24 |
| 9557 | SMS | 01/0 | SO075423-L | 16100023121116N-L | 1620252312111X303 | 2026-08-02 23:51:24 |
| 9558 | SMS | 01/0 | SO075423-L | 16100023121116N-L | 1620252312113X303 | 2026-08-02 23:51:24 |

样例说明：

- SO074723：原ERP完整执行单为1620252311221X301；借货匹配后还有1620252311223X301，两者的忽略版本短号都为162025231122X301。这是同短号的不同完整版本候选，不能只说成两个不同业务执行单。
- SO075153-L：由原ERP SO075153/合同16200523112415N派生；ERP原执行单为1620052311163X302，借货结果为1620252311201X303和1620252311203X303，短号均162025231120X303。借货原行1293、1294指向同一完整执行单，所以3个展开原头只形成2个不同完整关系。
- SO075423-L：同时有短号161000231201J301与162025231211X303，后者又有1X303/3X303完整版本。这里同时存在“不同短号关系”和“同短号版本”，不能靠全组MAX完整号统一解决。

## 3. 全部169组需要分成两类，原方案未覆盖这个区别

| 来源 | 每组短号数 | 订单业务键数 |
|---|---|---|
| D365 | 1 | 37 |
| SMS | 2 | 66 |
| SMS | 1 | 66 |

当前全表统计：169个订单键、410个原头、361个不同的订单/完整执行单关系；其中103个订单键只有1个忽略版本短号，66个SMS键有2个短号。410原头中的49行共享已有完整关系。每组其余九项业务属性均只有一个变体。

同一短号说明源系统把它们归入同一“忽略版本”键；**它本身没有决定哪个完整版本仍有效，也不等于可以按字符串最大号、原id或同步时间选主号。** 上述样例展开原行的syncTime相同。两个短号则不能仅通过同短号的版本消歧变成一个业务键。

### 全表冲突定位SQL（单表、无嵌套）

```sql
SELECT source, compCode, orderType, orderNumber,
       COUNT(*) AS original_rows,
       COUNT(DISTINCT CAST(orderExecNumber AS BINARY)) AS full_execution_count,
       GROUP_CONCAT(DISTINCT orderExecNumber ORDER BY orderExecNumber) AS full_execution_numbers,
       COUNT(DISTINCT CAST(JSON_ARRAY(orderCreateTime, customerRequireTime,
           customerCode, customerName, projectName, orderComment,
           salesType, customInfo, contractNo) AS BINARY)) AS non_execution_variants
FROM dppms.pm_order_data_from_erp
GROUP BY source, compCode, orderType, orderNumber,
         CAST(source AS BINARY), CAST(compCode AS BINARY), CAST(orderNumber AS BINARY)
HAVING COUNT(DISTINCT CAST(orderExecNumber AS BINARY)) > 1
ORDER BY source, orderNumber, orderType;
```

该SQL本次返回169行、原头合计410行，non_execution_variants均1。带原始ERP/借货短号的S09已在完整来源SQL中给出，只有一层派生表；165个ERP原id和169个结果订单号均已明确列出，实际返回全部169组，不用截断或取样解释全量。

[全部169组逐订单短号/完整号/原头数](P14-all-169-order-execution-groups-20261019.md)。

## 4. 为什么订单明细不能直接替代表头执行单关系

订单明细保留的是每行realOrderExecNumber；它并不保证覆盖表头展开的每一个版本或每一个短号。下面SQL按实际来源、公司、类型、订单号、执行单汇总，不跨订单借用行：

```sql
SELECT source, compCode, lineType, orderNumber, realOrderExecNumber,
       COUNT(*) AS original_rows,
       COUNT(DISTINCT CAST(lineNum AS BINARY)) AS different_line_numbers
FROM dppms.pm_order_line_from_erp
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075153-L',
                      'SO075423', 'SO075423-L')
GROUP BY source, compCode, lineType, orderNumber, realOrderExecNumber,
         CAST(source AS BINARY), CAST(compCode AS BINARY),
         CAST(orderNumber AS BINARY), CAST(realOrderExecNumber AS BINARY)
ORDER BY orderNumber, realOrderExecNumber;
```
| 来源 | 公司/类型 | 订单号 | 行实际执行单 | 原明细数 | 不同lineNum数 |
|---|---|---|---|---|---|
| D365 | 01/0 | SO074723 | 1620252311221X301 | 14 | 7 |
| D365 | 01/0 | SO075153 | 1620052311163X302 | 102 | 34 |
| SMS | 01/0 | SO075153-L | 1620252311201X303 | 123 | 41 |
| SMS | 01/0 | SO075423-L | 1610002312013X301 | 16 | 8 |
| SMS | 01/0 | SO075423-L | 1620252312111X303 | 6 | 3 |

例如SO074723明细只有1X301，表头却有1X301/3X301；SO075153-L明细只有1X303，表头有1X303/3X303；SO075423-L明细还出现1610002312013X301，而对应头的这支是1610002312013J301，不能自行把X与J当成同一完整执行单。此前全部169组头/行比较也表明仅1组集合一致、31组无原明细。逐条明细SQL见来源文件S06，本次例单共261原行，完整结果保留在JSON。

## 5. 正式字段、唯一键和现有业务读取影响

### 5.1 当前专用库的真实列与索引查询

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.columns
WHERE TABLE_SCHEMA = 'npdms_reverify_20261014'
  AND TABLE_NAME IN ('com_sales_order', 'com_sales_order_line', 'com_crm_execution_order')
  AND COLUMN_NAME IN ('id', 'tenant_id', 'source_system', 'source_record_key',
      'company_code', 'order_type', 'order_no', 'order_id', 'line_no',
      'execution_no', 'real_execution_no', 'primary_project_id')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

```sql
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS index_columns
FROM information_schema.statistics
WHERE TABLE_SCHEMA = 'npdms_reverify_20261014'
  AND TABLE_NAME IN ('com_sales_order', 'com_sales_order_line')
  AND NON_UNIQUE = 0
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;
```
| 正式字段/约束 | 实际限制 |
|---|---|
| com_sales_order.execution_no | VARCHAR(64) NULL，单值“主执行单号(快照来源订单头orderExecNumber)” |
| uk_sales_order_business | tenant_id,source_system,company_code,order_type,order_no |
| uk_sales_order_source | tenant_id,source_system,source_record_key |
| com_sales_order_line.real_execution_no | VARCHAR(64) NULL；每行实际执行单 |
| com_sales_order_line.order_id | BIGINT NOT NULL；不能仅因头有多个执行单就任意拆多个订单父ID |
| com_crm_execution_order | 保存CRM执行单，不含销售订单号/销售订单ID关联列 |

因此，“把执行单加到订单唯一键里”还会影响source_record_key、父订单定位及现有调用方，不能只改一条唯一索引。也不能从execution_no物理可空推导业务查询置NULL没有影响。

### 5.2 当前代码确实存在依赖表头执行单的读取

[pms-module-commerce的ProjectCommerceMapper.xml:74](../../../pms-module-commerce/src/main/resources/mapper/contract/ProjectCommerceMapper.xml)的selectExecutionOrders有两条UNION分支：一条通过CRM执行单primary_project_id直接找；另一条从有效项目合同关系→同公司有效销售订单→CRM执行单，并精确要求：

```sql
-- 仅展示现有查询中的连接条件；完整租户、授权和生效条件见原XML，未省略后执行。
JOIN com_crm_execution_order e ON e.tenant_id = o.tenant_id
 AND e.execution_no = o.execution_no
 AND CAST(e.execution_no AS BINARY) = CAST(o.execution_no AS BINARY)
 AND e.company_code = o.company_code
 AND CAST(e.company_code AS BINARY) = CAST(o.company_code AS BINARY)
```

若SMS订单头execution_no留NULL，该订单不能通过此“合同→订单头执行单”分支贡献执行单结果；是否还能由第一条主项目分支返回取决于正式数据，不能保证。新建关系表也不会自动改变这条现有读取，需要明确关系的正式消费者及读取改造范围。直接把D365原完整版本定为主号，同样需考虑正式CRM是否保留/提供该完整版本，而非只凭“存在上游值”就宣告业务闭环。

[SalesOrderLineMapper.xml:103](../../../pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml)另有selectDistinctRealExecutionNos，它只读取各订单行的real_execution_no，不能自动补足未出现在行里的表头完整号。这里提供的是代码路径证据，没有运行浏览器或宣称现有样例业务验收通过；目标样例订单本次查询0行，也不据此创建正式父数据。

### 5.3 新P14当前如何处理冲突

[P14SourceMapper.xml](../../../pms-module-integration/src/main/resources/base-sync-source/P14SourceMapper.xml)按公司编码归一后聚合订单键，虽有MAX(orderExecNumber)，但payloadVariants>1会先标DUPLICATE_BUSINESS_KEY_CONFLICT，实际预览因此失败；目前并没有按MAX擅自同步。现有保护正确，不能只删冲突判定让聚合最大值成为新业务规则。

主要字段对应：

| 当前来源 | 正式目标 | 既定含义 |
|---|---|---|
| pm_order_data_from_erp.source | com_sales_order.source_system | SAP/D365/SMS来源 |
| compCode | company_code | 01/02/03/04按既定映射为001/002/003/004 |
| orderType | order_type | 0正向/1退货，保持原类型 |
| orderNumber | order_no | 包括旧过程已生成的-L；不自行去掉后缀 |
| contractNo | contract_no | 包括原-L合同码；不顺带改变P15已决客户规则 |
| orderExecNumber | execution_no | 当前单值字段，正是本次版本/多短号待裁决点 |
| pm_order_line_from_erp.realOrderExecNumber | com_sales_order_line.real_execution_no | 原订单行实际执行单，不能由表头随意覆盖 |
| lineNum | line_no | 原行号 |
| orderQuantity/openQuantity | order_qty/open_qty | 原数量；本轮不调整 |

## 6. 当前待决：正式表承接已经拆好的原数据

来源范围已经裁决，不再询问总代借货、未拆分ERP或短号版本的重新解析规则。旧表保留的source、compCode、orderType、orderNumber、orderExecNumber及订单行realOrderExecNumber作为已有拆分结果读取，沿用已审字段映射，不自行还原、再拆或重选主执行单。

当前正式com_sales_order的业务唯一键仍为tenant_id/source_system/company_code/order_type/order_no；既有P14来源SQL按这个目标键聚合并对不同载荷阻断。本次未调整这一实现或放宽保护。下一步只围绕已拆分原行的正式身份、唯一键及订单行承接关系给出方案，不把目标模型容纳不了原数据解释为需要来源再消歧，也不自动新增关系表或改变唯一键。

此前回源选D365主号、SMS头NULL和按上游短号选版本的提议退出当前实施方案。103/66分组只作为已完成的历史分析保存，不再是预览前置。P14-2的40条缺订单号仍按已拆分订单行原值单独核清；P10名称255的已有裁决保持。

本轮仅修正来源边界和待决记录，没有新源查询、产品修改、任务/数据/权限/结构写入、迁移、真实同步或新预览，不动59191，自动迁移配置未变。

以下为此前实际执行的历史核查SQL与结果；其中上游部分不属于当前同步来源或执行步骤：

完整可执行SQL与结果：

- [来源dppms：S01～S09，含全169组](P14-decision-source-dppms-20261019.sql)
- [专用正式库：T01～T04，真实列/唯一键/表存在性及例单](P14-decision-target-reverify-20261019.sql)
- [13条SQL结果、源字段说明、全部169组追溯](P14-decision-query-results-20261019.json)
