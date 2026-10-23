-- CUT-02: customer service level is a reference dimension, not a computed submission gate.
-- Keep human-answer completeness and the source-specific EXE-06 requirement.
ALTER TABLE `cut_assessment`
  DROP CHECK `chk_cut_assessment_status_union`,
  ADD CONSTRAINT `chk_cut_assessment_status_union` CHECK (
    COALESCE((`assessment_status` = 'DRAFT'
      AND `submitted_by` IS NULL AND `submitted_at` IS NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `simple_flow` = b'0'), FALSE)
    OR
    COALESCE((`assessment_status` = 'SUBMITTED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `current_marker` = 1
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND ((JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
            AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0)
        OR (JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.intakeSourceType')) IN ('SELF_CREATED','ITR')
            AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'NOT_APPLICABLE'
            AND COALESCE(JSON_TYPE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.snapshotId')), 'NULL') = 'NULL'
            AND JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.snapshotVersion') = 0
            AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0))), FALSE)
    OR
    COALESCE((`assessment_status` = 'INVALIDATED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` > 0 AND `invalidated_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`invalidation_reason`)) BETWEEN 1 AND 1000
      AND `current_marker` IS NULL
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND ((JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
            AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0)
        OR (JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.intakeSourceType')) IN ('SELF_CREATED','ITR')
            AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'NOT_APPLICABLE'
            AND COALESCE(JSON_TYPE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.snapshotId')), 'NULL') = 'NULL'
            AND JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.snapshotVersion') = 0
            AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0))), FALSE)
  );
