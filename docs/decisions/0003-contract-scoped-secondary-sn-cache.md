# ADR-0003：合同维度附加SN关系与主档当前缓存

## 状态

Accepted

## 日期

2026-08-04

## 背景

`fb_shipment_barcode`以`barcode/item`保存主SN及其单一物料，特殊业务通过`barcode2/item2`提供附加SN线索。`fb_shipment_barcode_relation`以`sn1/item1/sn2/item2/contract`保存正式对应关系。

同一个主SN在不同合同下可能绑定不同附加SN。关系表需要保存合同维度的完整历史，但设备和项目列表高频读取当前关系；每次从大关系表按合同状态和时间选最新记录会增加连接、排序和重复计算成本。

## 决策

采用“关系明细权威、设备主档缓存当前值”的双层模型：

1. `pms_device_relation`保存合同维度的全部主SN—附加SN关系，是唯一权威来源。
2. 主SN和附加SN分别是独立`pms_device_sn`记录，各自保存自己的单一`item_code`。
3. 主设备只增加`secondary_sn`和`secondary_item`，缓存“该SN最新发货合同”下的附加SN和物料。
4. `pms_device_sn`不保存`secondary_contract_id`、`secondary_relation_id`或`secondary_effective_time`。最新发货合同由设备发货事件和装箱单合同归属确定，关系来源可按同一规则重算。
5. 合同特定查询、历史查询和审计查询必须读取`pms_device_relation`，不得只读主档缓存。

当前缓存按以下确定性规则选择：

1. 从该设备的有效发货事件连接装箱单，排除未解析合同的记录；
2. 按`COALESCE(event.shipment_time, package.shipment_time, event.update_time)`降序、事件`id`降序，确定唯一最新发货合同；
3. 只在该合同下查找`relation_type='EXTRA_SN'`且有效、未删除的关系；
4. 同一合同存在多条候选时，按`COALESCE(relation.effective_time, relation.update_time, relation.create_time)`降序、关系`id`降序取第一条；
5. 没有最新发货合同或该合同没有候选关系时，同时清空`secondary_sn/secondary_item`。

## 一致性规则

- 新发货事件、装箱单合同归属或关系明细新增、修改、停用时，重算受影响主SN缓存；单笔业务写入在同一事务中完成。
- 批量迁移和同步先完整写入关系明细，再用窗口排序批量计算每个主SN的唯一当前关系，只更新发生变化的设备主档。
- 每日运行“最新发货合同+关系明细—主档缓存”差异对账；差异只允许通过重算修复，禁止直接手改缓存。
- 同步批次失败时不得把部分计算结果标记为完成；重跑必须幂等。

## 索引

- `pms_device_sn(tenant_id, secondary_sn)`支持按附加SN反查主SN。
- `pms_device_shipment_event(tenant_id, device_id, shipment_time)`支持确定SN最新发货事件。
- `pms_device_relation(tenant_id, source_device_id, contract_id, relation_type, status, effective_time, id)`支持按主SN和最新发货合同选关系。
- `pms_device_relation(tenant_id, contract_id, relation_type, status, source_device_id)`支持合同变更批量重算。

## 备选方案

### 只在设备主档保存附加SN

无法表达同一主SN在不同合同下的不同关系，也会丢失关系历史和来源，因此拒绝。

### 每次只查询关系明细

语义最纯，但高频列表需要反复连接合同、过滤状态并按时间排序；在关系数据持续增长时成本不可控，因此拒绝作为默认查询路径。

### 单独建立当前关系表

可以避免污染设备主档，但高频设备列表仍需要额外连接。当前明确需要直接返回附加SN和物料，因此先使用主档缓存；若后续缓存字段继续扩张，再评估独立当前关系读模型。

## 影响

- 主档查询更快，但写入和合同状态变更需要维护缓存一致性。
- `secondary_sn/secondary_item`必须被视为可重建缓存，不参与合同关系审计和迁移对账的权威计数。
- API应明确区分“当前附加SN”与“按合同查询附加SN关系”。
