-- 找出每个项目下各关联实体数据不足3条的缺口
-- 先看哪些项目已有 briefing 数据
SELECT project_id, COUNT(*) AS cnt
FROM pms_eng_briefing WHERE deleted=b'0'
GROUP BY project_id ORDER BY project_id;

-- 看 form_instance 每项目分布
SELECT project_id, COUNT(*) AS cnt
FROM pms_eng_form_instance WHERE deleted=b'0'
GROUP BY project_id ORDER BY project_id;

-- 看 outsource 每项目分布
SELECT project_id, COUNT(*) AS cnt
FROM pms_eng_outsource_request WHERE deleted=b'0'
GROUP BY project_id ORDER BY project_id;

-- 看客户数量
SELECT COUNT(*) AS customer_cnt FROM pms_customer WHERE deleted=b'0';
SELECT id, name FROM pms_customer WHERE deleted=b'0' ORDER BY id;
