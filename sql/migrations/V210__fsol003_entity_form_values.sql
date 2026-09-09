-- PRE-04: business values belong to the SOL entity; PLT remains schema/file context.
-- Preserve legacy source values, reference identities, business versions and completed history.
ALTER TABLE sol_preparation
    ADD COLUMN entity_value_json JSON NULL COMMENT 'PRE-04 entity-owned ordinary values';

UPDATE sol_preparation p
JOIN plt_dynamic_form_instance i
  ON i.tenant_id = p.tenant_id AND i.id = p.dynamic_form_instance_id
 AND i.owner_context = 'SOL' AND i.object_type = 'REQUIREMENT_ANALYSIS'
 AND CAST(i.object_id AS BINARY) = CAST(p.id AS BINARY)
SET p.entity_value_json = i.value_json
WHERE p.preparation_type_code = 'PRE_04_REQUIREMENT_ANALYSIS'
  AND p.entity_value_json IS NULL;
