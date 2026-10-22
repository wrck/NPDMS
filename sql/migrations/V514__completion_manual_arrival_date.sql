-- Approved fallback: retain manually entered arrival dates only on completion certificates.
ALTER TABLE acc_completion_certificate ADD COLUMN manual_arrival_date DATE NULL COMMENT '无签收记录时手填到货日期';
