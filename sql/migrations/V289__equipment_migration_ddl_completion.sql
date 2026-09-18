-- V289: 补齐并行工作区残缺 V285 副本缺失的两段 DDL（ast_device_version 建表 + 配置日志表改名）
-- 数据段（主档与五张关系表）已由该副本生效，菜单/权限/字典已由 V288 补齐；此处只补本库仍缺失的表结构。

-- 设备档案版本历史（与 V285 定义一致；幂等建表）
CREATE TABLE IF NOT EXISTS `ast_device_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` BIGINT NOT NULL COMMENT '设备编号（ast_device.id）',
  `version_no` INT NOT NULL COMMENT '设备内递增版本号',
  `change_type` VARCHAR(32) NOT NULL COMMENT '变更类型 CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP',
  `change_description` VARCHAR(500) NULL DEFAULT NULL COMMENT '变更说明',
  `before_snapshot` JSON NULL COMMENT '变更前快照',
  `after_snapshot` JSON NULL COMMENT '变更后快照',
  `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ast_device_version_device` (`tenant_id`, `device_id`, `version_no`)
) ENGINE = InnoDB COMMENT = 'AST 设备档案版本历史（追加只读）';

-- 配置日志表跟随设备档案迁移（新链 DeviceConfigurationLogQueryService 已按 ast_device 主体消费本表）
RENAME TABLE `pms_equipment_config_log` TO `ast_device_config_log`;
ALTER TABLE `ast_device_config_log` RENAME COLUMN `equipment_id` TO `device_id`;
