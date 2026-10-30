-- 2026-10-30 approved feedback: only the authoritative 光模块 product type is exempt.
-- Prepared only. Do not execute without explicit migration authorization.
-- Upgrade only the untouched V521 empty seed; user configuration is never overwritten.
UPDATE infra_config
SET value = '{"productFilters":[{"productTypeNotIn":["光模块"]}]}',
    updater = 'seed_feedback20261030_optical', update_time = CURRENT_TIMESTAMP
WHERE config_key = 'pms.installation.address.product.filters' AND deleted = b'0'
  AND creator = 'seed_tf040_address_filters' AND updater = 'seed_tf040_address_filters'
  AND value = '{"productFilters":[]}';

INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT 992210300526, 'pms', 2, '安装地址产品分组过滤', 'pms.installation.address.product.filters',
       '{"productFilters":[{"productTypeNotIn":["光模块"]}]}', b'0',
       '按产品主档类型免除光模块安装地址；类型为空或缺主档仍必填。组间OR组内AND，匹配必填，其余免填。',
       'seed_feedback20261030_optical', 'seed_feedback20261030_optical', b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'pms.installation.address.product.filters' AND deleted = b'0');
