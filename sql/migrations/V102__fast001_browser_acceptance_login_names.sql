UPDATE `system_users`
SET `username` = CASE `id`
    WHEN 970000000000081001 THEN 'fast001readonly'
    WHEN 970000000000081002 THEN 'fast001operator'
    WHEN 970000000000081003 THEN 'fast001denied'
  END,
  `updater` = 'fast001_seed',
  `update_time` = NOW()
WHERE `id` IN (970000000000081001, 970000000000081002, 970000000000081003)
  AND `tenant_id` = 1
  AND `deleted` = b'0';
