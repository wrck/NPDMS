-- Preserve retired reference rows while allowing a later plan to reference the same code again.
ALTER TABLE proj_project_gate_reference
    ADD COLUMN active_ref_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN ref_code ELSE NULL END) VIRTUAL,
    DROP INDEX uk_proj_gate_ref,
    ADD UNIQUE KEY uk_proj_gate_ref (tenant_id,gate_id,ref_type,active_ref_code);
