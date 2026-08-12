-- =============================================================================
-- V45: PMS 字典类型补充（V43/V44 遗漏的辅助字段字典化）
-- 背景：V43/V44 覆盖了主要状态/类型，但触发来源、库存状态、资源类型、审核级别
--      仍为前端硬编码。本迁移补充这些字典类型及数据。
-- =============================================================================

-- ============================================================
-- 1. 字典类型（system_dict_type）
-- ============================================================
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(2100, 'PMS-触发来源', 'pms_trigger_source', 0, '触发来源(手动/资源就绪/方案)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2101, 'PMS-库存状态', 'pms_stock_status', 0, '库存状态(有库存/缺货/已预留)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2102, 'PMS-资源类型', 'pms_resource_type', 0, '工程资源类型(人员/备件/工具/测试环境/时间窗口/客户批准)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2103, 'PMS-审核级别', 'pms_review_level', 0, '方案审核级别(普通/重大)', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- ============================================================
-- 2. 字典数据（system_dict_data）
-- ============================================================
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- PMS-触发来源 (pms_trigger_source)
(21900, 1, '手动', 'MANUAL', 'pms_trigger_source', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21901, 2, '资源就绪', 'RESOURCE_READY', 'pms_trigger_source', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21902, 3, '方案', 'SOLUTION', 'pms_trigger_source', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-库存状态 (pms_stock_status)
(21910, 1, '有库存', 'IN_STOCK', 'pms_stock_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21911, 2, '缺货', 'OUT_OF_STOCK', 'pms_stock_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21912, 3, '已预留', 'RESERVED', 'pms_stock_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-资源类型 (pms_resource_type)
(21920, 1, '人员', 'PEOPLE', 'pms_resource_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21921, 2, '备件', 'SPARE', 'pms_resource_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21922, 3, '工具', 'TOOL', 'pms_resource_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21923, 4, '测试环境', 'TEST_ENV', 'pms_resource_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21924, 5, '时间窗口', 'WINDOW', 'pms_resource_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21925, 6, '客户批准', 'APPROVAL', 'pms_resource_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-审核级别 (pms_review_level)
(21930, 0, '普通', '0', 'pms_review_level', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21931, 1, '重大（需总部复审）', '1', 'pms_review_level', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
