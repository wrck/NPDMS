-- 同一来源版本可用于多个要求；旧材料ID和提交快照保持不变。
-- requirement_id 保留为历史来源锚，新读写使用独立履行关系。
ALTER TABLE plt_delivery_material
    ADD COLUMN source_identity_key CHAR(64) NULL COMMENT '新登记来源版本身份SHA256（旧行保持NULL）',
    ADD UNIQUE KEY uk_plt_delivery_material_source (tenant_id, source_identity_key);

CREATE TABLE plt_delivery_fulfillment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requirement_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NULL DEFAULT '',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater VARCHAR(64) NULL DEFAULT '',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plt_delivery_fulfillment (tenant_id, requirement_id, material_id),
    KEY idx_plt_delivery_fulfillment_material (tenant_id, material_id)
) ENGINE=InnoDB COMMENT '交付材料与要求的显式履行关系';

INSERT INTO plt_delivery_fulfillment
    (requirement_id, material_id, status, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT m.requirement_id, m.id, m.status, m.tenant_id, 'delivery-fulfillment-migration',
       m.create_time, 'delivery-fulfillment-migration', m.update_time, b'0'
FROM plt_delivery_material m
JOIN plt_delivery_requirement r ON r.id=m.requirement_id AND r.tenant_id=m.tenant_id AND r.deleted=b'0'
WHERE m.requirement_id IS NOT NULL AND m.deleted=b'0';
