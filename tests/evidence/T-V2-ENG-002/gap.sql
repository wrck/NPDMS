-- 找出"项目下关联数据<3条"的缺口
-- 1. 每个项目下的关键关联实体统计
SELECT
  p.id AS project_id,
  p.code AS project_code,
  p.name AS project_name,
  p.status AS project_status,
  (SELECT COUNT(*) FROM pms_project_phase ph WHERE ph.project_id = p.id) AS phase_cnt,
  (SELECT COUNT(*) FROM pms_project_task tk WHERE tk.project_id = p.id) AS task_cnt,
  (SELECT COUNT(*) FROM pms_project_team_member tm WHERE tm.project_id = p.id) AS team_cnt,
  (SELECT COUNT(*) FROM pms_project_risk rk WHERE rk.project_id = p.id) AS risk_cnt,
  (SELECT COUNT(*) FROM pms_eng_briefing br WHERE br.project_id = p.id) AS briefing_cnt,
  (SELECT COUNT(*) FROM pms_eng_form_instance fi WHERE fi.project_id = p.id) AS form_instance_cnt,
  (SELECT COUNT(*) FROM pms_eng_outsource_request os WHERE os.project_id = p.id) AS outsource_cnt,
  (SELECT COUNT(*) FROM pms_eng_material_requisition mr WHERE mr.project_id = p.id) AS material_cnt,
  (SELECT COUNT(*) FROM pms_cut_plan cp WHERE cp.project_id = p.id) AS cut_cnt,
  (SELECT COUNT(*) FROM pms_srv_task st WHERE st.project_id = p.id) AS srv_cnt,
  (SELECT COUNT(*) FROM pms_equipment eq WHERE eq.project_id = p.id) AS equip_cnt,
  (SELECT COUNT(*) FROM pms_acc_acceptance ac WHERE ac.project_id = p.id) AS acc_cnt
FROM pms_project p
ORDER BY p.id;
