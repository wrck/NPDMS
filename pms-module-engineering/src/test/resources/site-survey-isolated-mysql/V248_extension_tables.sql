CREATE TABLE plt_entity_extension_definition (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    revision_no INT NOT NULL,
    source_form_revision_id BIGINT NULL,
    fields_json JSON NOT NULL,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_extension_definition (tenant_id, owner_module, entity_type, revision_no),
    UNIQUE KEY uk_entity_definition_source (tenant_id, owner_module, entity_type, source_form_revision_id)
);

CREATE TABLE plt_entity_extension_value (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=current; positive=Owner history record ID',
    definition_revision_id BIGINT NOT NULL,
    values_json JSON NOT NULL,
    version INT NOT NULL DEFAULT 1,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_extension_target (tenant_id, owner_module, entity_type, entity_id, revision_id),
    CONSTRAINT fk_extension_definition FOREIGN KEY (definition_revision_id) REFERENCES plt_entity_extension_definition(id)
);

CREATE TABLE plt_entity_form_binding (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=current; positive=Owner history record ID',
    form_revision_id BIGINT NOT NULL,
    extension_definition_revision_id BIGINT NULL,
    field_bindings_json JSON NOT NULL COMMENT 'Control field key to stable business/extension field code',
    version INT NOT NULL DEFAULT 1,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_entity_form_target (tenant_id, owner_module, entity_type, entity_id, revision_id)
);
