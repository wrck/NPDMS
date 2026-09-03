-- F-CUT-002/F-CUT-003：冻结F-AST-002公开产品类型事实的最小CUT快照。
-- 既有任务保持NULL；禁止从产品编码、型号、CONP或默认值补造。

ALTER TABLE `cut_task_device_scope`
  ADD COLUMN `device_type_code_snapshot` varchar(64) DEFAULT NULL AFTER `project_assignment_version`,
  ADD COLUMN `device_type_source_version_snapshot` varchar(128) DEFAULT NULL AFTER `device_type_code_snapshot`,
  ADD CONSTRAINT `chk_cut_task_device_type_snapshot` CHECK (
    (`device_type_code_snapshot` IS NULL AND `device_type_source_version_snapshot` IS NULL)
    OR
    (`device_type_code_snapshot` IS NOT NULL
      AND `device_type_source_version_snapshot` IS NOT NULL
      AND CHAR_LENGTH(`device_type_code_snapshot`) = CHAR_LENGTH(TRIM(`device_type_code_snapshot`))
      AND CHAR_LENGTH(`device_type_code_snapshot`) BETWEEN 1 AND 64
      AND CHAR_LENGTH(`device_type_source_version_snapshot`) = CHAR_LENGTH(TRIM(`device_type_source_version_snapshot`))
      AND CHAR_LENGTH(`device_type_source_version_snapshot`) BETWEEN 1 AND 128)
  );
