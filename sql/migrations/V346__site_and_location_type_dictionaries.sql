-- 站点类型与站点内位置类型数据字典：值域取自现有站点与位置真实数据及位置树层级约定，不新增业务语义。
INSERT INTO system_dict_type (id,name,type,status,remark,creator,updater)
SELECT 993109110101,'站点类型','pms_site_type',0,'站点业务类型编码，值域取自现有站点数据','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_site_type' AND deleted=b'0');
INSERT INTO system_dict_type (id,name,type,status,remark,creator,updater)
SELECT 993109110102,'站点位置类型','pms_site_location_type',0,'站点内位置类型编码，值域取自现有位置数据','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type='pms_site_location_type' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110201,0,'客户站点','CUSTOMER_SITE','pms_site_type',0,'','','现有站点类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_type' AND value='CUSTOMER_SITE' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110202,1,'数据中心','DATA_CENTER','pms_site_type',0,'','','现有站点类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_type' AND value='DATA_CENTER' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110203,2,'办公场所','OFFICE','pms_site_type',0,'','','现有站点类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_type' AND value='OFFICE' AND deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110301,0,'园区','CAMPUS','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='CAMPUS' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110302,1,'楼栋','BUILDING','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='BUILDING' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110303,2,'楼层','FLOOR','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='FLOOR' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110304,3,'机房','ROOM','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='ROOM' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110305,4,'机柜','RACK','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='RACK' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater)
SELECT 993109110306,5,'U位','U_POSITION','pms_site_location_type',0,'','','现有位置类型值','ui-round3','ui-round3'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_site_location_type' AND value='U_POSITION' AND deleted=b'0');
