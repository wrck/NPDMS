-- Recovery candidate index only; adopted evidence and business history are not overwritten here.
CREATE TABLE proj_result_subscription_candidate (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    object_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    result_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    formation_sequence BIGINT NULL,
    observed_sequence BIGINT NOT NULL,
    observation LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_candidate (tenant_id,subscription_id,object_id,result_id),
    KEY ix_subscription_candidate_page (tenant_id,project_id,subscription_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
