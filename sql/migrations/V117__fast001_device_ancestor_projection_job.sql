INSERT INTO infra_job
    (name, status, handler_name, handler_param, cron_expression,
     retry_count, retry_interval, monitor_timeout,
     creator, create_time, updater, update_time, deleted)
SELECT
    '设备祖先投影重建', 1, 'deviceAssignedProjectionJob', '',
    '0/30 * * * * ?', 0, 0, 0,
    'fast001', CURRENT_TIMESTAMP, 'fast001', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM infra_job
    WHERE handler_name = 'deviceAssignedProjectionJob'
      AND deleted = b'0'
);
