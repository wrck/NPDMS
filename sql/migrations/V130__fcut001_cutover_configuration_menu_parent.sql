-- F-CUT-001: align the new configuration entry with the current cutover navigation group.
-- Later navigation migrations moved the existing CUT pages from project delivery (18000)
-- to the cutover delivery group (19263); keep this forward-only correction explicit.
UPDATE `system_menu`
SET `parent_id` = 19263,
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `id` = 199500
  AND `parent_id` = 18000;
