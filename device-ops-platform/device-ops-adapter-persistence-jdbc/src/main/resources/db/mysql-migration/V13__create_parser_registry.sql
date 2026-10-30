CREATE TABLE device_ops_parser_log_type (
    log_type VARCHAR(200) PRIMARY KEY,
    display_name VARCHAR(500) NOT NULL,
    description LONGTEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE device_ops_parser_release (
    release_id VARCHAR(100) PRIMARY KEY,
    log_type VARCHAR(200) NOT NULL,
    release_version VARCHAR(100) NOT NULL,
    state VARCHAR(20) NOT NULL,
    coordinate_json LONGTEXT NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    rules_json LONGTEXT NOT NULL,
    projections_json LONGTEXT NOT NULL,
    verification_cases_json LONGTEXT NOT NULL,
    draft_revision BIGINT NOT NULL,
    validation_report_json LONGTEXT,
    validation_revision BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    CONSTRAINT uk_device_ops_parser_release UNIQUE (log_type, release_version),
    CONSTRAINT fk_device_ops_parser_release_log_type FOREIGN KEY (log_type)
        REFERENCES device_ops_parser_log_type(log_type)
) ENGINE=InnoDB;

CREATE TABLE device_ops_parser_active_release (
    log_type VARCHAR(200) PRIMARY KEY,
    release_id VARCHAR(100) NOT NULL UNIQUE,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (log_type) REFERENCES device_ops_parser_log_type(log_type),
    FOREIGN KEY (release_id) REFERENCES device_ops_parser_release(release_id)
) ENGINE=InnoDB;
