-- Customer confirmation: immutable issue-time template and handwritten PNG.
ALTER TABLE imp_eng_training
    ADD COLUMN confirmation_template_id BIGINT NULL COMMENT 'Customer confirmation template',
    ADD COLUMN confirmation_revision_id BIGINT NULL COMMENT 'Frozen published revision',
    ADD COLUMN confirmation_form_rules MEDIUMTEXT NULL COMMENT 'Safe FormCreate issue snapshot',
    ADD COLUMN confirmation_values MEDIUMTEXT NULL COMMENT 'Customer confirmation answers',
    ADD COLUMN signature_image_data_url MEDIUMTEXT NULL COMMENT 'Validated PNG signature';

INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992209200001,'TRAINING_CUSTOMER_CONFIRMATION','现场培训客户确认表','CUSTOMER_CONFIRMATION','可复用的客户评价与手写签字表单；发布后供培训记录外发时冻结。','ENABLED',NULL,1,'seed_customer_confirmation','seed_customer_confirmation',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id=992209200001 OR (tenant_id=1 AND template_code='TRAINING_CUSTOMER_CONFIRMATION'));
INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992209200002,992209200001,1,'PUBLISHED',NULL,NULL,'{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}','[{"type": "radio", "field": "skillRating", "title": "培训工程师技术水平及表达能力", "options": [{"label": "很好", "value": "很好"}, {"label": "良好", "value": "良好"}, {"label": "一般", "value": "一般"}, {"label": "差", "value": "差"}], "validate": [{"required": true, "message": "请完成评价", "trigger": "change"}]}, {"type": "radio", "field": "effectRating", "title": "培训内容及讲解效果", "options": [{"label": "很好", "value": "很好"}, {"label": "良好", "value": "良好"}, {"label": "一般", "value": "一般"}, {"label": "差", "value": "差"}], "validate": [{"required": true, "message": "请完成评价", "trigger": "change"}]}, {"type": "radio", "field": "satisfactionRating", "title": "培训满意度", "options": [{"label": "非常满意", "value": "非常满意"}, {"label": "较满意", "value": "较满意"}, {"label": "一般", "value": "一般"}, {"label": "差", "value": "差"}], "validate": [{"required": true, "message": "请完成评价", "trigger": "change"}]}, {"type": "input", "field": "signOpinion", "title": "综合意见", "props": {"type": "textarea", "rows": 3, "maxlength": 500}}, {"type": "input", "field": "signConfirmerName", "title": "签字人姓名", "props": {"maxlength": 64}, "validate": [{"required": true, "message": "请填写签字人姓名"}]}, {"type": "signaturePad", "field": "signatureImageDataUrl", "title": "客户手写签字", "validate": [{"required": true, "message": "请手写签字"}]}]','FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_customer_confirmation','seed_customer_confirmation',b'0',1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992209200002);

UPDATE plt_dynamic_form_template SET current_published_revision_id=992209200002 WHERE id=992209200001 AND current_published_revision_id IS NULL AND creator='seed_customer_confirmation';
