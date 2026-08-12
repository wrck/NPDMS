-- 验证V29数据补充效果
SELECT 'customer_service_level' AS tbl, COUNT(*) AS cnt FROM pms_customer_service_level;
SELECT 'project_portfolio' AS tbl, COUNT(*) AS cnt FROM pms_project_portfolio;
SELECT 'project_portfolio_member' AS tbl, COUNT(*) AS cnt FROM pms_project_portfolio_member;
SELECT 'project_portfolio_rule' AS tbl, COUNT(*) AS cnt FROM pms_project_portfolio_rule;
SELECT 'eng_form_template' AS tbl, COUNT(*) AS cnt FROM pms_eng_form_template;
SELECT 'eng_briefing' AS tbl, COUNT(*) AS cnt FROM pms_eng_briefing;
SELECT 'eng_form_instance' AS tbl, COUNT(*) AS cnt FROM pms_eng_form_instance;

-- 项目1011-1015关联数据
SELECT p.id,
  (SELECT COUNT(*) FROM pms_eng_briefing t WHERE t.project_id=p.id AND t.deleted=b'0') AS brief,
  (SELECT COUNT(*) FROM pms_eng_form_instance t WHERE t.project_id=p.id AND t.deleted=b'0') AS form
FROM pms_project p WHERE p.id BETWEEN 1011 AND 1015 AND p.deleted=b'0' ORDER BY p.id;

-- Flyway状态
SELECT version, success FROM flyway_schema_history WHERE version='29';
