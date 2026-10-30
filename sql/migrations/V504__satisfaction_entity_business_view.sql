-- Add the existing satisfaction workbench to template bindings; no project/template revision is rewritten.
INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000504001,1,'SATISFACTION','ACC_SATISFACTION',1,'ACC','PAGE',NULL,'ACC_SATISFACTION','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY','MANAGE','LINK','UNLINK'),'ACC_SATISFACTION_QUERY','ACC_SATISFACTION_COMMAND','ACC_SATISFACTION_PERMISSION',NOW(6),1,'satisfaction-entity-20261019','satisfaction-entity-20261019',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='SATISFACTION' AND view_key='ACC_SATISFACTION' AND revision_no=1);
