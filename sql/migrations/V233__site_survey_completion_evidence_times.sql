-- Preserve old records without inventing historical completion evidence.
-- New formal Owner transitions record their time for execution-round validation.
ALTER TABLE pms_eng_site_survey
    ADD COLUMN confirmed_at DATETIME(3) NULL COMMENT '正式确认时间；历史未知不回填',
    ADD COLUMN archived_at DATETIME(3) NULL COMMENT '正式归档时间；历史未知不回填';
