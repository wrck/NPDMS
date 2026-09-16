-- =============================================================
-- V284__init_integration_extsystem_tables.sql
-- 迁移自源工程 V5__init_integration_tables.sql +
-- V19__init_d365_sync_tables.sql 的建表部分（原样复制，列名适配）。
-- 供 pms-module-integration extsystem 实体映射
-- （cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog、
--  extsystem.d365.entity.D365Invoice/D365PurchaseReceipt）。
--
-- 列名适配说明：源表 create_by/update_by 重命名为 creator/updater，
-- 与 yudao BaseDO 的 creator/updater 列名直接对齐；
-- 审计字段类型 VARCHAR(64)，与 BaseEntity 中 String 类型保持一致。
--
-- 表名适配说明：源表 d365_purchase_receipt/d365_invoice 重命名为
-- int_d365_purchase_receipt/int_d365_invoice，
-- 与现有 PMS 业务表 pms_<domain>_<entity> 命名规则对齐
-- （low_*、pms_eng_*、wf_approval_* 同规则）；--
-- 说明：源 V19 对 pms_settlement 的 invoice_no/payment_status 增列不迁移——
-- pms_settlement 属于尚未迁移的结算模块表；extsystem 的结算回写
-- （D365IntegrationServiceImpl.updateSettlementInvoiceNo）为运行时反射
-- 查找 settlementMapper，bean 缺失时仅 log.warn，不阻断本模块。
-- =============================================================

-- -------------------------------------------------------------
-- 1. 外部系统集成日志表 int_log
--    log_type：D365/FP/OA/SMS/EHR
--    response_status：SUCCESS/FAILED/PENDING
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `int_log` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `log_type`         VARCHAR(32)   NOT NULL COMMENT '外部系统类型: D365/FP/OA/SMS/EHR',
    `business_type`    VARCHAR(64)   NOT NULL COMMENT '业务类型: PURCHASE_RECEIPT/PURCHASE_ORDER/SETTLEMENT/INVOICE 等',
    `business_id`      VARCHAR(64)   DEFAULT NULL COMMENT '关联业务记录ID',
    `request_url`      VARCHAR(500)  DEFAULT NULL COMMENT '请求URL',
    `request_body`     TEXT          COMMENT '请求体（JSON）',
    `response_status`  VARCHAR(32)   DEFAULT 'PENDING' COMMENT '响应状态: SUCCESS/FAILED/PENDING',
    `response_body`    TEXT          COMMENT '响应体（JSON）',
    `error_message`    VARCHAR(1000) DEFAULT NULL COMMENT '失败错误信息',
    `retry_count`      INT           DEFAULT 0 COMMENT '当前重试次数',
    `max_retry`        INT           DEFAULT 3 COMMENT '最大重试次数',
    `next_retry_time`  DATETIME      DEFAULT NULL COMMENT '下次重试时间',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT NULL COMMENT '更新时间',
    `deleted`          TINYINT       DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    KEY `idx_log_type` (`log_type`),
    KEY `idx_business_type` (`business_type`),
    KEY `idx_business_id` (`business_id`),
    KEY `idx_response_status` (`response_status`),
    KEY `idx_next_retry_time` (`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部系统集成日志';

-- -------------------------------------------------------------
-- 2. D365 采购收货表 int_d365_purchase_receipt
--    push_status：PENDING/PUSHED/FAILED
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `int_d365_purchase_receipt` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `receipt_no`       VARCHAR(64)   DEFAULT NULL COMMENT '本地收货单号',
    `po_no`            VARCHAR(64)   DEFAULT NULL COMMENT '关联采购订单号',
    `asset_id`         BIGINT        DEFAULT NULL COMMENT '关联资产ID',
    `sn`               VARCHAR(128)  DEFAULT NULL COMMENT '收货单上的资产序列号',
    `quantity`         DECIMAL(18,4) DEFAULT NULL COMMENT '收货数量',
    `received_date`    DATETIME      DEFAULT NULL COMMENT '收货日期',
    `push_status`      VARCHAR(32)   DEFAULT 'PENDING' COMMENT '推送状态: PENDING/PUSHED/FAILED',
    `pushed_at`        DATETIME      DEFAULT NULL COMMENT '最近成功推送 D365 的时间',
    `d365_receipt_id`  VARCHAR(64)   DEFAULT NULL COMMENT '成功推送后 D365 返回的标识',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT NULL COMMENT '更新时间',
    `deleted`          TINYINT       DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    KEY `idx_d365_receipt_no` (`receipt_no`),
    KEY `idx_d365_po_no` (`po_no`),
    KEY `idx_d365_receipt_push_status` (`push_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='D365 采购收货';

-- -------------------------------------------------------------
-- 3. D365 发票表 int_d365_invoice
--    push_status：PENDING/PUSHED/FAILED
--    ocr_status：PENDING/RECOGNIZED/FAILED
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `int_d365_invoice` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `invoice_no`       VARCHAR(64)   DEFAULT NULL COMMENT '发票号',
    `settlement_no`    VARCHAR(64)   DEFAULT NULL COMMENT '关联结算单号',
    `amount`           DECIMAL(18,4) DEFAULT NULL COMMENT '发票金额（不含税）',
    `tax_amount`       DECIMAL(18,4) DEFAULT NULL COMMENT '税额',
    `total_amount`     DECIMAL(18,4) DEFAULT NULL COMMENT '总金额（含税）',
    `invoice_date`     DATETIME      DEFAULT NULL COMMENT '发票日期',
    `vendor_name`      VARCHAR(128)  DEFAULT NULL COMMENT '供应商名称',
    `push_status`      VARCHAR(32)   DEFAULT 'PENDING' COMMENT '推送状态: PENDING/PUSHED/FAILED',
    `pushed_at`        DATETIME      DEFAULT NULL COMMENT '最近成功推送 D365 的时间',
    `d365_invoice_id`  VARCHAR(64)   DEFAULT NULL COMMENT '成功推送后 D365 返回的标识',
    `ocr_status`       VARCHAR(32)   DEFAULT 'PENDING' COMMENT 'OCR 状态: PENDING/RECOGNIZED/FAILED',
    `creator`          VARCHAR(64)   DEFAULT '' COMMENT '创建人',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    `updater`          VARCHAR(64)   DEFAULT '' COMMENT '更新人',
    `update_time`      DATETIME      DEFAULT NULL COMMENT '更新时间',
    `deleted`          TINYINT       DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    PRIMARY KEY (`id`),
    KEY `idx_d365_invoice_no` (`invoice_no`),
    KEY `idx_d365_invoice_settlement_no` (`settlement_no`),
    KEY `idx_d365_invoice_push_status` (`push_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='D365 发票';
