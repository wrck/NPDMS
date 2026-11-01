-- 客户外发地址由管理员在配置管理中设置；不覆盖环境已有配置。
INSERT INTO infra_config (category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT 'pms-customer', 1, '客户外部访问基础地址', 'pms.customer.public-base-url', '', b'1',
       '请配置客户可访问的 HTTP(S) 地址；支持路径前缀', 'customer-public-base', 'customer-public-base', b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'pms.customer.public-base-url' AND deleted = b'0');
