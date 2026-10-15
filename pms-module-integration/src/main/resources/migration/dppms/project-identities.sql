SELECT p.projectId AS originalProjectId,p.projectType,p.projectCode,p.canonicalProjectId
FROM (/* @include valid-projects */) p
