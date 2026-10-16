-- Historical responses are filled only by reading the original authorized DAC task.
ALTER TABLE plt_device_log_parse
 ADD COLUMN result_snapshot_json LONGTEXT NULL COMMENT 'Original successful sanitized parser response, write once';
