-- 新范围覆盖视图供项目模板显式绑定；不改写已发布模板或既有执行快照。
INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000447001,1,'CUTOVER_COVERAGE','CUTOVER_COVERAGE',1,'IMP','PAGE',NULL,'IMP_CUTOVER_COVERAGE','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY'),'IMP_CUTOVER_COVERAGE_QUERY','IMP_CUTOVER_COVERAGE_COMMAND','IMP_CUTOVER_COVERAGE_PERMISSION',NOW(6),1,'cut-special-20261012','cut-special-20261012',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='CUTOVER_COVERAGE' AND view_key='CUTOVER_COVERAGE' AND revision_no=1);
