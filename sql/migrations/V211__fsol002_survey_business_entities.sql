-- PRE-02 / F-SOL-002: relational survey metadata and item business results.
-- No legacy JSON/state backfill. Identities reuse preparation/item identities.
CREATE TABLE sol_preparation_survey (
    tenant_id BIGINT NOT NULL,
    preparation_id BIGINT NOT NULL,
    survey_date DATE NULL,
    surveyor_user_id BIGINT NULL,
    location VARCHAR(1000) NULL,
    location_resolution_status VARCHAR(32) NULL,
    address_id BIGINT NULL,
    address_version INT NULL,
    site_id BIGINT NULL,
    site_version INT NULL,
    site_location_id BIGINT NULL,
    site_location_version INT NULL,
    address_snapshot JSON NULL COMMENT 'AST address history only',
    location_snapshot JSON NULL COMMENT 'AST location history only',
    grounding VARCHAR(1000) NULL,
    construction_resource VARCHAR(1000) NULL,
    conclusion VARCHAR(1000) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, preparation_id),
    CONSTRAINT fk_sol_survey_preparation FOREIGN KEY (tenant_id, preparation_id)
        REFERENCES sol_preparation (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PRE-02 survey business metadata';

CREATE TABLE sol_preparation_survey_result (
    tenant_id BIGINT NOT NULL,
    preparation_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    power_supply VARCHAR(1000) NULL,
    power_environment VARCHAR(1000) NULL,
    network_port VARCHAR(1000) NULL,
    fiber VARCHAR(1000) NULL,
    cabinet VARCHAR(1000) NULL,
    network_cable VARCHAR(1000) NULL,
    optical_module VARCHAR(1000) NULL,
    cabinet_available BIT(1) NULL,
    network_cable_available BIT(1) NULL,
    optical_module_available BIT(1) NULL,
    original_optical_module BIT(1) NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, item_id),
    KEY idx_sol_survey_result_preparation (tenant_id, preparation_id, item_id),
    CONSTRAINT fk_sol_survey_result_preparation FOREIGN KEY (tenant_id, preparation_id)
        REFERENCES sol_preparation (tenant_id, id),
    CONSTRAINT fk_sol_survey_result_item FOREIGN KEY (tenant_id, item_id)
        REFERENCES sol_preparation_item (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PRE-02 typed survey item results, protected by item/form/root versions';
