-- 割接专项运行入口；旧菜单、发布版本及人员授权保持不变。
-- 新审批角色对应基线二线/研发角色，不向任何用户自动分配角色或项目范围。

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 970445001,'割接工作台','pms:cutover-task:query',2,1,19263,'workbench','ep:switch','pms/cutover/workbench/index','PmsCutoverWorkbench',0,b'1',b'1',b'1','cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=970445001 OR (component='pms/cutover/workbench/index' AND deleted=b'0'));

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 970445002,'割接配置中心','pms:cutover-config:query',2,2,19263,'configuration','ep:setting','pms/cutover/configuration/index','PmsCutoverConfiguration',0,b'1',b'1',b'1','cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=970445002 OR (component='pms/cutover/configuration/index' AND deleted=b'0'));

INSERT INTO system_role
(id,name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id)
SELECT 970445101,'割接二线审批','CUT_SECOND_LINE_APPROVER',70,5,'[]',0,2,'基线P5二线审批候选；另须具备对应项目数据范围','cut-special-20261012','cut-special-20261012',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE tenant_id=1 AND code='CUT_SECOND_LINE_APPROVER' AND deleted=b'0');

INSERT INTO system_role
(id,name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id)
SELECT 970445102,'割接研发审批','CUT_RND_APPROVER',71,5,'[]',0,2,'基线P5研发审批候选；另须具备对应项目数据范围','cut-special-20261012','cut-special-20261012',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE tenant_id=1 AND code='CUT_RND_APPROVER' AND deleted=b'0');

-- 将新入口提供给已具备同一查询权限的角色；不扩大其功能或数据范围。
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT existing.role_id,target.id,'cut-special-20261012','cut-special-20261012',b'0',existing.tenant_id
FROM system_role_menu existing
JOIN system_menu original ON original.id=existing.menu_id AND original.deleted=b'0'
JOIN system_menu target ON target.id IN (970445001,970445002) AND target.permission=original.permission AND target.deleted=b'0'
WHERE existing.deleted=b'0' AND NOT EXISTS
 (SELECT 1 FROM system_role_menu present WHERE present.role_id=existing.role_id AND present.menu_id=target.id AND present.tenant_id=existing.tenant_id AND present.deleted=b'0');

INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT role.id,menu.id,'cut-special-20261012','cut-special-20261012',b'0',role.tenant_id
FROM system_role role JOIN system_menu menu
 ON menu.id IN (970445001,19263) OR menu.permission IN ('pms:cutover-task:query','pms:cutover-task:query-approval','pms:cutover-task:approve')
WHERE role.tenant_id=1 AND role.code IN ('CUT_SECOND_LINE_APPROVER','CUT_RND_APPROVER') AND role.deleted=b'0' AND menu.deleted=b'0'
 AND NOT EXISTS (SELECT 1 FROM system_role_menu present WHERE present.role_id=role.id AND present.menu_id=menu.id AND present.tenant_id=role.tenant_id AND present.deleted=b'0');

INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000445001,1,'CUTOVER_TASK','CUTOVER_WORKBENCH',1,'CUT','PAGE',NULL,'CUT_CUTOVER_WORKBENCH','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY','CREATE'),'CUT_CUTOVER_WORKBENCH_QUERY','CUT_CUTOVER_WORKBENCH_COMMAND','CUT_CUTOVER_WORKBENCH_PERMISSION',NOW(6),1,'cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='CUTOVER_TASK' AND view_key='CUTOVER_WORKBENCH' AND revision_no=1);

INSERT INTO infra_job
(id,name,status,handler_name,handler_param,cron_expression,retry_count,retry_interval,monitor_timeout,creator,updater,deleted)
SELECT 970445201,'割接成功结果投递',1,'cutoverResultDeliveryJob','','0/30 * * * * ?',0,0,0,'cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_job WHERE handler_name='cutoverResultDeliveryJob' AND deleted=b'0');
INSERT INTO infra_job
(id,name,status,handler_name,handler_param,cron_expression,retry_count,retry_interval,monitor_timeout,creator,updater,deleted)
SELECT 970445202,'割接审批站内通知',1,'cutoverRuntimeApprovalNotificationJob','','0/30 * * * * ?',0,0,0,'cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_job WHERE handler_name='cutoverRuntimeApprovalNotificationJob' AND deleted=b'0');

-- 沿用PLT既有只读命令示例，仅初始化草稿；由采集管理员按实际设备确认并发布。
INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445301,1,'CUT-EXAMPLE-CHECKLIST','示例：调研检查版本查询','CUT','cutover-checklist','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-CHECKLIST' AND revision=1);

INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445302,1,'CUT-EXAMPLE-PRE-CHECK','示例：割接前检查版本查询','CUT','cutover-pre-check','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-PRE-CHECK' AND revision=1);

INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445303,1,'CUT-EXAMPLE-EXECUTION','示例：割接执行版本查询','CUT','cutover-execution','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-EXECUTION' AND revision=1);

INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445304,1,'CUT-EXAMPLE-TEST','示例：业务测试版本查询','CUT','cutover-test','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-TEST' AND revision=1);

INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445305,1,'CUT-EXAMPLE-ROLLBACK','示例：回退核对版本查询','CUT','cutover-rollback','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-ROLLBACK' AND revision=1);

INSERT INTO plt_collection_template
(id,tenant_id,template_code,name,owner_context,purpose,protocol,revision,command_text,content_hash,status,creator,updater)
SELECT 970445306,1,'CUT-EXAMPLE-POST-COLLECTION','示例：收尾采集版本查询','CUT','cutover-post-collection','SSH',1,'show version',SHA2('show version',256),'DRAFT','cut-special-20261012','cut-special-20261012'
WHERE NOT EXISTS (SELECT 1 FROM plt_collection_template WHERE tenant_id=1 AND template_code='CUT-EXAMPLE-POST-COLLECTION' AND revision=1);

