-- V46's dictionary type ID 2105 collided with an upstream MES dictionary.
-- Keep the existing V46 business values and any installed configuration/history.
START TRANSACTION;

INSERT INTO system_dict_type
    (id, name, type, status, remark, creator, updater, deleted)
SELECT 975500000001, 'PMS-项目风险状态', 'pms_project_risk_status', 0,
       '项目风险状态(已识别/处理中/已关闭/已发生)', 'V500', 'V500', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_type
    WHERE type = 'pms_project_risk_status' AND deleted = b'0'
);

INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater, deleted)
SELECT seed.id, seed.sort, seed.label, seed.value, 'pms_project_risk_status', 0,
       seed.color_type, '', '', 'V500', 'V500', b'0'
FROM (
    SELECT 975500001000 AS id, 0 AS sort, '已识别' AS label, '0' AS value, 'info' AS color_type
    UNION ALL SELECT 975500001001, 1, '处理中', '1', 'primary'
    UNION ALL SELECT 975500001002, 2, '已关闭', '2', 'success'
    UNION ALL SELECT 975500001003, 3, '已发生', '3', 'danger'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_data existing
    WHERE existing.dict_type = 'pms_project_risk_status'
      AND existing.value = seed.value AND existing.deleted = b'0'
);

COMMIT;
