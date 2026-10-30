CREATE TABLE device_ops_parser_log_type (
    log_type VARCHAR(200) PRIMARY KEY,
    display_name VARCHAR(500) NOT NULL,
    description CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE device_ops_parser_release (
    release_id VARCHAR(100) PRIMARY KEY,
    log_type VARCHAR(200) NOT NULL,
    release_version VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL,
    coordinate_json CLOB NOT NULL,
    manifest_json CLOB NOT NULL,
    rules_json CLOB NOT NULL,
    projections_json CLOB NOT NULL,
    verification_cases_json CLOB NOT NULL,
    draft_revision BIGINT NOT NULL,
    validation_report_json CLOB,
    validation_revision BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_device_ops_parser_release UNIQUE (log_type, release_version),
    CONSTRAINT fk_device_ops_parser_release_log_type FOREIGN KEY (log_type)
        REFERENCES device_ops_parser_log_type(log_type)
);

CREATE TABLE device_ops_parser_active_release (
    log_type VARCHAR(200) PRIMARY KEY,
    release_id VARCHAR(100) NOT NULL UNIQUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (log_type) REFERENCES device_ops_parser_log_type(log_type),
    FOREIGN KEY (release_id) REFERENCES device_ops_parser_release(release_id)
);
