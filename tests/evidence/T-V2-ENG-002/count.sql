SELECT 'pms_eng_briefing' AS tbl, COUNT(*) AS cnt FROM pms_eng_briefing
UNION ALL
SELECT 'pms_eng_form_template', COUNT(*) FROM pms_eng_form_template
UNION ALL
SELECT 'pms_eng_form_instance', COUNT(*) FROM pms_eng_form_instance;
