-- TF-040: opt-in grouped installation address requirements using current product field names.
-- Prepared only: execution remains subject to explicit migration authorization.
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT 992210280521, 'pms', 2, '安装地址产品分组过滤', 'pms.installation.address.product.filters',
       '{"productFilters":[]}', b'0',
       '组间OR组内AND，匹配必填，其余免填；空组列表保持全部必填。支持编码、型号、描述、分类、类型的Like/In/NotIn及alternativeGroups。',
       'seed_tf040_address_filters', 'seed_tf040_address_filters', b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'pms.installation.address.product.filters' AND deleted = b'0');
