CREATE TABLE device_ops_collection_parser_request (
    task_id VARCHAR(100) PRIMARY KEY,
    log_type VARCHAR(200) NOT NULL,
    release_id VARCHAR(100) NOT NULL,
    input_format VARCHAR(100) NOT NULL,
    result_consumer_id VARCHAR(200),
    result_destination VARCHAR(2000),
    CONSTRAINT fk_device_ops_collection_parser_collection
        FOREIGN KEY (task_id) REFERENCES device_ops_collection(task_id),
    CONSTRAINT fk_device_ops_collection_parser_release
        FOREIGN KEY (release_id) REFERENCES device_ops_parser_release(release_id)
);
