# F-AST-001 设备序列号档案与时态归属前向迁移设计

## 1. 背景与目标

F-AST-001 建立 AST 设备当前事实的唯一写 Owner。现有 `pms_equipment`、`pms_equipment_version`、`pms_equipment_config_log` 及老系统发货、软件版本、维保数据只作为前向迁移输入和历史读取来源，不继续承担新设备当前写入。

本设计采用以下原则：

- 保留现有 `pms-module-asset-api` 与 `pms-module-asset`，不新增独立 Device 模块。
- 新管理端资源使用 `/pms/asset/devices`，Business API 使用 `/api/v1/pms/devices`。
- 旧 `/pms/equipment` 只保留历史列表和详情读取，停止全部写操作。
- `id` 与 `sn` 均为不可变量，关系、迁移、对账和公开业务契约优先按 SN 组织。
- 设备主档采用“稳定主档 + 高频当前事实投影”，避免约 200 万设备和 400 万以上发货记录下的高频联表。
- 完整发货、归属、位置、装配、版本和维保记录独立落表，主档投影必须可对账和重建。
- 软件版本在实体层使用继承，物理层按 Owner 和业务职责独立落表。
- 业务字段必须能追溯到当前需求、F-AST-001、SDS、`specs/001` 历史数据元、老系统物理字段、现有平台表或明确业务裁决。
- 没有明确业务收益或来源依据的字段不进入模型。

## 2. 核心裁决

### 2.1 设备身份

- `ast_device.id` 是平台全局唯一技术主键，创建后不可修改。
- `ast_device.sn` 是租户内唯一设备业务身份，创建后不可修改。
- 软删除或退役不释放 SN。
- 新模型不使用设备名称、产品型号、合同号、内部条码或相似 SN 推断设备身份。
- 历史来源按 `tenant_id + 精确 SN` 汇聚。

主表唯一约束仅保留：

```sql
PRIMARY KEY (`id`),
UNIQUE KEY `uk_ast_device_tenant_sn` (`tenant_id`, `sn`)
```

不增加语义重复的 `(tenant_id, id)` 唯一索引。

### 2.2 产品字段

设备主档不保存 `product_id`，直接保存来源产品快照：

```text
product_code
product_model
product_name
product_desc
```

产品编码是主要稳定标识，型号、名称和描述不得替代产品编码进行身份判断。

### 2.3 最新发货投影

设备主档直接保存最新有效发货记录的：

```text
shipment_time
package_no
contract_no
shipment_record_id
```

四个字段必须来自同一条 `ast_device_shipment` 记录。完整发货记录保留在独立表，普通设备列表不关联发货表。

统一使用 `shipment_time` 和 `package_no`：

- `shipment_time` 使用 `datetime(3)`，兼容老系统发货日期中的时分秒。
- `package_no` 表示装箱单编号，不沿用 `packlist_no`，也不与未来正式发货单号 `shipment_no` 混淆。

### 2.4 当前归属投影

当前直接项目和客户进入设备主档：

```text
project_id
project_assignment_version
customer_id
customer_assignment_version
```

完整时态关系分别保存在：

```text
ast_device_project_relationship
ast_device_customer_relationship
```

不再建立额外的 `_current` 或 `*_assignment` 当前投影表。

### 2.5 装配关系

物理组合统一使用父子语义：

```text
parent_device_sn
child_device_sn
```

`ast_device_assembly` 使用邻接关系表达任意深度装配树。装配、拔出和替换通过有效区间保留事实，不实现固定层级。

主附 SN 合同关系、RMA 替换等非装配关系进入 `ast_device_relationship`，不混入装配树。

### 2.6 软件版本

软件版本公共实体字段统一为：

```text
conp_version
conp_type
conp_series
conp_mark
boot_version
cpld_version
pcb_version
customized
source_system
source_key
source_version
source_updated_at
synced_at
sync_status
```

历史需求中的 conboot 和老系统 `boot` 统一映射为 `boot_version`。

技术公告匹配规则：

- `conp_version` 保存来源原始完整版本，`conp_type`、`conp_series`、`conp_mark` 保存公共解析结果。
- CONP 是主匹配条件，匹配同时使用原始版本、类型、系列和版本掩码；`conp_mark` 用于版本范围或掩码匹配。
- BOOT、CPLD、PCB 是可选附加条件。
- 解析字段不得反向覆盖原始版本；解析失败时保留原始值，解析字段允许为空。
- 解析失败、字段不足或无法可靠判断的范围匹配返回 `UNDETERMINED`，不能作为不命中。

实体层使用 `SoftwareVersion` 抽象实体，出厂、官网、在网和 CUT 目标版本继承公共字段。物理层按 Owner 独立落表，不使用单表继承。

### 2.7 维保

统一使用 `warranty`，不并行定义 `maintenance_*`。

设备主档保存高频投影：

```text
warranty_start_date
warranty_end_date
warranty_status
```

完整当前维保信息保存于 `ast_device_warranty`，全部维保和续保记录保存于 `ast_device_warranty_record`。

老系统 `fb_service.warranty` 的业务含义是续保期限月数，迁移为：

```text
warranty_months
```

## 3. 查询与性能边界

以下查询应由 `ast_device` 单表或单表加权限投影完成：

- 按 SN 精确查询。
- 设备分页列表。
- 按产品编码、型号筛选。
- 按当前项目或客户筛选。
- 展示最新发货时间、装箱单号和合同号。
- 展示当前维保起止日和维保状态。
- 展示当前位置摘要。
- 展示当前 CONP 原始版本、类型、系列和标记。

以下数据只在详情或专门子资源中按需读取：

- 全部发货记录。
- 项目和客户时态关系。
- 软件版本事件。
- 维保和续保记录。
- 装配树。
- 一般设备关系。
- 配置 Log 和技术公告。

设备列表 Mapper 必须显式选择列表字段，不得使用 `SELECT *` 读取 `product_desc`、`location_snapshot` 和其他详情字段。动态权限集合、联表、递归查询、锁查询和大集合过滤必须进入 Mapper XML，并使用场景化 Query 对象。

## 4. 完整 DDL 设计稿

以下 DDL 面向 MySQL 8，是物理设计输入，不是可直接追加到已执行环境的最终 Flyway 文件。正式实施时必须按依赖顺序拆分前向迁移，不能修改已执行 SQL。

### 4.1 设备主档

```sql
CREATE TABLE `ast_device` (
  `id` bigint NOT NULL COMMENT '设备编号',
  `sn` varchar(100) NOT NULL COMMENT '租户内唯一设备序列号',
  `name` varchar(128) DEFAULT NULL COMMENT '设备名称',
  `product_code` varchar(64) DEFAULT NULL COMMENT '产品编码',
  `product_model` varchar(128) DEFAULT NULL COMMENT '产品型号',
  `product_name` varchar(128) DEFAULT NULL COMMENT '产品名称',
  `product_desc` text DEFAULT NULL COMMENT '产品描述',
  `shipment_time` datetime(3) DEFAULT NULL COMMENT '最新有效发货时间',
  `package_no` varchar(64) DEFAULT NULL COMMENT '最新有效装箱单编号',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '最新有效发货合同编号',
  `shipment_record_id` bigint DEFAULT NULL COMMENT '最新有效发货记录编号',
  `project_id` bigint DEFAULT NULL COMMENT '当前直接归属项目编号',
  `project_assignment_version` int unsigned NOT NULL DEFAULT 0 COMMENT '项目归属版本',
  `customer_id` bigint DEFAULT NULL COMMENT '当前直接归属客户编号',
  `customer_assignment_version` int unsigned NOT NULL DEFAULT 0 COMMENT '客户归属版本',
  `site_id` bigint DEFAULT NULL COMMENT '当前站点编号',
  `site_location_id` bigint DEFAULT NULL COMMENT '当前站点位置编号',
  `location_resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '当前位置解析状态',
  `location_snapshot` text DEFAULT NULL COMMENT '当前位置快照',
  `location_effective_from` datetime(3) DEFAULT NULL COMMENT '当前位置生效时间',
  `location_record_id` bigint DEFAULT NULL COMMENT '当前位置记录编号',
  `warranty_start_date` date DEFAULT NULL COMMENT '当前维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '当前维保结束日期',
  `warranty_status` varchar(32) DEFAULT NULL COMMENT '当前客观维保状态',
  `conp_version` varchar(255) DEFAULT NULL COMMENT '当前在网CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT '当前在网CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT '当前在网CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT '当前在网CONP版本掩码',
  `status` varchar(32) NOT NULL COMMENT '设备状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '平台备注',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) DEFAULT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_tenant_sn` (`tenant_id`, `sn`),
  KEY `idx_ast_device_project` (`tenant_id`, `project_id`, `deleted`, `id`),
  KEY `idx_ast_device_customer` (`tenant_id`, `customer_id`, `deleted`, `id`),
  KEY `idx_ast_device_product` (`tenant_id`, `product_code`, `product_model`, `deleted`, `id`),
  KEY `idx_ast_device_warranty` (`tenant_id`, `warranty_status`, `warranty_end_date`, `deleted`, `id`),
  KEY `idx_ast_device_shipment` (`tenant_id`, `shipment_time`, `deleted`, `id`),
  KEY `idx_ast_device_conp_parse` (`tenant_id`, `conp_type`, `conp_series`, `conp_mark`, `deleted`, `id`),
  CONSTRAINT `chk_ast_device_location_resolution`
    CHECK (`location_resolution_status` IN ('UNRESOLVED', 'RESOLVED')),
  CONSTRAINT `chk_ast_device_warranty_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备主档及高频当前事实投影';
```

### 4.2 设备出厂信息

```sql
CREATE TABLE `ast_device_factory_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '出厂信息编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `manufacturer` varchar(128) DEFAULT NULL COMMENT '设备厂商',
  `manufacture_date` date DEFAULT NULL COMMENT '出厂日期',
  `factory_config` text DEFAULT NULL COMMENT '出厂配置',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_factory_info_device` (`tenant_id`, `device_sn`),
  UNIQUE KEY `uk_ast_device_factory_info_source` (`tenant_id`, `source_system`, `source_key`),
  CONSTRAINT `fk_ast_device_factory_info_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备出厂信息';
```

### 4.3 设备发货记录

```sql
CREATE TABLE `ast_device_shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发货记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `shipment_time` datetime(3) DEFAULT NULL COMMENT '发货时间',
  `package_no` varchar(64) DEFAULT NULL COMMENT '装箱单编号',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号',
  `event_type` varchar(32) NOT NULL COMMENT '发货事件类型',
  `warranty_start_date` date DEFAULT NULL COMMENT '发货约定维保开始日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '发货约定维保月数',
  `rma_no` varchar(128) DEFAULT NULL COMMENT 'RMA编号',
  `related_device_sn` varchar(100) DEFAULT NULL COMMENT '关联设备序列号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_shipment_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_shipment_device` (`tenant_id`, `device_sn`, `shipment_time`, `id`),
  KEY `idx_ast_device_shipment_package` (`tenant_id`, `package_no`, `device_sn`),
  KEY `idx_ast_device_shipment_contract` (`tenant_id`, `contract_no`, `device_sn`),
  CONSTRAINT `fk_ast_device_shipment_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_shipment_related_device`
    FOREIGN KEY (`tenant_id`, `related_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备发货记录';
```

### 4.4 设备出厂软件版本

```sql
CREATE TABLE `ast_device_factory_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '出厂版本编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `conp_version` varchar(255) DEFAULT NULL COMMENT 'CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT 'CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT 'CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT 'CONP版本掩码',
  `boot_version` varchar(255) DEFAULT NULL COMMENT 'BOOT版本',
  `cpld_version` varchar(255) DEFAULT NULL COMMENT 'CPLD版本',
  `pcb_version` varchar(255) DEFAULT NULL COMMENT 'PCB版本',
  `customized` bit(1) DEFAULT NULL COMMENT '是否定制版本',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_factory_version_device` (`tenant_id`, `device_sn`),
  UNIQUE KEY `uk_ast_device_factory_version_source` (`tenant_id`, `source_system`, `source_key`),
  CONSTRAINT `fk_ast_device_factory_version_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备出厂软件版本';
```

### 4.5 官网产品信息

```sql
CREATE TABLE `ast_product_official_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '官网产品信息编号',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码',
  `product_model` varchar(128) DEFAULT NULL COMMENT '产品型号',
  `product_name` varchar(128) DEFAULT NULL COMMENT '产品名称',
  `product_desc` text DEFAULT NULL COMMENT '产品描述',
  `technical_spec` text DEFAULT NULL COMMENT '技术规格',
  `document_id` bigint DEFAULT NULL COMMENT '官方文档稳定引用',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_product_official_info_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_product_official_info_product` (`tenant_id`, `product_code`, `product_model`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST官网产品信息副本';
```

### 4.6 官网软件版本

```sql
CREATE TABLE `ast_product_official_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '官网版本编号',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码',
  `product_model` varchar(128) DEFAULT NULL COMMENT '产品型号',
  `conp_version` varchar(255) DEFAULT NULL COMMENT 'CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT 'CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT 'CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT 'CONP版本掩码',
  `boot_version` varchar(255) DEFAULT NULL COMMENT 'BOOT版本',
  `cpld_version` varchar(255) DEFAULT NULL COMMENT 'CPLD版本',
  `pcb_version` varchar(255) DEFAULT NULL COMMENT 'PCB版本',
  `customized` bit(1) DEFAULT NULL COMMENT '是否定制版本',
  `release_date` date DEFAULT NULL COMMENT '版本发布日期',
  `version_desc` varchar(1000) DEFAULT NULL COMMENT '版本说明',
  `document_id` bigint DEFAULT NULL COMMENT '官方文档稳定引用',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_product_official_version_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_product_official_version_product` (`tenant_id`, `product_code`, `product_model`, `status`, `release_date`, `id`),
  KEY `idx_ast_product_official_version_conp` (`tenant_id`, `product_code`, `conp_version`, `id`),
  KEY `idx_ast_product_official_version_conp_parse` (`tenant_id`, `product_code`, `conp_type`, `conp_series`, `conp_mark`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST官网软件版本副本';
```

### 4.7 当前在网软件版本

```sql
CREATE TABLE `ast_device_network_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '当前在网版本编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `conp_version` varchar(255) DEFAULT NULL COMMENT 'CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT 'CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT 'CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT 'CONP版本掩码',
  `boot_version` varchar(255) DEFAULT NULL COMMENT 'BOOT版本',
  `cpld_version` varchar(255) DEFAULT NULL COMMENT 'CPLD版本',
  `pcb_version` varchar(255) DEFAULT NULL COMMENT 'PCB版本',
  `customized` bit(1) DEFAULT NULL COMMENT '是否定制版本',
  `release_date` date DEFAULT NULL COMMENT '版本发布日期',
  `version_desc` varchar(1000) DEFAULT NULL COMMENT '版本说明',
  `effective_from` datetime(3) DEFAULT NULL COMMENT '设备版本生效时间',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_network_version_device` (`tenant_id`, `device_sn`),
  UNIQUE KEY `uk_ast_device_network_version_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_network_version_conp` (`tenant_id`, `conp_version`, `device_sn`),
  KEY `idx_ast_device_network_version_conp_parse` (`tenant_id`, `conp_type`, `conp_series`, `conp_mark`, `device_sn`),
  CONSTRAINT `fk_ast_device_network_version_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备当前在网软件版本';
```

### 4.8 在网软件版本事件

```sql
CREATE TABLE `ast_device_network_version_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '在网版本事件编号',
  `device_sn` varchar(100) DEFAULT NULL COMMENT '已映射设备序列号',
  `source_device_key` varchar(128) NOT NULL COMMENT '来源设备键',
  `source_event_key` varchar(128) NOT NULL COMMENT '来源事件键',
  `conp_version` varchar(255) DEFAULT NULL COMMENT 'CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT 'CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT 'CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT 'CONP版本掩码',
  `boot_version` varchar(255) DEFAULT NULL COMMENT 'BOOT版本',
  `cpld_version` varchar(255) DEFAULT NULL COMMENT 'CPLD版本',
  `pcb_version` varchar(255) DEFAULT NULL COMMENT 'PCB版本',
  `customized` bit(1) DEFAULT NULL COMMENT '是否定制版本',
  `release_date` date DEFAULT NULL COMMENT '版本发布日期',
  `version_desc` varchar(1000) DEFAULT NULL COMMENT '版本说明',
  `event_time` datetime(3) NOT NULL COMMENT '来源事件发生时间',
  `received_time` datetime(3) NOT NULL COMMENT '平台接收时间',
  `change_reason` varchar(500) DEFAULT NULL COMMENT '版本变更原因',
  `changed_by` varchar(128) DEFAULT NULL COMMENT '版本变更人',
  `revoked` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否撤销',
  `mapping_status` varchar(32) NOT NULL COMMENT '设备映射状态',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_network_event_source` (`tenant_id`, `source_system`, `source_device_key`, `source_event_key`),
  KEY `idx_ast_device_network_event_device` (`tenant_id`, `device_sn`, `event_time`, `id`),
  KEY `idx_ast_device_network_event_mapping` (`tenant_id`, `mapping_status`, `received_time`, `id`),
  CONSTRAINT `fk_ast_device_network_event_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备在网软件版本来源事件';
```

### 4.9 项目时态关系

```sql
CREATE TABLE `ast_device_project_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目关系编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `effective_from` datetime(3) NOT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `assignment_version` int unsigned NOT NULL COMMENT '归属版本',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `operation_id` varchar(128) NOT NULL COMMENT '操作编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_direct_device_sn` varchar(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN `relationship_type` = 'DIRECT' AND `effective_to` IS NULL AND `deleted` = b'0'
        THEN `device_sn`
        ELSE NULL
      END
    ) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_project_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_project_relationship_current` (`tenant_id`, `current_direct_device_sn`),
  KEY `idx_ast_device_project_relationship_device` (`tenant_id`, `device_sn`, `effective_to`, `project_id`, `id`),
  KEY `idx_ast_device_project_relationship_project` (`tenant_id`, `project_id`, `effective_to`, `device_sn`, `id`),
  CONSTRAINT `fk_ast_device_project_relationship_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_project_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目时态关系';
```

### 4.10 项目祖先投影

```sql
CREATE TABLE `ast_device_project_ancestor` (
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint NOT NULL COMMENT '直接归属项目编号',
  `ancestor_project_id` bigint NOT NULL COMMENT '祖先项目编号',
  `tree_version` int unsigned NOT NULL COMMENT '项目树版本',
  `assignment_version` int unsigned NOT NULL COMMENT '设备归属版本',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`tenant_id`, `device_sn`, `project_id`, `ancestor_project_id`, `tree_version`),
  KEY `idx_ast_device_project_ancestor_project` (`tenant_id`, `ancestor_project_id`, `device_sn`),
  CONSTRAINT `fk_ast_device_project_ancestor_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目祖先投影';
```

### 4.11 客户时态关系

```sql
CREATE TABLE `ast_device_customer_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户关系编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `effective_from` datetime(3) NOT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `assignment_version` int unsigned NOT NULL COMMENT '归属版本',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `operation_id` varchar(128) NOT NULL COMMENT '操作编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_direct_device_sn` varchar(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN `relationship_type` = 'DIRECT' AND `effective_to` IS NULL AND `deleted` = b'0'
        THEN `device_sn`
        ELSE NULL
      END
    ) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_customer_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_customer_relationship_current` (`tenant_id`, `current_direct_device_sn`),
  KEY `idx_ast_device_customer_relationship_device` (`tenant_id`, `device_sn`, `effective_to`, `customer_id`, `id`),
  KEY `idx_ast_device_customer_relationship_customer` (`tenant_id`, `customer_id`, `effective_to`, `device_sn`, `id`),
  CONSTRAINT `fk_ast_device_customer_relationship_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_customer_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备客户时态关系';
```

### 4.12 项目客户归属核对

```sql
CREATE TABLE `ast_device_assignment_reconciliation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '归属核对编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint DEFAULT NULL COMMENT '当前项目编号',
  `project_customer_id` bigint DEFAULT NULL COMMENT '项目当前客户编号',
  `device_customer_id` bigint DEFAULT NULL COMMENT '设备当前客户编号',
  `status` varchar(32) NOT NULL COMMENT '核对状态',
  `reason` varchar(500) DEFAULT NULL COMMENT '核对原因',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ast_device_assignment_reconciliation_device` (`tenant_id`, `device_sn`, `status`, `id`),
  CONSTRAINT `fk_ast_device_assignment_reconciliation_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目客户归属核对';
```

### 4.13 设备装配关系

```sql
CREATE TABLE `ast_device_assembly` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '装配关系编号',
  `parent_device_sn` varchar(100) NOT NULL COMMENT '父设备序列号',
  `child_device_sn` varchar(100) NOT NULL COMMENT '子设备序列号',
  `position_code` varchar(64) NOT NULL COMMENT '装配位置编码',
  `assembly_type` varchar(32) NOT NULL COMMENT '装配类型',
  `effective_from` datetime(3) NOT NULL COMMENT '装配生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '装配失效时间',
  `evidence_ref` varchar(128) DEFAULT NULL COMMENT '装配证据引用',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_parent_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `parent_device_sn` ELSE NULL END) STORED,
  `current_child_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `child_device_sn` ELSE NULL END) STORED,
  `current_position_code` varchar(64)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `position_code` ELSE NULL END) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_assembly_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_assembly_current_child` (`tenant_id`, `current_child_device_sn`),
  UNIQUE KEY `uk_ast_device_assembly_current_position` (`tenant_id`, `current_parent_device_sn`, `current_position_code`),
  KEY `idx_ast_device_assembly_parent` (`tenant_id`, `parent_device_sn`, `effective_to`, `child_device_sn`, `id`),
  KEY `idx_ast_device_assembly_child` (`tenant_id`, `child_device_sn`, `effective_to`, `parent_device_sn`, `id`),
  CONSTRAINT `fk_ast_device_assembly_parent`
    FOREIGN KEY (`tenant_id`, `parent_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_assembly_child`
    FOREIGN KEY (`tenant_id`, `child_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_assembly_self` CHECK (`parent_device_sn` <> `child_device_sn`),
  CONSTRAINT `chk_ast_device_assembly_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备装配时态关系';
```

### 4.14 一般设备关系

```sql
CREATE TABLE `ast_device_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备关系编号',
  `source_device_sn` varchar(100) NOT NULL COMMENT '来源设备序列号',
  `target_device_sn` varchar(100) NOT NULL COMMENT '目标设备序列号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号',
  `effective_from` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_relationship_source_device` (`tenant_id`, `source_device_sn`, `relationship_type`, `effective_to`, `id`),
  KEY `idx_ast_device_relationship_target_device` (`tenant_id`, `target_device_sn`, `relationship_type`, `effective_to`, `id`),
  CONSTRAINT `fk_ast_device_relationship_source_device`
    FOREIGN KEY (`tenant_id`, `source_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_relationship_target_device`
    FOREIGN KEY (`tenant_id`, `target_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_relationship_self` CHECK (`source_device_sn` <> `target_device_sn`),
  CONSTRAINT `chk_ast_device_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST一般设备时态关系';
```

### 4.15 设备位置事实

```sql
CREATE TABLE `ast_device_location` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备位置记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `site_id` bigint DEFAULT NULL COMMENT '站点编号',
  `site_location_id` bigint DEFAULT NULL COMMENT '站点位置编号',
  `resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '位置解析状态',
  `location_snapshot` text DEFAULT NULL COMMENT '位置快照',
  `effective_from` datetime(3) NOT NULL COMMENT '位置生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '位置失效时间',
  `installation_id` bigint DEFAULT NULL COMMENT '安装记录编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `device_sn` ELSE NULL END) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_location_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_location_current` (`tenant_id`, `current_device_sn`),
  KEY `idx_ast_device_location_device` (`tenant_id`, `device_sn`, `effective_to`, `effective_from`, `id`),
  KEY `idx_ast_device_location_site` (`tenant_id`, `site_id`, `site_location_id`, `effective_to`, `id`),
  CONSTRAINT `fk_ast_device_location_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_location_resolution` CHECK (`resolution_status` IN ('UNRESOLVED', 'RESOLVED')),
  CONSTRAINT `chk_ast_device_location_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备位置时态事实';
```

### 4.16 当前维保投影

```sql
CREATE TABLE `ast_device_warranty` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '当前维保编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `warranty_start_date` date DEFAULT NULL COMMENT '维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '维保结束日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '维保期限月数',
  `warranty_grade` varchar(32) DEFAULT NULL COMMENT '维保等级',
  `warranty_contract_no` varchar(64) DEFAULT NULL COMMENT '维保合同编号',
  `warranty_provider` varchar(128) DEFAULT NULL COMMENT '维保服务商',
  `warranty_type` varchar(32) DEFAULT NULL COMMENT '维保类型',
  `warranty_status` varchar(32) DEFAULT NULL COMMENT '客观维保状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_warranty_device` (`tenant_id`, `device_sn`),
  UNIQUE KEY `uk_ast_device_warranty_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_warranty_status` (`tenant_id`, `warranty_status`, `warranty_end_date`, `device_sn`),
  CONSTRAINT `fk_ast_device_warranty_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_warranty_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备当前维保投影';
```

### 4.17 维保与续保记录

```sql
CREATE TABLE `ast_device_warranty_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '维保记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `warranty_start_date` date DEFAULT NULL COMMENT '维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '维保结束日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '维保期限月数',
  `warranty_grade` varchar(32) DEFAULT NULL COMMENT '维保等级',
  `warranty_contract_no` varchar(64) DEFAULT NULL COMMENT '维保合同编号',
  `extended` bit(1) DEFAULT NULL COMMENT '是否延保',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_warranty_record_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_warranty_record_device` (`tenant_id`, `device_sn`, `warranty_start_date`, `id`),
  KEY `idx_ast_device_warranty_record_contract` (`tenant_id`, `warranty_contract_no`, `device_sn`),
  CONSTRAINT `fk_ast_device_warranty_record_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_warranty_record_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备维保与续保记录';
```

### 4.18 主档投影来源外键

主档必须在发货表和位置表创建后追加投影来源外键：

```sql
ALTER TABLE `ast_device`
  ADD CONSTRAINT `fk_ast_device_shipment_record`
    FOREIGN KEY (`shipment_record_id`) REFERENCES `ast_device_shipment` (`id`),
  ADD CONSTRAINT `fk_ast_device_location_record`
    FOREIGN KEY (`location_record_id`) REFERENCES `ast_device_location` (`id`);
```

应用服务仍必须校验投影记录与设备主档租户、SN 一致。最终 Flyway 若需要数据库级组合一致性约束，应基于真实写入性能测试决定是否增加组合引用键，不能为了形式完整添加重复唯一索引。

## 5. 投影一致性规则

### 5.1 发货投影

写入有效发货记录时：

1. 使用 `(tenant_id, source_system, source_key)` 幂等写入 `ast_device_shipment`。
2. 判断该记录是否成为当前最新有效发货记录。
3. 同步更新设备主档的 `shipment_time/package_no/contract_no/shipment_record_id`。
4. 四个字段必须来自同一条记录。
5. 迟到旧记录只写记录表，不回退主档投影。
6. 撤销或冲突记录不能成为主档投影。

### 5.2 项目和客户归属投影

直接归属变化在同一事务中：

1. 锁定设备主档。
2. 校验对应 `assignment_version`。
3. 关闭原时态关系。
4. 插入新的 `DIRECT` 关系。
5. 更新主档 `project_id/customer_id` 和对应归属版本。
6. 项目变化时重建祖先投影。
7. 项目客户与设备客户不一致时写入核对记录，不自动覆盖。

### 5.3 位置投影

位置生效时更新：

```text
site_id
site_location_id
location_resolution_status
location_snapshot
location_effective_from
location_record_id
```

所有字段来自同一条 `ast_device_location` 当前记录。未解析文本不得自动创建地点节点。

### 5.4 维保投影

当前维保变化时同步更新主档：

```text
warranty_start_date
warranty_end_date
warranty_status
```

完整字段保留在 `ast_device_warranty`，全部原始和续保记录保留在 `ast_device_warranty_record`。

### 5.5 当前主版本投影

当前在网版本变化时同源更新主档：

```text
conp_version
conp_type
conp_series
conp_mark
```

`conp_version` 保留来源原始完整版本，其余三个字段保存解析结果。解析失败不得清空或改写原始版本，解析字段允许为空。BOOT、CPLD、PCB 不进入设备主档，正式公告匹配读取 `ast_device_network_version` 完整组合。

## 6. 迁移来源映射

### 6.1 设备身份

```text
pms_equipment.serial_number
fb_shipment_barcode.barcode
pm_project_shipment.SN
pm_project_soft_version.barCode
pm_project_soft_version_history.barCode
fb_service.barcode
```

均按租户内精确 SN 汇聚。现有 `pms_equipment.id` 保留；没有平台 ID 的老设备分配新 ID。

### 6.2 主档与产品

```text
pms_equipment.name          → ast_device.name
pms_equipment.model         → ast_device.product_model
历史物料编码                → ast_device.product_code
历史产品名称                → ast_device.product_name
历史产品描述                → ast_device.product_desc
pms_equipment.status        → ast_device.status
```

仅在来源语义明确时映射，不将同一旧字段同时复制为编码和名称。

### 6.3 发货

```text
fb_shipment.packdate        → shipment_time
packlist_id/packlist_no     → package_no
合同业务编号                → contract_no
```

多次发货、返还、RMA 和再发货均保留记录。无法确认事件类型的记录进入迁移问题，不按时间猜测业务含义。

### 6.4 软件版本

```text
pm_project_soft_version.conp        → conp_version
pm_project_soft_version.conpType    → conp_type
pm_project_soft_version.conpSeries  → conp_series
pm_project_soft_version.conpMark    → conp_mark
pm_project_soft_version.boot        → boot_version
pm_project_soft_version.cpld        → cpld_version
pm_project_soft_version.pcb         → pcb_version
```

`pm_project_soft_version_history` 按完整版本组合迁移，不拆为四行。`*Bak`、`*Change` 和 `pm_project_soft_change_logs` 只用于迁移核对和操作证据，不进入软件版本公共字段。

### 6.5 归属

```text
pms_equipment.project_id
pm_project_shipment.projectId
```

用于形成项目关系候选。

```text
pms_equipment.customer_id
```

经 CUS 稳定 ID 校验后形成 `DIRECT` 客户关系候选。多当前关系、跨租户或无法解析均进入迁移问题。

### 6.6 位置

```text
pms_equipment.location                         → location_snapshot
pms_equipment.site_id                          → site_id
pms_equipment.site_location_id                 → site_location_id
pms_equipment.location_resolution_status       → resolution_status
pms_equipment.location_effective_from           → effective_from
pms_equipment.location_source_installation_id  → installation_id
```

### 6.7 维保与续保

```text
pms_equipment.warranty_start_date → warranty_start_date
pms_equipment.warranty_end_date   → warranty_end_date
fb_service.begin_date             → warranty_start_date
fb_service.end_date               → warranty_end_date
fb_service.warranty               → warranty_months
fb_service.con_xb                 → warranty_contract_no
fb_service.grade                  → warranty_grade
fb_service.isyb = 1               → extended = true
```

`isyb = 0` 不自动解释为非延保。日期和月数不一致时保留来源值并生成迁移问题，不自行覆盖。

## 7. 索引和近生产规模验证

首期索引只覆盖已明确查询：

- SN 精确查询。
- 项目设备分页。
- 客户设备分页。
- 产品和型号筛选。
- 维保状态与结束日期筛选。
- 发货时间范围筛选。
- 发货记录按设备、装箱单和合同查询。
- 当前归属唯一性。
- 装配树父查子和子查父。
- 当前在网 CONP 查询。

实施时必须在接近以下规模的数据上保存执行计划：

```text
ast_device                    约 200 万唯一设备
ast_device_shipment           400 万以上发货记录
项目与客户关系                按设备变更次数扩展
在网版本事件                  按设备版本变化次数扩展
维保记录                      按原始维保和续保记录扩展
```

验收重点：

- 设备列表不得关联 `ast_device_shipment`。
- SN 精确查询命中唯一索引。
- 项目和客户设备分页命中主档组合索引。
- 发货记录详情命中 `(tenant_id, device_sn, shipment_time, id)`。
- 设备列表不得读取 `product_desc`、`location_snapshot` 等详情字段。
- 空权限集合必须直接返回空结果。
- 索引调整只能通过新的前向迁移完成。

## 8. 旧路径退役与模块边界

- 新 AST 设备当前写路径为 `/pms/asset/devices`。
- Business API 为 `/api/v1/pms/devices`。
- 旧 `/pms/equipment` 只保留历史列表和详情读取。
- 旧新增、更新、删除、状态、归属、位置、版本、配置 Log、维保和装配写请求返回稳定退役错误。
- 不设置双写、反向同步、隐式转发或并行 Owner。
- PROJ、CUS、CUT、KNO、IMP 和其他模块只能通过 `pms-module-asset-api` 访问 AST，不得直接访问 AST Service、Mapper 或业务表。
- KNO 技术公告、EQP-02 配置 Log 文件、CUT 目标版本和平台 Outbox、幂等、审计表不在本 DDL 中重复建设。

## 9. 待实施阶段验证

- 在真实 MySQL 8 上验证全部生成列、检查约束和外键创建顺序。
- 通过失败测试验证租户内 SN 唯一和软删除不释放 SN。
- 验证发货四字段投影来自同一记录。
- 验证迟到发货不回退主档。
- 验证项目和客户当前直接关系唯一及 CAS。
- 验证装配无环、当前父节点唯一、当前位置唯一和有效区间不重叠。
- 验证老系统 `boot` 迁移为 `boot_version`。
- 验证 `fb_service.warranty` 迁移为 `warranty_months`。
- 验证软件版本完整组合不拆行。
- 验证 `conp_type`、`conp_series`、`conp_mark` 作为公共解析字段进入各版本事实和设备主档当前投影。
- 验证原始 `conp_version` 不被解析字段覆盖，解析失败时保留原始值且匹配返回 `UNDETERMINED`。
- 验证技术公告按 CONP 原始版本、类型、系列和标记执行精确或范围主匹配，BOOT/CPLD/PCB 为附加条件。
- 验证旧入口只读和真实浏览器六个详情分区。
