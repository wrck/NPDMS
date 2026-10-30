CREATE TABLE proj_preparation_confirmation (
 id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, project_id bigint NOT NULL,
 revision_no int NOT NULL, form_revision_id bigint NOT NULL, form_revision_version int NOT NULL,
 form_snapshot_json json NOT NULL, values_json json NOT NULL,
 submitted_by bigint NOT NULL, submitted_at datetime NOT NULL,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY(id), UNIQUE KEY uk_proj_preparation_confirmation(tenant_id,project_id,revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目经理工前确认不可变历史';

-- Field names and branch conditions are configuration data, not application-code switches.
INSERT INTO plt_dynamic_form_template
(id,template_code,template_name,category_code,description,availability_code,current_published_revision_id,version,creator,updater,deleted,tenant_id)
SELECT 992209220342,'PROJECT_PREPARATION_CONFIRMATION','工前确认表','PROJECT_PREPARATION_CONFIRMATION','流程图工前判断；项目经理确认并留痕，三个支线独立办理。','ENABLED',NULL,1,'seed_preparation_confirmation','seed_preparation_confirmation',b'0',1
WHERE NOT EXISTS(SELECT 1 FROM plt_dynamic_form_template WHERE id=992209220342 OR (tenant_id=1 AND template_code='PROJECT_PREPARATION_CONFIRMATION'));
INSERT INTO plt_dynamic_form_template_revision
(id,template_id,revision_no,status_code,draft_marker,source_revision_id,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,published_by,published_at,version,creator,updater,deleted,tenant_id)
SELECT 992209220343,992209220342,1,'PUBLISHED',NULL,NULL,
'{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}',
'[{"type":"radio","field":"materialEnvironmentFit","title":"物料是否适配环境","options":[{"label":"是","value":"YES"},{"label":"否","value":"NO"}],"validate":[{"required":true,"message":"请确认物料适配情况"}]},{"type":"radio","field":"manufacturerProvidesAccessories","title":"原厂是否提供辅料","options":[{"label":"是","value":"YES"},{"label":"否","value":"NO"}],"validate":[{"required":true,"message":"请确认辅料责任"}]},{"type":"radio","field":"bomRequisitionAvailable","title":"是否有 BOM 申领","options":[{"label":"是","value":"YES"},{"label":"否","value":"NO"}],"validate":[{"required":true,"message":"请确认 BOM 申领情况"}]}]',
'FORM_CREATE_ELEMENT_PLUS','3.4.0','3.2.38',1,NOW(),1,'seed_preparation_confirmation','seed_preparation_confirmation',b'0',1
WHERE NOT EXISTS(SELECT 1 FROM plt_dynamic_form_template_revision WHERE id=992209220343);
UPDATE plt_dynamic_form_template SET current_published_revision_id=992209220343
WHERE id=992209220342 AND current_published_revision_id IS NULL AND creator='seed_preparation_confirmation';

INSERT INTO infra_config(id,category,type,name,config_key,value,visible,remark,creator,updater,deleted)
SELECT 992209220344,'pms',2,'工前确认表模板','pms.project-preparation.form-template-id','992209220342',b'0',
'已发布动态表单模板；规则字段自动读取其元数据。','seed_preparation_confirmation','seed_preparation_confirmation',b'0'
WHERE NOT EXISTS(SELECT 1 FROM infra_config WHERE config_key='pms.project-preparation.form-template-id' AND deleted=b'0');
INSERT INTO infra_config(id,category,type,name,config_key,value,visible,remark,creator,updater,deleted)
SELECT 992209220345,'pms',2,'工前独立支线条件','pms.project-preparation.branches',
'[{"title":"CRM 改单（独立流程）","when":{"materialEnvironmentFit":"NO"}},{"title":"物料申领（独立流程）","when":{"manufacturerProvidesAccessories":"YES","bomRequisitionAvailable":"YES"}},{"title":"外采事前申请（独立流程）","when":{"manufacturerProvidesAccessories":"YES","bomRequisitionAvailable":"NO"}}]',
b'0','按原图：物料不适配触发 CRM；原厂提供辅料时按 BOM 判断申领或外采。不设统一回流。','seed_preparation_confirmation','seed_preparation_confirmation',b'0'
WHERE NOT EXISTS(SELECT 1 FROM infra_config WHERE config_key='pms.project-preparation.branches' AND deleted=b'0');
