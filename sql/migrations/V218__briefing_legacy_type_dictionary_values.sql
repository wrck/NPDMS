-- PRE-05 legacy-first reuse: the old BriefingSaveReqVO/BriefingDO and retained
-- pms_eng_briefing rows use STANDARD / EMERGENCY / CUSTOM. Reuse the platform
-- dictionary; do not replace TECHNICAL / SAFETY or re-enable configured values.
INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT seed.id, seed.sort, seed.label, seed.value, 'pms_briefing_type', 0,
       seed.color_type, '', '来源：旧工程交底类型及保留记录', 'demo-s1-briefing', 'demo-s1-briefing'
FROM (
    SELECT 993109100301 AS id, 10 AS sort, '标准' AS label, 'STANDARD' AS value, 'primary' AS color_type
    UNION ALL SELECT 993109100302, 20, '紧急', 'EMERGENCY', 'warning'
    UNION ALL SELECT 993109100303, 30, '自定义', 'CUSTOM', 'info'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_data existing
    WHERE existing.dict_type = 'pms_briefing_type'
      AND existing.value = seed.value AND existing.deleted = b'0'
);
