-- One outsourcing shortcut per original site-survey entity; existing history is unchanged.
ALTER TABLE pms_eng_site_survey
    ADD COLUMN outsource_required BIT(1) NOT NULL DEFAULT b'0' COMMENT '整单是否需要转包',
    ADD COLUMN outsource_request_id BIGINT NULL COMMENT '关联转包申请ID，不代表审批完成';

INSERT INTO system_dict_data
(id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT 993109090004, 4, '现场工勘', 'SITE_SURVEY', 'pms_trigger_source', 0, 'info', '',
       '现场工勘整单转包快捷入口', 'site-survey-restoration', NOW(), 'site-survey-restoration', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'pms_trigger_source' AND value = 'SITE_SURVEY' AND deleted = b'0');
