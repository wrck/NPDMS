-- P19 / pm_project_related_party | 原任务 2109968219214897153 v5
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT r.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_related_party r
JOIN pm_project p ON p.projectId=r.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(r.projectId,:projectIds));
