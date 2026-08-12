-- 项目下关联实体缺口分析（仅统计有 project_id 的核心业务表）
SELECT
  p.id AS pid,
  p.code,
  p.status,
  (SELECT COUNT(*) FROM pms_project_phase ph WHERE ph.project_id=p.id AND ph.deleted=0) AS phase,
  (SELECT COUNT(*) FROM pms_project_task tk WHERE tk.project_id=p.id AND tk.deleted=0) AS task,
  (SELECT COUNT(*) FROM pms_project_team_member tm WHERE tm.project_id=p.id AND tm.deleted=0) AS team,
  (SELECT COUNT(*) FROM pms_project_risk rk WHERE rk.project_id=p.id AND rk.deleted=0) AS risk,
  (SELECT COUNT(*) FROM pms_eng_briefing br WHERE br.project_id=p.id AND br.deleted=0) AS brief,
  (SELECT COUNT(*) FROM pms_eng_form_instance fi WHERE fi.project_id=p.id AND fi.deleted=0) AS form,
  (SELECT COUNT(*) FROM pms_eng_outsource_request os WHERE os.project_id=p.id AND os.deleted=0) AS outs,
  (SELECT COUNT(*) FROM pms_eng_material_requisition mr WHERE mr.project_id=p.id AND mr.deleted=0) AS matl,
  (SELECT COUNT(*) FROM pms_cut_task ct WHERE ct.project_id=p.id AND ct.deleted=0) AS cut,
  (SELECT COUNT(*) FROM pms_srv_task st WHERE st.project_id=p.id AND st.deleted=0) AS srv,
  (SELECT COUNT(*) FROM pms_equipment eq WHERE eq.project_id=p.id AND eq.deleted=0) AS equip,
  (SELECT COUNT(*) FROM pms_acc_acceptance ac WHERE ac.project_id=p.id AND ac.deleted=0) AS acc
FROM pms_project p
WHERE p.deleted=0
ORDER BY p.id;
