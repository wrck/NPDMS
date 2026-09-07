-- NFR-01/02, PRD amendment 014: default to the existing tenant 1.
-- V1 creates tenant 1. Keep all historical rows; disable only identified built-in samples.
UPDATE system_tenant
SET status = 1, updater = 'seed', update_time = CURRENT_TIMESTAMP
WHERE deleted = b'0' AND status = 0
  AND ((id = 121 AND name = '小租户' AND creator = '1' AND package_id = 111)
    OR (id = 122 AND name = '测试租户' AND creator = '1' AND package_id = 111)
    OR (id = 970000000000090000 AND name = 'FCUS001验收租户' AND creator = 'seed' AND package_id = 111));
