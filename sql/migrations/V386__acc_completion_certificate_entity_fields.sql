-- V386: 完工证明表单附加字段实体化（设备明细子表）。
--
-- 背景：完工证明按需求方 7 点反馈重构表单（客户单位/合同号/工程师/服务类型/
-- 工程服务内容五项/设备类型和数量子表/双方签章及日期）。这些字段此前随
-- remark JSON 信封存储（remark varchar(500) 装不下且无法统计取数），需求方
-- 明确要求全部落实为实体字段，设备明细建子表，支撑按型号/类型统计数量。
--
-- 既有数据的 remark/content 信封由前端读取兼容，编辑保存后落实体列；
-- 本迁移不回填历史数据，不动既有列。
ALTER TABLE `acc_completion_certificate`
    ADD COLUMN `service_type` varchar(32) DEFAULT NULL COMMENT '工程服务类型：工程实施/工程督导' AFTER `certificate_no`,
    ADD COLUMN `engineer_user_id` bigint DEFAULT NULL COMMENT '迪普工程师用户编号' AFTER `service_type`,
    ADD COLUMN `engineer_name` varchar(64) DEFAULT NULL COMMENT '迪普工程师姓名（落证时快照）' AFTER `engineer_user_id`,
    ADD COLUMN `engineer_contact` varchar(64) DEFAULT NULL COMMENT '迪普工程师联系方式' AFTER `engineer_name`,
    ADD COLUMN `customer_unit` varchar(255) DEFAULT NULL COMMENT '客户单位' AFTER `engineer_contact`,
    ADD COLUMN `contract_no` varchar(64) DEFAULT NULL COMMENT '合同号' AFTER `customer_unit`,
    ADD COLUMN `item_arrival` varchar(16) DEFAULT NULL COMMENT '工程服务内容① 完成到货验收：是/否/不涉及' AFTER `contract_no`,
    ADD COLUMN `item_install` varchar(16) DEFAULT NULL COMMENT '工程服务内容② 完成设备硬件安装和软件调测：是/否/不涉及' AFTER `item_arrival`,
    ADD COLUMN `item_cutover` varchar(16) DEFAULT NULL COMMENT '工程服务内容③ 完成业务上线/割接且业务测试正常：是/否/不涉及' AFTER `item_install`,
    ADD COLUMN `item_training` varchar(16) DEFAULT NULL COMMENT '工程服务内容④ 完成产品维护现场讲解和培训：是/否/不涉及' AFTER `item_cutover`,
    ADD COLUMN `item_docs` varchar(16) DEFAULT NULL COMMENT '工程服务内容⑤ 工程文档、帐号密码已移交并协助修改：是/否/不涉及' AFTER `item_training`,
    ADD COLUMN `customer_sign_url` varchar(500) DEFAULT NULL COMMENT '甲方签章图片地址' AFTER `item_docs`,
    ADD COLUMN `customer_sign_date` date DEFAULT NULL COMMENT '甲方签章日期' AFTER `customer_sign_url`,
    ADD COLUMN `vendor_sign_url` varchar(500) DEFAULT NULL COMMENT '服务方签章图片地址' AFTER `customer_sign_date`,
    ADD COLUMN `vendor_sign_date` date DEFAULT NULL COMMENT '服务方签章日期' AFTER `vendor_sign_url`;

CREATE TABLE `acc_completion_certificate_device` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `certificate_id` bigint NOT NULL COMMENT '完工证明编号',
    `device_type` varchar(128) DEFAULT NULL COMMENT '设备类型',
    `device_model` varchar(128) DEFAULT NULL COMMENT '设备型号',
    `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
    `sort` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_acc_cert_device_certificate` (`certificate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电子完工证明设备明细';
