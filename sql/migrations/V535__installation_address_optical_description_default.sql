-- Prepared only; execution requires separate migration authorization.
-- Upgrade only the unchanged V526 default, never overwrite a customized rule.
UPDATE infra_config
SET value = '{"productFilters":[{"productTypeNotIn":["光模块"],"productDescNotLike":"%光模块%"}]}',
    updater = 'seed_20261103_optical_description', update_time = CURRENT_TIMESTAMP
WHERE config_key = 'pms.installation.address.product.filters' AND deleted = b'0'
  AND updater = 'seed_feedback20261030_optical'
  AND value = '{"productFilters":[{"productTypeNotIn":["光模块"]}]}';
