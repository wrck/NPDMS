-- 外部最终用户使用同一成员区间，不创建系统账号、不改写既有成员。
ALTER TABLE proj_project_member_assignment MODIFY COLUMN user_id BIGINT NULL COMMENT '系统账号ID；外部最终用户为空';

SET @col := (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='proj_project_member_assignment' AND column_name='external_mobile');
SET @sql := IF(@col=0, 'ALTER TABLE proj_project_member_assignment ADD COLUMN external_mobile VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='proj_project_member_assignment' AND column_name='external_email');
SET @sql := IF(@col=0, 'ALTER TABLE proj_project_member_assignment ADD COLUMN external_email VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO system_dict_data
 (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 993109100509,6,'最终用户','END_USER','pms_project_member_role',0,'info','',
 '外部最终用户，不授予系统身份或任务执行权限','external-end-user','external-end-user',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_project_member_role' AND value='END_USER' AND deleted=b'0');
