-- 项目总数与状态分布
SELECT 'pms_project' AS tbl, COUNT(*) AS cnt FROM pms_project;
SELECT status, COUNT(*) AS cnt FROM pms_project GROUP BY status ORDER BY status;

-- 各关联实体数据量与项目维度分布
SELECT 'pms_project_member' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_member;
SELECT 'pms_project_milestone' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_milestone;
SELECT 'pms_project_task' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_task;
SELECT 'pms_project_risk' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_risk;
SELECT 'pms_project_change' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_change;
SELECT 'pms_project_governance' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_project_governance;
SELECT 'pms_eng_briefing' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_briefing;
SELECT 'pms_eng_form_instance' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_form_instance;
SELECT 'pms_eng_outsource_request' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_outsource_request;
SELECT 'pms_eng_material_request' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_material_request;
SELECT 'pms_eng_purchase_request' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_purchase_request;
SELECT 'pms_eng_exchange_request' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_eng_exchange_request;
SELECT 'pms_cutover_plan' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_cutover_plan;
SELECT 'pms_service_acceptance' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_service_acceptance;
SELECT 'pms_asset_equipment' AS tbl, COUNT(*) AS cnt, COUNT(DISTINCT project_id) AS projects FROM pms_asset_equipment;
