-- CUS-04: configurable contact fields; preserve existing values and disabled configuration.
INSERT INTO system_dict_type (id,name,type,status,remark,creator,updater)
SELECT 993109100101,'联系人职务','pms_contact_title',0,'职务初始值取自现有联系人，不改变原字段值','demo-s1-contacts','demo-s1-contacts'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_contact_title' AND deleted=b'0');
INSERT INTO system_dict_type (id,name,type,status,remark,creator,updater)
SELECT 993109100102,'客户联系人角色','pms_customer_contact_role',0,'客户侧业务联络身份，不关联系统RBAC角色或项目成员权限','demo-s1-contacts','demo-s1-contacts'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_customer_contact_role' AND deleted=b'0');
INSERT INTO system_dict_type (id,name,type,status,remark,creator,updater)
SELECT 993109100103,'联系人状态','pms_contact_status',0,'生命周期编码0启用、1停用，显示名称由字典管理','demo-s1-contacts','demo-s1-contacts'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_contact_status' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109101000 + ROW_NUMBER() OVER (ORDER BY existing_title),
       ROW_NUMBER() OVER (ORDER BY existing_title),existing_title,existing_title,'pms_contact_title',0,'','',
       '来源：现有客户联系人职务','demo-s1-contacts','demo-s1-contacts'
FROM (SELECT DISTINCT title AS existing_title FROM cus_customer_contact WHERE title IS NOT NULL AND TRIM(title)<>'' AND deleted=b'0') titles
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data d WHERE d.dict_type='pms_contact_title' AND d.value=titles.existing_title AND d.deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109100201,0,'启用','0','pms_contact_status',0,'success','','CUS-04固定生命周期编码','demo-s1-contacts','demo-s1-contacts'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_contact_status' AND value='0' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109100202,1,'停用','1','pms_contact_status',0,'danger','','CUS-04固定生命周期编码','demo-s1-contacts','demo-s1-contacts'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_contact_status' AND value='1' AND deleted=b'0');
