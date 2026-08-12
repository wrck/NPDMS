-- 检查关联表 project_id 实际值
SELECT 'phase' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_project_phase;
SELECT 'task' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_project_task;
SELECT 'team' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_project_team_member;
SELECT 'risk' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_project_risk;
SELECT 'brief' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_eng_briefing;
SELECT 'form' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_eng_form_instance;
SELECT 'outs' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_eng_outsource_request;
SELECT 'matl' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_eng_material_requisition;
SELECT 'cut' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_cut_task;
SELECT 'srv' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_srv_task;
SELECT 'equip' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_equipment;
SELECT 'acc' AS tbl, COUNT(*) AS total, COUNT(DISTINCT project_id) AS distinct_projects FROM pms_acc_acceptance;

-- 项目ID范围
SELECT MIN(id), MAX(id) FROM pms_project;
SELECT id FROM pms_project ORDER BY id LIMIT 15;
