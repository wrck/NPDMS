-- Additive archive; old ast_device_config_log and imp_collection_log remain untouched.
CREATE TABLE IF NOT EXISTS ast_device_log_import (
 id BIGINT NOT NULL PRIMARY KEY, project_id BIGINT NOT NULL, source_key VARCHAR(180) NOT NULL,
 source_type VARCHAR(32) NOT NULL, source_file_name VARCHAR(180) NOT NULL, input_sha256 CHAR(64) NOT NULL,
 outcome VARCHAR(16) NOT NULL, rejection_reason VARCHAR(300), device_count INT NOT NULL DEFAULT 0,
 redacted_value_count INT NOT NULL DEFAULT 0, actor_id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL, creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted BIT NOT NULL DEFAULT b'0',
 UNIQUE KEY uk_device_log_import (tenant_id,source_key), KEY idx_device_log_import_project(tenant_id,project_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ast_device_log_file (
 id BIGINT NOT NULL PRIMARY KEY, import_id BIGINT NOT NULL, project_id BIGINT NOT NULL, device_id BIGINT,
 serial_number VARCHAR(128), device_version BIGINT, source_type VARCHAR(32) NOT NULL, file_name VARCHAR(180) NOT NULL,
 material_id BIGINT, file_reference_id BIGINT, artifact_id BIGINT, artifact_version INT, sha256 CHAR(64),
 tenant_id BIGINT NOT NULL, creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), deleted BIT NOT NULL DEFAULT b'0',
 UNIQUE KEY uk_device_log_version(tenant_id,device_id,device_version),
 UNIQUE KEY uk_device_log_import_device(tenant_id,import_id,device_id),
 KEY idx_device_log_project(tenant_id,project_id,device_id,device_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Allocate without locking an empty history index range; counters participate in the archive transaction.
CREATE TABLE IF NOT EXISTS ast_device_log_version (
 tenant_id BIGINT NOT NULL, device_id BIGINT NOT NULL, current_version BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO plt_delivery_type(type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT s.code,s.name,'设备日志',JSON_ARRAY('txt','log'),20971520,b'1',s.remark,0,t.tenant_id,'device-log-archive',NOW(3),'device-log-archive',NOW(3),b'0'
FROM (SELECT DISTINCT tenant_id FROM plt_delivery_type WHERE deleted=b'0') t
CROSS JOIN (SELECT 'AST.DEVICE_CONFIGURATION_LOG' code,'设备配置日志' name,'DAC split by serial; immutable per-device archive' remark
 UNION ALL SELECT 'AST.DEVICE_CONFIGURATION_LOG_SOURCE','设备配置日志来源文件','Sanitized complete input preserved with its split files') s
WHERE NOT EXISTS(SELECT 1 FROM plt_delivery_type d WHERE d.tenant_id=t.tenant_id AND d.type_code=s.code AND d.deleted=b'0');

INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 970000000000099251,'设备配置日志归档','pms:device-log-archive:query',2,65,parent.id,'device-log-archive','ep:document','pms/asset/device-log-archive/index','DeviceLogArchive',0,b'1',b'1',b'1','device-log-archive','device-log-archive',b'0'
FROM (SELECT MIN(parent_id) id FROM system_menu WHERE permission='pms:device:query' AND deleted=b'0' AND type=2) parent
WHERE parent.id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id=970000000000099251);
INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT action.id,action.name,action.permission,3,1,970000000000099251,'','',NULL,NULL,0,b'0',b'1',b'1','device-log-archive','device-log-archive',b'0'
FROM (SELECT 970000000000099252 id,'导入配置日志' name,'pms:device-log-archive:import' permission
 UNION ALL SELECT 970000000000099253,'下载配置日志','pms:device-log-archive:download') action
WHERE EXISTS(SELECT 1 FROM system_menu WHERE id=970000000000099251 AND deleted=b'0')
AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id=action.id);

-- Enhanced copied configuration page; existing menu/component and business APIs are preserved.
INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 970000000000099254,'配置调试与设备日志','pms:imp-configuration:query',2,66,parent.id,'configuration-device-logs','ep:setting','pms/engineering/configuration/with-device-logs','PmsEngConfigurationWithDeviceLogs',0,b'1',b'1',b'1','device-log-archive','device-log-archive',b'0'
FROM (SELECT MIN(parent_id) id FROM system_menu WHERE permission='pms:imp-configuration:query' AND deleted=b'0' AND type=2) parent
WHERE parent.id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id=970000000000099254);
