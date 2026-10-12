-- Restore pre-existing field comments omitted by V437; no business values or receipts are rewritten.
ALTER TABLE com_project_contract_relation
    MODIFY COLUMN creator VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN updater VARCHAR(64) NULL COMMENT '更新人';
