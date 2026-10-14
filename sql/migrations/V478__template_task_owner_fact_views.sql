-- Template task Owner views. Existing views, templates and execution snapshots remain immutable.
INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000478001,1,'ARRIVAL','IMP_ARRIVAL_RECEIPT',1,'IMP','PAGE',NULL,'IMP_ARRIVAL_RECEIPT','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY'),'IMP_ARRIVAL_RECEIPT_QUERY','IMP_ARRIVAL_RECEIPT_COMMAND','IMP_ARRIVAL_RECEIPT_PERMISSION',NOW(6),1,'template-task-20261014','template-task-20261014',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='ARRIVAL' AND view_key='IMP_ARRIVAL_RECEIPT' AND revision_no=1);

INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000478002,1,'INSTALLATION','IMP_PROJECT_INSTALLATION',1,'IMP','PAGE',NULL,'IMP_PROJECT_INSTALLATION','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY'),'IMP_PROJECT_INSTALLATION_QUERY','IMP_PROJECT_INSTALLATION_COMMAND','IMP_PROJECT_INSTALLATION_PERMISSION',NOW(6),1,'template-task-20261014','template-task-20261014',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='INSTALLATION' AND view_key='IMP_PROJECT_INSTALLATION' AND revision_no=1);

INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000478003,1,'TRAINING','IMP_TRAINING_CONFIRMATION',1,'IMP','PAGE',NULL,'IMP_TRAINING_CONFIRMATION','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY'),'IMP_TRAINING_CONFIRMATION_QUERY','IMP_TRAINING_CONFIRMATION_COMMAND','IMP_TRAINING_CONFIRMATION_PERMISSION',NOW(6),1,'template-task-20261014','template-task-20261014',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='TRAINING' AND view_key='IMP_TRAINING_CONFIRMATION' AND revision_no=1);

INSERT INTO plt_business_view_revision
(id,tenant_id,entity_type,view_key,revision_no,owner_context,view_source,dynamic_form_revision_id,component_key,component_version,context_schema,supported_actions,query_provider_key,command_provider_key,permission_provider_key,published_at,version,creator,updater,deleted)
SELECT 970000000000478004,1,'CUTOVER_APPROVAL','CUT_CUTOVER_APPROVAL',1,'CUT','PAGE',NULL,'CUT_CUTOVER_APPROVAL','1',
JSON_OBJECT('type','object','required',JSON_ARRAY('projectId'),'properties',JSON_OBJECT('projectId',JSON_OBJECT('oneOf',JSON_ARRAY(JSON_OBJECT('type','integer','minimum',1),JSON_OBJECT('type','string','pattern','^[1-9][0-9]*$'))))),
JSON_ARRAY('QUERY'),'CUT_CUTOVER_APPROVAL_QUERY','CUT_CUTOVER_APPROVAL_COMMAND','CUT_CUTOVER_APPROVAL_PERMISSION',NOW(6),1,'template-task-20261014','template-task-20261014',b'0'
WHERE NOT EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1 AND entity_type='CUTOVER_APPROVAL' AND view_key='CUT_CUTOVER_APPROVAL' AND revision_no=1);
