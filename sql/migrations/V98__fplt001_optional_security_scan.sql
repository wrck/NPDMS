-- Requirement: PLT-02 / CHG-PRD-2026-08-27-004
-- Preserve immutable historical scan facts while allowing deployments with scanning disabled.

ALTER TABLE `plt_file_version`
    DROP CHECK `chk_plt_file_version_scan`,
    ADD CONSTRAINT `chk_plt_file_version_scan`
        CHECK (`scan_status_code` IN ('PASSED', 'SKIPPED'));
