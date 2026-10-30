-- ACC-01: independent published print layout; preserve legacy files and confirmation snapshots.
ALTER TABLE imp_eng_training
    ADD COLUMN print_template_id BIGINT NULL COMMENT 'Independent print template',
    ADD COLUMN print_revision_id BIGINT NULL COMMENT 'Published revision captured on selection',
    ADD COLUMN print_layout_snapshot TEXT NULL COMMENT 'Validated immutable layout for this selection';

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992209200101,'TRAINING_PRINT_STANDARD','现场培训标准打印模板','TRAINING_PRINT','独立打印版式；通过现场培训的打印模板配置入口维护。','ENABLED',NULL,1,'seed_training_print','seed_training_print',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992209200101 OR (tenant_id=1 AND template_code='TRAINING_PRINT_STANDARD'));

INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992209200102,992209200101,1,'PUBLISHED',NULL,NULL,
'{"trainingPrint":{"title":"现场培训记录表","header":"","footer":"","paper":"A4","landscape":false,"fontSize":11,"margin":40,"sections":["BASIC","CONTENT","CONFIRMATION","REMARK"]},"submitBtn":false,"resetBtn":false}',
'[{"type":"input","field":"trainingPrintLayout","title":"培训打印版式","props":{"disabled":true}}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_training_print','seed_training_print',b'0',1
WHERE EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992209200101 AND creator='seed_training_print')
AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992209200102);

UPDATE plt_dynamic_form_template SET current_published_revision_id=992209200102
WHERE id=992209200101 AND current_published_revision_id IS NULL AND creator='seed_training_print';
