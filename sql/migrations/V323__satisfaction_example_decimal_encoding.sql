-- 修正V322草稿示例的十进制编码；受控问卷Schema要求分值使用十进制字符串。
-- 只更新未发布、未修订的本示例，不触碰发布版本或客户答卷。
UPDATE acc_satisfaction_questionnaire_template_revision
SET frozen_question_json=JSON_SET(frozen_question_json,
    '$.questions[0].options[0].score','100.00',
    '$.questions[0].options[1].score','80.00',
    '$.questions[0].options[2].score','60.00',
    '$.questions[0].options[3].score','0.00',
    '$.scoring.scoreMin','0.00','$.scoring.scoreMax','100.00','$.scoring.threshold','80.00'),
    updater='facc002_manual_example', update_time=NOW(3)
WHERE id=992209210002 AND template_id=992209210001 AND tenant_id=1
  AND revision_status='DRAFT' AND version=0 AND deleted=b'0'
  AND creator='facc002_manual_example'
  AND JSON_TYPE(JSON_EXTRACT(frozen_question_json,'$.scoring.scoreMin'))='INTEGER';
