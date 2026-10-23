-- Approved F-ACC-002: independent, editable print templates for the two-form confirmation.
-- New seed records only; preserve all published layouts and historical documents.

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992610100001,'COMPLETION_PRINT_STANDARD','完工证明标准打印模板','COMPLETION_PRINT','成套确认的独立打印版式；编辑发布产生新修订，不改变历史快照。','ENABLED',NULL,1,'seed_completion_confirmation','seed_completion_confirmation',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100001 OR (tenant_id=1 AND template_code='COMPLETION_PRINT_STANDARD'));

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100002,992610100001,1,'PUBLISHED',NULL,NULL,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"fcTitle","props":{"title":"完工证明","align":"center","size":"h2"}},{"type":"fcTable","props":{"border":true,"borderColor":"#cbd5e1","borderWidth":"1px","rule":{"row":9,"col":1,"layout":[],"style":{},"class":{}}},"children":[{"type":"input","field":"name","title":"证明名称","slot":"0:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"customerUnit","title":"用户单位","slot":"1:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"contractNo","title":"合同号","slot":"2:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"serviceType","title":"工程服务类型","slot":"3:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"engineerName","title":"迪普工程师","slot":"4:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"engineerContact","title":"联系方式","slot":"5:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"completionDate","title":"完工日期","slot":"6:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"serviceItemsText","title":"工程服务内容","slot":"7:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"devicesText","title":"设备类型和数量","slot":"8:0","props":{"type":"textarea","autosize":{"minRows":1}}}]},{"type":"signaturePad","field":"signatureImageDataUrl","title":"客户手写签字"},{"type":"input","field":"signTime","title":"确认日期"}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_confirmation','seed_completion_confirmation',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100001 AND creator='seed_completion_confirmation')
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100002);

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100002
WHERE id=992610100001 AND current_published_revision_id IS NULL AND creator='seed_completion_confirmation';

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992610100003,'SATISFACTION_PRINT_STANDARD','满意度调查表标准打印模板','SATISFACTION_PRINT','成套确认的独立打印版式；编辑发布产生新修订，不改变历史快照。','ENABLED',NULL,1,'seed_completion_confirmation','seed_completion_confirmation',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100003 OR (tenant_id=1 AND template_code='SATISFACTION_PRINT_STANDARD'));

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100004,992610100003,1,'PUBLISHED',NULL,NULL,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"fcTitle","props":{"title":"满意度调查表","align":"center","size":"h2"}},{"type":"fcTable","props":{"border":true,"borderColor":"#cbd5e1","borderWidth":"1px","rule":{"row":3,"col":1,"layout":[],"style":{},"class":{}}},"children":[{"type":"input","field":"name","title":"工程名称","slot":"0:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"customerUnit","title":"用户单位","slot":"1:0","props":{"type":"textarea","autosize":{"minRows":1}}},{"type":"input","field":"collectionKey","title":"调查编号","slot":"2:0","props":{"type":"textarea","autosize":{"minRows":1}}}]},{"type":"CompletionSatisfactionQuestionnaire","field":"questionnaireData","title":"满意度调查表"},{"type":"signaturePad","field":"signatureImageDataUrl","title":"客户手写签字"},{"type":"input","field":"signTime","title":"确认日期"}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_confirmation','seed_completion_confirmation',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100003 AND creator='seed_completion_confirmation')
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100004);

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100004
WHERE id=992610100003 AND current_published_revision_id IS NULL AND creator='seed_completion_confirmation';
