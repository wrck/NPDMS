-- INT-12 / EXE-03 / EXE-04：统一采集应用层，保留旧执行及凭证历史。
CREATE TABLE plt_collection_template (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, template_code VARCHAR(64) NOT NULL,
 name VARCHAR(128) NOT NULL, owner_context VARCHAR(32) NOT NULL, purpose VARCHAR(32) NOT NULL,
 protocol VARCHAR(16) NOT NULL, device_model VARCHAR(128) NULL, revision INT NOT NULL, command_text MEDIUMTEXT NOT NULL,
 content_hash CHAR(64) NOT NULL, status VARCHAR(24) NOT NULL, publication_started BIT NOT NULL DEFAULT 0, published_at DATETIME NULL,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_plt_collection_template_version (tenant_id,template_code,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE plt_collection_request (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, entry VARCHAR(32) NOT NULL,
 object_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL,
 request_digest CHAR(64) NOT NULL, platform_task_id VARCHAR(64) NOT NULL,
 command_text MEDIUMTEXT NULL, template_name VARCHAR(128) NULL, template_revision_id BIGINT NULL,
 credential_version BIGINT NULL, retry_of_id BIGINT NULL, consumed_result_version BIGINT NULL,
 creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted BIT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_plt_collection_request_key (tenant_id,request_key),
 UNIQUE KEY uk_plt_collection_request_task (tenant_id,platform_task_id),
 KEY idx_plt_collection_request_source (tenant_id,entry,object_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 复制已有关系与冻结命令到统一查询投影；不修改旧 IMP 表和 PLT 任务，不回填未知旧命令。
INSERT INTO plt_collection_request
 (id,tenant_id,entry,object_id,actor_id,request_key,request_digest,platform_task_id,command_text,
 consumed_result_version,creator,create_time,updater,update_time,deleted)
SELECT id,tenant_id,'configuration',configuration_id,actor_id,request_key,request_digest,platform_task_id,
 command_text,consumed_result_version,creator,create_time,updater,update_time,deleted
FROM imp_configuration_collection;

ALTER TABLE plt_device_credential
 ADD COLUMN external_connection_id VARCHAR(100) NULL,
 ADD COLUMN registration_digest CHAR(64) NULL,
 ADD COLUMN registration_key VARCHAR(64) NULL,
 ADD COLUMN registration_template_id BIGINT NULL,
 ADD COLUMN registration_expires_at DATETIME NULL,
 ADD COLUMN project_id BIGINT NULL,
 ADD COLUMN device_id BIGINT NULL,
 ADD COLUMN host VARCHAR(255) NULL,
 ADD COLUMN port INT NULL,
 ADD UNIQUE KEY uk_plt_credential_external (tenant_id,external_connection_id);

-- 示例只提供草稿，不臆造发布批准，不包含秘密或自动执行命令。
INSERT INTO plt_collection_template (id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator)
SELECT 993012328001,1,'EXAMPLE-SHOW-RUN','示例：配置采集','IMP','configuration','SSH',1,'show run',SHA2('show run',256),'DRAFT','dac-collection-example'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='EXAMPLE-SHOW-RUN' AND revision=1);
INSERT INTO plt_collection_template (id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator)
SELECT 993012328002,1,'EXAMPLE-SHOW-TECH','示例：联调采集','IMP','joint-test','SSH',1,'show tech',SHA2('show tech',256),'DRAFT','dac-collection-example'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='EXAMPLE-SHOW-TECH' AND revision=1);
INSERT INTO plt_collection_template (id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator)
SELECT 993012328003,1,'EXAMPLE-CENTER-TELNET','示例：Telnet 版本查询','PLT','center','TELNET',1,'show version',SHA2('show version',256),'DRAFT','dac-collection-example'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='EXAMPLE-CENTER-TELNET' AND revision=1);

-- 公共工作台与功能权限；不创建角色、不自动授权。
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328100,'设备连接与采集','',2,170,18000,'device-collection','ep:connection','pms/platform/device-collection/index','PmsDeviceCollection',0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=993012328100);
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328101,'采集查询','pms:device-collection:query',3,171,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-collection:query' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328102,'执行采集','pms:device-collection:execute',3,172,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-collection:execute' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328103,'模板查询','pms:collection-template:query',3,173,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:collection-template:query' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328104,'模板创建','pms:collection-template:create',3,174,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:collection-template:create' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328105,'模板编辑','pms:collection-template:update',3,175,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:collection-template:update' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328106,'模板发布停用','pms:collection-template:publish',3,176,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:collection-template:publish' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328107,'使用发布模板','pms:collection-template:use',3,177,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:collection-template:use' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328108,'连接查询','pms:device-credential:query',3,178,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-credential:query' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328109,'连接保存','pms:device-credential:create',3,179,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-credential:create' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328110,'连接停用','pms:device-credential:update',3,180,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-credential:update' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328111,'连接授权','pms:device-credential:grant',3,181,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-credential:grant' AND deleted=b'0');
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 993012328112,'使用保存连接','pms:device-credential:use',3,182,993012328100,'','ep:connection',NULL,NULL,0,b'1',b'0',b'1','dac-collection-example','dac-collection-example',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='pms:device-credential:use' AND deleted=b'0');
