CREATE TABLE device_ops_parser_worker (
    worker_id VARCHAR(200) PRIMARY KEY,
    last_heartbeat_at TIMESTAMP(6) NOT NULL,
    heartbeat_expires_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE device_ops_parser_worker_capability (
    worker_id VARCHAR(200) NOT NULL,
    engine_version VARCHAR(100) NOT NULL,
    extension_id VARCHAR(200) NOT NULL DEFAULT '',
    extension_version VARCHAR(100) NOT NULL DEFAULT '',
    PRIMARY KEY(worker_id, engine_version, extension_id, extension_version),
    FOREIGN KEY (worker_id) REFERENCES device_ops_parser_worker(worker_id)
) ENGINE=InnoDB;

CREATE INDEX idx_device_ops_parser_worker_expiry
    ON device_ops_parser_worker(heartbeat_expires_at);
