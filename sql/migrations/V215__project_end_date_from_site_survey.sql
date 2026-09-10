-- The survey supplies the required finish date; existing planning revisions are not rewritten.
ALTER TABLE proj_project ADD COLUMN project_end_date DATE NULL COMMENT '工勘提供的项目结束日期，工期倒排截止日期';
