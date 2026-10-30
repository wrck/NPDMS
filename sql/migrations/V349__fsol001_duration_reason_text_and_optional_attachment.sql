-- =============================================================================
-- F-SOL-001：工期变更原因改自由文本填写，客户依据改可选附件。
-- 原因码固定落字典新值 OTHER（其它）；OTHER 不在客户依据必填原因配置内，
-- 提交时不再按原因要求冻结客户依据文件，已上传附件仍会冻结具体版本。
-- =============================================================================

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992101020002, 2, '其它', 'OTHER',
       'pms_duration_change_reason_type', 0, 'info', '',
       '工期变更原因改自由文本填写后的默认原因码；不要求冻结客户依据',
       'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'pms_duration_change_reason_type'
      AND `value` = 'OTHER' AND `deleted` = b'0'
);

UPDATE `system_dict_data`
SET `sort` = 2, `label` = '其它', `status` = 0,
    `color_type` = 'info',
    `remark` = '工期变更原因改自由文本填写后的默认原因码；不要求冻结客户依据',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'pms_duration_change_reason_type'
  AND `value` = 'OTHER';
