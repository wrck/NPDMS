-- =============================================================================
-- V309: 交付件归集字典口径对齐（FR-ENG-027 / Demo 6.5）
-- 背景：
--   1) pms_deliverable_status 被两个不同状态域共用：
--      - 验收侧交付件检查 acc_deliverable_checklist：0草稿/1已提交/2已通过/3已驳回（服务端 STATUS_DRAFT/SUBMITTED/PASSED/REJECTED）
--      - 工程交付件归集 imp_eng_deliverable：0待归集/1已归集/2已作废（EngStatusEnum.DELIVERABLE_PENDING/ARCHIVED/VOID）
--      原种子标签（待提交/已提交/已审核/已驳回）与检查单服务端口径不一致，且完全错位于归集域。
--   2) 工程归集件类型值域由表结构注释与自动归档路径定义：
--      DAILY 日报 / RECEIPT 签收单 / SERVICE 服务单 / COMPLETION 完工证明 / TEST 测试记录 /
--      CONFIG 配置档案（V10 表注释）+ TRAINING 现场培训（ACC-01 自动归档）+ IMPLEMENTATION 实施方案（4.1 审批通过自动归档）。
--      前端误用 pms_document_type（归档文档类型）作为归集类型下拉，导致签收单等类型无法登记。
-- 方案：pms_deliverable_status 标签对齐检查单域；新增 pms_eng_deliverable_type/pms_eng_deliverable_status 归集域专用字典。
-- =============================================================================

-- 1. 检查单域状态标签对齐（0草稿/1已提交/2已通过/3已驳回）
UPDATE `system_dict_data` SET `label` = '草稿', `update_time` = NOW(), `updater` = 'admin'
 WHERE `dict_type` = 'pms_deliverable_status' AND `value` = '0' AND `deleted` = b'0';
UPDATE `system_dict_data` SET `label` = '已提交', `update_time` = NOW(), `updater` = 'admin'
 WHERE `dict_type` = 'pms_deliverable_status' AND `value` = '1' AND `deleted` = b'0';
UPDATE `system_dict_data` SET `label` = '已通过', `update_time` = NOW(), `updater` = 'admin'
 WHERE `dict_type` = 'pms_deliverable_status' AND `value` = '2' AND `deleted` = b'0';
UPDATE `system_dict_data` SET `label` = '已驳回', `update_time` = NOW(), `updater` = 'admin'
 WHERE `dict_type` = 'pms_deliverable_status' AND `value` = '3' AND `deleted` = b'0';

-- 2. 新增归集域字典类型（幂等）
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(2115, 'PMS-交付件归集类型', 'pms_eng_deliverable_type', 0, '工程交付件归集类型（FR-ENG-027，含自动归档来源类型）', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2116, 'PMS-交付件归集状态', 'pms_eng_deliverable_status', 0, '工程交付件归集状态（0待归集/1已归集/2已作废）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- 3. 归集类型数据（值域=表注释 + TRAINING/IMPLEMENTATION 自动归档）
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(22950, 1, '工程日报', 'DAILY', 'pms_eng_deliverable_type', 0, 'primary', '', '施工日报归集', 'admin', NOW(), 'admin', NOW(), b'0'),
(22951, 2, '签收单', 'RECEIPT', 'pms_eng_deliverable_type', 0, 'success', '', '到货签收单归集（Demo 5.1→6.4）', 'admin', NOW(), 'admin', NOW(), b'0'),
(22952, 3, '服务单', 'SERVICE', 'pms_eng_deliverable_type', 0, 'info', '', '服务单归集', 'admin', NOW(), 'admin', NOW(), b'0'),
(22953, 4, '完工证明', 'COMPLETION', 'pms_eng_deliverable_type', 0, 'success', '', '完工交付件归集', 'admin', NOW(), 'admin', NOW(), b'0'),
(22954, 5, '测试记录', 'TEST', 'pms_eng_deliverable_type', 0, 'warning', '', '联调测试记录归集', 'admin', NOW(), 'admin', NOW(), b'0'),
(22955, 6, '配置档案', 'CONFIG', 'pms_eng_deliverable_type', 0, 'info', '', '设备配置档案归集', 'admin', NOW(), 'admin', NOW(), b'0'),
(22956, 7, '现场培训记录', 'TRAINING', 'pms_eng_deliverable_type', 0, 'success', '', 'ACC-01 客户确认自动归档', 'admin', NOW(), 'admin', NOW(), b'0'),
(22957, 8, '实施方案', 'IMPLEMENTATION', 'pms_eng_deliverable_type', 0, 'danger', '', '4.1 审批通过自动归档', 'admin', NOW(), 'admin', NOW(), b'0');

-- 4. 归集状态数据（0待归集/1已归集/2已作废，同 EngStatusEnum）
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(22960, 0, '待归集', '0', 'pms_eng_deliverable_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22961, 1, '已归集', '1', 'pms_eng_deliverable_status', 0, 'primary', '', '已归集不可修改/删除，仅可作废', 'admin', NOW(), 'admin', NOW(), b'0'),
(22962, 2, '已作废', '2', 'pms_eng_deliverable_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
