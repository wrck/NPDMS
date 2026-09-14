-- Technical Outbox delivery only. No project state/history rewrite and no business calendar.
INSERT INTO infra_job
    (id, name, status, handler_name, handler_param, cron_expression,
     retry_count, retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted)
SELECT 993009230001, '项目规则重评事件投递', 1, 'projectRuleOutboxDeliveryJob', '', '0/30 * * * * ?',
       0, 0, 0, 'rule_remediation', NOW(), 'rule_remediation', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM infra_job WHERE handler_name = 'projectRuleOutboxDeliveryJob' AND deleted = b'0'
);
