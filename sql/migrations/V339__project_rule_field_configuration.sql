-- Entity properties and types are discovered; this seed only controls authoring metadata.
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT 992209220339, 'pms', 2, '项目规则字段配置', 'pms.project-rule.fields',
       '{}', b'0',
       '属性自动发现；按字段编码配置 label、enabled、availableAtCreation。禁止改变历史编码与属性绑定。',
       'seed_project_rule_fields', 'seed_project_rule_fields', b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'pms.project-rule.fields' AND deleted = b'0');
