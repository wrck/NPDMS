ALTER TABLE proj_project_task
    ADD COLUMN required_task BIT(1) NOT NULL DEFAULT b'1' COMMENT '任务必选标志，旧任务保持必选';
