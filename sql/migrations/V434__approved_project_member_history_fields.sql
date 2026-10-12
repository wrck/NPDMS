-- 2026-10-11 whole-table adjudication: 29 existing columns plus these five.
-- Closed-project import uses the dedicated Owner; ordinary assignment rules remain.
ALTER TABLE proj_project_member_assignment
    ADD COLUMN member_code VARCHAR(45) NULL COMMENT '成员区间人员编码',
    ADD COLUMN phone_number VARCHAR(20) NULL COMMENT '成员区间联系电话',
    ADD COLUMN email VARCHAR(45) NULL COMMENT '成员区间邮箱',
    ADD COLUMN registration_source VARCHAR(2) NULL COMMENT '登记来源：1项目信息，2手工成员',
    ADD COLUMN authorization_confirmed BIT NOT NULL DEFAULT b'1' COMMENT '是否经Owner确认参与当前授权',
    MODIFY COLUMN user_id BIGINT NULL COMMENT '正式用户；未关联历史可空',
    MODIFY COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，未确认历史可保留原NULL',
    MODIFY COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，未确认历史可保留原NULL',
    ADD CONSTRAINT ck_proj_member_confirmed_identity_audit
        CHECK (authorization_confirmed=b'0' OR (user_id IS NOT NULL AND create_time IS NOT NULL AND update_time IS NOT NULL));
