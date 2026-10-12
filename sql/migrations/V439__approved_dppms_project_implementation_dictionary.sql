-- Approved implementation_mode values from DPPMS fnd_basic_data, dataTypeCode=15.
-- Original value 2 expired on 2015-12-28: retain its label for historical projects,
-- but do not offer it for a new normal implementation. Value 4 is effective.
-- Existing definitions and their audit fields are never overwritten.
INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark,
     creator, create_time, updater, update_time, deleted)
SELECT 918439001, 4, '直服转包', 'DIRECT_SUBCONTRACT', 'pms_implementation_method',
       1, '', '', 'DPPMS实施方式2；原值已失效，仅保留历史含义',
       'dppms-migration-seed', NOW(), 'dppms-migration-seed', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data
    WHERE dict_type='pms_implementation_method' AND value='DIRECT_SUBCONTRACT' AND deleted=b'0');

INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark,
     creator, create_time, updater, update_time, deleted)
SELECT 918439002, 5, '原厂集成', 'FACTORY_INTEGRATION', 'pms_implementation_method',
       0, '', '', 'DPPMS实施方式4；原厂集成',
       'dppms-migration-seed', NOW(), 'dppms-migration-seed', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data
    WHERE dict_type='pms_implementation_method' AND value='FACTORY_INTEGRATION' AND deleted=b'0');
