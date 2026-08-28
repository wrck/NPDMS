-- =============================================================================
-- V60: 项目树与进度汇总基座前向扩列（F-PM02 / PM-02）
--
-- 依据：F-PM02 Feature Spec §7 与 Technical Plan §2。
-- proj_project 已在 V57 落地树邻接列（parent_id/root_id/tree_path/tree_depth/
-- tree_sort），本迁移只补 PM-02 缺列：业务层级标签、进度、权重（等权兜底）。
--
-- 前向扩列依据：SDS 09 §12.1（目标 DDL 缺列，按前向扩列补齐，不反向改目标 DDL）。
-- 权重/汇总口径审批版本化、子项目进度来源（阶段推进/任务闭环）均不在本 Feature
-- （Out of Scope），本迁移仅承载等权兜底 + 手动权重 + 合计 100% 应用层校验所需列。
-- =============================================================================

-- 1. proj_project 前向扩列（业务层级 + 进度 + 权重）
ALTER TABLE `proj_project`
    ADD COLUMN `business_level_code` VARCHAR(64)  NULL COMMENT '业务层级标签编码（与结构层级 tree_depth 分离；字典 pms_project_business_level）' AFTER `tree_sort`,
    ADD COLUMN `business_level_name` VARCHAR(128) NULL COMMENT '业务层级标签名称（拆分/移动时可指定）' AFTER `business_level_code`,
    ADD COLUMN `progress`            DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '项目进度百分比（来源属 PM-11 阶段推进/任务闭环，本 Feature 仅消费做汇总）' AFTER `status`,
    ADD COLUMN `aggregation_weight`  DECIMAL(5,2) NULL COMMENT '相对直接父项目的权重（NULL=等权，读时按直接子项目数归一化）' AFTER `progress`,
    ADD COLUMN `weight_source`       VARCHAR(32)  NULL COMMENT '权重来源：DEFAULT_EQUAL 默认等权 / MANUAL 人工设置' AFTER `aggregation_weight`;

-- 2. 字典：业务层级标签（示例值，非权威来源映射，可扩展）
INSERT IGNORE INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
('PMS-项目业务层级', 'pms_project_business_level', 0, '项目业务层级标签（与结构层级分离，PM-02）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '大区', 'LEVEL_REGION', 'pms_project_business_level', 0, 'primary', '', '大区级业务层级', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '办事处', 'LEVEL_OFFICE', 'pms_project_business_level', 0, 'info', '', '办事处级业务层级', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '节点', 'LEVEL_NODE', 'pms_project_business_level', 0, 'warning', '', '节点级业务层级', 'admin', NOW(), 'admin', NOW(), b'0');

-- 3. 菜单：项目详情工作台（18071 页面，挂 19261 项目管理组；非导航直链，由列表"详情"跳转）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18071, '项目详情', 'pms:project:query', 2, 0, 19261, 'project-master-detail', 'ep:document', 'pms/project/project-master-detail/index', 'PmsProjectMasterDetail', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `update_time`=NOW(), `deleted`=b'0';
