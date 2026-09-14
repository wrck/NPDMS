ALTER TABLE int_sync_run
    ADD COLUMN page_number int NULL COMMENT '自动分页子批次序号；主运行为空',
    ADD COLUMN paging_json json NULL COMMENT '已提交分页游标与固定来源主键上界';
