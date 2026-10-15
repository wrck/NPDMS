-- P16 合并任务的有效项目范围；2026-10-15派生评审SELECT，未执行、未写入运行配置。
-- 依据、排除规则与使用边界见 ../project-scope.md；不是新的独立迁移任务。
-- projectTypes='10'; projectIds='' 仅表示在有效规范项目内不再按ID缩小。
-- 同一冻结来源先取MAX，再判有效；原ID自身也有效才参与来源绑定及项目归属明细。
-- 下游复用此结果；空结果保持零行，不得把空集合回传成全历史查询。
SELECT original.projectId AS originalProjectId,
       original.projectType,
       original.projectCode,
       p.projectId AS canonicalProjectId
FROM pm_project p
JOIN (
    SELECT MAX(projectId) AS projectId
    FROM pm_project
    GROUP BY CAST(projectType AS BINARY), CAST(projectCode AS BINARY)
) canonical ON canonical.projectId = p.projectId
JOIN pm_project original
  ON CAST(original.projectType AS BINARY) <=> CAST(p.projectType AS BINARY)
 AND CAST(original.projectCode AS BINARY) <=> CAST(p.projectCode AS BINARY)
WHERE FIND_IN_SET(p.projectType, :projectTypes)
  AND (:projectIds = '' OR FIND_IN_SET(p.projectId, :projectIds))
  AND p.disabled = 0
  AND p.effectiveTo IS NULL
  AND original.disabled = 0
  AND original.effectiveTo IS NULL
ORDER BY p.projectId, original.projectId;
