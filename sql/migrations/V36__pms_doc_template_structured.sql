-- =====================================================================
-- V36: 工程文档模板（需求分析/实施方案）结构化模板化
-- 参考中兴iEPMS结构化文档自动生成、华为IPD章节裁剪、CISCO PPDIOO设计文档
-- 设计要点：
--   1. 新增 pms_eng_doc_template（文档模板主表）+ pms_eng_doc_template_version（版本表）
--   2. 扩展 pms_eng_requirement / pms_eng_solution 增加 template_id / template_snapshot / section_data
--   3. 保留现有固定列，向后兼容（双写过渡）
--   4. applicability JSON 支持组合条件筛选（项目类型+网络类型+产品类型+实施模式）
--   5. sections JSON 采用"章节+字段"两层结构，兼容 form-create rule
-- =====================================================================

-- ============ 1. 文档模板主表 ============
CREATE TABLE IF NOT EXISTS `pms_eng_doc_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(64) NOT NULL COMMENT '模板编号（如 DT-REQ-2026-001），全局唯一',
  `name` varchar(200) NOT NULL COMMENT '模板名称',
  `doc_category` varchar(32) NOT NULL COMMENT '文档类别：REQUIREMENT 需求分析 / SOLUTION 实施方案',
  `parent_template_id` bigint DEFAULT NULL COMMENT '父模板ID（支持继承，NULL表示基础模板）',
  `applicability` json NOT NULL COMMENT '适用条件JSON：projectType/networkType/productType/implementMode/priority/isDefault',
  `description` varchar(500) DEFAULT NULL COMMENT '模板说明',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前生效版本ID（指向 pms_eng_doc_template_version.id）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 1 已发布 2 已停用',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_doc_template_code` (`code`, `tenant_id`),
  KEY `idx_pms_eng_doc_template_category` (`doc_category`, `status`),
  KEY `idx_pms_eng_doc_template_parent` (`parent_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程文档模板（需求分析/实施方案）';

-- ============ 2. 文档模板版本表 ============
CREATE TABLE IF NOT EXISTS `pms_eng_doc_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `version_label` varchar(32) NOT NULL COMMENT '版本标签（SemVer，如 1.0.0）',
  `sections` json NOT NULL COMMENT '章节定义JSON（sections数组，每个section含code/title/fields的form-create规则）',
  `section_overrides` json DEFAULT NULL COMMENT '相对父模板的章节覆盖声明（key=章节编码，value=覆盖配置）',
  `excluded_sections` json DEFAULT NULL COMMENT '排除的父模板章节编码列表（如 ["wireless","log_retention"]）',
  `change_log` text DEFAULT NULL COMMENT '版本变更说明',
  `published` tinyint NOT NULL DEFAULT 0 COMMENT '0 未发布 1 已发布（已发布版本不可修改）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_eng_doc_template_version` (`template_id`, `version_label`, `tenant_id`),
  KEY `idx_pms_eng_doc_template_version_template` (`template_id`, `published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程文档模板版本';

-- ============ 3. 扩展需求分析表（保留固定列，新增模板关联字段） ============
ALTER TABLE `pms_eng_requirement`
  ADD COLUMN `template_id` bigint DEFAULT NULL COMMENT '关联文档模板ID' AFTER `requirement_type`,
  ADD COLUMN `template_version_id` bigint DEFAULT NULL COMMENT '关联模板版本ID' AFTER `template_id`,
  ADD COLUMN `template_snapshot` json DEFAULT NULL COMMENT '模板快照JSON（创建时的模板结构，不可变）' AFTER `template_version_id`,
  ADD COLUMN `section_data` json DEFAULT NULL COMMENT '章节填写数据JSON（key=章节编码，value=章节内容）' AFTER `template_snapshot`;

-- ============ 4. 扩展实施方案表（保留固定列，新增模板关联字段） ============
ALTER TABLE `pms_eng_solution`
  ADD COLUMN `template_id` bigint DEFAULT NULL COMMENT '关联文档模板ID' AFTER `solution_type`,
  ADD COLUMN `template_version_id` bigint DEFAULT NULL COMMENT '关联模板版本ID' AFTER `template_id`,
  ADD COLUMN `template_snapshot` json DEFAULT NULL COMMENT '模板快照JSON' AFTER `template_version_id`,
  ADD COLUMN `section_data` json DEFAULT NULL COMMENT '章节填写数据JSON' AFTER `template_snapshot`;
