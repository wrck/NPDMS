-- IMP当前有效安装为来源，AST设备当前文本为目标；不修改安装记录、位置历史或快照。
SET SESSION lock_wait_timeout = 15;
SET @installation_address_column := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ast_device' AND column_name = 'installation_address'
);
SET @installation_address_ddl := IF(@installation_address_column > 0, 'SELECT 1',
    'ALTER TABLE ast_device ADD COLUMN installation_address TEXT NULL COMMENT "当前有效安装完整地址文本", ALGORITHM=INSTANT');
PREPARE installation_address_stmt FROM @installation_address_ddl;
EXECUTE installation_address_stmt;
DEALLOCATE PREPARE installation_address_stmt;

-- 严格核对租户、设备、项目、当前AST位置记录与安装来源、生效时间，防止旧记录覆盖新位置。
UPDATE ast_device d
JOIN ast_device_location l ON l.tenant_id = d.tenant_id AND l.id = d.location_record_id AND l.device_sn = d.sn
JOIN imp_eng_installation i ON i.tenant_id = l.tenant_id AND i.id = l.installation_id AND i.equipment_id = d.id AND i.project_id = d.project_id
SET d.installation_address = i.install_location, d.version = d.version + 1,
    d.updater = 'installation-address-projection', d.update_time = CURRENT_TIMESTAMP
WHERE d.deleted = b'0' AND l.deleted = b'0' AND i.deleted = b'0'
  AND d.installation_address IS NULL AND l.source_system = 'IMP'
  AND l.effective_to IS NULL AND i.status = 2 AND i.effective_to IS NULL
  AND i.effective_from IS NOT NULL AND i.effective_from = l.effective_from
  AND i.effective_from = d.location_effective_from
  AND i.install_location IS NOT NULL AND TRIM(i.install_location) <> '';
