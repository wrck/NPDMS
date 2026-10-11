-- AST technical lock: serializes graph changes within a tenant, including initially empty trees.
CREATE TABLE IF NOT EXISTS ast_device_assembly_graph_lock (
 tenant_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
