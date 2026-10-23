-- 复用当前位置快照的完整文本；只修正AST当前投影，不改安装和位置历史。
SET SESSION lock_wait_timeout = 15;
UPDATE ast_device d
JOIN ast_device_location l ON l.tenant_id = d.tenant_id AND l.id = d.location_record_id AND l.device_sn = d.sn
JOIN imp_eng_installation i ON i.tenant_id = l.tenant_id AND i.id = l.installation_id AND i.equipment_id = d.id AND i.project_id = d.project_id
SET d.location_snapshot = JSON_SET(
        CASE WHEN d.location_snapshot IS NULL OR TRIM(d.location_snapshot) = '' THEN JSON_OBJECT()
             ELSE d.location_snapshot END, '$.fallbackLocation', i.install_location),
    d.version = d.version + 1, d.updater = 'installation-snapshot-projection', d.update_time = CURRENT_TIMESTAMP
WHERE d.deleted = b'0' AND l.deleted = b'0' AND i.deleted = b'0'
  AND l.source_system = 'IMP' AND l.effective_to IS NULL
  AND i.status = 2 AND i.effective_to IS NULL AND i.effective_from IS NOT NULL
  AND i.effective_from = l.effective_from AND i.effective_from = d.location_effective_from
  AND i.install_location IS NOT NULL AND TRIM(i.install_location) <> ''
  AND (d.location_snapshot IS NULL OR TRIM(d.location_snapshot) = ''
       OR (JSON_VALID(d.location_snapshot) AND JSON_TYPE(CASE WHEN JSON_VALID(d.location_snapshot) THEN d.location_snapshot ELSE NULL END) = 'OBJECT'))
  AND NOT (JSON_UNQUOTE(JSON_EXTRACT(CASE WHEN JSON_VALID(d.location_snapshot) THEN d.location_snapshot ELSE NULL END, '$.fallbackLocation')) <=> i.install_location);

-- V420引入的冗余列已由原快照字段承接；安装来源及历史始终保留。
ALTER TABLE ast_device DROP COLUMN installation_address, ALGORITHM=INSTANT;
