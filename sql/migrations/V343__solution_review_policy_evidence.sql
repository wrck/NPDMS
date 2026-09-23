CREATE TABLE sol_solution_review_policy (
    id bigint NOT NULL AUTO_INCREMENT,
    tenant_id bigint NOT NULL,
    project_id bigint NOT NULL,
    solution_id bigint NOT NULL,
    source_version int NOT NULL,
    review_level int NOT NULL,
    evidence_json json NOT NULL,
    creator varchar(64) DEFAULT '',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '',
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sol_review_policy (tenant_id, solution_id),
    CONSTRAINT ck_sol_review_policy_level CHECK (review_level IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案提交时的审核规则判定快照';
