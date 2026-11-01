CREATE TABLE device_ops_desktop_connection_scope (
    connection_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(200) NOT NULL,
    namespace VARCHAR(100) NOT NULL,
    project_id VARCHAR(200) NOT NULL,
    device_id VARCHAR(200),
    protocol VARCHAR(20) NOT NULL,
    CONSTRAINT fk_desktop_connection_scope FOREIGN KEY (connection_id) REFERENCES device_ops_saved_connection(connection_id)
);
CREATE INDEX idx_desktop_connection_device ON device_ops_desktop_connection_scope(owner_id, namespace, device_id, protocol);
CREATE INDEX idx_desktop_connection_project ON device_ops_desktop_connection_scope(owner_id, namespace, project_id, protocol);
