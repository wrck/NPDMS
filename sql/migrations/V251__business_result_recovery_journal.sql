-- Infrastructure ordering only; native Owner result IDs and revisions are not replaced or backfilled.
-- NO PAD binary collation keeps native string IDs exact, including trailing spaces.
-- A writer retains the channel lock until its original business transaction commits or rolls back.
CREATE TABLE proj_business_result_channel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    owner_context VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    entity_type VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    result_type VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    committed_sequence BIGINT NOT NULL DEFAULT 0,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_channel (tenant_id,project_id,owner_context,entity_type,result_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE proj_business_result_change (
    tenant_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    source_event_id VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    notification_id VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    object_id VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    result_id VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    formation_marker TINYINT NULL,
    payload LONGTEXT NOT NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (channel_id,sequence_no),
    UNIQUE KEY uk_result_source_event (channel_id,source_event_id),
    UNIQUE KEY uk_result_notification (tenant_id,notification_id),
    UNIQUE KEY uk_result_first_formation (channel_id,object_id,result_id,formation_marker),
    CONSTRAINT ck_result_formation_marker CHECK (formation_marker IS NULL OR formation_marker=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
