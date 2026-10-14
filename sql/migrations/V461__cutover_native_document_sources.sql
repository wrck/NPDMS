-- CUT native documents use the platform file identity/version and delivery catalog.
-- Add only the declared CUT source classifications; retain existing catalog settings.
INSERT INTO `plt_delivery_type`
(`type_code`, `name`, `category`, `allowed_media_json`, `max_size_bytes`, `enabled`,
 `remark`, `version`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT source.`code`, source.`name`, '割接交付',
       JSON_ARRAY('application/pdf', 'image/jpeg', 'image/png', 'text/plain',
                  'application/msword',
                  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                  'application/vnd.ms-excel',
                  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
       52428800, b'1', 'CUT native document source; file authorization and version remain with PLT',
       0, tenant.`tenant_id`, 'cut-special-20261013', NOW(), 'cut-special-20261013', NOW(), b'0'
FROM (SELECT DISTINCT `tenant_id` FROM `plt_delivery_type` WHERE `deleted` = b'0') tenant
CROSS JOIN (
    SELECT 'CUT.PLAN' AS `code`, '割接方案' AS `name`
    UNION ALL SELECT 'CUT.CHECKLIST_EVIDENCE', '割接清单证据'
    UNION ALL SELECT 'CUT.POST_COLLECTION_CHECKLIST', '割接后检查清单'
    UNION ALL SELECT 'CUT.IMPLEMENTATION_COMMITMENT', '割接实施承诺'
    UNION ALL SELECT 'CUT.CLOSURE_EVIDENCE', '割接闭环其他证据'
    UNION ALL SELECT 'CUT.MANUAL_COLLECTION_RESULT', '割接闭环人工采集结果'
) source
WHERE NOT EXISTS (
    SELECT 1 FROM `plt_delivery_type` existing
    WHERE existing.`tenant_id` = tenant.`tenant_id`
      AND existing.`type_code` = source.`code` AND existing.`deleted` = b'0'
);
