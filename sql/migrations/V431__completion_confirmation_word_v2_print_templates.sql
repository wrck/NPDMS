-- 2026-10-10 approved reference: 完工证明V2.0.docx contains both print layouts.
-- Add new templates only. Published V1 revisions, bound snapshots and registered PDFs are immutable.

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992610100007,'COMPLETION_PRINT_WORD_V2','完工证明V2.0打印模板','COMPLETION_PRINT','按需求方Word样张打印工程完工证明，设备按类型、型号、数量列表。','ENABLED',NULL,1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100007 OR (tenant_id=1 AND template_code='COMPLETION_PRINT_WORD_V2'));

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100008,992610100007,1,'PUBLISHED',NULL,NULL,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"CompletionCertificatePrintV2","field":"completionPrintData"}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100007 AND tenant_id=1 AND creator='seed_completion_print_v2')
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100008 OR (tenant_id=1 AND template_id=992610100007 AND revision_no=1));

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100008
WHERE id=992610100007 AND tenant_id=1 AND current_published_revision_id IS NULL AND creator='seed_completion_print_v2'
AND EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100008 AND template_id=992610100007 AND tenant_id=1 AND status_code='PUBLISHED');

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992610100009,'SATISFACTION_PRINT_WORD_V2','满意度调查表V2.0打印模板','SATISFACTION_PRINT','按需求方Word样张打印冻结调查矩阵及统一签字，不改变问卷计分规则。','ENABLED',NULL,1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100009 OR (tenant_id=1 AND template_code='SATISFACTION_PRINT_WORD_V2'));

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100010,992610100009,1,'PUBLISHED',NULL,NULL,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"CompletionSatisfactionPrintV2","field":"satisfactionPrintData"}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100009 AND tenant_id=1 AND creator='seed_completion_print_v2')
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100010 OR (tenant_id=1 AND template_id=992610100009 AND revision_no=1));

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100010
WHERE id=992610100009 AND tenant_id=1 AND current_published_revision_id IS NULL AND creator='seed_completion_print_v2'
AND EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100010 AND template_id=992610100009 AND tenant_id=1 AND status_code='PUBLISHED');
