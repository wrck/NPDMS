-- V383(原 theirs V361): com_sales_order 增加主导引用列(主合同号/主执行单号)。
-- 依据:源表 pm_order_data_from_erp 头级原生列 contractNo/orderExecNumber,
-- 老系统 query-project-bycontractno 全链路 JOIN 键;ADR-0001 关系表承载 N:N 特例,
-- 主导引用直接落列供合同→订单→执行单链路索引直连。
ALTER TABLE com_sales_order
    ADD COLUMN contract_no VARCHAR(64) NULL
        COMMENT '主合同号(快照来源订单头contractNo,对应合同主档业务键)' AFTER sales_type,
    ADD COLUMN execution_no VARCHAR(64) NULL
        COMMENT '主执行单号(快照来源订单头orderExecNumber)' AFTER contract_no,
    ADD KEY idx_sales_order_contract (tenant_id, contract_no),
    ADD KEY idx_sales_order_execution (tenant_id, execution_no);
