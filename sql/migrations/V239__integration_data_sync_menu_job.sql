-- Generic integration UI and one Quartz trigger; individual tasks start paused.
INSERT INTO infra_job
(id,name,status,handler_name,handler_param,cron_expression,retry_count,retry_interval,monitor_timeout,creator,create_time,updater,update_time,deleted)
SELECT 993014239001,'通用数据同步调度',1,'dataSyncDispatchJob','','0/30 * * * * ?',0,0,0,
'integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM infra_job WHERE handler_name='dataSyncDispatchJob' AND deleted=b'0');

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239100,'数据集成','',1,90,0,'/data-integration','ep:connection',NULL,NULL,0,b'1',b'1',b'1','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239100);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239101,'迁移与同步','pms:integration:view',2,1,993014239100,'workspace','ep:refresh','pms/integration/index','PmsDataIntegration',0,b'1',b'0',b'1','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239101);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239110,'连接管理','pms:integration:connection',3,0,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239110);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239111,'任务配置','pms:integration:configure',3,1,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239111);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239112,'SQL配置','pms:integration:sql',3,2,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239112);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239113,'执行与重试','pms:integration:execute',3,3,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239113);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239114,'同步调度','pms:integration:schedule',3,4,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239114);
INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 993014239115,'接管已有组织','pms:integration:adopt',3,5,993014239101,'','#',0,b'1',b'0',b'0','integration_sync',NOW(),'integration_sync',NOW(),b'0'
WHERE NOT EXISTS(SELECT 1 FROM system_menu WHERE id=993014239115);

