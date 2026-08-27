# Task 1 Review Round 2 修复报告

日期：2026-08-13  
状态：完成，等待独立复审；未提交  
边界：未使用项目记忆；未连接或修改旧 `dppms`；P3-E09 仍为 `OPEN`，V1.7 Delta 仍为 `BLOCKED_BY_REVIEW`。

## 修复结果

- 历史工时：`transitHour` 与 `processHour` 按 `COALESCE(transitHour,0)+COALESCE(processHour,0)` 汇总到 `duration_hours`，仅在两者都为 NULL 时结果为 NULL；两个原值同时完整保存在 `source_payload`。没有动作方向证据，因此 `direction_code`、`signed_adjustment_hours` 不绑定来源字段并保持 NULL，字段目录标为 `PENDING_FIELD_MAPPING`。
- 编码/名称：`client_supplier_code/customerCode/itemCode` 分别绑定代码列；`client_supplier_name/customerName` 分别绑定 `customer_name`；`itemName` 绑定现有 `item_desc` 名称快照列，不再绑定 `item_code`。
- 目标引用 ID：所有旧来源到目标 `*_id` 的绑定显式声明 `EXTERNAL_KEY_MAPPING`、`TARGET_KEY_LOOKUP` 或 `NEW_GENERATED` 策略，禁止直接复制旧 ID。
- 证据精度：字段目录仅从维护的字段绑定派生旧数据元证据，范围会解析为实际含源字段的单元格；无绑定的设计新增字段仅保留 `basisRefs`。JSONL 不再输出空 `dataElementRefs`/`basisRefs` 数组。
- 无旧数据元的签名、必答校验、CUT 结束窗口/责任人、方向/调整字段共 14 个明确标为 `PENDING_FIELD_MAPPING`，未误标 `BLOCKED_BY_SPEC`。
- 校验器新增：证据 URI 坐标与 sourceObject/sourceField 交叉核验；名称到代码、耗时到调整/方向的语义类别拒绝；目标 `*_id` 必须显式键解析策略；统计必须由生成结果计算。
- 删除未接入正式链的一次性 `scripts/update_v17_core_contract.py`。

## 生成统计

- 领域对象/来源：88 / 100。
- 全契约字段绑定：53。
- V1.7 目标字段绑定：31，其中旧库字段绑定 27、旧来源表 9、展开源字段 44、去重源字段 36。统计来自生成契约的 `bindingStatistics`，未硬编码“30条/10表”。
- V1.7 字段目录：14 表、235 字段、缺失设计依据 0；22 个目标字段具有逐字段精确数据元坐标。
- DDL 未改变：64 表、1300 列、MySQL 约束 340；SHA-256 `42D99DBDC28A4586D0FEC913096C4ABF19865032C20EBC78E28DADEA8BE19AB5`。
- 已有 MySQL 8.4.10 隔离证据仍与该哈希一致：64 表、1300 列、PK 64、UNIQUE 133、FK 48、CHECK 95。

## 验证结果

- 新增负测先红：5 项按预期失败（坐标错配、sourceObject 越界、名称→代码、耗时→调整、`*_id` 无解析策略）；实现后定点测试 26/26 PASS。
- 全量 unittest：136/136 PASS。
- 生成器 `--check`：DDL 模型目录、领域迁移契约、Phase2 map、8 个 Phase3 包、字段目录全部 PASS。
- validator：核心迁移契约、命名、DDL 逐项登记、领域迁移、市场关系、Phase3 register 全部 PASS；8 个 Phase3 submission 逐个 PASS。
- `git diff --check`：PASS。
- legacy JSONL 语义 diff：canonical 3908 条、mapping 3931 条；忽略 `targetDdlSha256` 后均为 0 条语义变化，记录数与顺序不变。整文件 diff 仅因所有记录必须绑定当前统一 DDL 哈希。

## 已知限制

- 旧问卷没有可证明的必答/签名事实，CUT 来源没有完整结束窗口/当前责任人，旧工时没有动作方向证据；这些内容保持 `PENDING_FIELD_MAPPING`/迁移问题处置，不伪造值。
- AI-MIG-000 与 P3-E09 均未关闭；本报告不构成需求方或数据架构 Owner 批准。
