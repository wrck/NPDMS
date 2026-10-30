-- Approved 2026-10-29: report-input business validation belongs to SRV services.
-- UMC keeps every authentic result (including unsupported/UNJUDGED and ERROR) and valid zero-rule reports.
-- InspectionReportService.create and InspectionUmcWorkflow.adopt validate report GENERATING state,
-- tenant/task/attempt/source, canonical result membership and duplicate result identities before insertion.
-- Retire only this obsolete insert gate. Do not change rows, foreign keys, CHECKs or immutable-history guards.
-- This forward migration does not authorize execution against the protected 178 database.
DROP TRIGGER IF EXISTS trg_ins_report_result_bi;
