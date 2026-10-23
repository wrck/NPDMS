# 新P30：首次非空输入的SN身份及容量核查

## 2026-10-22真实预览、执行和重放已完成

目标仅172.17.90.178:13306/npdms，新BASE_P30。按已确认正式SN键、最新发货时间及并列最大正式发运ID，成功初始化3,486,084台设备；重放全部未变。原4,283,326条正式发运保留，来源及设备SN均未清洗或改写。

| 阶段 | 正式Run | 读取 | 新增 | 未变 | 完成时间 |
|---|---|---|---|---|---|
| preview | 2113015218036645889 | 3486084 | 3486084 | 0 | 2026-10-22 05:33:18.935000 |
| execute | 2113020841616842754 | 3486084 | 3486084 | 0 | 2026-10-22 07:30:31.949000 |
| replay | 2113050695838048258 | 3486084 | 0 | 3486084 | 2026-10-22 07:58:52.732000 |

三阶段各观察到6973次驱动游标fetch，与3,486,084行按500行分块的预期一致；没有将全部来源快照缓冲在客户端。另对全部13,402个大小写变体组、33,154条正式发运逐组核对：选中原SN、shipment_record_id、shipment_time、product_code、package_no、contract_no均正确，差异0。94项独立定向检查保持通过。

新P30 maxRows仅按批准由100万改为500万；未增加DDL或修改其他任务配置。P22/P33实际写入、B稿、迁移和59191仍不操作。当前SQL见[P30-formal-sn-key-current-20261022.sql](P30-formal-sn-key-current-20261022.sql)，24项结果及剩余前置见[本轮逐项报告](remote-tenant-parser-p30-execution-20261022.md)。

## 以下为裁决与候选包阶段的历史记录

用户已确认：按正式SN键归组，取最新发货时间、并列时最大正式发运ID对应的原SN和快照初始化一台设备。新P30代码已完成，94项有效检查通过；新增9项原生P30检查含跨驱动游标批次和同事务插入。新BASE_P30容量已仅由100万改至500万，其余任务列保持。候选包已冻结，等待当前P15执行及重放结束后在空闲任务边界仅重载59284，再做非空真实预览、执行和重放。此时尚未用新代码写入设备，不能把下面源统计或原生夹具视为正式运行结果。

以下保留裁决前的原始核查及方案，状态以本段和后续正式回执为准。

核查时间：2026-10-22T00:54:02.196595+08:00。目标172.17.90.178:13306/npdms；来源只读。P15原始发运事务仍在进行，下列来源统计不是P15提交或P30执行回执。

## 已核实事实

原fb_shipment_barcode有4,283,326条、3,499,488个逐字节不同SN；空SN为0，最大36字符。按正式设备SN的utf8mb4_unicode_ci比较对应3,486,084个唯一值。13,402组仅大小写不同，涉及33,154条发运原行，额外逐字节身份13,404个；全部原SN是可打印ASCII，这些冲突组中未发现尾空格差异或其他字符等价问题。

正式ast_device.sn为VARCHAR(100)、utf8mb4_unicode_ci，唯一键uk_ast_device_tenant_sn(tenant_id,sn)，大小写不同的同一SN无法保存为两台正式设备。ast_device_shipment.device_sn使用同一排序规则，但发运历史允许保存各条来源记录及其原SN。

新P30目前按CAST(device_sn AS BINARY)归组，后续IN查询却按正式列排序规则匹配，存在SQL和Java键不一致；从空输入结果无法发现这点。首次非空输入也超过新BASE_P30当前maxRows=1,000,000，正式元数据CHECK已允许5,000,000，不需要DDL。

## 具体原始记录

| 原发运行ID | 原SN | 原包裹 | 原发货时间 | 原订单号 |
|---|---|---|---|---|
| 1133812 | 01100178D181000005 | 40082 | 2018-01-31 00:00:00 | 24587 |
| 1296169 | 02010001d249000070 | edd27a80-9520-4d92-84d7-7a387520f6f7 | 2024-10-08 00:00:00 | SO088158 |
| 1746260 | 01100178d181000005 | 60edf61e-4e24-4b11-b006-80eeb1aa2952 | 2018-01-31 00:00:00 | 24587 |
| 2420835 | 02010001d249000029 | a43fe407-d373-48f7-8cea-e1c48e27f49a | 2024-09-30 00:00:00 | SO088116 |
| 3945903 | 02010001D249000070 | e26229c8-4fea-4e2c-b85f-5ebef183c70b | 2025-12-16 00:00:00 | SO088158 |
| 4031981 | 02010001D249000029 | 87e41dbd-1ed3-4de7-872c-9f43aae8b9db | 2025-12-30 00:00:00 | SO088158 |

## 查询SQL及代码

三份源统计、全部差异原文及逐项来源ID保存在`D:\CodexTaskArtifacts\project-detail-followup\base-sync-tenant-parser-fix-20261021\P30-sn-variant-audit.json`。成功核查用现有barcode覆盖索引流式读取，EXPLAIN为Using where; Using index，没有临时分组或排序；逐行对比总数、逐字节键数和正式键数全部与独立统计相符。此前大型CONVERT分组查询触及本机MySQL临时盘容量而失败，临时文件已由MySQL释放，未将该失败当作成功证据，也未删除其他文件。

```sql
SELECT b.id,b.barcode,b.pack_id,p.packdate AS shipment_time,b.item,b.orderNumber,b.updateTime FROM fb_shipment_barcode b LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id WHERE b.id IN (1133812,1746260,2420835,4031981,1296169,3945903) ORDER BY b.id;
```

```sql
SELECT barcode,id FROM fb_shipment_barcode
 WHERE barcode NOT REGEXP '[^ -~]' ORDER BY barcode,id;
```

当前实现：`pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml`的selectDeviceBase、selectLatestShipments、selectExisting；`P30DeviceBaseSyncTask.syncDataInsertBefore`使用原String键。已有P33的requested_sn修复只用于匹配既存设备，没有裁决新设备主档在多个大小写原文中选哪条发运快照，不能据此自动扩写新的快照选择。

## 待确认的最小方案

建议仅新P30按现有正式SN唯一键归组，将同组最新发货时间、并列时最大正式发运ID对应的原SN及发运快照用于初始化一台设备。原发运历史全部保留，来源SN不统一转大写/小写，不改既存设备SN，不修改发运原文、旧任务、AST约束或授权。同时仅将新BASE_P30的maxRows从100万调至500万，以容纳已批准的整表非空输入。

另一方案是保留差异清单并暂缓P30真实写入，等待权威设备身份和快照依据。当前没有修改P30逻辑、容量或数据；P22/P33实际写入、B稿仍暂缓，不执行DDL、迁移或59191操作。
