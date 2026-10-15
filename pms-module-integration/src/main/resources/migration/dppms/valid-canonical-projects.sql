SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (/* @include valid-projects */) scoped
WHERE scoped.projectId=scoped.canonicalProjectId
