-- User-adjudicated SYSTEM_USERS additions only. Other legacy user fields remain pending.
-- Existing business fields and authorization tables retain their definitions.
ALTER TABLE system_users
    ADD COLUMN employee_no VARCHAR(50) NULL COMMENT '真实员工工号',
    ADD COLUMN custom_info JSON NULL COMMENT '账号扩展业务属性';
