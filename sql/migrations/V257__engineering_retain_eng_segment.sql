-- V257: engineering 表命名规则修正返工（需求方2026-09-17指示）
-- 规则：原 pms_eng_{实体} 表，pms_ 段替换为对应领域前缀，保留 eng_ 段，实体名称不变。
-- 例：pms_eng_site_survey -> sol_eng_site_survey；不采用 09 目标表名（那是未来新模型的表，不与当前承载混用）。
-- schedule_backward 原名 pms_schedule_backward（非 pms_eng 开头），保持 sol_schedule_backward 不变。

RENAME TABLE
  -- SOL 域
  `sol_site_survey` TO `sol_eng_site_survey`,
  `sol_requirement` TO `sol_eng_requirement`,
  `sol_solution_source` TO `sol_eng_solution_source`,
  `sol_solution` TO `sol_eng_solution`,
  `sol_briefing` TO `sol_eng_briefing`,
  `sol_resource_ready` TO `sol_eng_resource_ready`,
  -- PLT 域
  `plt_form_template` TO `plt_eng_form_template`,
  `plt_form_instance` TO `plt_eng_form_instance`,
  `plt_authorization` TO `plt_eng_authorization`,
  -- IMP 域
  `imp_arrival` TO `imp_eng_arrival`,
  `imp_installation_record` TO `imp_eng_installation`,
  `imp_configuration_collection_result` TO `imp_eng_configuration`,
  `imp_joint_debugging_result` TO `imp_eng_joint_test`,
  `imp_issue` TO `imp_eng_issue`,
  `imp_deliverable` TO `imp_eng_deliverable`,
  `imp_material_requisition` TO `imp_eng_material_requisition`,
  `imp_material_exchange` TO `imp_eng_material_exchange`,
  `imp_external_procurement` TO `imp_eng_external_procurement`,
  `imp_doc_template` TO `imp_eng_doc_template`,
  `imp_doc_template_version` TO `imp_eng_doc_template_version`,
  -- KNO 域
  `kno_announcement` TO `kno_eng_announcement`,
  `kno_announcement_check` TO `kno_eng_announcement_check`,
  -- RES 域
  `res_outsource_request` TO `res_eng_outsource_request`,
  -- IMP 域（V255 改名返工）
  `imp_risk` TO `imp_eng_risk`;
