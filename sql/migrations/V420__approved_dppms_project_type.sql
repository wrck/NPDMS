-- User-approved original business code. Do not reinterpret it as a finer project category.
-- Keep any existing definition unchanged; conflicting definitions must block migration preview.
INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark,
     creator, create_time, updater, update_time, deleted)
SELECT 10, '用服售后项目', '10', 'pms_project_type', 0, '', '',
       'DPPMS fnd_basic_data.projectTypes 原业务码；用户确认同义承接',
       'dppms-migration', CURRENT_TIMESTAMP, 'dppms-migration', CURRENT_TIMESTAMP, b'0'
WHERE EXISTS (
    SELECT 1 FROM system_dict_type
    WHERE type = 'pms_project_type' AND deleted = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM system_dict_data
    WHERE dict_type = 'pms_project_type' AND value = '10' AND deleted = b'0'
);
