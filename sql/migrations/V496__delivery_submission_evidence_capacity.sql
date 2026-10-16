-- A submission freezes evidence for every submitted material. The previous 1024-character
-- column failed on the fourth normal device log. Retain the complete immutable snapshot.
ALTER TABLE plt_delivery_submission
 MODIFY COLUMN submit_evidence_json LONGTEXT NULL COMMENT '提交证据快照（文件版本与摘要）';
