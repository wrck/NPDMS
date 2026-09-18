-- V300: 合同主档字段归并（需求方 2026-09-18 指令）
-- com_contract 作为唯一合同主档直接包含合并后的全部合同级字段；
-- 不创建 com_contract_summary 视图（该视图方案由本迁移替代）。
-- 语义契约（记录于 Q-MIG-DIM-006 与 F-COM-001）：
--   1. 主档新增列为回款/发货归属来源中承载的合同级属性，与主档既有列去除重复含义后归并：
--      客户、合同类型、合同名称、合同金额、币种编码、生效/失效日期等既有主档列不重复增设。
--   2. 来源表（com_contract_receivable、com_shipment_contract_reference）保持不变，继续作为来源证据；
--      "去除重复含义"指展示与消费以主档列为唯一权威，不在主档外再建汇总视图。
--   3. 来源数据同步入库时，以该来源行的非空值更新主档对应列，最新同步覆盖先前值；
--      回款与发货归属共用同一主档列（如办事处、市场部、项目名称）。
--   4. ERP Owner 字段（客户、合同类型、合同名称、合同金额、币种编码、生效/失效日期、状态）
--      仍由 ERP 主档同步负责，回款/发货归属同步不覆盖。

ALTER TABLE com_contract
    ADD COLUMN currency_name VARCHAR(32) NULL COMMENT '币种名称' AFTER currency_code,
    ADD COLUMN contract_create_time DATETIME(3) NULL COMMENT '合同创建时间' AFTER currency_name,
    ADD COLUMN project_name VARCHAR(512) NULL COMMENT '项目名称' AFTER expiry_date,
    ADD COLUMN project_code VARCHAR(80) NULL COMMENT '项目编码' AFTER project_name,
    ADD COLUMN market_code VARCHAR(64) NULL COMMENT '市场部编码' AFTER project_code,
    ADD COLUMN market_name VARCHAR(128) NULL COMMENT '市场部名称' AFTER market_code,
    ADD COLUMN department_code VARCHAR(64) NULL COMMENT '办事处编码' AFTER market_name,
    ADD COLUMN department_name VARCHAR(128) NULL COMMENT '办事处名称' AFTER department_code,
    ADD COLUMN system_code VARCHAR(64) NULL COMMENT '系统部编码' AFTER department_name,
    ADD COLUMN system_name VARCHAR(255) NULL COMMENT '系统部名称' AFTER system_code,
    ADD COLUMN expend_code VARCHAR(64) NULL COMMENT '拓展部编码' AFTER system_name,
    ADD COLUMN expend_name VARCHAR(255) NULL COMMENT '拓展部名称' AFTER expend_code,
    ADD COLUMN industry_code VARCHAR(64) NULL COMMENT '行业编码' AFTER expend_name,
    ADD COLUMN industry_name VARCHAR(128) NULL COMMENT '行业名称' AFTER industry_code,
    ADD COLUMN marketing_representative_code VARCHAR(64) NULL COMMENT '市场代表编码' AFTER industry_name,
    ADD COLUMN marketing_representative_name VARCHAR(128) NULL COMMENT '市场代表名称' AFTER marketing_representative_code,
    ADD COLUMN secondary_representative_code VARCHAR(64) NULL COMMENT '辅助代表编码' AFTER marketing_representative_name;
