-- F-SOL-001 / PRE-01：冻结客户延期依据的精确PLT文件事实。
-- 既有无材料记录保持NULL；材料提交时由应用事务写入完整引用和版本事实。

ALTER TABLE `sol_construction_plan_change`
    ADD COLUMN `customer_evidence_reference_key` VARCHAR(128) NULL
        COMMENT '客户依据FileReference稳定槽位键'
        AFTER `customer_evidence_file_version`,
    ADD COLUMN `customer_evidence_artifact_version` INT UNSIGNED NULL
        COMMENT '冻结FileArtifact事实版本'
        AFTER `customer_evidence_reference_key`,
    ADD COLUMN `customer_evidence_reference_version` INT UNSIGNED NULL
        COMMENT '冻结FileReference事实版本'
        AFTER `customer_evidence_artifact_version`,
    ADD COLUMN `customer_evidence_availability_version` INT UNSIGNED NULL
        COMMENT '冻结FileVersion可用性事实版本'
        AFTER `customer_evidence_reference_version`,
    ADD COLUMN `customer_evidence_scope_version` BIGINT NULL
        COMMENT '冻结业务对象授权范围版本'
        AFTER `customer_evidence_availability_version`;
