-- 检查1001-1010每个项目各关联实体分布，找出<3的缺口
SELECT p.id,
  (SELECT COUNT(*) FROM pms_project_phase t WHERE t.project_id=p.id AND t.deleted=b'0') AS phase,
  (SELECT COUNT(*) FROM pms_project_task t WHERE t.project_id=p.id AND t.deleted=b'0') AS task,
  (SELECT COUNT(*) FROM pms_project_team_member t WHERE t.project_id=p.id AND t.deleted=b'0') AS team,
  (SELECT COUNT(*) FROM pms_project_risk t WHERE t.project_id=p.id AND t.deleted=b'0') AS risk,
  (SELECT COUNT(*) FROM pms_eng_briefing t WHERE t.project_id=p.id AND t.deleted=b'0') AS brief,
  (SELECT COUNT(*) FROM pms_eng_form_instance t WHERE t.project_id=p.id AND t.deleted=b'0') AS form,
  (SELECT COUNT(*) FROM pms_eng_outsource_request t WHERE t.project_id=p.id AND t.deleted=b'0') AS outs,
  (SELECT COUNT(*) FROM pms_eng_material_requisition t WHERE t.project_id=p.id AND t.deleted=b'0') AS matl,
  (SELECT COUNT(*) FROM pms_cut_task t WHERE t.project_id=p.id AND t.deleted=b'0') AS cut,
  (SELECT COUNT(*) FROM pms_srv_task t WHERE t.project_id=p.id AND t.deleted=b'0') AS srv,
  (SELECT COUNT(*) FROM pms_equipment t WHERE t.project_id=p.id AND t.deleted=b'0') AS equip,
  (SELECT COUNT(*) FROM pms_acc_acceptance t WHERE t.project_id=p.id AND t.deleted=b'0') AS acc
FROM pms_project p
WHERE p.id BETWEEN 1001 AND 1010 AND p.deleted=b'0'
ORDER BY p.id;
