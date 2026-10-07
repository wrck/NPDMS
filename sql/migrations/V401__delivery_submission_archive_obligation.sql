-- Archive targets are identified by the existing source projection submission.
-- Shared material identity and immutable submission payloads remain unchanged.
ALTER TABLE plt_delivery_submission
 ADD COLUMN archive_status VARCHAR(32) NULL,
 ADD COLUMN archive_failure_code VARCHAR(128) NULL,
 ADD COLUMN archive_retry_count INT NOT NULL DEFAULT 0,
 ADD KEY idx_delivery_submission_archive (tenant_id, archive_status, id);
-- A material archived for one target does not prove another target archived.
-- Consumers replay each native target's existing idempotency receipt before marking completion.
UPDATE plt_delivery_submission s
SET s.archive_status = CASE WHEN s.status='WITHDRAWN' THEN 'INVALID' ELSE 'PENDING_COMPENSATION' END
WHERE s.deleted=b'0' AND s.source_type='AUTO_PROJECTION'
 AND (s.request_key LIKE 'report:%' OR s.request_key LIKE 'satisfaction-result:%');
