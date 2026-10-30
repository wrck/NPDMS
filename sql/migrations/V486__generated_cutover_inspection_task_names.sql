-- 新建任务名称由项目名及实际生成单号组成；不重命名既有任务或改写不可变历史。
-- 最大长度：项目名255 + 两个分隔符2 + 业务名称2 + 单号23 = 282。
ALTER TABLE cut_task MODIFY COLUMN task_name VARCHAR(282) NOT NULL;
ALTER TABLE ast_device_cutover_result MODIFY COLUMN task_name VARCHAR(282) NULL DEFAULT NULL
    COMMENT '割接任务名称；历史原件缺失时可空，新业务按既有来源/提交规则必填';
ALTER TABLE srv_inspection_task MODIFY COLUMN task_name VARCHAR(282) NOT NULL;
ALTER TABLE cus_inspection_history MODIFY COLUMN task_name VARCHAR(282) NOT NULL;
