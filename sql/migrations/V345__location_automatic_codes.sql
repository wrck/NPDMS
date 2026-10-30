ALTER TABLE ast_site MODIFY COLUMN code varchar(96) NOT NULL;
ALTER TABLE ast_site_location MODIFY COLUMN code varchar(128) NOT NULL;

CREATE TABLE ast_location_code_sequence (
    tenant_id bigint NOT NULL,
    code_namespace varchar(128) NOT NULL,
    next_value bigint NOT NULL DEFAULT 1,
    PRIMARY KEY (tenant_id, code_namespace)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
