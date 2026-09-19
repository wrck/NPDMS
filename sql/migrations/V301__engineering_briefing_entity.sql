-- 工程交底独立承接：保留旧表、旧入口、旧菜单和旧权限，不执行隐式存量搬运。
-- 已有数据通过 /api/v1/pms/engineering-briefings/import-legacy 逐对象显式承接。
CREATE TABLE `sol_engineering_briefing` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键；承接对象保留原主键',
  `code` VARCHAR(64) NOT NULL COMMENT '租户内唯一交底书编号',
  `project_id` BIGINT NOT NULL COMMENT '关联项目ID',
  `name` VARCHAR(200) NOT NULL COMMENT '交底书名称',
  `briefing_type` VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  `template_id` BIGINT DEFAULT NULL,
  `template_snapshot` TEXT COMMENT '固定模板快照JSON',
  `source_snapshot` TEXT COMMENT '前序基线数据快照JSON',
  `content` TEXT COMMENT '交底内容富文本',
  `file_url` VARCHAR(512) DEFAULT NULL,
  `file_name` VARCHAR(200) DEFAULT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `file_checksum` VARCHAR(64) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿/1已生成/2已审核/3已发布/4已作废',
  `version` INT NOT NULL DEFAULT 0,
  `generate_time` DATETIME DEFAULT NULL,
  `publish_time` DATETIME DEFAULT NULL,
  `approver_user_id` BIGINT DEFAULT NULL,
  `approve_opinion` VARCHAR(500) DEFAULT NULL,
  `approve_time` DATETIME DEFAULT NULL,
  `creator_user_id` BIGINT DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `creator` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT(1) NOT NULL DEFAULT b'0',
  `tenant_id` BIGINT NOT NULL DEFAULT 0,
  `legacy_source_id` BIGINT DEFAULT NULL COMMENT '原交底主键；仅专用承接写入',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sol_briefing_code` (`code`, `tenant_id`),
  UNIQUE KEY `uk_sol_briefing_source` (`tenant_id`, `legacy_source_id`),
  KEY `idx_sol_briefing_project` (`project_id`),
  KEY `idx_sol_briefing_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='独立工程交底';

-- 只新增入口，沿用原菜单的父级和有效状态，不修改原菜单或扩大原权限范围。
INSERT INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 993109170001, '工程交底（独立实体）', permission, type, sort + 1, parent_id,
 'eng-briefing-entity', icon, 'pms/engineering/briefing/entity/index', 'PmsEngBriefingEntity',
 status, visible, keep_alive, always_show, 'briefing-entity-migration', NOW(),
 'briefing-entity-migration', NOW(), b'0'
FROM system_menu WHERE id = 19196 AND deleted = b'0';

-- 仅为已有原菜单关联的角色增加同权限入口，不新授予业务操作权限。
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT source.role_id, target.id, 'briefing-entity-migration', NOW(),
 'briefing-entity-migration', NOW(), b'0'
FROM system_role_menu source
JOIN system_menu target ON target.id = 993109170001 AND target.deleted = b'0'
WHERE source.menu_id = 19196 AND source.deleted = b'0'
AND NOT EXISTS (SELECT 1 FROM system_role_menu existing
                WHERE existing.role_id = source.role_id AND existing.menu_id = target.id);
