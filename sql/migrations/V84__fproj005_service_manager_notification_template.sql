-- F-PROJ-005 / PM-08：服务经理指派站内信模板。
-- 模板参数由指派事务冻结，异步投递不得从当前项目状态重建。

INSERT INTO system_notify_template
    (name, code, nickname, content, type, params, status, remark,
     creator, create_time, updater, update_time, deleted)
SELECT
    '服务经理指派通知',
    'pms_project_service_manager_assigned',
    '项目交付平台',
    '项目 {projectId} 的服务经理指派已生效，责任类型 {assignmentType}，层级 {levelCode}，生效时间 {effectiveFrom}',
    2,
    '["projectId","assignmentId","assignmentType","levelCode","effectiveFrom"]',
    0,
    'F-PROJ-005 服务经理人工指派通知',
    'fproj005', CURRENT_TIMESTAMP, 'fproj005', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM system_notify_template
    WHERE code = 'pms_project_service_manager_assigned'
      AND deleted = b'0'
);

INSERT INTO infra_job
    (name, status, handler_name, handler_param, cron_expression,
     retry_count, retry_interval, monitor_timeout,
     creator, create_time, updater, update_time, deleted)
SELECT
    '服务经理指派通知投递', 1, 'projectServiceManagerNotificationJob', '',
    '0/30 * * * * ?', 0, 0, 0,
    'fproj005', CURRENT_TIMESTAMP, 'fproj005', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM infra_job
    WHERE handler_name = 'projectServiceManagerNotificationJob'
      AND deleted = b'0'
);
