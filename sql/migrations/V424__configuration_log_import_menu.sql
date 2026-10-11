-- Route the existing global entry to the enhanced copy; keep menu identity, roles and the old page source.
UPDATE system_menu SET component = 'pms/engineering/configuration/with-device-logs', update_time = CURRENT_TIMESTAMP
WHERE id = 19015 AND permission = 'pms:imp-configuration:query'
  AND component = 'pms/engineering/configuration/index' AND deleted = b'0';
