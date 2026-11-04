-- Forward application of domain-migration 9a757268b to production B531 + V531.2 + V533.
-- Preserve the production utf8mb4_unicode_ci comparison semantics and all existing values.
SET SESSION lock_wait_timeout = 15;

ALTER TABLE proj_project
    MODIFY COLUMN contract_no MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        NULL COMMENT '手工登记合同号（正式商业关系随 INT-02 接管；前向扩列）';

INSERT INTO infra_job
    (id, name, status, handler_name, handler_param, cron_expression,
     retry_count, retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted)
SELECT 993009532001, '项目合同号每日同步', 1, 'projectContractProjectionJob', '', '0 0 2 * * ?',
       3, 60000, 0, 'contract_projection', NOW(), 'contract_projection', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_job WHERE handler_name = 'projectContractProjectionJob' AND deleted = b'0');
