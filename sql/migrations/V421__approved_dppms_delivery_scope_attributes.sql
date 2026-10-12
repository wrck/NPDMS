-- Three columns explicitly adjudicated by the user. Existing allocations remain confirmed.
-- Historical project scopes retain mandatory office, quantity, state, version and effective date.
ALTER TABLE com_delivery_scope
    ADD COLUMN allocation_confirmed BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否确认占用订单分配数量',
    ADD COLUMN delivered_quantity DECIMAL(18,6) NULL COMMENT '项目范围原已发货数量',
    ADD COLUMN open_quantity DECIMAL(18,6) NULL COMMENT '项目范围原订单未交数量',
    MODIFY COLUMN current_order_line_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN allocation_confirmed=b'1' AND deleted=0 AND effective_to IS NULL
            THEN order_line_id ELSE NULL END) STORED,
    DROP INDEX uk_scope_version,
    ADD UNIQUE KEY uk_scope_version
        (tenant_id, (CASE WHEN allocation_confirmed=b'1' THEN order_line_id ELSE NULL END),
         project_id, allocation_version);
