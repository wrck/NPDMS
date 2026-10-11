-- Approved joint workflow: saved configuration, frozen print layouts; no historical backfill.
ALTER TABLE acc_completion_certificate
    ADD COLUMN satisfaction_template_id BIGINT NULL,
    ADD COLUMN completion_print_template_id BIGINT NULL,
    ADD COLUMN completion_print_revision_id BIGINT NULL,
    ADD COLUMN completion_print_snapshot LONGTEXT NULL,
    ADD COLUMN satisfaction_print_template_id BIGINT NULL,
    ADD COLUMN satisfaction_print_revision_id BIGINT NULL,
    ADD COLUMN satisfaction_print_snapshot LONGTEXT NULL;

ALTER TABLE acc_completion_satisfaction_signing ADD COLUMN signature_image_data_url LONGTEXT NULL;
