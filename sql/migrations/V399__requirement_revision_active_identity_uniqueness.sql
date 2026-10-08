-- Requirement analysis keeps immutable revision numbers when an un-frozen draft is logically discarded.
-- Active rows retain the same unique project/revision/draft constraints; deleted rows release active slots.
-- No revision body, source ID, frozen/effective state or historical number is rewritten.
ALTER TABLE sol_requirement_analysis_revision
    ADD COLUMN active_revision_no INT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN revision_no ELSE NULL END) STORED,
    ADD COLUMN active_draft_marker TINYINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN draft_marker ELSE NULL END) STORED,
    DROP INDEX uk_ra_revision,
    DROP INDEX uk_ra_project_revision,
    DROP INDEX uk_ra_draft,
    ADD UNIQUE KEY uk_ra_revision (tenant_id,entity_id,active_revision_no),
    ADD UNIQUE KEY uk_ra_project_revision (tenant_id,project_id,active_revision_no),
    ADD UNIQUE KEY uk_ra_draft (tenant_id,project_id,active_draft_marker);
