-- 来源分类及原生Owner锚独立于模板要求编码；历史材料内容与主键保持原样。
ALTER TABLE plt_delivery_material
    ADD COLUMN business_type_code VARCHAR(128) NULL COMMENT '稳定业务类型/来源目录编码，不是模板要求编码',
    ADD COLUMN source_owner_module VARCHAR(64) NULL,
    ADD COLUMN source_entity_type VARCHAR(64) NULL,
    ADD COLUMN source_entity_id BIGINT NULL,
    ADD COLUMN source_revision_id BIGINT NULL,
    ADD KEY idx_plt_delivery_native_source (tenant_id, source_owner_module, source_entity_type, source_entity_id);

-- 目录来源已有稳定类型；模板冻结来源不从要求编码猜测分类。
UPDATE plt_delivery_material m
LEFT JOIN plt_delivery_requirement r ON r.id=m.requirement_id AND r.tenant_id=m.tenant_id
SET m.business_type_code=m.type_code,
    m.source_owner_module=m.owner_module, m.source_entity_type=m.entity_type, m.source_entity_id=m.entity_id
WHERE m.requirement_id IS NULL AND m.deleted=b'0';
