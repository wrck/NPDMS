-- Add native print-policy bindings to new Word seed revisions.
-- Preserve published revision 1 and all historical snapshots. Advance only untouched seed pointers.

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100011,992610100007,2,'PUBLISHED',NULL,992610100008,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"CompletionCertificatePrintV2","field":"completionPrintData"},{"type":"input","field":"name","hidden":true},{"type":"input","field":"signatureImageDataUrl","hidden":true},{"type":"input","field":"completionDate","hidden":true}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100007 AND tenant_id=1 AND creator='seed_completion_print_v2' AND current_published_revision_id=992610100008)
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100011 OR (tenant_id=1 AND template_id=992610100007 AND (revision_no=2 OR status_code='DRAFT')));

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100011,version=version+1,updater='seed_completion_print_v2'
WHERE id=992610100007 AND tenant_id=1 AND current_published_revision_id=992610100008 AND creator='seed_completion_print_v2'
AND EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100011 AND template_id=992610100007 AND tenant_id=1 AND status_code='PUBLISHED' AND creator='seed_completion_print_v2');

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992610100012,992610100009,2,'PUBLISHED',NULL,992610100010,
'{"form":{"labelPosition":"top","labelWidth":"auto"},"submitBtn":false,"resetBtn":false}',
'[{"type":"CompletionSatisfactionPrintV2","field":"satisfactionPrintData"},{"type":"input","field":"name","hidden":true},{"type":"input","field":"signatureImageDataUrl","hidden":true},{"type":"input","field":"answersText","hidden":true}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_completion_print_v2','seed_completion_print_v2',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992610100009 AND tenant_id=1 AND creator='seed_completion_print_v2' AND current_published_revision_id=992610100010)
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100012 OR (tenant_id=1 AND template_id=992610100009 AND (revision_no=2 OR status_code='DRAFT')));

UPDATE plt_dynamic_form_template SET current_published_revision_id=992610100012,version=version+1,updater='seed_completion_print_v2'
WHERE id=992610100009 AND tenant_id=1 AND current_published_revision_id=992610100010 AND creator='seed_completion_print_v2'
AND EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992610100012 AND template_id=992610100009 AND tenant_id=1 AND status_code='PUBLISHED' AND creator='seed_completion_print_v2');
