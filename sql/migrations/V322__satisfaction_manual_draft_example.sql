-- ACC-02 / F-ACC-002: 可编辑的问卷草稿示例，不自动发布、不改变任何项目或历史调查。
-- 示例阈值不是统一业务规则，管理人员须确认并通过正式发布命令启用。
INSERT INTO acc_satisfaction_questionnaire_template
    (id,tenant_id,template_code,name,status,current_revision_id,version,creator,updater,deleted)
SELECT 992209210001,1,'SAT_MANUAL_EXAMPLE','满意度问卷示例（请按业务确认后发布）','DRAFT',NULL,0,'facc002_manual_example','facc002_manual_example',b'0'
WHERE EXISTS (SELECT 1 FROM system_tenant WHERE id=1 AND deleted=b'0')
  AND NOT EXISTS (SELECT 1 FROM acc_satisfaction_questionnaire_template WHERE tenant_id=1 AND template_code='SAT_MANUAL_EXAMPLE');

INSERT INTO acc_satisfaction_questionnaire_template_revision
    (id,tenant_id,template_id,revision_no,project_type,signing_mode,implementation_mode,business_purpose_code,
     applicable_timing_code,priority,frozen_question_json,frozen_threshold,rule_version,revision_status,version,creator,updater,deleted)
SELECT 992209210002,1,992209210001,1,'*','*','*','ACCEPTANCE','MANUAL',100,
    '{"schemaVersion":1,"questions":[{"code":"overall","title":"您对本次项目交付的总体评价","type":"RATING","required":true,"options":[{"code":"excellent","label":"非常满意","score":100},{"code":"good","label":"满意","score":80},{"code":"fair","label":"一般","score":60},{"code":"poor","label":"不满意","score":0}]},{"code":"suggestion","title":"改进建议","type":"TEXT","required":false,"minLength":0,"maxLength":1000}],"scoring":{"ruleVersion":"SAT-EXAMPLE-V1","strategy":"SUM_V1","scoreMin":0,"scoreMax":100,"precision":2,"roundingMode":"HALF_UP","threshold":80}}',
    80,'SAT-EXAMPLE-V1','DRAFT',0,'facc002_manual_example','facc002_manual_example',b'0'
WHERE EXISTS (SELECT 1 FROM acc_satisfaction_questionnaire_template WHERE id=992209210001 AND tenant_id=1 AND template_code='SAT_MANUAL_EXAMPLE')
  AND NOT EXISTS (SELECT 1 FROM acc_satisfaction_questionnaire_template_revision WHERE template_id=992209210001 AND revision_no=1 AND tenant_id=1);
