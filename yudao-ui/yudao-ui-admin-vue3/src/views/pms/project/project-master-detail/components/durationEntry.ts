import type { ProjectMasterVO } from '@/api/pms/project/projects'

// Use the server's assignment fact; a displayed stage label does not describe parallel active stages.
export const initialDurationHint = (project: ProjectMasterVO) =>
  project.projectManagerAssigned === false
    ? '项目尚未指派项目经理，请先完成项目经理指派，再由有效项目经理录入工期。'
    : ''
